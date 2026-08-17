/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.Probabilistic;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.lang.Factory;
import opennlp.tools.util.DownloadUtil;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;

/**
 * A sentence detector for splitting up raw text into sentences.
 * <p>
 * A maximum entropy model is used to evaluate end-of-sentence characters in a
 * string to determine if they signify the end of a sentence.
 */
public class SentenceDetectorME implements SentenceDetector, Probabilistic {

  /**
   * Constant indicates a sentence split.
   */
  public static final String SPLIT = "s";

  /**
   * Constant indicates no sentence split.
   */
  public static final String NO_SPLIT = "n";

  /**
   * The maximum entropy model to use to evaluate contexts.
   */
  private final MaxentModel model;

  /**
   * The feature context generator.
   */
  private final SDContextGenerator cgen;

  /**
   * The {@link EndOfSentenceScanner} to use when scanning for end of sentence offsets.
   */
  private final EndOfSentenceScanner scanner;

  /**
   * The list of probabilities associated with each decision.
   */
  private final List<Double> sentProbs = new ArrayList<>();

  /**
   * The abbreviation dictionary index that backs {@link #isAcceptableBreak(CharSequence, int, int)}.
   * It is {@code null} if no abbreviation dictionary is available for the underlying model.
   */
  private final AbbreviationIndex abbIndex;

  protected final boolean useTokenEnd;

  /**
   * Initializes the sentence detector by downloading a default model.
   * @param language The language of the sentence detector.
   * @throws IOException Thrown if the model cannot be downloaded or saved.
   */
  public SentenceDetectorME(String language) throws IOException {
    this(DownloadUtil.downloadModel(language,
            ModelType.SENTENCE_DETECTOR, SentenceModel.class));
  }

  /**
   * Initializes the current instance.
   *
   * @param model the {@link SentenceModel}
   */
  public SentenceDetectorME(SentenceModel model) {
    this(model, model.getAbbreviations());
  }

  /**
   * Instantiates a {@link SentenceDetectorME} with an existing {@link SentenceModel}.
   *
   * @param model The {@link SentenceModel} to be used.
   * @param abbDict The {@link Dictionary} to be used. It must fit the language of the {@code model}.
   *     Its entries are read once, here; later changes to the {@code abbDict} instance do not
   *     affect this detector.
   */
  public SentenceDetectorME(SentenceModel model, Dictionary abbDict) {
    this.model = model.getMaxentModel();
    this.abbIndex = AbbreviationIndex.of(abbDict);
    SentenceDetectorFactory sdFactory = model.getFactory();
    cgen = sdFactory.getSDContextGenerator();
    scanner = sdFactory.getEndOfSentenceScanner();
    useTokenEnd = sdFactory.isUseTokenEnd();
  }

  /**
   * @deprecated Use a {@link SentenceDetectorFactory} to extend
   *             SentenceDetector functionality.
   */
  @Deprecated
  public SentenceDetectorME(SentenceModel model, Factory factory) {
    this.model = model.getMaxentModel();
    // if the model has custom EOS characters set, use this to get the context
    // generator and the EOS scanner; otherwise use language-specific defaults
    char[] customEOSCharacters = model.getEosCharacters();
    if (customEOSCharacters == null) {
      cgen = factory.createSentenceContextGenerator(model.getLanguage(),
          getAbbreviations(model.getAbbreviations()));
      scanner = factory.createEndOfSentenceScanner(model.getLanguage());
    } else {
      cgen = factory.createSentenceContextGenerator(
          getAbbreviations(model.getAbbreviations()), customEOSCharacters);
      scanner = factory.createEndOfSentenceScanner(customEOSCharacters);
    }
    abbIndex = AbbreviationIndex.of(model.getAbbreviations());
    useTokenEnd = model.useTokenEnd();
  }

  private static Set<String> getAbbreviations(Dictionary abbreviations) {
    if (abbreviations == null) {
      return Collections.emptySet();
    }
    return abbreviations.asStringSet();
  }

  /**
   * Detects sentences in given input {@link CharSequence}..
   *
   * @param s  The {@link CharSequence}. to be processed.
   *
   * @return   A string array containing individual sentences as elements.
   */
  @Override
  public String[] sentDetect(CharSequence s) {
    Span[] spans = sentPosDetect(s);
    String[] sentences;
    if (spans.length != 0) {
      sentences = new String[spans.length];
      for (int si = 0; si < spans.length; si++) {
        sentences[si] = spans[si].getCoveredText(s).toString();
      }
    }
    else {
      sentences = new String[] {};
    }
    return sentences;
  }

  private int getFirstWS(CharSequence s, int pos) {
    while (pos < s.length() && !StringUtil.isWhitespace(s.charAt(pos)))
      pos++;
    return pos;
  }

