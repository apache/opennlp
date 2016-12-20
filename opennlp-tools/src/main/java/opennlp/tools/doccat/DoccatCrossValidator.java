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

import java.io.IOException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.Mean;

/**
 * Cross validator for document categorization
 */
public class DoccatCrossValidator {

  private final String languageCode;

  private final TrainingParameters params;

  private Mean documentAccuracy = new Mean();

  private DoccatEvaluationMonitor[] listeners;

  private DoccatFactory factory;


  /**
   * Creates a {@link DoccatCrossValidator} with the given
   * {@link FeatureGenerator}s.
   */
  public DoccatCrossValidator(String languageCode, TrainingParameters mlParams,
      DoccatFactory factory, DoccatEvaluationMonitor ... listeners) {
    this.languageCode = languageCode;
    this.params = mlParams;
    this.listeners = listeners;
    this.factory = factory;
  }

  /**
   * Starts the evaluation.
   *
   * @param samples
   *          the data to train and test
   * @param nFolds
   *          number of folds
   *
   * @throws IOException
   */
  public void evaluate(ObjectStream<DocumentSample> samples, int nFolds)
      throws IOException {

    CrossValidationPartitioner<DocumentSample> partitioner = new CrossValidationPartitioner<>(
        samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<DocumentSample> trainingSampleStream = partitioner
          .next();

      DoccatModel model = DocumentCategorizerME.train(languageCode,
          trainingSampleStream, params, factory);

      DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(
          new DocumentCategorizerME(model), listeners);

      evaluator.evaluate(trainingSampleStream.getTestSampleStream());

      documentAccuracy.add(evaluator.getAccuracy(),
          evaluator.getDocumentCount());

    }
  }

  /**
   * Retrieves the accuracy for all iterations.
   *
   * @return the word accuracy
   */
  public double getDocumentAccuracy() {
    return documentAccuracy.mean();
  }

  /**
   * Retrieves the number of words which where validated over all iterations.
   * The result is the amount of folds multiplied by the total number of words.
   *
   * @return the word count
   */
  public long getDocumentCount() {
    return documentAccuracy.count();
  }
}
