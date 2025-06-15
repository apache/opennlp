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

package opennlp.tools.models;

import java.io.IOException;

import opennlp.tools.util.model.BaseModel;

/**
 * A provider for obtaining pre-trained OpenNLP {@link BaseModel models} from
 * an application's classpath.
 * <p>
 * Providing models available in the classpath requires a {@link ClassPathModelFinder finder}
 * and a {@link ClassPathModelLoader} at runtime. Depending on the environment,
 * a custom {@code finder} can be used.
 *
 * @see BaseModel
 * @see ClassPathModelFinder
 * @see ClassPathModelLoader
 */
public interface ClassPathModelProvider {

  /**
   * Restores a {@link T model} among all classpath models at runtime
   * according to the specified parameters {@code lang} and {@code type}.
   *
   * @param lang      The ISO language code of the requested model. If {@code null} or empty,
     *                the result will be {@code null} as well.
   * @param type      The type string to narrow down the model variant. Note: Custom naming patterns
   *                  can be applied here, as the {@code type} fragment will be used for a 'contains'
   *                  check internally.
   * @param modelType The class of model type parameter {@link T} to create an instance of.
   *
   * @return An model instance of type {@link T}, or {@code null} if no match was found
   *         for the specified parameters.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   * @throws IOException Thrown if something went wrong during reading resources from the classpath.
   */
  <T extends BaseModel> T load(String lang, ModelType type, Class<T> modelType)
          throws IOException;

  /**
   * Restores a {@link T model} among all classpath models at runtime
   * according to the specified parameters {@code lang} and {@code type}.
   *
   * @param lang      The ISO language code of the requested model. If {@code null} or empty,
   *                  the result will be {@code null} as well.
   * @param type      The type of model to specifiy the model variant.
   * @param modelType The class of model type parameter {@link T} to create an instance of.
   * @param reloadCache {@code true}, if the internal cache of the {@link ClassPathModelFinder}
   *                    should explicitly be reloaded, {@code false} otherwise.
   *
   * @return An model instance of type {@link T}, or {@code null} if no match was found
   *         for the specified parameters.
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   * @throws IOException Thrown if something went wrong during reading resources from the classpath.
   */
  <T extends BaseModel> T load(String lang, ModelType type, Class<T> modelType,
                               boolean reloadCache) throws IOException;
}
