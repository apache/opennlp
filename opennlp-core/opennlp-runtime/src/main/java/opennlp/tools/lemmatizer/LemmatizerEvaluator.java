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

package opennlp.tools.lemmatizer;

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link LemmatizerEvaluator} measures the performance of
 * the given {@link Lemmatizer} with the provided reference
 * {@link LemmaSample samples}.
 */
public class LemmatizerEvaluator extends Evaluator<LemmaSample> {

  private final Lemmatizer lemmatizer;

  private final Mean wordAccuracy = new Mean();

  /**
   * Initializes a {@link LemmatizerEvaluator} instance with the given {@link Lemmatizer}.
   *
   * @param aLemmatizer The {@link Lemmatizer} to evaluate.
   * @param listeners The {@link LemmatizerEvaluationMonitor evaluation listeners}.
   */
  public LemmatizerEvaluator(Lemmatizer aLemmatizer, LemmatizerEvaluationMonitor ... listeners) {
    super(listeners);
    this.lemmatizer = aLemmatizer;
  }

  /**
   * Evaluates the given reference {@link LemmaSample} object.
   * <p>
   * This is done by tagging the sentence from the reference
   * {@link LemmaSample} with the {@link Lemmatizer}. The
   * tags are then used to update the word accuracy score.
   *
   * @param reference the reference {@link LemmaSample}.
   *
   * @return The predicted {@link LemmaSample}.
   */
  @Override
  protected LemmaSample processSample(LemmaSample reference) {

    String[] predictedLemmas = lemmatizer.lemmatize(reference.getTokens(), reference.getTags());
    String[] referenceLemmas = reference.getLemmas();

    for (int i = 0; i < referenceLemmas.length; i++) {
      if (referenceLemmas[i].equals(predictedLemmas[i])) {
        wordAccuracy.add(1);
      }
      else {
        wordAccuracy.add(0);
      }
    }
    return new LemmaSample(reference.getTokens(), reference.getTags(), predictedLemmas);
  }

  /**
   * Accuracy is defined as:
   * {@code word accuracy = correctly detected tags / total words}
   *
   * @return Retrieves the word accuracy.
   */
  public double getWordAccuracy() {
    return wordAccuracy.mean();
  }

  /**
   * @return Retrieves the total number of words considered in the evaluation.
   */
  public long getWordCount() {
    return wordAccuracy.count();
  }

  /**
   * Returns this object's human-readable {@link String} representation.
   */
  @Override
  public String toString() {
    return "Accuracy:" + wordAccuracy.mean() +
        " Number of Samples: " + wordAccuracy.count();
  }
}
