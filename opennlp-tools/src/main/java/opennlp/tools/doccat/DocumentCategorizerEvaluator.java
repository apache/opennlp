/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.util.Iterator;

import opennlp.tools.postag.POSSample;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.eval.Mean;

/**
 * The {@link DocumentCategorizerEvaluator} measures the performance of
 * the given {@link DocumentCategorizer} with the provided reference
 * {@link DocumentSample}s.
 *
 * @see DocumentCategorizer
 * @see DocumentSample
 */
public class DocumentCategorizerEvaluator {

  private DocumentCategorizer categorizer;

  private Mean accuracy = new Mean();

  /**
   * Initializes the current instance.
   *
   * @param categorizer
   */
  public DocumentCategorizerEvaluator(DocumentCategorizer categorizer) {
    this.categorizer = categorizer;
  }

  /**
   * Evaluates the given reference {@link DocumentSample} object.
   *
   * This is done by categorizing the document from the provided
   * {@link DocumentSample}. The detected category is then used
   * to calculate and update the score.
   *
   * @param sample the reference {@link TokenSample}.
   */
  public void evaluteSample(DocumentSample sample) {

    String document[] = sample.getText();

    double probs[] = categorizer.categorize(document);

    String cat = categorizer.getBestCategory(probs);

    if (sample.getCategory().equals(cat)) {
      accuracy.add(1);
    }
    else {
      accuracy.add(0);
    }
  }

  /**
   * Reads all {@link DocumentSample} objects from the stream
   * and evaluates each {@link DocumentSample} object with
   * {@link #evaluteSample(DocumentSample)} method.
   *
   * @param samples the stream of reference {@link POSSample} which
   * should be evaluated.
   */
  public void evaluate(Iterator<DocumentSample> samples) {

    while (samples.hasNext()) {
      evaluteSample(samples.next());
    }
  }

  /**
   * Retrieves the accuracy of provided {@link DocumentCategorizer}.
   *
   * accuracy = correctly categorized documents / total documents
   *
   * @return the accuracy
   */
  public double getAccuracy() {
    return accuracy.mean();
  }

  /**
   * Represents this objects as human readable {@link String}.
   */
  @Override
  public String toString() {
    return "Accuracy: " + accuracy.mean() + "\n" +
        "Number of documents: " + accuracy.count();
  }
}
