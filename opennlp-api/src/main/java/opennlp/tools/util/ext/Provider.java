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
 * Creates instances of one service interface from a {@link ProviderSpec}.
 * <p>
 * Every service interface has an SPI of its own that extends this interface, for example
 * {@code DocumentAnnotatorProvider extends Provider<DocumentAnnotator>}. Implementations are
 * listed in {@code META-INF/services/<SPI fully qualified name>}, need a public no-argument
 * constructor and are resolved through {@link Providers}.
 * <p>
 * Implementations must be stateless and thread-safe. The constructor, the static initializer of
 * the implementation class, {@link #isAvailable()} and {@link #supports(ProviderSpec)} must not
 * load models or initialize native libraries: they run while providers are looked up, before a
 * caller has chosen one.
 *
 * @param <T> The service interface created by this provider.
 * @see Providers
 * @since 3.0.0
 */
public interface Provider<T> {

  /**
   * @return The case-sensitive name of this provider: at most 64 characters of ASCII letters,
   *         digits, {@code .}, {@code _} or {@code -}, since it is used as a configuration
   *         value. A
   *         provider with another name is skipped when providers are looked up. Providers of one
   *         SPI may share a name; {@link #priority()} ranks them.
   */
  String name();

  /**
   * @return The rank of this provider among providers that support the same spec, larger
   *         wins. The default is {@code 0}.
   */
  default int priority() {
    return 0;
  }

  /**
   * Checks whether this provider can be used in the running process, for example whether an
   * optional dependency is on the class path. Check a class by class literal and catch
   * {@link LinkageError} instead of loading it by name, which also keeps a native image free of
   * reflection metadata. The check runs on every resolution, so it must not block.
   *
   * @return {@code true} if this provider can be used. The default is {@code true}.
   */
  default boolean isAvailable() {
    return true;
  }

  /**
   * Checks whether this provider can create an instance for a spec, based on the spec alone.
   * The check runs on every resolution, so it must not block.
   *
   * @param spec The spec to check. Must not be {@code null}.
   * @return {@code true} if {@link #create(ProviderSpec)} accepts {@code spec}.
   * @throws IllegalArgumentException Thrown if {@code spec} is {@code null}.
   */
  boolean supports(ProviderSpec spec);

  /**
   * Creates a new instance. The caller owns it and closes it if it is closeable. The location of
   * the spec is unvalidated input of the caller: a provider that opens it decides itself which
   * schemes and which directories it accepts.
   *
   * @param spec The spec to create an instance for. Must not be {@code null}.
   * @return The new instance. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code spec} is {@code null} or not supported.
   * @throws IOException Thrown if a resource named by {@code spec} could not be read.
   */
  T create(ProviderSpec spec) throws IOException;
}
