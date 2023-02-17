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

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.ml.EventTrainer;

/**
 * Declares and handles default parameters used for or during training models.
 */
public class TrainingParameters {

  // TODO: are them duplicated?
  public static final String ALGORITHM_PARAM = "Algorithm";
  public static final String TRAINER_TYPE_PARAM = "TrainerType";

  public static final String ITERATIONS_PARAM = "Iterations";
  public static final String CUTOFF_PARAM = "Cutoff";
  public static final String THREADS_PARAM = "Threads";
  public static final int ITERATIONS_DEFAULT_VALUE = 100;
  public static final int CUTOFF_DEFAULT_VALUE = 5;

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

  /**
   * @return Retrieves the training algorithm name for a given name space, or {@code null} if unset.
   */
  public String algorithm(String namespace) {
    return (String)parameters.get(getKey(namespace, ALGORITHM_PARAM));
  }

  /**
   * @return  Retrieves the training algorithm name. or @code null} if not set.
   */
  public String algorithm() {
    return (String)parameters.get(ALGORITHM_PARAM);
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
   * @param namespace The name space to filter or narrow the search space. May be {@code null}.
   *
   * @return Retrieves a parameter {@link Map} which can be passed to the train and validate methods.
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
   * @return Retrieves a parameter {@link Map} of all parameters without narrowing.
   */
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

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   * 
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, String value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String key, String value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, int value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String key, int value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, double value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String key, double value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String namespace, String key, boolean value) {
    parameters.putIfAbsent(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link TrainingParameters} instance.
   */
  public void putIfAbsent(String key, boolean value) {
    putIfAbsent(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String namespace, String key, String value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String key, String value) {
    put(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String namespace, String key, int value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String key, int value) {
    put(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String namespace, String key, double value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String key, double value) {
    put(null, key, value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String namespace, String key, boolean value) {
    parameters.put(getKey(namespace, key), value);
  }

  /**
   * Puts a {@code value} into the current {@link TrainingParameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link TrainingParameters} instance.
   */
  public void put(String key, boolean value) {
    put(null, key, value);
  }

  /**
   * Serializes a {@link TrainingParameters} instance via a specified {@link OutputStream}.
   *
   * @param out A valid, open {@link OutputStream} to write to.
   *
   * @throws IOException Thrown if errors occurred.
   */
  public void serialize(OutputStream out) throws IOException {
    Properties properties = new Properties();
    properties.putAll(parameters);
    properties.store(out, null);
  }

  /**
   * Obtains a training parameter value.
   * <p>
   * Note:
   * {@link java.lang.ClassCastException} can be thrown if the value is not {@code String}
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link String training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public String getStringParameter(String key, String defaultValue) {
    return getStringParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * Note:
   * {@link java.lang.ClassCastException} can be thrown if the value is not {@link String}
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link String training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
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
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Integer training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public int getIntParameter(String key, int defaultValue) {
    return getIntParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link Integer training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public int getIntParameter(String namespace, String key, int defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
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

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Double training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public double getDoubleParameter(String key, double defaultValue) {
    return getDoubleParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link Double training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public double getDoubleParameter(String namespace, String key, double defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
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

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Boolean training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public boolean getBooleanParameter(String key, boolean defaultValue) {
    return getBooleanParameter(null, key, defaultValue);
  }

  /**
   * Obtains a training parameter value in the specified namespace.
   * <p>
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link Boolean training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  public boolean getBooleanParameter(String namespace, String key, boolean defaultValue) {
    Object value = parameters.get(getKey(namespace, key));
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
    mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM, EventTrainer.EVENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, ITERATIONS_DEFAULT_VALUE);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, CUTOFF_DEFAULT_VALUE);

    return mlParams;
  }

  /**
   * @param params The parameters to additionally apply into the new {@link TrainingParameters instance}.
   *               
   * @return Retrieves a new {@link TrainingParameters instance} initialized with given parameter values.
   */
  public static TrainingParameters setParams(String[] params) {
    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ALGORITHM_PARAM , "MAXENT");
    mlParams.put(TrainingParameters.TRAINER_TYPE_PARAM , EventTrainer.EVENT_VALUE);
    mlParams.put(TrainingParameters.ITERATIONS_PARAM ,
        null != CmdLineUtil.getIntParameter("-" +
                TrainingParameters.ITERATIONS_PARAM.toLowerCase() , params) ?
            CmdLineUtil.getIntParameter("-" + TrainingParameters.ITERATIONS_PARAM.toLowerCase() , params) :
            ITERATIONS_DEFAULT_VALUE);
    mlParams.put(TrainingParameters.CUTOFF_PARAM ,
        null != CmdLineUtil.getIntParameter("-" +
                TrainingParameters.CUTOFF_PARAM.toLowerCase() , params) ?
            CmdLineUtil.getIntParameter("-" + TrainingParameters.CUTOFF_PARAM.toLowerCase() , params) :
            CUTOFF_DEFAULT_VALUE);

    return mlParams;
  }

  /**
   * @param namespace The namespace used as prefix or {@code null}.
   *                  If {@code null} the {@code key} is left unchanged.
   * @param key The identifying key to process. 
   *
   * @return Retrieves a prefixed key in the specified {@code namespace}.
   *         If no {@code namespace} was specified the returned String is equal to {@code key}.
   */
  static String getKey(String namespace, String key) {
    if (namespace == null) {
      return key;
    }
    else {
      return namespace + "." + key;
    }
  }
}
