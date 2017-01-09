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

import java.util.Map;

import opennlp.tools.ml.maxent.GIS;

public abstract class AbstractTrainer {

  public static final String ALGORITHM_PARAM = "Algorithm";

  public static final String TRAINER_TYPE_PARAM = "TrainerType";

  public static final String CUTOFF_PARAM = "Cutoff";
  public static final int CUTOFF_DEFAULT = 5;

  public static final String ITERATIONS_PARAM = "Iterations";
  public static final int ITERATIONS_DEFAULT = 100;

  public static final String VERBOSE_PARAM = "PrintMessages";
  public static final boolean VERBOSE_DEFAULT = true;
  
  protected PluggableParameters parameters;

  public AbstractTrainer() {
  }

  public void init(Map<String, String> trainParams, Map<String, String> reportMap) {
    parameters = new PluggableParameters(trainParams, reportMap);
  }

  public String getAlgorithm() {
    return parameters.getStringParam(ALGORITHM_PARAM, GIS.MAXENT_VALUE);
  }

  public int getCutoff() {
    return parameters.getIntParam(CUTOFF_PARAM, CUTOFF_DEFAULT);
  }

  public int getIterations() {
    return parameters.getIntParam(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
  }

  public boolean isValid() {

    // TODO: Need to validate all parameters correctly ... error prone?!

    // should validate if algorithm is set? What about the Parser?

    try {
      parameters.getIntParam(CUTOFF_PARAM, CUTOFF_DEFAULT);
      parameters.getIntParam(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
    } catch (NumberFormatException e) {
      return false;
    }

    return true;
  }

/**
   * Use the PluggableParameters directly...
   * @param key
   * @param value
   */
  @Deprecated
  protected String getStringParam(String key, String defaultValue) {
    return parameters.getStringParam(key, defaultValue);
  }

  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param value
   */
  @Deprecated
  protected int getIntParam(String key, int defaultValue) {
    return parameters.getIntParam(key, defaultValue);
  }
  
  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param value
   */
  @Deprecated
  protected double getDoubleParam(String key, double defaultValue) {
    return parameters.getDoubleParam(key, defaultValue);
  }

  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param value
   */
  @Deprecated
  protected boolean getBooleanParam(String key, boolean defaultValue) {
    return parameters.getBooleanParam(key, defaultValue);
  }

  /**
   * Use the PluggableParameters directly...
   * @param key
   * @param value
   */
  @Deprecated
  protected void addToReport(String key, String value) {
    parameters.addToReport(key, value);
  }
}
