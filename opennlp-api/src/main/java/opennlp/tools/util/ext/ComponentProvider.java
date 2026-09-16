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
package opennlp.tools.util.ext;

import java.io.IOException;

/**
 * Creates components of one contract, such as a document annotator or a gazetteer, from a
 * {@link ComponentSpec}. Providers register in
 * {@code META-INF/services/opennlp.tools.util.ext.ComponentProvider} and are found through
 * {@link ComponentProviders} by {@link #type() contract}: a provider takes part in selection
 * when it {@link #isAvailable() is available} and {@link #supports(ComponentSpec) supports}
 * the request, and the highest {@link #priority()} wins. Construction must not load models or
 * initialize native runtimes. Thread safety is implementation specific.
 *
 * @param <T> The contract the provider creates.
 * @since 3.0.0
 */
public interface ComponentProvider<T> {

  /**
   * @return The contract this provider creates, such as {@code DocumentAnnotator.class}.
   *         Never {@code null}.
   */
  Class<T> type();

  /**
   * @return The nonblank, case-sensitive name of this provider within its contract, used to
   *         pin a provider through {@link ComponentProviders#pinProperty(Class)} and in
   *         diagnostics.
   */
  String name();

  /**
   * Ranks this provider against others of the same contract that support the same request.
   * A provider that replaces another one with the same {@link #name()} declares a larger
   * value.
   *
   * @return The priority; larger wins. The default is {@code 0}.
   */
  default int priority() {
    return 0;
  }

  /**
   * Tells whether this provider can create components in this process, for example whether
   * its runtime classes are present. The check must be cheap and must not initialize a
   * native runtime.
   *
   * @return {@code true} if the provider can be used. The default is {@code true}.
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * Tells whether this provider can create a component for the request, judged from the
   * spec alone, without opening its location.
   *
   * @param spec The request. Must not be {@code null}.
   * @return {@code true} if {@link #create(ComponentSpec)} with the same spec is meant for
   *         this provider.
   */
  boolean supports(ComponentSpec spec);

  /**
   * Creates an independently owned component. The caller owns it and closes it when the
   * contract is closeable.
   *
   * @param spec The request. Must not be {@code null}.
   * @return The component. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the spec is invalid for this provider.
   * @throws IOException Thrown if a model or resource cannot be read.
   */
  T create(ComponentSpec spec) throws IOException;
}
