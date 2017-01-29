/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.namefind.BioCodec;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.SequenceCodec;
import opennlp.tools.util.SequenceValidator;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.AdditionalContextFeatureGenerator;

/**
 * Class for creating a maximum-entropy-based Sentiment Analysis model.
 */
public class SentimentME {

  public static final String OTHER = "other";
  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final int DEFAULT_BEAM_SIZE = 3;

  private static String[][] EMPTY = new String[0][0];

  protected SentimentContextGenerator contextGenerator;
  private AdditionalContextFeatureGenerator additionalContextFeatureGenerator =
    new AdditionalContextFeatureGenerator();
  private Sequence bestSequence;
  protected SequenceClassificationModel<String> model;
  private SequenceValidator<String> sequenceValidator;
  private SentimentFactory factory;
  private MaxentModel maxentModel;
  private SequenceCodec<String> seqCodec = new BioCodec();
  private AdaptiveFeatureGenerator featureGenerators[];

  /**
   * Constructor, initialises
   *
   * @param sentModel
   *          sentiment analysis model
   */
  public SentimentME(SentimentModel sentModel) {

    this.model = sentModel.getSentimentModel();
    maxentModel = sentModel.getMaxentModel();

    factory = sentModel.getFactory();

    contextGenerator = factory.createContextGenerator();
  }

  /**
   * Trains a Sentiment Analysis model.
   *
   * @param languageCode
   *          the code for the language of the text, e.g. "en"
   * @param samples
   *          the sentiment samples to be used
   * @param trainParams
   *          parameters for training
   * @param factory
   *          a Sentiment Analysis factory
   * @return a Sentiment Analysis model
   */
  public static SentimentModel train(String languageCode,
      ObjectStream<SentimentSample> samples, TrainingParameters trainParams,
      SentimentFactory factory) throws IOException {

    Map<String, String> entries = new HashMap<String, String>();

    MaxentModel sentimentModel = null;

    ObjectStream<Event> eventStream = new SentimentEventStream(samples,
        factory.createContextGenerator());

    EventTrainer trainer = TrainerFactory
        .getEventTrainer(trainParams.getSettings(), entries);
    sentimentModel = trainer.train(eventStream);

    Map<String, String> manifestInfoEntries = new HashMap<String, String>();

    return new SentimentModel(languageCode, sentimentModel, manifestInfoEntries,
        factory);

  }

  /**
   * Makes a sentiment prediction
   *
   * @param sentence
   *          the text to be analysed for its sentiment
   * @return the predicted sentiment
   */
  public String predict(String sentence) {
    String[] tokens = factory.getTokenizer().tokenize(sentence);

    return predict(tokens);
  }

  public String predict(String[] tokens) {

    double prob[] = probabilities(tokens);
    String sentiment = getBestSentiment(prob);

    return sentiment;
  }

  /**
   * Returns the best chosen sentiment for the text predicted on
   *
   * @param outcome
   *          the outcome
   * @return the best sentiment
   */
  public String getBestSentiment(double[] outcome) {
    return maxentModel.getBestOutcome(outcome);
  }

  /**
   * Returns the analysis probabilities
   *
   * @param text
   *          the text to categorize
   */
  public double[] probabilities(String text[]) {
    return maxentModel.eval(contextGenerator.getContext(text));
  }

  /**
   * Returns an array of probabilities for each of the specified spans which is
   * the arithmetic mean of the probabilities for each of the outcomes which
   * make up the span.
   *
   * @param spans
   *          The spans of the sentiments for which probabilities are desired.
   * @return an array of probabilities for each of the specified spans.
   */
  public double[] probs(Span[] spans) {

    double[] sprobs = new double[spans.length];
    double[] probs = bestSequence.getProbs();

    for (int si = 0; si < spans.length; si++) {

      double p = 0;

      for (int oi = spans[si].getStart(); oi < spans[si].getEnd(); oi++) {
        p += probs[oi];
      }

      p /= spans[si].length();

      sprobs[si] = p;
    }

    return sprobs;
  }

  /**
   * Sets the probs for the spans
   *
   * @param spans
   *          the spans to be analysed
   * @return the span of probs
   */
  private Span[] setProbs(Span[] spans) {
    double[] probs = probs(spans);
    if (probs != null) {

      for (int i = 0; i < probs.length; i++) {
        double prob = probs[i];
        spans[i] = new Span(spans[i], prob);
      }
    }
    return spans;
  }

  /**
   * Generates sentiment tags for the given sequence, typically a sentence,
   * returning token spans for any identified sentiments.
   *
   * @param tokens
   *          an array of the tokens or words of the sequence, typically a
   *          sentence
   * @return an array of spans for each of the names identified.
   */
  public Span[] find(String[] tokens) {
    return find(tokens, EMPTY);
  }

  /**
   * Generates sentiment tags for the given sequence, typically a sentence,
   * returning token spans for any identified sentiments.
   *
   * @param tokens
   *          an array of the tokens or words of the sequence, typically a
   *          sentence.
   * @param additionalContext
   *          features which are based on context outside of the sentence but
   *          which should also be used.
   *
   * @return an array of spans for each of the names identified.
   */
  public Span[] find(String[] tokens, String[][] additionalContext) {

    additionalContextFeatureGenerator.setCurrentContext(additionalContext);

    bestSequence = model.bestSequence(tokens, additionalContext,
        contextGenerator, sequenceValidator);

    List<String> c = bestSequence.getOutcomes();

    contextGenerator.updateAdaptiveData(tokens,
        c.toArray(new String[c.size()]));
    Span[] spans = seqCodec.decode(c);
    spans = setProbs(spans);
    return spans;
  }

  /**
   * Makes a sentiment prediction by calling the helper method
   *
   * @param tokens
   *          the text to be analysed for its sentiment
   * @return the prediction made by the helper method
   */
  public Span[] predict2(String[] tokens) {
    return predict2(tokens, EMPTY);
  }

  /**
   * Makes a sentiment prediction
   *
   * @param tokens
   *          the text to be analysed for its sentiment
   * @param additionalContext
   *          any required additional context
   * @return the predictions
   */
  public Span[] predict2(String[] tokens, String[][] additionalContext) {

    additionalContextFeatureGenerator.setCurrentContext(additionalContext);

    bestSequence = model.bestSequence(tokens, additionalContext,
        contextGenerator, sequenceValidator);

    List<String> c = bestSequence.getOutcomes();

    Span[] spans = seqCodec.decode(c);
    return spans;
  }

}
