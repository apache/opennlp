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
import java.util.HashMap;
import java.util.Map;

import opennlp.perceptron.PerceptronTrainer;
import opennlp.perceptron.SimplePerceptronSequenceTrainer;

public class TrainUtil {

  public static final String ALGORITHM_PARAM = "Algorithm";
  
  public static final String MAXENT_VALUE = "MAXENT";
  public static final String PERCEPTRON_VALUE = "PERCEPTRON";
  public static final String PERCEPTRON_SEQUENCE_VALUE = "PERCEPTRON_SEQUENCE";
  
  
  public static final String CUTOFF_PARAM = "Cutoff";
  private static final int CUTOFF_DEFAULT = 5;
  
  public static final String ITERATIONS_PARAM = "Iterations";
  private static final int ITERATIONS_DEFAULT = 100;
  
  public static final String DATA_INDEXER_PARAM = "DataIndexer";
  public static final String DATA_INDEXER_ONE_PASS_VALUE = "OnePass";
  public static final String DATA_INDEXER_TWO_PASS_VALUE = "TwoPass";
  
  
  private static String getStringParam(Map<String, String> trainParams, String key,
      String defaultValue, Map<String, String> reportMap) {

    String valueString = trainParams.get(key);

    if (valueString == null)
      valueString = defaultValue;
    
    if (reportMap != null)
      reportMap.put(key, valueString);
    
    return valueString;
  }
  
  private static int getIntParam(Map<String, String> trainParams, String key,
      int defaultValue, Map<String, String> reportMap) {

    String valueString = trainParams.get(key);

    if (valueString != null)
      return Integer.parseInt(valueString);
    else
      return defaultValue;
  }
  
  private static double getDoubleParam(Map<String, String> trainParams, String key,
      double defaultValue, Map<String, String> reportMap) {
    
    String valueString = trainParams.get(key);
    
    if (valueString != null)
      return Double.parseDouble(valueString);
    else
      return defaultValue;
  }
  
  private static boolean getBooleanParam(Map<String, String> trainParams, String key,
      boolean defaultValue, Map<String, String> reportMap) {

    String valueString = trainParams.get(key);

    if (valueString != null)
      return Boolean.parseBoolean(valueString);
    else
      return defaultValue;
  }
  
  public static boolean isValid(Map<String, String> trainParams) {

    // TODO: Need to validate all parameters correctly ... error prone?!
    
    String algorithmName = trainParams.get(ALGORITHM_PARAM);

    if (algorithmName != null && !(MAXENT_VALUE.equals(algorithmName) || 
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
    
    String dataIndexer = trainParams.get(DATA_INDEXER_PARAM);
    
    if (dataIndexer != null) {
      if (!("OnePass".equals(dataIndexer) || "TwoPass".equals(dataIndexer))) {
        return false;
      }
    }
    
    // TODO: Check data indexing ... 
     
    return true;
  }
  
  
  
  // TODO: Need a way to report results and settings back for inclusion in model ...
  
  public static AbstractModel train(EventStream events, Map<String, String> trainParams, Map<String, String> reportMap) 
      throws IOException {
    
    if (!isValid(trainParams))
        throw new IllegalArgumentException("trainParams are not valid!");
    
    if(isSequenceTraining(trainParams))
      throw new IllegalArgumentException("sequence training is not supported by this method!");
    
    String algorithmName = getStringParam(trainParams, ALGORITHM_PARAM, MAXENT_VALUE, reportMap);
    
    int iterations = getIntParam(trainParams, ITERATIONS_PARAM, ITERATIONS_DEFAULT, reportMap);
        
    int cutoff = getIntParam(trainParams, CUTOFF_PARAM, CUTOFF_DEFAULT, reportMap);

    boolean sortAndMerge;
    
    if (MAXENT_VALUE.equals(algorithmName))
        sortAndMerge = true;
    else if (PERCEPTRON_VALUE.equals(algorithmName))
      sortAndMerge = false;
    else
      throw new IllegalStateException("Unexpected algorihtm name: " + algorithmName);

    HashSumEventStream hses = new HashSumEventStream(events);
    
    String dataIndexerName = getStringParam(trainParams, DATA_INDEXER_PARAM,
        DATA_INDEXER_TWO_PASS_VALUE, reportMap);

    DataIndexer indexer = null;
    
    if (DATA_INDEXER_ONE_PASS_VALUE.equals(dataIndexerName)) {
      indexer = new OnePassDataIndexer(hses, cutoff, sortAndMerge);
    }
    else if (DATA_INDEXER_TWO_PASS_VALUE.equals(dataIndexerName)) {
      indexer = new TwoPassDataIndexer(hses, cutoff, sortAndMerge);
    }
    else {
      throw new IllegalStateException("Unexpected data indexer name: " +  dataIndexerName);
    }
    
    AbstractModel model;
    if (MAXENT_VALUE.equals(algorithmName)) {
      
      int threads = getIntParam(trainParams, "Threads", 1, reportMap);
      
      model = opennlp.maxent.GIS.trainModel(iterations, indexer,
          true, false, null, 0, threads);
    }
    else if (PERCEPTRON_VALUE.equals(algorithmName)) {
      boolean useAverage = getBooleanParam(trainParams, "UseAverage", true, reportMap);
      
      boolean useSkippedAveraging = getBooleanParam(trainParams, "UseSkippedAveraging", false, reportMap);
      
      // overwrite otherwise it might not work
      if (useSkippedAveraging)
        useAverage = true;
      
      double stepSizeDecrease = getDoubleParam(trainParams, "StepSizeDecrease", 0, reportMap);
      
      double tolerance = getDoubleParam(trainParams, "Tolerance", PerceptronTrainer.TOLERANCE_DEFAULT, reportMap);
      
      opennlp.perceptron.PerceptronTrainer perceptronTrainer = new opennlp.perceptron.PerceptronTrainer();
      perceptronTrainer.setSkippedAveraging(useSkippedAveraging);
      
      if (stepSizeDecrease > 0)
        perceptronTrainer.setStepSizeDecrease(stepSizeDecrease);
      
      perceptronTrainer.setTolerance(tolerance);
      
      model = perceptronTrainer.trainModel(
          iterations, indexer, cutoff, useAverage);
    }
    else {
      throw new IllegalStateException("Algorithm not supported: " + algorithmName);
    }
    
    if (reportMap != null)
        reportMap.put("Training-Eventhash", hses.calculateHashSum().toString(16));
    
    return model;
  }
  
  /**
   * Detects if the training algorithm requires sequence based feature generation
   * or not.
   */
  public static boolean isSequenceTraining(Map<String, String> trainParams) {
    return PERCEPTRON_SEQUENCE_VALUE.equals(trainParams.get(ALGORITHM_PARAM));
  }
  
  public static AbstractModel train(SequenceStream events, Map<String, String> trainParams,
      Map<String, String> reportMap) throws IOException {
    
    if (!isValid(trainParams))
      throw new IllegalArgumentException("trainParams are not valid!");
  
    if (!isSequenceTraining(trainParams))
      throw new IllegalArgumentException("Algorithm must be a sequence algorithm!");
    
    int iterations = getIntParam(trainParams, ITERATIONS_PARAM, ITERATIONS_DEFAULT, reportMap);
    int cutoff = getIntParam(trainParams, CUTOFF_PARAM, CUTOFF_DEFAULT, reportMap);
    
    boolean useAverage = getBooleanParam(trainParams, "UseAverage", true, reportMap);
    
    return new SimplePerceptronSequenceTrainer().trainModel(
        iterations, events, cutoff,useAverage);
  }
}
