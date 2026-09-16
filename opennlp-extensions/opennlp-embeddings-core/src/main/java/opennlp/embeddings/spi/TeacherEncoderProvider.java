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
package opennlp.embeddings.spi;

import java.nio.file.Path;

/**
 * Creates independently owned teacher encoders. Providers register through
 * {@link java.util.ServiceLoader} and are selected by {@link TeacherEncoderProviders}: a
 * provider takes part in selection when it {@link #isAvailable() is available} and
 * {@link #supports(Path) supports} the model, and the highest {@link #priority()} wins.
 * Constructors must not load models or initialize native runtimes.
 * Thread safety is implementation specific.
 *
 * @since 3.0.0
 */
public interface TeacherEncoderProvider {

  /**
   * @return The nonblank, case-sensitive identifier of this provider, used to pin a provider
   *         through {@link TeacherEncoderProviders#PROVIDER_PROPERTY} and in diagnostics.
   */
  String name();

  /**
   * Ranks this provider against others that support the same model. A provider that replaces
   * another one with the same {@link #name()} declares a larger value.
   *
   * @return The priority; larger wins. The default is {@code 0}.
   */
  default int priority() {
    return 0;
  }

  /**
   * Tells whether this provider can open models in this process, for example whether its
   * runtime classes are present. The check must be cheap and must not initialize a native
   * runtime.
   *
   * @return {@code true} if the provider can be used. The default is {@code true}.
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * Tells whether this provider can open the given model, judged from the path alone.
   *
   * @param model The model file. Must not be {@code null}.
   * @return {@code true} if {@link #load(Path)} with the same argument is meant for this
   *         provider.
   */
  boolean supports(Path model);

  /**
   * Opens a teacher model. The caller closes the returned encoder.
   *
   * @param model The model file. Must not be {@code null}.
   * @return The encoder. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the model is invalid or cannot be loaded.
   */
  TeacherEncoder load(Path model);
}
