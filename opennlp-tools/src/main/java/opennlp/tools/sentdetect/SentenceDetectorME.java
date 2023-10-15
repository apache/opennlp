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

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
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
public class SentenceDetectorME implements SentenceDetector {

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
   * The {@link Dictionary abbreviation dictionary} if available (may be {@code null}).
   */
  private final Dictionary abbDict;

  protected boolean useTokenEnd;

  /**
   * Initializes the sentence detector by downloading a default model.
   * @param language The language of the sentence detector.
   * @throws IOException Thrown if the model cannot be downloaded or saved.
   */
  public SentenceDetectorME(String language) throws IOException {
    this(DownloadUtil.downloadModel(language,
            DownloadUtil.ModelType.SENTENCE_DETECTOR, SentenceModel.class));
  }

  /**
   * Initializes the current instance.
   *
   * @param model the {@link SentenceModel}
   */
  public SentenceDetectorME(SentenceModel model) {
    SentenceDetectorFactory sdFactory = model.getFactory();
    this.model = model.getMaxentModel();
    cgen = sdFactory.getSDContextGenerator();
    scanner = sdFactory.getEndOfSentenceScanner();
    abbDict = model.getAbbreviations();
    useTokenEnd = sdFactory.isUseTokenEnd();
  }

  /**
   * @deprecated Use a {@link SentenceDetectorFactory} to extend
   *             SentenceDetector functionality.
   */
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
        continue;
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

    int[] starts = new int[positions.size()];
    for (int i = 0; i < starts.length; i++) {
      starts[i] = positions.get(i);
    }

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
   * Returns the probabilities associated with the most recent
   * calls to {@link SentenceDetectorME#sentDetect(CharSequence)}.
   *
   * @return The probability for each sentence returned for the most recent
   *     call to {@link SentenceDetectorME#sentDetect(CharSequence)}.
   *     If not applicable, an empty array is returned.
   */
  public double[] getSentenceProbabilities() {
    double[] sentProbArray = new double[sentProbs.size()];
    for (int i = 0; i < sentProbArray.length; i++) {
      sentProbArray[i] = sentProbs.get(i);
    }
    return sentProbArray;
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

    for (StringList abb : abbDict) {
      String token = abb.getToken(0);
      int tokenLength = token.length();
      int tokenPosition = s.toString().indexOf(token, fromIndex);
      if (tokenPosition + tokenLength < candidateIndex || tokenPosition > candidateIndex)
        continue;

      return false;
    }
    return true;
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
