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

package opennlp.tools.tokenize;

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

public class TokenizerCrossValidator {
  
  private final String language;
  private final boolean alphaNumericOptimization;
  
  private final TrainingParameters params;
  
  private final Dictionary abbreviations;
  
  private FMeasure fmeasure = new FMeasure();
  private LinkedList<EvaluationSampleListener<TokenSample>> listeners;
  
  
  public TokenizerCrossValidator(String language, boolean alphaNumericOptimization, int cutoff, int iterations) {
    this(language, alphaNumericOptimization, createTrainingParameters(iterations, cutoff));
  }
  
  public TokenizerCrossValidator(String language, boolean alphaNumericOptimization) {
    this(language, alphaNumericOptimization, createTrainingParameters(100, 5));
  }  
  
  public TokenizerCrossValidator(String language, boolean alphaNumericOptimization, TrainingParameters params) {
    this(language, null, alphaNumericOptimization, params);
  }
  
  public TokenizerCrossValidator(String language, Dictionary abbreviations,
      boolean alphaNumericOptimization, TrainingParameters params) {
    
    this.language = language;
    this.alphaNumericOptimization = alphaNumericOptimization;
    this.abbreviations = abbreviations;
    this.params = params;
    
  }
  
  public TokenizerCrossValidator(String language,
      boolean alphaNumericOptimization, TrainingParameters params,
      List<? extends EvaluationSampleListener<TokenSample>> listeners) {
    this(language, null, alphaNumericOptimization, params, listeners);
  }

  public TokenizerCrossValidator(String language, Dictionary abbreviations,
      boolean alphaNumericOptimization, TrainingParameters params,
      List<? extends EvaluationSampleListener<TokenSample>> listeners) {

    this(language, abbreviations, alphaNumericOptimization, params);
    if (listeners != null) {
      this.listeners = new LinkedList<EvaluationSampleListener<TokenSample>>(
          listeners);
    }
  }

  public TokenizerCrossValidator(String language,
      boolean alphaNumericOptimization, TrainingParameters params,
      EvaluationSampleListener<TokenSample> listener) {
    this(language, null, alphaNumericOptimization, params, Collections
        .singletonList(listener));
  }

  public TokenizerCrossValidator(String language, Dictionary abbreviations,
      boolean alphaNumericOptimization, TrainingParameters params,
      EvaluationSampleListener<TokenSample> listener) {
    this(language, abbreviations, alphaNumericOptimization, params, Collections
        .singletonList(listener));
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
  public void evaluate(ObjectStream<TokenSample> samples, int nFolds) throws IOException {
    
    CrossValidationPartitioner<TokenSample> partitioner = 
      new CrossValidationPartitioner<TokenSample>(samples, nFolds);
  
     while (partitioner.hasNext()) {
       
       CrossValidationPartitioner.TrainingSampleStream<TokenSample> trainingSampleStream =
         partitioner.next();
       
       // Maybe throws IOException if temporary file handling fails ...
       TokenizerModel model;
       
      model = TokenizerME.train(language, trainingSampleStream, abbreviations,
          alphaNumericOptimization, params);
       
       TokenizerEvaluator evaluator = new TokenizerEvaluator(new TokenizerME(model), listeners);
       
       evaluator.evaluate(trainingSampleStream.getTestSampleStream());
       fmeasure.mergeInto(evaluator.getFMeasure());
     }
  }
  
  public FMeasure getFMeasure() {
    return fmeasure;
  }
  
  //TODO: this could go to a common util method, maybe inside TrainingParameters class
  static TrainingParameters createTrainingParameters(int iterations, int cutoff) {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.ITERATIONS_PARAM,
        Integer.toString(iterations));
    mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(cutoff));
    return mlParams;
  }
}
