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

package opennlp.tools.ml;

import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.util.TrainingParameters;

public abstract class AbstractTrainer {

  public static final String ALGORITHM_PARAM = "Algorithm";

  public static final String TRAINER_TYPE_PARAM = "TrainerType";

  public static final String CUTOFF_PARAM = "Cutoff";
  public static final int CUTOFF_DEFAULT = 5;

  public static final String ITERATIONS_PARAM = "Iterations";
  public static final int ITERATIONS_DEFAULT = 100;

  public static final String VERBOSE_PARAM = "PrintMessages";
  public static final boolean VERBOSE_DEFAULT = true;

  protected TrainingParameters trainingParameters;
  protected Map<String,String> reportMap;

  protected boolean printMessages;

  public AbstractTrainer() {
  }

  public AbstractTrainer(TrainingParameters parameters) {
    init(parameters,new HashMap<>());
  }
  
  public void init(TrainingParameters trainingParameters, Map<String,String> reportMap) {
    this.trainingParameters = trainingParameters;
    if (reportMap == null) reportMap = new HashMap<>();
    this.reportMap = reportMap;
    printMessages = trainingParameters.getBooleanParameter(VERBOSE_PARAM, VERBOSE_DEFAULT);
  }
  
  @Deprecated
  public void init(Map<String, String> trainParams, Map<String, String> reportMap) {
    init(new TrainingParameters(trainParams),reportMap);
  }

  public String getAlgorithm() {
    return trainingParameters.getStringParameter(ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);
  }

  public int getCutoff() {
    return trainingParameters.getIntParameter(CUTOFF_PARAM, CUTOFF_DEFAULT);
  }

  public int getIterations() {
    return trainingParameters.getIntParameter(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
  }

  public boolean isValid() {

    // TODO: Need to validate all parameters correctly ... error prone?!

    // should validate if algorithm is set? What about the Parser?

    try {
      trainingParameters.getIntParameter(CUTOFF_PARAM, CUTOFF_DEFAULT);
      trainingParameters.getIntParameter(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
    } catch (NumberFormatException e) {
      return false;
    }
    
    return true;
  }

/**
   * Use the TrainingParameters directly...
   * @param key
   * @param defaultValue
   */
  @Deprecated
  protected String getStringParam(String key, String defaultValue) {
    return trainingParameters.getStringParameter(key, defaultValue);
  }

  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param defaultValue
   */
  @Deprecated
  protected int TrainingParameters(String key, int defaultValue) {
    return trainingParameters.getIntParameter(key, defaultValue);
  }
  
  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param defaultValue
   */
  @Deprecated
  protected double getDoubleParam(String key, double defaultValue) {
    return trainingParameters.getDoubleParameter(key, defaultValue);
  }

  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param defaultValue
   */
  @Deprecated
  protected boolean getBooleanParam(String key, boolean defaultValue) {
    return trainingParameters.getBooleanParameter(key, defaultValue);
  }

  /**
   * Adds the key/Value to the report map.
   * @param key
   * @param value
   */
  protected void addToReport(String key, String value) {
    reportMap.put(key, value);
  }

  protected void display(String s) {
    if (printMessages) {
      System.out.print(s);
    }
  }
}
