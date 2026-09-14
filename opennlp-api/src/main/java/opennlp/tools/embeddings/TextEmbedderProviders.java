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
package opennlp.tools.embeddings;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Discovers embedding factories without opening models. Providers register through
 * {@link ServiceLoader}; their constructors must not load models or initialize native runtimes.
 * No provider or model instances are cached globally.
 *
 * @since 3.0.0
 */
public final class TextEmbedderProviders {

  /** The provider selected by {@link #getDefault()}. */
  public static final String DEFAULT_PROVIDER = "onnx";

  private TextEmbedderProviders() {
  }

  /**
   * Selects the default ONNX provider using the thread context class loader.
   *
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the default provider is not installed.
   * @throws IllegalStateException Thrown if multiple providers use the default identifier.
   * @throws ServiceConfigurationError Thrown if a service registration is invalid.
   */
  public static TextEmbedderProvider getDefault() {
    return get(DEFAULT_PROVIDER);
  }

  /**
   * Selects a provider using the thread context class loader, or this class's loader when absent.
   *
   * @param name The case-sensitive provider identifier. Must not be null or blank.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name is null, blank, or not installed.
   * @throws IllegalStateException Thrown if multiple providers use this identifier.
   * @throws ServiceConfigurationError Thrown if a service registration is invalid.
   */
  public static TextEmbedderProvider get(String name) {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return get(name, loader == null ? TextEmbedderProviders.class.getClassLoader() : loader);
  }

  /**
   * Selects a provider visible to a class loader. Selection never depends on discovery order.
   *
   * @param name The case-sensitive provider identifier. Must not be null or blank.
   * @param loader The service class loader. Must not be {@code null}.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is invalid or the name is not installed.
   * @throws IllegalStateException Thrown if multiple providers use this identifier.
   * @throws ServiceConfigurationError Thrown if a service registration is invalid.
   */
  public static TextEmbedderProvider get(String name, ClassLoader loader) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (loader == null) {
      throw new IllegalArgumentException("loader must not be null");
    }
    TextEmbedderProvider selected = null;
    for (TextEmbedderProvider provider : ServiceLoader.load(TextEmbedderProvider.class, loader)) {
      if (name.equals(provider.name())) {
        if (selected != null) {
          throw new IllegalStateException("Duplicate text embedder provider: " + name);
        }
        selected = provider;
      }
    }
    if (selected == null) {
      throw new IllegalArgumentException("Text embedder provider is not installed: " + name);
    }
    return selected;
  }
}
