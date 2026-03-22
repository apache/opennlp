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

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * A {@link SentimentDetector} implementation for creating and using
 * maximum-entropy-based Sentiment Analysis models.
 *
 * @see SentimentModel
 */
public class SentimentME implements SentimentDetector {

  private final SentimentContextGenerator contextGenerator;
  private final SentimentFactory factory;
  private final MaxentModel maxentModel;

  /**
   * Instantiates a {@link SentimentME} with the specified model.
   *
   * @param sentModel The {@link SentimentModel sentiment analysis model} to use.
   *                  It must not be {@code null}.
   * @throws IllegalArgumentException Thrown if parameters are invalid.
   */
  public SentimentME(SentimentModel sentModel) {
    if (sentModel == null) {
      throw new IllegalArgumentException("SentimentModel must not be null!");
    }
    maxentModel = sentModel.getMaxentModel();
    factory = sentModel.getFactory();
    contextGenerator = factory.createContextGenerator();
  }

  /**
   * Trains a {@link SentimentModel Sentiment Analysis model}.
   *
   * @param languageCode
   *          the code for the language of the text, e.g. "en"
   * @param samples
   *          the sentiment samples to be used
   * @param trainParams
   *          parameters for training
   * @param factory
   *          a Sentiment Analysis factory
   * @return A valid {@link SentimentModel}.
   * @throws IOException Thrown if IO errors occurred during training.
   */
  public static SentimentModel train(String languageCode, ObjectStream<SentimentSample> samples,
                                     TrainingParameters trainParams, SentimentFactory factory)
          throws IOException {

    Map<String, String> entries = new HashMap<>();

    ObjectStream<Event> eventStream = new SentimentEventStream(samples, factory.createContextGenerator());

    EventTrainer<TrainingParameters> trainer = TrainerFactory.getEventTrainer(trainParams, entries);
    MaxentModel sentimentModel = trainer.train(eventStream);

    return new SentimentModel(languageCode, sentimentModel, entries, factory);
  }

  @Override
  public String predict(String sentence) {
    String[] tokens = factory.getTokenizer().tokenize(sentence);
    return predict(tokens);
  }

  @Override
  public String predict(String[] tokens) {
    double[] prob = probabilities(tokens);
    return getBestSentiment(prob);
  }

  /**
   * Returns the best chosen sentiment for the given probability distribution.
   *
   * @param outcome the probability distribution over outcomes.
   * @return the best sentiment label.
   */
  public String getBestSentiment(double[] outcome) {
    return maxentModel.getBestOutcome(outcome);
  }

  /**
   * Returns the probability distribution over sentiment labels for the given tokens.
   *
   * @param text the tokens to classify.
   * @return the probability distribution over sentiment labels.
   */
  public double[] probabilities(String[] text) {
    return maxentModel.eval(contextGenerator.getContext(text));
  }
}
