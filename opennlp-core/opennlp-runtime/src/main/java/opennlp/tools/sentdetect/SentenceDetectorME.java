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
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
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
import opennlp.tools.util.OwnerOrPerThreadState;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;

/**
 * A sentence detector for splitting up raw text into sentences.
 * <p>
 * A maximum entropy model is used to evaluate end-of-sentence characters in a
 * string to determine if they signify the end of a sentence.
 * <p>
 * A sentence detector instance is thread-safe. One instance can be shared across multiple threads to save
 * memory.
 * <p>
 * <b>Note:</b> In container environments with classloader isolation (e.g. Jakarta EE), ensure instances do
 * not outlive the application's lifecycle, as underlying components use {@link ThreadLocal} state that may
 * pin the classloader.
 */
@ThreadSafe
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

  private static final class SentenceDetectorState {
    private List<Double> sentProbs = new ArrayList<>();
  }

  private final OwnerOrPerThreadState<SentenceDetectorState> perThreadState =
      new OwnerOrPerThreadState<>(SentenceDetectorState::new,
          s -> s.sentProbs = new ArrayList<>());

  /**
   * The {@link Dictionary abbreviation dictionary} if available (may be {@code null}).
   */
  private final Dictionary abbDict;

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
   */
  public SentenceDetectorME(SentenceModel model, Dictionary abbDict) {
    this.model = model.getMaxentModel();
    this.abbDict = abbDict;
    SentenceDetectorFactory sdFactory = model.getFactory();
    cgen = sdFactory.getSDContextGenerator();
    scanner = sdFactory.getEndOfSentenceScanner();
    useTokenEnd = sdFactory.isUseTokenEnd();
  }

  /**
   * @deprecated Use a {@link SentenceDetectorFactory} to extend SentenceDetector functionality.
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
    abbDict = model.getAbbreviations();
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
   * @param s The {@link CharSequence} to be processed.
   * @return An {@link Span span array} containing the positions of the end index of every sentence.
   */
  @Override
  public Span[] sentPosDetect(CharSequence s) {
    SentenceDetectorState state = perThreadState.get();
    List<Double> localProbs = new ArrayList<>();
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
          localProbs.add(probs[model.getIndex(bestOutcome)]);
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
        localProbs.add(1d);
        state.sentProbs = localProbs;
        return new Span[] {new Span(start, end)};
      }
      else {
        state.sentProbs = localProbs;
        return new Span[0];
      }
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
        localProbs.remove(si);
      }
    }

    if (leftover) {
      Span span = new Span(starts[starts.length - 1], s.length()).trim(s);
      if (span.length() > 0) {
        spans[spans.length - 1] = span;
        localProbs.add(1d);
      }
    }
    /*
     * set the prob for each span
     */
    for (int i = 0; i < spans.length; i++) {
      double prob = localProbs.get(i);
      spans[i] = new Span(spans[i], prob);

    }

    // Publish for backward-compatible probs() access (last-writer-wins under concurrency)
    state.sentProbs = localProbs;

    return spans;
  }

  /**
   * {@inheritDoc}
   *
   * The sequence was determined based on the previous call to {@link #sentDetect(CharSequence)}.
   *
   * @return an array with the same number of probabilities as for the last
   *     {@link #sentDetect(CharSequence)} call; if not applicable, an empty array
   */
  @Override
  public double[] probs() {
    return ArrayMath.toDoubleArray(perThreadState.get().sentProbs);
  }

  /**
   * Removes thread-local state to prevent classloader leaks in container environments.
   * Call when the thread is returned to a pool or the sentence detector is no longer needed.
   */
  public void clearThreadLocalState() {
    perThreadState.clearForCurrentThread();
  }

  /**
   * @return The probability for each sentence returned for the most recent call to
   *     {@link #sentDetect(CharSequence)}; if not applicable, an empty array
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
   * @param s the {@link CharSequence} in which the break occurred.
   * @param fromIndex the start of the segment currently being evaluated.
   * @param candidateIndex the index of the candidate sentence ending.
   * @return {@code true} if the break is acceptable, {@code false} otherwise.
   */
  protected boolean isAcceptableBreak(CharSequence s, int fromIndex, int candidateIndex) {
    if (abbDict == null)
      return true;

    final String text = s.toString();
    final boolean caseSensitive = abbDict.isCaseSensitive();
    final String searchText = caseSensitive ? text : StringUtil.toLowerCase(text);
    for (StringList abb : abbDict) {
      final String abbToken = caseSensitive ? abb.getToken(0)
          : StringUtil.toLowerCase(abb.getToken(0));
      final int tokenLength = abbToken.length();
      int tokenStartPos = searchText.indexOf(abbToken, fromIndex);
      while (tokenStartPos != -1) {
        if (tokenStartPos > candidateIndex) {
          break; // past candidate position, no point searching further
        }
        if (tokenStartPos == fromIndex
            && searchText.substring(tokenStartPos, candidateIndex + 1).equals(abbToken)) {
          return false; // full abbreviation match at segment start -> no acceptable break
        }
        final char prevChar = s.charAt(tokenStartPos == fromIndex ? tokenStartPos : tokenStartPos - 1);
        if (tokenStartPos + tokenLength >= candidateIndex
          /*
           * Note:
           * Skip abbreviation candidate if regular characters exist directly before it,
           * That is, any letter or digit except: a whitespace, an apostrophe, or an opening round bracket.
           * This prevents mismatches from overlaps close to an actual sentence end.
           */
            && (Character.isWhitespace(prevChar) || isApostrophe(prevChar) || prevChar == '(')) {
          return false; // in case of a valid abbreviation: the (sentence) break is not accepted
        }
        // Try next occurrence of this abbreviation in the text
        tokenStartPos = searchText.indexOf(abbToken, tokenStartPos + 1);
      }
    }
    return true; // no abbreviation(s) at given positions: valid sentence boundary
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
   * @param sdFactory The {@link SentenceDetectorFactory} for creating related objects as defined via
   *     {@code mlParams}.
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

    EventTrainer<TrainingParameters> trainer = TrainerFactory.getEventTrainer(mlParams, manifestInfoEntries);
    MaxentModel sentModel = trainer.train(eventStream);

    return new SentenceModel(languageCode, sentModel, manifestInfoEntries, sdFactory);
  }

}
