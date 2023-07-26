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

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.util.TrainingParameters;

public abstract class AbstractTrainer implements Trainer {

  public static final String ALGORITHM_PARAM = "Algorithm";

  public static final String TRAINER_TYPE_PARAM = "TrainerType";

  public static final String CUTOFF_PARAM = "Cutoff";
  public static final int CUTOFF_DEFAULT = 5;

  public static final String ITERATIONS_PARAM = "Iterations";
  public static final int ITERATIONS_DEFAULT = 100;

  protected TrainingParameters trainingParameters;
  protected Map<String,String> reportMap;

  public AbstractTrainer() {
  }

  /**
   * Initializes a {@link AbstractTrainer} via {@link TrainingParameters}.
   *
   * @param trainParams The {@link TrainingParameters} to use.
   */
  public AbstractTrainer(TrainingParameters trainParams) {
    init(trainParams,new HashMap<>());
  }

  /**
   * Initializes a {@link AbstractTrainer} via {@link TrainingParameters} and
   * a {@link Map report map}.
   *
   * @param trainParams The {@link TrainingParameters} to use.
   * @param reportMap The {@link Map} instance used as report map.
   */
  @Override
  public void init(TrainingParameters trainParams, Map<String,String> reportMap) {
    this.trainingParameters = trainParams;
    if (reportMap == null) reportMap = new HashMap<>();
    this.reportMap = reportMap;
  }

  /**
   * @return Retrieves the configured {@link #ALGORITHM_PARAM} value.
   */
  public String getAlgorithm() {
    return trainingParameters.getStringParameter(ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);
  }

  /**
   * @return Retrieves the configured {@link #CUTOFF_PARAM} value.
   */
  public int getCutoff() {
    return trainingParameters.getIntParameter(CUTOFF_PARAM, CUTOFF_DEFAULT);
  }

  /**
   * @return Retrieves the configured {@link #ITERATIONS_PARAM} value.
   */
  public int getIterations() {
    return trainingParameters.getIntParameter(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
  }

  /**
   * Checks the configured {@link TrainingParameters parameters}.
   * If a subclass overrides this, it should call {@code super.validate();}.
   *
   * @throws IllegalArgumentException Thrown if default training parameters are invalid.
   */
  public void validate() {
    // TODO: Need to validate all parameters correctly ... error prone?!
    // should validate if algorithm is set? What about the Parser?

    try {
      trainingParameters.getIntParameter(CUTOFF_PARAM, CUTOFF_DEFAULT);
      trainingParameters.getIntParameter(ITERATIONS_PARAM, ITERATIONS_DEFAULT);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Adds the key-value pair to the report map.
   * @param key The identifying string associated with a certain training parameter.
   * @param value The parameter value associated with {@code key}.
   */
  protected void addToReport(String key, String value) {
    reportMap.put(key, value);
  }

}
