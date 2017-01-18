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

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.model.ModelUtil;

/**
 * A cross validator for the sentence detector.
 */
public class SDCrossValidator {

  private final String languageCode;

  private final TrainingParameters params;

  private FMeasure fmeasure = new FMeasure();

  private SentenceDetectorEvaluationMonitor[] listeners;

  private SentenceDetectorFactory sdFactory;

  public SDCrossValidator(String languageCode, TrainingParameters params,
      SentenceDetectorFactory sdFactory, SentenceDetectorEvaluationMonitor... listeners) {
    this.languageCode = languageCode;
    this.params = params;
    this.listeners = listeners;
    this.sdFactory = sdFactory;
  }

  /**
   * @deprecated Use
   *             {@link #SDCrossValidator(String, TrainingParameters,
   *             SentenceDetectorFactory, SentenceDetectorEvaluationMonitor...)}
   *             and pass in a {@link SentenceDetectorFactory}.
   */
  public SDCrossValidator(String languageCode, TrainingParameters params) {
    this(languageCode, params, new SentenceDetectorFactory(languageCode, true,
        null, null));
  }

  /**
   * @deprecated use
   *             {@link #SDCrossValidator(String, TrainingParameters, SentenceDetectorFactory,
   *             SentenceDetectorEvaluationMonitor...)}
   *             instead and pass in a TrainingParameters object.
   */
  public SDCrossValidator(String languageCode, TrainingParameters params,
      SentenceDetectorEvaluationMonitor... listeners) {
    this(languageCode, params, new SentenceDetectorFactory(languageCode, true,
        null, null), listeners);
  }

  /**
   * @deprecated use {@link #SDCrossValidator(String, TrainingParameters,
   *     SentenceDetectorFactory, SentenceDetectorEvaluationMonitor...)}
   *     instead and pass in a TrainingParameters object.
   */
  public SDCrossValidator(String languageCode) {
    this(languageCode, ModelUtil.createDefaultTrainingParameters());
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
  public void evaluate(ObjectStream<SentenceSample> samples, int nFolds) throws IOException {

    CrossValidationPartitioner<SentenceSample> partitioner =
        new CrossValidationPartitioner<>(samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<SentenceSample> trainingSampleStream =
          partitioner.next();

      SentenceModel model;

      model = SentenceDetectorME.train(languageCode, trainingSampleStream,
          sdFactory, params);

      // do testing
      SentenceDetectorEvaluator evaluator = new SentenceDetectorEvaluator(
          new SentenceDetectorME(model), listeners);

      evaluator.evaluate(trainingSampleStream.getTestSampleStream());

      fmeasure.mergeInto(evaluator.getFMeasure());
    }
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
