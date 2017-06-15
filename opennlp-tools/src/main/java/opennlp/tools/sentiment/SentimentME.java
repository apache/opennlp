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
import java.util.Map;
import java.util.Objects;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * Class for creating a maximum-entropy-based Sentiment Analysis model.
 */
public class SentimentME implements Sentiment {

  public static final int DEFAULT_BEAM_SIZE = 3;

  protected SentimentContextGenerator contextGenerator;
  private Sequence bestSequence;
  protected SequenceClassificationModel<String> model;
  private SentimentFactory factory;
  private MaxentModel maxentModel;

  private String lang;
  private TrainingParameters params;

  /**
   * Constructor, initializes.
   *
   * @param sentModel
   *          sentiment analysis model
   */
  public SentimentME(SentimentModel sentModel) {
    model = sentModel.getSentimentModel();
    maxentModel = sentModel.getMaxentModel();
    factory = sentModel.getFactory();
    contextGenerator = factory.createContextGenerator();
  }

  public SentimentME(String lang, TrainingParameters params,
      SentimentFactory factory) {
    this.lang = Objects.requireNonNull(lang, "lang must be provided");
    this.params = Objects.requireNonNull(params, "params must be provided");
    this.factory = Objects.requireNonNull(factory, "factory must be provided");
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
  public SentimentModel train(ObjectStream<SentimentSample> samples)
      throws IOException {
    Map<String, String> entries = new HashMap<String, String>();
    ObjectStream<Event> eventStream = new SentimentEventStream(samples,
        contextGenerator);
    EventTrainer trainer = TrainerFactory.getEventTrainer(params, entries);
    maxentModel = trainer.train(eventStream);
    Map<String, String> manifestInfoEntries = new HashMap<String, String>();
    SentimentModel sentimentModel = new SentimentModel(lang, maxentModel,
        manifestInfoEntries, factory);
    model = sentimentModel.getSentimentModel();
    return sentimentModel;
  }

  /**
   * Makes a sentiment prediction
   *
   * @param tokens
   *          the tokens to be analyzed for its sentiment
   * @return the predicted sentiment
   */
  @Override
  public String predict(String[] tokens) {
    if (tokens == null || tokens.length == 0) {
      throw new IllegalArgumentException("Tokens must be not empty");
    }
    double[] prob = probabilities(tokens);
    return getBestSentiment(prob);
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
  public double[] probabilities(String[] text) {
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

}
