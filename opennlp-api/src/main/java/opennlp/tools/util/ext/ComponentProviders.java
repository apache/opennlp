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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and selects {@link ComponentProvider providers} by contract without creating
 * components. Providers register through {@link ServiceLoader} under
 * {@code opennlp.tools.util.ext.ComponentProvider}; one service file per jar lists every
 * provider it ships, whatever their contracts. This class names no implementation: which
 * provider creates a component follows from the installed providers of the requested
 * contract, their {@link ComponentProvider#supports capability},
 * {@link ComponentProvider#isAvailable() availability} and
 * {@link ComponentProvider#priority() priority}, or from the system property named by
 * {@link #pinProperty(Class)}, which pins a provider by name for one contract.
 * No provider or component instances are cached. This class is thread-safe.
 *
 * @since 3.0.0
 */
public final class ComponentProviders {

  /** Prefix of the per-contract pin properties, see {@link #pinProperty(Class)}. */
  public static final String PIN_PROPERTY_PREFIX = "opennlp.provider.";

  private static final Logger logger = LoggerFactory.getLogger(ComponentProviders.class);

  /** Bounds the number of broken registrations skipped in one lookup. */
  private static final int MAX_SKIPPED_REGISTRATIONS = 1000;

  private ComponentProviders() {
  }

  /**
   * Names the system property that pins the provider {@link #select} returns for a contract:
   * {@value #PIN_PROPERTY_PREFIX} followed by the contract's simple class name, for example
   * {@code opennlp.provider.DocumentAnnotator}. Unset or blank means selection by capability
   * and priority.
   *
   * @param type The contract. Must not be {@code null}.
   * @return The property name. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code type} is {@code null}.
   */
  public static String pinProperty(Class<?> type) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    return PIN_PROPERTY_PREFIX + type.getSimpleName();
  }

  /**
   * Lists the providers of a contract visible to the thread context class loader, or this
   * class's loader when absent, in registration order.
   *
   * @param type The contract. Must not be {@code null}.
   * @param <T> The contract.
   * @return The installed providers; empty when none is registered. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code type} is {@code null}.
   * @throws IllegalStateException Thrown if a provider declares a null contract or a null or
   *                               blank name.
   */
  public static <T> List<ComponentProvider<T>> installed(Class<T> type) {
    return installed(type, contextLoader());
  }

  /**
   * Lists the providers of a contract visible to a class loader, in registration order. A
   * registration that cannot be loaded or instantiated is skipped with a warning, so one
   * broken provider jar does not hide the others. Providers of other contracts are ignored.
   *
   * @param type The contract; a provider matches when its {@link ComponentProvider#type()}
   *             is this class. Must not be {@code null}.
   * @param loader The service class loader. Must not be {@code null}.
   * @param <T> The contract.
   * @return The installed providers; empty when none is registered. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws IllegalStateException Thrown if a provider declares a null contract or a null or
   *                               blank name.
   */
  @SuppressWarnings("unchecked")
  public static <T> List<ComponentProvider<T>> installed(Class<T> type, ClassLoader loader) {
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (loader == null) {
      throw new IllegalArgumentException("loader must not be null");
    }
    List<ComponentProvider<T>> providers = new ArrayList<>();
    Iterator<ServiceLoader.Provider<ComponentProvider>> registrations =
        ServiceLoader.load(ComponentProvider.class, loader).stream().iterator();
    int skipped = 0;
    while (skipped < MAX_SKIPPED_REGISTRATIONS) {
      ComponentProvider<?> provider;
      try {
        if (!registrations.hasNext()) {
          break;
        }
        provider = registrations.next().get();
      } catch (ServiceConfigurationError e) {
        skipped++;
        logger.warn("Skipping a component provider registration: {}", e.getMessage());
        continue;
      }
      if (provider.type() == null) {
        throw new IllegalStateException("Component provider declares no contract: "
            + provider.getClass().getName());
      }
      if (provider.name() == null || provider.name().isBlank()) {
        throw new IllegalStateException("Component provider declares a blank name: "
            + provider.getClass().getName());
      }
      if (provider.type() == type) {
        providers.add((ComponentProvider<T>) provider);
      }
    }
    return Collections.unmodifiableList(providers);
  }

  /**
   * Selects a provider of a contract by name using the thread context class loader, or this
   * class's loader when absent.
   *
   * @param type The contract. Must not be {@code null}.
   * @param name The case-sensitive provider name. Must not be null or blank.
   * @param <T> The contract.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is invalid or no provider of the
   *                                  contract has the name.
   * @throws IllegalStateException Thrown if no provider of that name is available, or if two
   *                               available providers of that name share the highest priority.
   */
  public static <T> ComponentProvider<T> get(Class<T> type, String name) {
    return get(type, name, contextLoader());
  }

  /**
   * Selects a provider of a contract by name among those visible to a class loader. When
   * several providers declare the name, the available one with the highest priority wins, so
   * a module can replace another module's provider by registering the same name with a larger
   * priority. Selection never depends on registration order.
   *
   * @param type The contract. Must not be {@code null}.
   * @param name The case-sensitive provider name. Must not be null or blank.
   * @param loader The service class loader. Must not be {@code null}.
   * @param <T> The contract.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is invalid or no provider of the
   *                                  contract has the name.
   * @throws IllegalStateException Thrown if no provider of that name is available, or if two
   *                               available providers of that name share the highest priority.
   */
  public static <T> ComponentProvider<T> get(Class<T> type, String name, ClassLoader loader) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    List<ComponentProvider<T>> installed = installed(type, loader);
    List<ComponentProvider<T>> named = new ArrayList<>();
    for (ComponentProvider<T> provider : installed) {
      if (name.equals(provider.name())) {
        named.add(provider);
      }
    }
    if (named.isEmpty()) {
      throw new IllegalArgumentException(type.getSimpleName() + " provider is not installed: "
          + name + " (installed: " + names(installed) + ")");
    }
    List<ComponentProvider<T>> available = available(named);
    if (available.isEmpty()) {
      throw new IllegalStateException(type.getSimpleName()
          + " provider is installed but not available: " + name + " (" + classes(named) + ")");
    }
    return highest(available, type.getSimpleName() + " providers named " + name
        + " share the highest priority: ");
  }

  /**
   * Lists the available providers of a contract that support a request, best first, using
   * the thread context class loader, or this class's loader when absent. See
   * {@link #supporting(Class, ComponentSpec, ClassLoader)}.
   *
   * @param type The contract. Must not be {@code null}.
   * @param spec The request. Must not be {@code null}.
   * @param <T> The contract.
   * @return The supporting providers, highest priority first; empty when none. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  public static <T> List<ComponentProvider<T>> supporting(Class<T> type, ComponentSpec spec) {
    return supporting(type, spec, contextLoader());
  }

  /**
   * Lists the available providers of a contract that support a request, best first. Providers
   * with the same priority keep their registration order. This is the list for callers that
   * want every matching component, such as a pipeline collecting all annotators for a
   * document; {@link #select} is the single best one.
   *
   * @param type The contract. Must not be {@code null}.
   * @param spec The request. Must not be {@code null}.
   * @param loader The service class loader. Must not be {@code null}.
   * @param <T> The contract.
   * @return The supporting providers, highest priority first; empty when none. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  public static <T> List<ComponentProvider<T>> supporting(Class<T> type, ComponentSpec spec,
                                                          ClassLoader loader) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    List<ComponentProvider<T>> candidates = new ArrayList<>();
    for (ComponentProvider<T> provider : available(installed(type, loader))) {
      if (provider.supports(spec)) {
        candidates.add(provider);
      }
    }
    candidates.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
    return Collections.unmodifiableList(candidates);
  }

  /**
   * Selects the provider of a contract for a request using the thread context class loader,
   * or this class's loader when absent. See {@link #select(Class, ComponentSpec, ClassLoader)}.
   *
   * @param type The contract. Must not be {@code null}.
   * @param spec The request. Must not be {@code null}.
   * @param <T> The contract.
   * @return The provider to create the component with. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, if no installed and
   *                                  available provider supports the request, or if the pinned
   *                                  provider is not installed.
   * @throws IllegalStateException Thrown if two supporting providers share the highest priority,
   *                               or if the pinned provider is not available.
   */
  public static <T> ComponentProvider<T> select(Class<T> type, ComponentSpec spec) {
    return select(type, spec, contextLoader());
  }

  /**
   * Selects the provider of a contract for a request among those visible to a class loader.
   * When the system property named by {@link #pinProperty(Class)} is set, the provider of that
   * name is returned as by {@link #get(Class, String, ClassLoader)}. Otherwise the candidates
   * are the installed providers of the contract that are available and support the request,
   * and the one with the highest priority wins. Selection never depends on registration order.
   *
   * @param type The contract. Must not be {@code null}.
   * @param spec The request. Must not be {@code null}.
   * @param loader The service class loader. Must not be {@code null}.
   * @param <T> The contract.
   * @return The provider to create the component with. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, if no installed and
   *                                  available provider supports the request, or if the pinned
   *                                  provider is not installed.
   * @throws IllegalStateException Thrown if two supporting providers share the highest priority,
   *                               or if the pinned provider is not available.
   */
  public static <T> ComponentProvider<T> select(Class<T> type, ComponentSpec spec,
                                                ClassLoader loader) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    String pinned = System.getProperty(pinProperty(type));
    if (pinned != null && !pinned.isBlank()) {
      return get(type, pinned.strip(), loader);
    }
    List<ComponentProvider<T>> candidates = supporting(type, spec, loader);
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("No installed " + type.getSimpleName()
          + " provider supports " + spec + " (installed: " + names(installed(type, loader)) + ")");
    }
    return highest(candidates, type.getSimpleName() + " providers for " + spec
        + " share the highest priority, set -D" + pinProperty(type) + "=<name> to pin one: ");
  }

  private static ClassLoader contextLoader() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return loader == null ? ComponentProviders.class.getClassLoader() : loader;
  }

  /** Keeps the providers whose availability check passes; a failing check counts as absent. */
  private static <T> List<ComponentProvider<T>> available(List<ComponentProvider<T>> providers) {
    List<ComponentProvider<T>> available = new ArrayList<>();
    for (ComponentProvider<T> provider : providers) {
      boolean usable;
      try {
        usable = provider.isAvailable();
      } catch (RuntimeException | LinkageError e) {
        logger.warn("Component provider {} failed its availability check: {}",
            provider.getClass().getName(), e.toString());
        usable = false;
      }
      if (usable) {
        available.add(provider);
      }
    }
    return available;
  }

  /** Returns the single provider with the highest priority, or throws when two share it. */
  private static <T> ComponentProvider<T> highest(List<ComponentProvider<T>> candidates,
                                                  String ambiguous) {
    ComponentProvider<T> best = null;
    boolean tie = false;
    for (ComponentProvider<T> candidate : candidates) {
      if (best == null || candidate.priority() > best.priority()) {
        best = candidate;
        tie = false;
      } else if (candidate.priority() == best.priority()) {
        tie = true;
      }
    }
    if (tie) {
      List<ComponentProvider<T>> tied = new ArrayList<>();
      for (ComponentProvider<T> candidate : candidates) {
        if (candidate.priority() == best.priority()) {
          tied.add(candidate);
        }
      }
      throw new IllegalStateException(ambiguous + classes(tied));
    }
    return best;
  }

  private static String names(List<? extends ComponentProvider<?>> providers) {
    List<String> names = new ArrayList<>();
    for (ComponentProvider<?> provider : providers) {
      names.add(provider.name());
    }
    return names.toString();
  }

  private static String classes(List<? extends ComponentProvider<?>> providers) {
    List<String> classes = new ArrayList<>();
    for (ComponentProvider<?> provider : providers) {
      classes.add(provider.getClass().getName());
    }
    return classes.toString();
  }
}