  private int getFirstNonWS(CharSequence s, int pos) {
    while (pos < s.length() && StringUtil.isWhitespace(s.charAt(pos)))
      pos++;
    return pos;
  }

  /**
   * Detects the position of the first words of sentences in a {@link CharSequence}.
   *
   * @param s  The {@link CharSequence} to be processed.
   * @return   An {@link Span span array} containing the positions of the end index of
   *           every sentence.
   *
   */
  @Override
  public Span[] sentPosDetect(CharSequence s) {
    sentProbs.clear();
    List<Integer> enders = scanner.getPositions(s);
    List<Integer> positions = new ArrayList<>(enders.size());

    for (int i = 0, end = enders.size(), index = 0; i < end; i++) {
      int cint = enders.get(i);
      // skip over the leading parts of non-token final delimiters
      int fws = getFirstWS(s,cint + 1);
      if (i + 1 < end && enders.get(i + 1) < fws) {
        // Do not skip if the character right after the delimiter is uppercase,
        // as this likely indicates the start of a new sentence (e.g., "Gedanken.Bek.")
        // rather than a multi-period abbreviation (e.g., "z.B.").
        int nextCharIdx = cint + 1;
        if (nextCharIdx >= s.length() || !Character.isUpperCase(s.charAt(nextCharIdx))) {
          continue;
        }
      }
      if (positions.size() > 0 && cint < positions.get(positions.size() - 1)) continue;

      double[] probs = model.eval(cgen.getContext(s, cint));
      String bestOutcome = model.getBestOutcome(probs);

      if (bestOutcome.equals(SPLIT) && isAcceptableBreak(s, index, cint)) {
        if (index != cint) {
          if (useTokenEnd) {
            positions.add(getFirstNonWS(s, getFirstWS(s,cint + 1)));
          }
          else {
            positions.add(getFirstNonWS(s, cint + 1));
          }
          sentProbs.add(probs[model.getIndex(bestOutcome)]);
        }

        index = cint + 1;
      }
    }

    int[] starts = ArrayMath.toIntArray(positions);

    // string does not contain sentence end positions
    if (starts.length == 0) {

      // remove leading and trailing whitespace
      int start = 0;
      int end = s.length();

      while (start < s.length() && StringUtil.isWhitespace(s.charAt(start)))
        start++;

      while (end > 0 && StringUtil.isWhitespace(s.charAt(end - 1)))
        end--;

      if (end - start > 0) {
        sentProbs.add(1d);
        return new Span[] {new Span(start, end)};
      }
      else
        return new Span[0];
    }

    // Convert the sentence end indexes to spans

    boolean leftover = starts[starts.length - 1] != s.length();
    Span[] spans = new Span[leftover ? starts.length + 1 : starts.length];

    for (int si = 0; si < starts.length; si++) {
      int start;

      if (si == 0) {
        start = 0;
      }
      else {
        start = starts[si - 1];
      }

      // A span might contain only white spaces, in this case the length of
      // the span will be zero after trimming and should be ignored.
      Span span = new Span(start, starts[si]).trim(s);
      if (span.length() > 0) {
        spans[si] = span;
      }
      else {
        sentProbs.remove(si);
      }
    }

    if (leftover) {
      Span span = new Span(starts[starts.length - 1], s.length()).trim(s);
      if (span.length() > 0) {
        spans[spans.length - 1] = span;
        sentProbs.add(1d);
      }
    }
    /*
     * set the prob for each span
     */
    for (int i = 0; i < spans.length; i++) {
      double prob = sentProbs.get(i);
      spans[i] = new Span(spans[i], prob);

    }

    return spans;
  }

  /**
   * {@inheritDoc}
   *
   * The sequence was determined based on the previous call to
   * {@link #sentDetect(CharSequence)}.
   *
   * @return An array with the same number of probabilities as tokens were sent to
   *         {@link #sentDetect(CharSequence)} when it was last called.
   *         If not applicable, an empty array is returned.
   */
  @Override
  public double[] probs() {
    return ArrayMath.toDoubleArray(sentProbs);
  }

  /**
   *
   * @return The probability for each sentence returned for the most recent
   *     call to {@link #sentDetect(CharSequence)}.
   *     If not applicable, an empty array is returned.
   *
   * @deprecated Use {@link #probs()} instead.
   */
  @Deprecated(forRemoval = true, since = "2.5.5")
  public double[] getSentenceProbabilities() {
    return probs();
  }

