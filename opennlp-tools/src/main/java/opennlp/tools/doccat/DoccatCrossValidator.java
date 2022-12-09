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
 * Cross validator for {@link DocumentCategorizer}.
 */
public class DoccatCrossValidator {

  private final String languageCode;

  private final TrainingParameters params;

  private final Mean documentAccuracy = new Mean();

  private final DoccatEvaluationMonitor[] listeners;

  private final DoccatFactory factory;


  /**
   * Instantiates a {@link DoccatCrossValidator} with the
   * given {@link FeatureGenerator generators}.
   *
   * @param languageCode An ISO conform language code.
   * @param mlParams The {@link TrainingParameters} for the context of cross validation.
   * @param factory The {@link DoccatFactory} for creating related objects.
   * @param listeners the {@link DoccatEvaluationMonitor evaluation listeners}.
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
   * @param samples The {@link ObjectStream} of {@link DocumentSample samples} to train and test with.
   * @param nFolds Number of folds. It must be greater than zero.
   *
   * @throws IOException Thrown if IO errors occurred.
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
   * @return Retrieves the accuracy for all iterations.
   */
  public double getDocumentAccuracy() {
    return documentAccuracy.mean();
  }

  /**
   * @return Retrieves the number of words which where validated over all iterations.
   *         The result is the amount of folds multiplied by the total number of words.
   */
  public long getDocumentCount() {
    return documentAccuracy.count();
  }
}
