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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import opennlp.tools.util.model.BaseModel;

/**
 * Responsible for loading OpenNLP models from the classpath via {@link ClassPathModelEntry entries}.
 * If models could be loaded successfully, those are provided as {@link ClassPathModel model references}
 * or as instances of the specified {@link ModelType}.
 *
 * @see ClassPathModel
 * @see ClassPathModelEntry
 */
public class ClassPathModelLoader {

  /**
   * Loads a {@link ClassPathModel} from a {@link ClassPathModelEntry}
   *
   * @param entry A valid {@link ClassPathModelEntry}, it must not be {@code null}.
   *              Moreover, it is validated that the {@link ClassPathModelEntry#model() binary data}
   *              and {@link ClassPathModelEntry#properties() meta-data} are not {@code null}.
   * @return A {@link ClassPathModel} containing the model resources.
   * @throws IOException Thrown if something went wrong during reading resources from the classpath.
   */
  public ClassPathModel load(ClassPathModelEntry entry) throws IOException {
    Objects.requireNonNull(entry, "entry must not be null");
    Objects.requireNonNull(entry.properties(), "entry.properties() must not be null");
    Objects.requireNonNull(entry.model(), "entry.model() must not be null");

    final Properties properties = new Properties();

    if (entry.properties().isPresent()) {
      try (InputStream inputStream = new BufferedInputStream(entry.properties().get().toURL().openStream())) {
        properties.load(inputStream);
      }
    }

    final byte[] model;
    try (InputStream inputStream = entry.model().toURL().openStream()) {
      model = inputStream.readAllBytes();
    }

    return new ClassPathModel(properties, model);
  }

  /**
   * Restores a {@link T model} among a set {@link ClassPathModelEntry classpath entries}
   * according to the specified parameters {@code lang} and {@code type}.
   *
   * @param classPathEntries A non-empty set of {@link ClassPathModelEntry candidates} to find a matching
   *                         model in. Must not be {@code null}. If it is empty, the result will
   *                         be {@code null}.
   * @param lang      The language code of the requested model. If {@code null} or empty,
   *                  the result will be {@code null} as well.
   * @param type      The {@link ModelType} to select the model variant. It must not be {@code null}.
   * @param modelType The class of model type parameter {@link T} to create an instance of.
   *
   * @return An model instance of type {@link T}, or {@code null} if no match was found
   *         for the specified parameters.
   *         
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   * @throws ClassPathLoaderException Thrown if {@link T} could not be instantiated correctly.
   * @throws IOException Thrown if something went wrong during reading resources from the classpath.
   */
  <T extends BaseModel> T load(Set<ClassPathModelEntry> classPathEntries, String lang,
                               ModelType type, Class<T> modelType) throws IOException {
    if (type == null) {
      throw new IllegalArgumentException("ModelType must not be null.");
    }
    return load(classPathEntries, lang, type.getName(), modelType);
  }

  /**
   * Restores a {@link T model} among a set {@link ClassPathModelEntry classpath entries}
   * according to the specified parameters {@code lang} and {@code type}.
   *
   * @param classPathEntries A non-empty set of {@link ClassPathModelEntry candidates} to find a matching
   *                         model in. Must not be {@code null}. If it is empty, the result will
   *                         be {@code null}.
   * @param lang      The language code of the requested model.  If {@code null} or empty,
   *                  the result will be {@code null} as well.
   * @param type      The type string to narrow down the model variant. Note: Custom naming patterns
   *                  can be applied here, as the {@code type} fragment will be used for a 'contains'
   *                  check internally.
   * @param modelType The class of model type parameter {@link T} to create an instance of.
   *                  It must not be {@code null}.
   *
   * @return An model instance of type {@link T}, or {@code null} if no match was found
   *         for the specified parameters.
   *         
   * @throws IllegalArgumentException Thrown if parameters were invalid.
   * @throws ClassPathLoaderException Thrown if {@link T} could not be instantiated correctly.
   * @throws IOException Thrown if something went wrong during reading resources from the classpath.
   */
  <T extends BaseModel> T load(Set<ClassPathModelEntry> classPathEntries, String lang,
                               String type, Class<T> modelType) throws IOException {
    if (classPathEntries == null) {
      throw new IllegalArgumentException("The provided ClassPath entries must not be null!");
    }
    if (lang == null || lang.isBlank()) {
      throw new IllegalArgumentException("The provided language must not be null and not be empty!");
    }
    if (type == null) {
      throw new IllegalArgumentException("The provided ModelType must not be null!");
    }
    T result = null;
    if (classPathEntries.isEmpty()) {
      return result;
    }
    try {
      for (ClassPathModelEntry entry : classPathEntries) {
        final ClassPathModel cpm = load(entry);
        if (cpm != null && cpm.getModelLanguage().equals(lang) && cpm.getModelName().contains(type)) {
          try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(cpm.model()))) {
            result = modelType.getConstructor(InputStream.class).newInstance(is);
            break; // found a match
          }
        }
      }
    } catch (InstantiationException | IllegalAccessException |
             InvocationTargetException | NoSuchMethodException e) {
      throw new ClassPathLoaderException(e);
    }
    return result;
  }
}