  /**
   * Allows subclasses to check an overzealous (read: poorly
   * trained) model from flagging obvious non-breaks as breaks based
   * on some boolean determination of a break's acceptability.
   *
   * <p>Note: The implementation always returns {@code true} if no
   * abbreviation dictionary is available for the underlying model.</p>
   *
   * <p>Only a bounded region of {@code s} is consulted. An abbreviation occurrence starting at
   * {@code p} with length {@code L} can veto the break only when it starts at or before the
   * candidate and reaches it, that is when
   * {@code candidateIndex - L <= p <= candidateIndex}. Positions outside that window are
   * irrelevant by construction, so the decision costs time proportional to the longest
   * dictionary entry rather than to the length of {@code s}.</p>
   *
   * <p>A {@code candidateIndex} past the end of {@code s} cannot carry an abbreviation, so it
   * is accepted rather than raising an {@link IndexOutOfBoundsException}. At
   * {@code candidateIndex == s.length()}, the decision matches the previous implementation when
   * the match is not at {@code fromIndex}; only the match-at-{@code fromIndex} case is an
   * accept-instead-of-throw relaxation.</p>
   *
   * @param s the {@link CharSequence} in which the break occurred.
   * @param fromIndex the start of the segment currently being evaluated.
   * @param candidateIndex the index of the candidate sentence ending. Must be greater than or
   *     equal to {@code fromIndex} and a valid index into {@code s}.
   * @return {@code true} if the break is acceptable, {@code false} otherwise.
   */
  protected boolean isAcceptableBreak(CharSequence s, int fromIndex, int candidateIndex) {
    return abbIndex == null || abbIndex.allowsBreak(s, fromIndex, candidateIndex);
  }

  /**
   * An immutable, length-bucketed index over an abbreviation {@link Dictionary}. It answers
   * {@link #isAcceptableBreak(CharSequence, int, int)} by enumerating the few text positions
   * that can carry a relevant abbreviation and asking a hash set what is there, instead of
   * searching the whole text once per dictionary entry.
   */
  private static final class AbbreviationIndex {

    /**
     * The dictionary entries, folded to lower case unless the dictionary is case-sensitive.
     * Only the first token of a multi-token entry participates, as before.
     */
    private final Set<String> entries;

    /**
     * The distinct entry lengths, ascending. These are the only window sizes worth probing.
     */
    private final int[] entryLengths;

    /**
     * The longest entry length, that is how far past a candidate a relevant match can reach.
     */
    private final int maxEntryLength;

    /**
     * Whether the dictionary matches case-sensitively.
     */
    private final boolean caseSensitive;

    /**
     * @param abbDict The {@link Dictionary} to index, may be {@code null}.
     * @return An index over {@code abbDict}, or {@code null} if {@code abbDict} is {@code null}.
     */
    static AbbreviationIndex of(Dictionary abbDict) {
      return abbDict == null ? null : new AbbreviationIndex(abbDict);
    }

    /**
     * Reads {@code abbDict} once, so this instance is immutable and safe to share.
     *
     * @param abbDict The {@link Dictionary} to index. Must not be {@code null}.
     */
    private AbbreviationIndex(Dictionary abbDict) {
      caseSensitive = abbDict.isCaseSensitive();
      final Set<String> tokens = new HashSet<>();
      final SortedSet<Integer> lengths = new TreeSet<>();
      for (StringList abb : abbDict) {
        final String token = caseSensitive ? abb.getToken(0)
            : StringUtil.toLowerCase(abb.getToken(0));
        tokens.add(token);
        lengths.add(token.length());
      }
      entries = tokens;
      entryLengths = lengths.stream().mapToInt(Integer::intValue).toArray();
      maxEntryLength = entryLengths.length == 0 ? 0 : entryLengths[entryLengths.length - 1];
    }

    /**
     * @param s The text in which the break occurred.
     * @param fromIndex The start of the segment currently being evaluated.
     * @param candidateIndex The index of the candidate sentence ending.
     * @return {@code true} if a break at {@code candidateIndex} is allowed.
     */
    boolean allowsBreak(CharSequence s, int fromIndex, int candidateIndex) {
      final int textLength = s.length();
      if (entryLengths.length == 0 || candidateIndex < fromIndex || candidateIndex < 0
          || candidateIndex > textLength) {
        return true;
      }
      // Occurrences before the segment start do not participate.
      final int scanStart = StrictMath.max(0, fromIndex);
      // The window holds every position a relevant occurrence can start at, plus the longest
      // entry so that the text of an occurrence starting at the candidate is covered too.
      final int windowStart = codePointStart(s,
          StrictMath.max(scanStart, candidateIndex - maxEntryLength));
      final int windowEnd = codePointEnd(s,
          StrictMath.min(textLength, candidateIndex + maxEntryLength));
      // Case folding is applied to the window only. It is per code point, so it preserves
      // indices and yields exactly the characters a folding of the whole text would.
      final String window = caseSensitive
          ? s.subSequence(windowStart, windowEnd).toString()
          : toLowerCase(s, windowStart, windowEnd);

      for (final int tokenLength : entryLengths) {
        for (int pos = StrictMath.max(scanStart, candidateIndex - tokenLength);
             pos <= candidateIndex; pos++) {
          final int endPos = pos + tokenLength;
          if (endPos > textLength) {
            break; // the entry no longer fits, and it fits even less further right
          }
          if (!entries.contains(window.substring(pos - windowStart, endPos - windowStart))) {
            continue;
          }
          if (pos == fromIndex && endPos == candidateIndex + 1) {
            return false; // full abbreviation match at segment start -> no acceptable break
          }
          final char prevChar = s.charAt(pos == fromIndex ? pos : pos - 1);
          /*
           * Note:
           * Skip abbreviation candidate if regular characters exist directly before it,
           * That is, any letter or digit except: a whitespace, an apostrophe, or an opening round bracket.
           * This prevents mismatches from overlaps close to an actual sentence end.
           */
          if (Character.isWhitespace(prevChar) || isApostrophe(prevChar) || prevChar == '(') {
            return false; // in case of a valid abbreviation: the (sentence) break is not accepted
          }
        }
      }
      return true; // no abbreviation(s) at given positions: valid sentence boundary
    }

