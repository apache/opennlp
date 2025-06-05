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
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import opennlp.tools.ml.EventTrainer;

/**
 * Declares and handles default parameters used for or during training models.
 */
public class TrainingParameters implements Parameters {

  private final Map<String, Object> parameters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  /**
   * No-arg constructor to create a default {@link TrainingParameters} instance.
   */
  public TrainingParameters() {
  }

  /**
   * Copy constructor to hand over the config of existing {@link TrainingParameters}.
   */
  public TrainingParameters(TrainingParameters trainingParameters) {
    this.parameters.putAll(trainingParameters.parameters);
  }

  /**
   * Key-value based constructor to apply a {@link Map} based configuration initialization.
   */
  public TrainingParameters(Map<String,Object> map) {
    parameters.putAll(map);
  }

  /**
   * {@link InputStream} based constructor that reads in {@link TrainingParameters}.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public TrainingParameters(InputStream in) throws IOException {

    Properties properties = new Properties();
    properties.load(in);

    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      parameters.put((String) entry.getKey(), entry.getValue());
    }
  }

  @Override
  public String algorithm(String namespace) {
    return (String)parameters.get(Parameters.getKey(namespace, Parameters.ALGORITHM_PARAM));
  }

  @Override
  public String algorithm() {
    return (String)parameters.get(Parameters.ALGORITHM_PARAM);
  }

  @Override
  public Map<String, Object> getObjectSettings(String namespace) {

    Map<String, Object> trainingParams = new HashMap<>();
    String prefix = namespace + ".";

    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
      String key = entry.getKey();

      if (namespace != null) {
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

  @Override
  public Map<String, Object> getObjectSettings() {
    return getObjectSettings(null);
  }

  /**
   * @param namespace The name space to filter or narrow the search space. May be {@code null}.
   *
   * @return Retrieves {@link TrainingParameters} which can be passed to the train and validate methods.
   */
  public TrainingParameters getParameters(String namespace) {

    TrainingParameters params = new TrainingParameters();
    Map<String, Object> settings = getObjectSettings(namespace);

    for (Entry<String, Object> entry: settings.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Integer) {
        params.put(key, (Integer)value);
      }
      else if (value instanceof Double) {
        params.put(key, (Double)value);
      }
      else if (value instanceof Boolean) {
        params.put(key, (Boolean)value);
      }
      else {
        params.put(key, (String)value);
      }
    }

