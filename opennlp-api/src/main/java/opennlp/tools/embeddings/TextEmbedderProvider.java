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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Creates text embedders from model files or directories. Providers register through
 * {@link java.util.ServiceLoader} and are selected by {@link TextEmbedderProviders}: a provider
 * takes part in selection when it {@link #isAvailable() is available} and
 * {@link #supports(Path, Map) supports} the request, and the highest {@link #priority()} wins.
 * Constructors must not load models or initialize native runtimes.
 * Thread safety is implementation specific.
 *
 * @since 3.0.0
 */
public interface TextEmbedderProvider {

  /**
   * @return The nonblank, case-sensitive identifier of this provider, used to pin a provider
   *         through {@link TextEmbedderProviders#PROVIDER_PROPERTY} and in diagnostics.
   */
  String name();

  /**
   * Ranks this provider against others that support the same request. A provider that
   * replaces another one with the same {@link #name()} declares a larger value.
   *
   * @return The priority; larger wins. The default is {@code 0}.
   */
  default int priority() {
    return 0;
  }

  /**
   * Tells whether this provider can load models in this process, for example whether its
   * runtime classes are present. The check must be cheap and must not initialize a native
   * runtime.
   *
   * @return {@code true} if the provider can be used. The default is {@code true}.
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * Tells whether this provider can load the given model with the given options, judged from
   * the request alone, without opening the model.
   *
   * @param model The model file or directory. Must not be {@code null}.
   * @param options Provider-specific options. Must not be {@code null}.
   * @return {@code true} if {@link #load(Path, Map)} with the same arguments is meant for this
   *         provider.
   */
  boolean supports(Path model, Map<String, String> options);

  /**
   * Loads an independently owned embedder. The caller closes the returned instance.
   *
   * @param model The model file or directory. Must not be {@code null}.
   * @param options Provider-specific options. Must not be {@code null} or contain null keys
   *                or values. Supported options and defaults are documented by the provider.
   * @return The loaded embedder. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument or option is invalid or unsupported.
   * @throws IOException Thrown if the model cannot be loaded.
   */
  TextEmbedder load(Path model, Map<String, String> options) throws IOException;
}
