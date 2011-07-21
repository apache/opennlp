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

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

/**
 * 
 */
public class SDCrossValidator {
  
  private final String languageCode;
  
  private final Dictionary abbreviations;
  
  private final TrainingParameters params;
  
  private FMeasure fmeasure = new FMeasure();
  
  public SDCrossValidator(String languageCode, int cutoff, int iterations) {
    this(languageCode, createParams(cutoff, iterations));
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params) {
    this(languageCode, params, null);
  }
  
  public SDCrossValidator(String languageCode, int cutoff, int iterations, Dictionary abbreviations) {
    this(languageCode, createParams(cutoff, iterations), abbreviations);
  }
  
  public SDCrossValidator(String languageCode, TrainingParameters params, Dictionary abbreviations) {
    this.languageCode = languageCode;
    this.params = params;
    this.abbreviations = abbreviations;
  }
  
  private static TrainingParameters createParams(int cutoff, int iterations) {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.ITERATIONS_PARAM,
        Integer.toString(iterations));
    mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cutoff));
    return mlParams;
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
  public void evaluate(ObjectStream<SentenceSample> samples, int nFolds)
      throws IOException {
    evaluate(samples, nFolds, false);
  }
  
  /**
   * Starts the evaluation.
   * 
   * @param samples
   *          the data to train and test
   * @param nFolds
   *          number of folds
   * @param printErrors
   *          if true will print errors
   * 
   * @throws IOException
   */
  public void evaluate(ObjectStream<SentenceSample> samples, int nFolds,
      boolean printErrors) throws IOException {

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
          new SentenceDetectorME(model), printErrors);

      evaluator.evaluate(trainingSampleStream.getTestSampleStream());
      
      fmeasure.mergeInto(evaluator.getFMeasure());
    }
  }
  
  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
