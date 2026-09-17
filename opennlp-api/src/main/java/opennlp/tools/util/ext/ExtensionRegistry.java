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

import java.util.Collections;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the extensions the {@link ExtensionLoader} can create without reflection.
 * <p>
 * An extension is registered under the name of its implementation class, which is
 * the name model manifests, feature generator descriptors and training parameters
 * refer to. Each name maps to one {@link Supplier} for extensions with a no-arg
 * constructor, and to any number of typed factories for extensions that need
 * constructor arguments, keyed by the factory interface.
 * <p>
 * The {@link #getDefault() default registry} is filled by every
 * {@link ExtensionRegistrar} found through {@link ServiceLoader} the first time it
 * is used. Applications can add their own extensions to it at startup; a
 * registration replaces an earlier one with the same name and factory type.
 * <p>
 * All methods are safe for concurrent use.
 *
 * @see ExtensionRegistrar
 * @see ExtensionLoader
 */
public final class ExtensionRegistry {

  private static final Logger logger = LoggerFactory.getLogger(ExtensionRegistry.class);

  private static final class Holder {
    static final ExtensionRegistry DEFAULT = load();
  }

  private final Map<String, Class<?>> implementations = new ConcurrentHashMap<>();
  private final Map<String, Supplier<?>> suppliers = new ConcurrentHashMap<>();
  private final Map<String, Map<Class<?>, Object>> factories = new ConcurrentHashMap<>();

  /**
   * Creates an empty registry. Nothing is discovered; use {@link #getDefault()} for
   * the registry the {@link ExtensionLoader} consults.
   */
  public ExtensionRegistry() {
  }

  /**
   * @return The registry the {@link ExtensionLoader} consults, filled from every
   *         {@link ExtensionRegistrar} on the class path.
   */
  public static ExtensionRegistry getDefault() {
    return Holder.DEFAULT;
  }

  private static ExtensionRegistry load() {
    final ExtensionRegistry registry = new ExtensionRegistry();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = ExtensionRegistrar.class.getClassLoader();
    }
    final ServiceLoader<ExtensionRegistrar> registrars = ServiceLoader.load(ExtensionRegistrar.class, cl);
    registrars.stream().forEach(provider -> {
      try {
        provider.get().register(registry);
      } catch (ServiceConfigurationError e) {
        logger.warn("Skipping broken ExtensionRegistrar registration: {}", e.getMessage());
      }
    });
    return registry;
  }

  /**
   * Registers an extension that is created through a no-arg constructor or a
   * singleton accessor.
   *
   * @param implementation The extension class. Its name is the registration key.
   *                       Must not be {@code null}.
   * @param supplier Creates or returns the extension instance. Must not be {@code null}.
   * @param <T> The extension type.
   *
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public <T> void register(Class<T> implementation, Supplier<? extends T> supplier) {
    if (implementation == null) {
      throw new IllegalArgumentException("implementation must not be null");
    }
    if (supplier == null) {
      throw new IllegalArgumentException("supplier must not be null");
    }
    implementations.put(implementation.getName(), implementation);
    suppliers.put(implementation.getName(), supplier);
  }

  /**
   * Registers a typed factory for an extension that needs constructor arguments,
   * for instance a model reader that wraps a data reader.
   *
   * @param implementation The extension class. Its name is the registration key.
   *                       Must not be {@code null}.
   * @param factoryType The factory interface callers look the factory up by.
   *                    Must not be {@code null}.
   * @param factory The factory. Must not be {@code null}.
   * @param <F> The factory type.
   *
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public <F> void register(Class<?> implementation, Class<F> factoryType, F factory) {
    if (implementation == null) {
      throw new IllegalArgumentException("implementation must not be null");
    }
    if (factoryType == null) {
      throw new IllegalArgumentException("factoryType must not be null");
    }
    if (factory == null) {
      throw new IllegalArgumentException("factory must not be null");
    }
    implementations.put(implementation.getName(), implementation);
    factories.computeIfAbsent(implementation.getName(), k -> new ConcurrentHashMap<>())
        .put(factoryType, factory);
  }

  /**
   * @param name The extension name, usually the implementation class name.
   * @return {@code true} if a supplier or a factory is registered under {@code name}.
   */
  public boolean isRegistered(String name) {
    return name != null && implementations.containsKey(name);
  }

  /**
   * @param name The extension name, usually the implementation class name.
   * @return The registered implementation class, or {@code null} if nothing is
   *         registered under {@code name}.
   */
  public Class<?> implementation(String name) {
    return name == null ? null : implementations.get(name);
  }

  /**
   * @param name The extension name, usually the implementation class name.
   * @return The supplier registered under {@code name}, or {@code null} if there is none.
   */
  public Supplier<?> supplier(String name) {
    return name == null ? null : suppliers.get(name);
  }

  /**
   * @param name The extension name, usually the implementation class name.
   * @param factoryType The factory interface the factory was registered with.
   *                    Must not be {@code null}.
   * @param <F> The factory type.
   * @return The factory registered under {@code name} for {@code factoryType},
   *         or {@code null} if there is none.
   *
   * @throws IllegalArgumentException Thrown if {@code factoryType} is {@code null}.
   */
  public <F> F factory(String name, Class<F> factoryType) {
    if (factoryType == null) {
      throw new IllegalArgumentException("factoryType must not be null");
    }
    if (name == null) {
      return null;
    }
    final Map<Class<?>, Object> byType = factories.get(name);
    return byType == null ? null : factoryType.cast(byType.get(factoryType));
  }

  /**
   * @return An unmodifiable view of every registered extension name.
   */
  public Set<String> names() {
    return Collections.unmodifiableSet(implementations.keySet());
  }
}
