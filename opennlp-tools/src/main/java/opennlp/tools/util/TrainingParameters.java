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

package opennlp.tools.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import opennlp.tools.ml.EventTrainer;

public class TrainingParameters {

  // TODO: are them duplicated?
  public static final String ALGORITHM_PARAM = "Algorithm";
  public static final String TRAINER_TYPE_PARAM = "TrainerType";

  public static final String ITERATIONS_PARAM = "Iterations";
  public static final String CUTOFF_PARAM = "Cutoff";
  public static final String THREADS_PARAM = "Threads";

  private Map<String, String> parameters = new HashMap<>();

  public TrainingParameters() {
  }

  public TrainingParameters(TrainingParameters trainingParameters) {
    this.parameters.putAll(trainingParameters.parameters);
  }
  
  public TrainingParameters(Map<String,String> map) {
    parameters.putAll(map);
  }
  
  public TrainingParameters(InputStream in) throws IOException {

    Properties properties = new Properties();
    properties.load(in);

    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      parameters.put((String) entry.getKey(), (String) entry.getValue());
    }
  }

  /**
   * Retrieves the training algorithm name for a given name space.
   *
   * @return the name or null if not set.
   */
  public String algorithm(String namespace) {
    return parameters.get(namespace + "." + ALGORITHM_PARAM);
  }

  /**
   * Retrieves the training algorithm name.
   *
   * @return the name or null if not set.
   */
  public String algorithm() {
    return parameters.get(ALGORITHM_PARAM);
  }

  /**
   * Retrieves a map with the training parameters which have the passed name space.
   *
   * @param namespace
   *
   * @return a parameter map which can be passed to the train and validate methods.
   */
  public Map<String, String> getSettings(String namespace) {

    Map<String, String> trainingParams = new HashMap<>();

    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      String key = entry.getKey();

      if (namespace != null) {
        String prefix = namespace + ".";

        if (key.startsWith(prefix))  {
          trainingParams.put(key.substring(prefix.length()), entry.getValue());
        }
      }
      else {
        if (!key.contains(".")) {
          trainingParams.put(key, entry.getValue());
        }
      }
    }

    return Collections.unmodifiableMap(trainingParams);
  }

  /**
   * Retrieves all parameters without a name space.
   *
   * @return the settings map
   */
  public Map<String, String> getSettings() {
    return getSettings(null);
  }

  // reduces the params to contain only the params in the name space
  public TrainingParameters getParameters(String namespace) {

    TrainingParameters params = new TrainingParameters();

    for (Map.Entry<String, String> entry : getSettings(namespace).entrySet()) {
      params.put(entry.getKey(), entry.getValue());
    }

    return params;
  }

  public void put(String namespace, String key, String value) {

    if (namespace == null) {
      parameters.put(key, value);
    }
    else {
      parameters.put(namespace + "." + key, value);
    }
  }

  public void put(String key, String value) {
    put(null, key, value);
  }

  public void serialize(OutputStream out) throws IOException {
    Properties properties = new Properties();

    for (Map.Entry<String, String> entry : parameters.entrySet()) {
      properties.put(entry.getKey(), entry.getValue());
    }

    properties.store(out, null);
  }

  public String getStringParameter(String key, String defaultValue) {
    return parameters.getOrDefault(key, defaultValue);
  }
  
  public String getStringParameter(String namespace, String key, String defaultValue) {
    if (namespace == null) {
      return getStringParameter(key, defaultValue);
    }
    return parameters.getOrDefault(namespace + "." + key, defaultValue);
  }
  
  public int getIntParameter(String key, int defaultValue) {
    String value = parameters.getOrDefault(key, Integer.toString(defaultValue));
    return Integer.parseInt(value);
  }
  
  public int getIntParameter(String namespace, String key, int defaultValue) {
    if (namespace == null) {
      return getIntParameter(key, defaultValue);
    }
    String value = parameters.getOrDefault(namespace + "." + key, Integer.toString(defaultValue));
    return Integer.parseInt(value);
  }
  
  public double getDoubleParameter(String key, double defaultValue) {
    String value = parameters.getOrDefault(key, Double.toString(defaultValue));
    return Double.parseDouble(value);
  }
  
  public double getDoubleParameter(String namespace, String key, double defaultValue) {
    if (namespace == null) {
      return getDoubleParameter(key, defaultValue);
    }
    String value = parameters.getOrDefault(namespace + "." + key, Double.toString(defaultValue));
    return Double.parseDouble(value);
  }
  
  public boolean getBooleanParameter(String key, boolean defaultValue) {
    String value = parameters.getOrDefault(key, Boolean.toString(defaultValue));
    return Boolean.parseBoolean(value);
  }
  
  public boolean getBooleanParameter(String namespace, String key, boolean defaultValue) {
    if (namespace == null) {
      return getBooleanParameter(key, defaultValue);
    }
    String value = parameters.getOrDefault(namespace + "." + key, Boolean.toString(defaultValue));
    return Boolean.parseBoolean(value);
  }
  
  public static TrainingParameters defaultParams() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, Integer.toString(100));
    mlParams.put(TrainingParameters.CUTOFF_PARAM, Integer.toString(5));

    return mlParams;
  }
}