    return params;
  }

  @Override
  public void putIfAbsent(String namespace, String key, String value) {
    parameters.putIfAbsent(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void putIfAbsent(String key, String value) {
    putIfAbsent(null, key, value);
  }

  @Override
  public void putIfAbsent(String namespace, String key, int value) {
    parameters.putIfAbsent(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void putIfAbsent(String key, int value) {
    putIfAbsent(null, key, value);
  }

  @Override
  public void putIfAbsent(String namespace, String key, double value) {
    parameters.putIfAbsent(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void putIfAbsent(String key, double value) {
    putIfAbsent(null, key, value);
  }

  @Override
  public void putIfAbsent(String namespace, String key, boolean value) {
    parameters.putIfAbsent(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void putIfAbsent(String key, boolean value) {
    putIfAbsent(null, key, value);
  }

  @Override
  public void put(String namespace, String key, String value) {
    parameters.put(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void put(String key, String value) {
    put(null, key, value);
  }

  @Override
  public void put(String namespace, String key, int value) {
    parameters.put(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void put(String key, int value) {
    put(null, key, value);
  }

  @Override
  public void put(String namespace, String key, double value) {
    parameters.put(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void put(String key, double value) {
    put(null, key, value);
  }

  @Override
  public void put(String namespace, String key, boolean value) {
    parameters.put(Parameters.getKey(namespace, key), value);
  }

  @Override
  public void put(String key, boolean value) {
    put(null, key, value);
  }

  @Override
  public void serialize(OutputStream out) throws IOException {
    Properties properties = new Properties();
    properties.putAll(parameters);
    properties.store(out, null);
  }

  @Override
  public String getStringParameter(String key, String defaultValue) {
    return getStringParameter(null, key, defaultValue);
  }

  @Override
  public String getStringParameter(String namespace, String key, String defaultValue) {
    Object value = parameters.get(Parameters.getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      return (String)value;
    }
  }

  @Override
  public int getIntParameter(String key, int defaultValue) {
    return getIntParameter(null, key, defaultValue);
  }

  @Override
  public int getIntParameter(String namespace, String key, int defaultValue) {
    Object value = parameters.get(Parameters.getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      try {
        return (Integer) value;
      }
      catch (ClassCastException e) {
        return Integer.parseInt((String)value);
      }
    }
  }

  @Override
  public double getDoubleParameter(String key, double defaultValue) {
    return getDoubleParameter(null, key, defaultValue);
  }

  @Override
  public double getDoubleParameter(String namespace, String key, double defaultValue) {
    Object value = parameters.get(Parameters.getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      try {
        return (Double) value;
      }
      catch (ClassCastException e) {
        return Double.parseDouble((String)value);
      }
    }
  }

  @Override
  public boolean getBooleanParameter(String key, boolean defaultValue) {
    return getBooleanParameter(null, key, defaultValue);
  }

  @Override
  public boolean getBooleanParameter(String namespace, String key, boolean defaultValue) {
    Object value = parameters.get(Parameters.getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      try {
        return (Boolean) value;
      }
      catch (ClassCastException e) {
        return Boolean.parseBoolean((String)value);
      }
    }
  }

  /**
   * @return Retrieves a new {@link TrainingParameters instance} initialized with default values.
   */
  public static TrainingParameters defaultParams() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(Parameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(Parameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
    mlParams.put(Parameters.ITERATIONS_PARAM, Parameters.ITERATIONS_DEFAULT_VALUE);
    mlParams.put(Parameters.CUTOFF_PARAM, Parameters.CUTOFF_DEFAULT_VALUE);

    return mlParams;
  }

  /**
   * @param params The parameters to additionally apply into the new {@link TrainingParameters instance}.
   *               
   * @return Retrieves a new {@link TrainingParameters instance} initialized with given parameter values.
   */
  public static TrainingParameters setParams(String[] params) {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(Parameters.ALGORITHM_PARAM , "MAXENT");
    mlParams.put(Parameters.TRAINER_TYPE_PARAM , EventTrainer.EVENT_VALUE);
    mlParams.put(Parameters.ITERATIONS_PARAM ,
        null != getIntParameter("-" + Parameters.ITERATIONS_PARAM.toLowerCase() , params) ?
            getIntParameter("-" + Parameters.ITERATIONS_PARAM.toLowerCase() , params) :
            Parameters.ITERATIONS_DEFAULT_VALUE);
    mlParams.put(Parameters.CUTOFF_PARAM ,
        null != getIntParameter("-" + Parameters.CUTOFF_PARAM.toLowerCase() , params) ?
            getIntParameter("-" + Parameters.CUTOFF_PARAM.toLowerCase() , params) :
            Parameters.CUTOFF_DEFAULT_VALUE);

    return mlParams;
  }

  /**
   * Retrieves the specified parameter from the specified arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  private static Integer getIntParameter(String param, String[] args) {
    String value = getParameter(param, args);

    try {
      if (value != null)
        return Integer.parseInt(value);
    }
    catch (NumberFormatException ignored) {
      // in this case return null
    }

    return null;
  }

  /**
   * Retrieves the specified parameter from the given arguments.
   *
   * @param param parameter name
   * @param args arguments
   * @return parameter value
   */
  private static String getParameter(String param, String[] args) {
    int i = getParameterIndex(param, args);
    if (-1 < i) {
      i++;
      if (i < args.length) {
        return args[i];
      }
    }

    return null;
  }

  /**
   * Returns the index of the parameter in the arguments, or {@code -1} if the parameter is not found.
   *
   * @param param parameter name
   * @param args arguments
   * @return the index of the parameter in the arguments, or {@code -1} if the parameter is not found
   */
  private static int getParameterIndex(String param, String[] args) {
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("-") && args[i].equals(param)) {
        return i;
      }
    }

    return -1;
  }

}
