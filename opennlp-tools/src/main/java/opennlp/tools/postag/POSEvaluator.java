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


package opennlp.tools.postag;

import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link POSEvaluator} measures the performance of the given {@link POSTagger}
 * with the provided reference {@link POSSample samples}.
 */
public class POSEvaluator extends Evaluator<POSSample> {

  private final POSTagger tagger;

  private final Mean wordAccuracy = new Mean();

  /**
   * Initializes the current instance.
   *
   * @param tagger The {@link POSTagger} to evaluate.
   * @param listeners the {@link POSTaggerEvaluationMonitor evaluation listeners}.
   */
  public POSEvaluator(POSTagger tagger, POSTaggerEvaluationMonitor ... listeners) {
    super(listeners);
    this.tagger = tagger;
  }

  /**
   * Evaluates the given reference {@link POSSample} object.
   * This is done by tagging the sentence from the reference
   * {@link POSSample} with the {@link POSTagger}. The
   * tags are then used to update the word accuracy score.
   *
   * @param reference The {@link POSSample} to process.
   *
   * @return The predicted {@link POSSample}.
   */
  @Override
  protected POSSample processSample(POSSample reference) {

    String[] predictedTags = tagger.tag(reference.getSentence(), reference.getAdditionalContext());
    String[] referenceTags = reference.getTags();

    for (int i = 0; i < referenceTags.length; i++) {
      if (referenceTags[i].equals(predictedTags[i])) {
        wordAccuracy.add(1);
      }
      else {
        wordAccuracy.add(0);
      }
    }

    return new POSSample(reference.getSentence(), predictedTags);
  }

  /**
   * Accuracy defined as:
   * {@code word accuracy = correctly detected tags / total words}
   *
   * @return Retrieves the mean word accuracy.
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
   * Represents this object as human-readable {@link String}.
   */
  @Override
  public String toString() {
    return "Accuracy:" + wordAccuracy.mean() +
        " Number of Samples: " + wordAccuracy.count();
  }

}
