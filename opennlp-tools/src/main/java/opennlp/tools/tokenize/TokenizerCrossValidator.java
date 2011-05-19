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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectStreamException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

public class TokenizerCrossValidator {
  
  private final String language;
  private final boolean alphaNumericOptimization;
  
  private final TrainingParameters params;
  
  private final int cutoff;
  private final int iterations;
  
  private FMeasure fmeasure = new FMeasure();
  
  
  public TokenizerCrossValidator(String language, boolean alphaNumericOptimization, int cutoff, int iterations) {
    this.language = language;
    this.alphaNumericOptimization = alphaNumericOptimization;
    this.cutoff = cutoff;
    this.iterations = iterations;
    
    params = null;
  }
  
  public TokenizerCrossValidator(String language, boolean alphaNumericOptimization) {
    this(language, alphaNumericOptimization, 5, 100);
  }  
  
  public TokenizerCrossValidator(String language, boolean alphaNumericOptimization, TrainingParameters params) {
    this.language = language;
    this.alphaNumericOptimization = alphaNumericOptimization;
    this.cutoff = -1;
    this.iterations = -1;
    
    this.params = params;
  }
  
  
  public void evaluate(ObjectStream<TokenSample> samples, int nFolds) 
      throws IOException {
    
    CrossValidationPartitioner<TokenSample> partitioner = 
      new CrossValidationPartitioner<TokenSample>(samples, nFolds);
  
     while (partitioner.hasNext()) {
       
       CrossValidationPartitioner.TrainingSampleStream<TokenSample> trainingSampleStream =
         partitioner.next();
       
       // Maybe throws IOException if temporary file handling fails ...
       TokenizerModel model;
       
       if (params == null) {
         model = TokenizerME.train(language, trainingSampleStream, 
             alphaNumericOptimization, cutoff, iterations);
       }
       else {
         model = TokenizerME.train(language, trainingSampleStream, 
             alphaNumericOptimization, params);
       }
       
       TokenizerEvaluator evaluator = new TokenizerEvaluator(new TokenizerME(model));
       evaluator.evaluate(trainingSampleStream.getTestSampleStream());
       fmeasure.mergeInto(evaluator.getFMeasure());
     }
  }
  
  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
