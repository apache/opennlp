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

package opennlp.tools.sentdetect;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.EvaluationSampleListener;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.model.ModelUtil;

/**
 * 
 */
public class SDCrossValidator {
  
  private final String languageCode;
  
  private final Dictionary abbreviations;
  
  private final TrainingParameters params;
  
  private FMeasure fmeasure = new FMeasure();

  private LinkedList<EvaluationSampleListener<SentenceSample>> listeners;
  
  public SDCrossValidator(String languageCode, int cutoff, int iterations) {
    this(languageCode, ModelUtil.createTrainingParameters(cutoff, iterations));
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params) {
    this(languageCode, params, (Dictionary)null);
  }
  
  public SDCrossValidator(String languageCode, int cutoff, int iterations, Dictionary abbreviations) {
    this(languageCode, ModelUtil.createTrainingParameters(cutoff, iterations), abbreviations);
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params, Dictionary abbreviations) {
    this.languageCode = languageCode;
    this.params = params;
    this.abbreviations = abbreviations;
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params,
      List<? extends EvaluationSampleListener<SentenceSample>> listeners) {
    this(languageCode, params, null, listeners);
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params,
      Dictionary abbreviations,
      List<? extends EvaluationSampleListener<SentenceSample>> listeners) {
    this(languageCode, params, abbreviations);
    if (listeners != null) {
      this.listeners = new LinkedList<EvaluationSampleListener<SentenceSample>>(
          listeners);
    }
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params,
      EvaluationSampleListener<SentenceSample> listener) {
    this(languageCode, params, null, listener);
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params,
      Dictionary abbreviations,
      EvaluationSampleListener<SentenceSample> listener) {
    this(languageCode, params, abbreviations, Collections
        .singletonList(listener));
  }
  
  public SDCrossValidator(String languageCode) {
    this(languageCode, 5, 100);
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
        new CrossValidationPartitioner<SentenceSample>(samples, nFolds);
    
   while (partitioner.hasNext()) {
     
     CrossValidationPartitioner.TrainingSampleStream<SentenceSample> trainingSampleStream =
         partitioner.next();
     
      SentenceModel model; 
      
      model = SentenceDetectorME.train(languageCode, trainingSampleStream,
          true, abbreviations, params);
      
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