    /**
     * Lower-cases {@code [from, to)} exactly as {@link StringUtil#toLowerCase(CharSequence)}
     * lower-cases a whole text: per code point via {@link Character#toLowerCase(int)}.
     *
     * <p>Folding only the window is correct only because that mapping is 1:1 in {@code char}
     * count, so {@code pos - windowStart} taken from the unfolded text still indexes the folded
     * window. {@link String#toLowerCase()} must not be used here: full case mapping can expand
     * a code point (for example {@code İ} / U+0130) and would corrupt every offset in the window.</p>
     *
     * @param s The text to read from.
     * @param from The first index to fold, at a code point boundary.
     * @param to The index to stop at, at a code point boundary.
     * @return The folded characters of {@code [from, to)}.
     */
    private static String toLowerCase(CharSequence s, int from, int to) {
      final StringBuilder folded = new StringBuilder(to - from);
      int i = from;
      while (i < to) {
        final int cp = Character.codePointAt(s, i);
        folded.appendCodePoint(Character.toLowerCase(cp));
        i += Character.charCount(cp);
      }
      return folded.toString();
    }

    /**
     * @param s The text {@code index} refers to.
     * @param index The index to align.
     * @return {@code index}, moved one character left if it points at the trailing half of a
     *     surrogate pair, which is the only index a code point walk cannot start at.
     */
    private static int codePointStart(CharSequence s, int index) {
      if (index > 0 && index < s.length() && Character.isLowSurrogate(s.charAt(index))
          && Character.isHighSurrogate(s.charAt(index - 1))) {
        return index - 1;
      }
      return index;
    }

    /**
     * @param s The text {@code index} refers to.
     * @param index The index to align.
     * @return {@code index}, moved one character right if it splits a surrogate pair, which is
     *     the only index a code point walk cannot stop at.
     */
    private static int codePointEnd(CharSequence s, int index) {
      if (index > 0 && index < s.length() && Character.isHighSurrogate(s.charAt(index - 1))
          && Character.isLowSurrogate(s.charAt(index))) {
        return index + 1;
      }
      return index;
    }
  }

  /**
   * @param c The character to check.
   * @return {@code true} if the character represents an apostrophe, {@code false} otherwise.
   */
  private static boolean isApostrophe(char c) {
    return c == '\'' || c == '`' || c == '´';
  }

  /**
   * Starts a training of a {@link SentenceModel} with the given parameters.
   *
   * @param languageCode The ISO language code to train the model. Must not be {@code null}.
   * @param samples The {@link ObjectStream} of {@link SentenceSample} used as input for training.
   * @param sdFactory The {@link SentenceDetectorFactory} for creating related objects as defined
   *                  via {@code mlParams}.
   * @param mlParams The {@link TrainingParameters} for the context of the training process.
   *
   * @return A valid, trained {@link SentenceModel} instance.
   * @throws IOException Thrown if IO errors occurred.
   */
  public static SentenceModel train(String languageCode,
      ObjectStream<SentenceSample> samples, SentenceDetectorFactory sdFactory,
      TrainingParameters mlParams) throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    // TODO: Fix the EventStream to throw exceptions when training goes wrong
    ObjectStream<Event> eventStream = new SDEventStream(samples,
        sdFactory.getSDContextGenerator(), sdFactory.getEndOfSentenceScanner());

    EventTrainer trainer = TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);
    MaxentModel sentModel = trainer.train(eventStream);

    return new SentenceModel(languageCode, sentModel, manifestInfoEntries, sdFactory);
  }

}
