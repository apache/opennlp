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

  private Map<String, String> trainParams;
  private Map<String, String> reportMap;

  public AbstractTrainer() {
  }

  public void init(Map<String, String> trainParams, Map<String, String> reportMap) {
    this.trainParams = trainParams;
    this.reportMap = reportMap;
  }

  public String getAlgorithm() {
    return getStringParam(ALGORITHM_PARAM, GIS.MAXENT_VALUE);
  }

  public int getCutoff() {
    return getIntParam(CUTOFF_PARAM, CUTOFF_DEFAULT);
  }

  public int getIterations() {
    return getIntParam(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
  }

  protected String getStringParam(String key, String defaultValue) {

    String valueString = trainParams.get(key);

    if (valueString == null)
      valueString = defaultValue;

    if (reportMap != null)
      reportMap.put(key, valueString);

    return valueString;
  }

  protected int getIntParam(String key, int defaultValue) {

    String valueString = trainParams.get(key);

    if (valueString != null)
      return Integer.parseInt(valueString);
    else
      return defaultValue;
  }

  protected double getDoubleParam(String key, double defaultValue) {

    String valueString = trainParams.get(key);

    if (valueString != null)
      return Double.parseDouble(valueString);
    else
      return defaultValue;
  }

  protected boolean getBooleanParam(String key, boolean defaultValue) {

    String valueString = trainParams.get(key);

    if (valueString != null)
      return Boolean.parseBoolean(valueString);
    else
      return defaultValue;
  }

  protected void addToReport(String key, String value) {
    if (reportMap != null) {
      reportMap.put(key, value);
    }
  }

  public boolean isValid() {

    // TODO: Need to validate all parameters correctly ... error prone?!

    // should validate if algorithm is set? What about the Parser?

    try {
      String cutoffString = trainParams.get(CUTOFF_PARAM);
      if (cutoffString != null)
        Integer.parseInt(cutoffString);

      String iterationsString = trainParams.get(ITERATIONS_PARAM);
      if (iterationsString != null)
        Integer.parseInt(iterationsString);
    } catch (NumberFormatException e) {
      return false;
    }

    return true;
  }
}
