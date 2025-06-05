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
import java.io.OutputStream;
import java.util.Map;

import opennlp.tools.ml.AlgorithmType;

public interface Parameters {
  
  String ALGORITHM_PARAM = "Algorithm";
  String TRAINER_TYPE_PARAM = "TrainerType";
  String ITERATIONS_PARAM = "Iterations";
  String CUTOFF_PARAM = "Cutoff";
  String THREADS_PARAM = "Threads";

  String ALGORITHM_DEFAULT_VALUE = AlgorithmType.MAXENT.getAlgorithmType();

  /**
   * The default number of iterations is 100.
   */
  int ITERATIONS_DEFAULT_VALUE = 100;
  /**
   * The default cut off value is 5.
   */
  int CUTOFF_DEFAULT_VALUE = 5;

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

  /**
   * @return Retrieves the (training) algorithm name for a given name space, or {@code null} if unset.
   */
  String algorithm(String namespace);

  /**
   * @return Retrieves the (training) algorithm name. or @code null} if not set.
   */
  String algorithm();

  /**
   * @param namespace The name space to filter or narrow the search space. May be {@code null}.
   *
   * @return Retrieves a parameter {@link Map} which can be passed to the train and validate methods.
   */
  Map<String, Object> getObjectSettings(String namespace);

  /**
   * @return Retrieves a parameter {@link Map} of all parameters without narrowing.
   */
  Map<String, Object> getObjectSettings();

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String namespace, String key, String value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String key, String value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String namespace, String key, int value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String key, int value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String namespace, String key, double value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String key, double value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String namespace, String key, boolean value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key},
   * if the value was not present before.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link Parameters} instance.
   */
  void putIfAbsent(String key, boolean value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link Parameters} instance.
   */
  void put(String namespace, String key, String value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link String} parameter to put into this {@link Parameters} instance.
   */
  void put(String key, String value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link Parameters} instance.
   */
  void put(String namespace, String key, int value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Integer} parameter to put into this {@link Parameters} instance.
   */
  void put(String key, int value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link Parameters} instance.
   */
  void put(String namespace, String key, double value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Double} parameter to put into this {@link Parameters} instance.
   */
  void put(String key, double value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   * The {@code namespace} can be used to prefix the {@code key}.
   *
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be put.
   *                  May be {@code null}.
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link Parameters} instance.
   */
  void put(String namespace, String key, boolean value);

  /**
   * Puts a {@code value} into the current {@link Parameters} under a certain {@code key}.
   * If the value was present before, the previous value will be overwritten with the specified one.
   *
   * @param key The identifying key to put or retrieve a {@code value} with.
   * @param value The {@link Boolean} parameter to put into this {@link Parameters} instance.
   */
  void put(String key, boolean value);

  /**
   * Serializes a {@link Parameters} instance via a specified {@link OutputStream}.
   *
   * @param out A valid, open {@link OutputStream} to write to.
   *
   * @throws IOException Thrown if errors occurred.
   */
  void serialize(OutputStream out) throws IOException;

  /**
   * Obtains a training parameter value.
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link String training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   * @throws java.lang.ClassCastException} Thrown if the value is not {@code String}
   */
  String getStringParameter(String key, String defaultValue);

  /**
   * Obtains a training parameter value in the specified namespace.
   * 
   * @param namespace A prefix to declare or use a name space under which {@code key} shall be searched.
   *                  May be {@code null}.
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   *
   * @return The {@link String training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   * @throws java.lang.ClassCastException} Thrown if the value is not {@code String}
   */
  String getStringParameter(String namespace, String key, String defaultValue);

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Integer training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  int getIntParameter(String key, int defaultValue);

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
  int getIntParameter(String namespace, String key, int defaultValue);

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Double training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  double getDoubleParameter(String key, double defaultValue);

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
  double getDoubleParameter(String namespace, String key, double defaultValue);

  /**
   * Obtains a training parameter value.
   * <p>
   *
   * @param key The identifying key to retrieve a {@code value} with.
   * @param defaultValue The alternative value to use, if {@code key} was not present.
   * @return The {@link Boolean training value} associated with {@code key} if present,
   *         or a {@code defaultValue} if not.
   */
  boolean getBooleanParameter(String key, boolean defaultValue);


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
  boolean getBooleanParameter(String namespace, String key, boolean defaultValue);
}
