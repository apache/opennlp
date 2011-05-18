/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.model;

import java.io.IOException;
import java.util.Map;

import opennlp.perceptron.SimplePerceptronSequenceTrainer;

public class TrainUtil {

  public static final String ALGORITHM_PARAM = "Algorithm";
  
  public static final String MAXENT_VALUE = "MAXENT";
  public static final String PERCEPTRON_VALUE = "PERCEPTRON";
  public static final String PERCEPTRON_SEQUENCE_VALUE = "PERCEPTRON_SEQUENCE";
  
  
  public static final String CUTOFF_PARAM = "Cutoff";
  public static final String ITERATIONS_PARAM = "Iterations";
  
  private static final int ITERATIONS_DEFAULT = 100;
  private static final int CUTOFF_DEFAULT = 5;
  
  
  private static int getIntParam(Map<String, String> trainParams, String key,
      int defaultValue) {
    
    String valueString = trainParams.get(key);
    
    if (valueString != null)
      return Integer.parseInt(valueString);
    else
      return defaultValue;
  }
  
  public static boolean isValid(Map<String, String> trainParams) {
    
    String algorithmName = trainParams.get(ALGORITHM_PARAM);
    
    if (!(MAXENT_VALUE.equals(algorithmName) || 
        PERCEPTRON_VALUE.equals(algorithmName) ||
        PERCEPTRON_SEQUENCE_VALUE.equals(algorithmName))) {
      return false;
    }
    
    try {
      String cutoffString = trainParams.get(CUTOFF_PARAM);
      if (cutoffString != null) Integer.parseInt(cutoffString);
      
      String iterationsString = trainParams.get(ITERATIONS_PARAM);
      if (iterationsString != null) Integer.parseInt(iterationsString);
    }
    catch (NumberFormatException e) {
      return false;
    }
    
    // TODO: Check data indexing ... 
     
    return true;
  }
  
  public static AbstractModel train(EventStream events, Map<String, String> trainParams) 
      throws IOException {
    
    // if PERCEPTRON or MAXENT
    String algorithmName = trainParams.get(ALGORITHM_PARAM);
    
    // String DataIndexing -> OnePass|TwoPass
    // TODO: Make data indexing configurable ... 
    
    int iterations = getIntParam(trainParams, ITERATIONS_PARAM, ITERATIONS_DEFAULT);
    int cutoff = getIntParam(trainParams, CUTOFF_PARAM, CUTOFF_DEFAULT);
    
    AbstractModel model;
    if (MAXENT_VALUE.equals(algorithmName)) {
      model = opennlp.maxent.GIS.trainModel(iterations,
          new TwoPassDataIndexer(events, cutoff));
    }
    else if (PERCEPTRON_VALUE.equals(algorithmName)) {
      boolean useAverage = true; // <- read from params 
      boolean sort = false; // <- read from params
      
      model = new opennlp.perceptron.PerceptronTrainer().trainModel(
          iterations, new TwoPassDataIndexer(events,
          cutoff, sort), cutoff, useAverage);
    }
    else {
      throw new IllegalStateException("Algorithm not supported: " + algorithmName);
    }
    
    return model;
  }
  
  /**
   * Detects if the training algorithm requires sequence based feature generation
   * or not.
   */
  public static boolean isSequenceTraining(Map<String, String> trainParams) {
    
    String algorithmName = trainParams.get(ALGORITHM_PARAM);
    
    return PERCEPTRON_SEQUENCE_VALUE.equals(algorithmName);
  }
  
  public static AbstractModel train(SequenceStream events, Map<String, String> trainParams) 
      throws IOException {
    
    if (!isSequenceTraining(trainParams))
      throw new IllegalArgumentException("Algorithm must be a sequence algorithm!");
    
    int iterations = getIntParam(trainParams, ITERATIONS_PARAM, ITERATIONS_DEFAULT);
    int cutoff = getIntParam(trainParams, CUTOFF_PARAM, CUTOFF_DEFAULT);
    
    boolean useAverage = true; // <- TODO: read from params
    
    return new SimplePerceptronSequenceTrainer().trainModel(
        iterations, events, cutoff,useAverage);
  }
}
