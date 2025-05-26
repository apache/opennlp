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

package opennlp.tools.doccat;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link DocumentCategorizerEvaluator} measures the performance of
 * the given {@link DocumentCategorizer} with the provided reference
 * {@link DocumentSample samples}.
 *
 * @see DocumentCategorizer
 * @see DocumentSample
 * @see Evaluator
 */
public class DocumentCategorizerEvaluator extends Evaluator<DocumentSample> {

  private final DocumentCategorizer categorizer;

  private final Mean accuracy = new Mean();

  /**
   * Initializes a {@link DocumentCategorizerEvaluator} instance.
   *
   * @param categorizer the {@link DocumentCategorizer} instance.
   * @param listeners the {@link DoccatEvaluationMonitor evaluation listeners}.
   */
  public DocumentCategorizerEvaluator(DocumentCategorizer categorizer,
      DoccatEvaluationMonitor ... listeners) {
    super(listeners);
    this.categorizer = categorizer;
  }

  /**
   * Evaluates the given reference {@link DocumentSample sample}.
   * <p>
   * This is done by categorizing the document from the provided
   * {@link DocumentSample}. The detected category is then used
   * to calculate and update the score.
   *
   * @param sample The reference {@link TokenSample}.
   * @return The processed {@link TokenSample}.
   */
  public DocumentSample processSample(DocumentSample sample) {

    String[] document = sample.getText();

    double[] probs = categorizer.categorize(document);

    String cat = categorizer.getBestCategory(probs);

    if (sample.getCategory().equals(cat)) {
      accuracy.add(1);
    }
    else {
      accuracy.add(0);
    }

    return new DocumentSample(cat, sample.getText());
  }

  /**
   * {@code accuracy = correctly categorized documents / total documents}
   *
   * @return Retrieves the accuracy of provided {@link DocumentCategorizer}.
   */
  public double getAccuracy() {
    return accuracy.mean();
  }

  public long getDocumentCount() {
    return accuracy.count();
  }

  /**
   * Represents this object as human-readable {@link String}.
   */
  @Override
  public String toString() {
    return "Accuracy: " + accuracy.mean() + "\n" +
        "Number of documents: " + accuracy.count();
  }
}
