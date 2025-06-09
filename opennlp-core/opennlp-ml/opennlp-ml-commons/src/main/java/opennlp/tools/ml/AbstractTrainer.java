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
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingConfiguration;

public abstract class AbstractTrainer<P extends Parameters> implements Trainer<P> {

  protected P trainingParameters;
  protected Map<String,String> reportMap;
  protected TrainingConfiguration trainingConfiguration;

  public AbstractTrainer() {
  }

  /**
   * Initializes a {@link AbstractTrainer} via {@link Parameters}.
   *
   * @param trainParams The {@link Parameters} to use.
   */
  public AbstractTrainer(P trainParams) {
    init(trainParams,new HashMap<>());
  }

  /**
   * Initializes a {@link AbstractTrainer} via {@link Parameters} and
   * a {@link Map report map}.
   *
   * @param trainParams The {@link Parameters} to use.
   * @param reportMap The {@link Map} instance used as report map.
   */
  @Override
  public void init(P trainParams, Map<String,String> reportMap) {
    this.trainingParameters = trainParams;
    if (reportMap == null) reportMap = new HashMap<>();
    this.reportMap = reportMap;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void init(P trainParams, Map<String, String> reportMap,
                   TrainingConfiguration config) {
    init(trainParams, reportMap);
    this.trainingConfiguration = config;
  }

  /**
   * @return Retrieves the configured {@link Parameters#ALGORITHM_PARAM} value.
   */
  public String getAlgorithm() {
    return trainingParameters.getStringParameter(Parameters.ALGORITHM_PARAM,
        Parameters.ALGORITHM_DEFAULT_VALUE);
  }

  /**
   * @return Retrieves the configured {@link Parameters#CUTOFF_PARAM} value.
   */
  public int getCutoff() {
    return trainingParameters.getIntParameter(Parameters.CUTOFF_PARAM,
        Parameters.CUTOFF_DEFAULT_VALUE);
  }

  /**
   * @return Retrieves the configured {@link Parameters#ITERATIONS_PARAM} value.
   */
  public int getIterations() {
    return trainingParameters.getIntParameter(Parameters.ITERATIONS_PARAM,
        Parameters.ITERATIONS_DEFAULT_VALUE);
  }

  /**
   * Checks the configured {@link Parameters parameters}.
   * If a subclass overrides this, it should call {@code super.validate();}.
   *
   * @throws IllegalArgumentException Thrown if default training parameters are invalid.
   */
  public void validate() {
    // TODO: Need to validate all parameters correctly ... error prone?!
    // should validate if algorithm is set? What about the Parser?

    try {
      trainingParameters.getIntParameter(Parameters.CUTOFF_PARAM,
          Parameters.CUTOFF_DEFAULT_VALUE);
      trainingParameters.getIntParameter(Parameters.ITERATIONS_PARAM,
          Parameters.ITERATIONS_DEFAULT_VALUE);
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

  /**
   * Retrieves the {@link TrainingConfiguration} associated with an {@link AbstractTrainer}.
   * @return {@link TrainingConfiguration}
   */
  public TrainingConfiguration getTrainingConfiguration() {
    return trainingConfiguration;
  }

}
