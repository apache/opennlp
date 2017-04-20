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

  private Map<String, Object> parameters = new HashMap<>();

  public TrainingParameters() {
  }

  public TrainingParameters(TrainingParameters trainingParameters) {
    this.parameters.putAll(trainingParameters.parameters);
  }

  /**
   *
   * @deprecated
   */
  public TrainingParameters(Map<String,String> map) {
    //parameters.putAll(map);
    // try to respect their original type...
    for (String key: map.keySet()) {
      String value = map.get(key);
      try {
        int intValue = Integer.parseInt(value);
        parameters.put(key, intValue);
      }
      catch (NumberFormatException ei) {
        try {
          double doubleValue = Double.parseDouble(value);
          parameters.put(key, doubleValue);
        }
        catch (NumberFormatException ed) {
          // Because Boolean.parseBoolean() doesn't throw NFE, it just checks the value is either
          // true or yes. So let's see their letters here.
          if (value.toLowerCase().equals("true") || value.toLowerCase().equals("false")) {
            parameters.put(key, Boolean.parseBoolean(value));
          }
          else {
            parameters.put(key, value);
          }
        }
      }
    }
  }

  /* TODO: Once we throw Map<String,String> away, have this constructor to be uncommented
  public TrainingParameters(Map<String,Object> map) {
    parameters.putAll(map);
  }
  */

  public TrainingParameters(InputStream in) throws IOException {

    Properties properties = new Properties();
    properties.load(in);

    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      parameters.put((String) entry.getKey(), entry.getValue());
    }
  }

  /**
   * Retrieves the training algorithm name for a given name space.
   *
   * @return the name or null if not set.
   */
  public String algorithm(String namespace) {
    return (String)parameters.get(getKey(namespace, ALGORITHM_PARAM));
  }

  /**
   * Retrieves the training algorithm name.
   *
   * @return the name or null if not set.
   */
  public String algorithm() {
    return (String)parameters.get(ALGORITHM_PARAM);
  }

  /**
   * Retrieves a map with the training parameters which have the passed name space.
   *
   * @param namespace
   *
   * @return a parameter map which can be passed to the train and validate methods.
   *
   * @deprecated use {@link #getObjectSettings(String)} instead
   */
  public Map<String, String> getSettings(String namespace) {

    Map<String, String> trainingParams = new HashMap<>();
    String prefix = namespace + ".";

    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
      String key = entry.getKey();

      if (namespace != null) {
        if (key.startsWith(prefix))  {
          trainingParams.put(key.substring(prefix.length()), getStringValue(entry.getValue()));
        }
      }
      else {
        if (!key.contains(".")) {
          trainingParams.put(key, getStringValue(entry.getValue()));
        }
      }
    }

    return Collections.unmodifiableMap(trainingParams);
  }

  private static String getStringValue(Object value) {
    if (value instanceof Integer) {
      return Integer.toString((Integer)value);
    }
    else if (value instanceof Double) {
      return Double.toString((Double)value);
    }
    else if (value instanceof Boolean) {
      return Boolean.toString((Boolean)value);
    }
    else {
      return (String)value;
    }
  }

  /**
   * Retrieves all parameters without a name space.
   *
   * @return the settings map
   *
   * @deprecated use {@link #getObjectSettings()} instead
   */
  public Map<String, String> getSettings() {
    return getSettings(null);
  }

  /**
   * Retrieves a map with the training parameters which have the passed name space.
   *
   * @param namespace
   *
   * @return a parameter map which can be passed to the train and validate methods.
   */
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

  /**
   * Retrieves all parameters without a name space.
   *
   * @return the settings map
   */
  public Map<String, Object> getObjectSettings() {
    return getObjectSettings(null);
  }

  // reduces the params to contain only the params in the name space
  public TrainingParameters getParameters(String namespace) {

    TrainingParameters params = new TrainingParameters();
    Map<String, Object> settings = getObjectSettings(namespace);

    for (String key: settings.keySet()) {
      Object value = settings.get(key);
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

  public void putIfAbsent(String namespace, String key, String value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  public void putIfAbsent(String key, String value) {
    putIfAbsent(null, key, value);
  }

  public void putIfAbsent(String namespace, String key, int value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  public void putIfAbsent(String key, int value) {
    putIfAbsent(null, key, value);
  }

  public void putIfAbsent(String namespace, String key, double value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  public void putIfAbsent(String key, double value) {
    putIfAbsent(null, key, value);
  }

  public void putIfAbsent(String namespace, String key, boolean value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  public void putIfAbsent(String key, boolean value) {
    putIfAbsent(null, key, value);
  }

  public void put(String namespace, String key, String value) {
    parameters.put(getKey(namespace, key), value);
  }

  public void put(String key, String value) {
    put(null, key, value);
  }

  public void put(String namespace, String key, int value) {
    parameters.put(getKey(namespace, key), value);
  }

  public void put(String key, int value) {
    put(null, key, value);
  }

  public void put(String namespace, String key, double value) {
    parameters.put(getKey(namespace, key), value);
  }

  public void put(String key, double value) {
    put(null, key, value);
  }

  public void put(String namespace, String key, boolean value) {
    parameters.put(getKey(namespace, key), value);
  }

  public void put(String key, boolean value) {
    put(null, key, value);
  }

  public void serialize(OutputStream out) throws IOException {
    Properties properties = new Properties();

    for (Map.Entry<String, Object> entry: parameters.entrySet()) {
      properties.put(entry.getKey(), entry.getValue());
    }

    properties.store(out, null);
  }

  /**
   * get a String parameter
   * @param key
   * @param defaultValue
   * @return
   * @throws {@link java.lang.ClassCastException} can be thrown if the value is not {@link String}
   */
  public String getStringParameter(String key, String defaultValue) {
    return getStringParameter(null, key, defaultValue);
  }

  /**
   * get a String parameter in the specified namespace
   * @param namespace
   * @param key
   * @param defaultValue
   * @return
   * @throws {@link java.lang.ClassCastException} can be thrown if the value is not {@link String}
   */
  public String getStringParameter(String namespace, String key, String defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      return (String)value;
    }
  }

  /**
   * get an Integer parameter
   * @param key
   * @param defaultValue
   * @return
   */
  public int getIntParameter(String key, int defaultValue) {
    return getIntParameter(null, key, defaultValue);
  }

  /**
   * get an Integer parameter in the specified namespace
   * @param namespace
   * @param key
   * @param defaultValue
   * @return
   */
  public int getIntParameter(String namespace, String key, int defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      // TODO: We have this try-catch for back-compat reason. After removing deprecated flag,
      // we can remove try-catch block and just return (Integer)value;
      try {
        return (Integer) value;
      }
      catch (ClassCastException e) {
        return Integer.parseInt((String)value);
      }
    }
  }

  /**
   * get a Double parameter
   * @param key
   * @param defaultValue
   * @return
   */
  public double getDoubleParameter(String key, double defaultValue) {
    return getDoubleParameter(null, key, defaultValue);
  }

  /**
   * get a Double parameter in the specified namespace
   * @param namespace
   * @param key
   * @param defaultValue
   * @return
   */
  public double getDoubleParameter(String namespace, String key, double defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      // TODO: We have this try-catch for back-compat reason. After removing deprecated flag,
      // we can remove try-catch block and just return (Double)value;
      try {
        return (Double) value;
      }
      catch (ClassCastException e) {
        return Double.parseDouble((String)value);
      }
    }
  }

  /**
   * get a Boolean parameter
   * @param key
   * @param defaultValue
   * @return
   */
  public boolean getBooleanParameter(String key, boolean defaultValue) {
    return getBooleanParameter(null, key, defaultValue);
  }

  /**
   * get a Boolean parameter in the specified namespace
   * @param namespace
   * @param key
   * @param defaultValue
   * @return
   */
  public boolean getBooleanParameter(String namespace, String key, boolean defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
    if (value == null) {
      return defaultValue;
    }
    else {
      // TODO: We have this try-catch for back-compat reason. After removing deprecated flag,
      // we can remove try-catch block and just return (Boolean)value;
      try {
        return (Boolean) value;
      }
      catch (ClassCastException e) {
        return Boolean.parseBoolean((String)value);
      }
    }
  }
  
  public static TrainingParameters defaultParams() {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, 5);

    return mlParams;
  }

  static String getKey(String namespace, String key) {
    if (namespace == null) {
      return key;
    }
    else {
      return namespace + "." + key;
    }
  }
}
