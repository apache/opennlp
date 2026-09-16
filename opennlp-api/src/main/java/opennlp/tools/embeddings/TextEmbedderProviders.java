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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and selects {@link TextEmbedderProvider providers} without opening models.
 * Providers register through {@link ServiceLoader}. This class names no provider: which one
 * loads a model follows from the installed providers, their {@link TextEmbedderProvider#supports
 * capability}, {@link TextEmbedderProvider#isAvailable() availability} and
 * {@link TextEmbedderProvider#priority() priority}, or from the system property
 * {@link #PROVIDER_PROPERTY}, which pins a provider by name.
 * No provider or model instances are cached. This class is thread-safe.
 *
 * @since 3.0.0
 */
public final class TextEmbedderProviders {

  /**
   * System property that pins the provider {@link #select(Path, Map)} returns, by
   * {@link TextEmbedderProvider#name() name}. Unset or blank means selection by capability and
   * priority.
   */
  public static final String PROVIDER_PROPERTY = "opennlp.embedder.provider";

  private static final Logger logger = LoggerFactory.getLogger(TextEmbedderProviders.class);

  /** Bounds the number of broken registrations skipped in one lookup. */
  private static final int MAX_SKIPPED_REGISTRATIONS = 1000;

  private TextEmbedderProviders() {
  }

  /**
   * Lists the providers visible to the thread context class loader, or this class's loader
   * when absent, in registration order. A registration that cannot be loaded or instantiated
   * is skipped with a warning, so one broken provider jar does not hide the others.
   *
   * @return The installed providers; empty when none is registered. Never {@code null}.
   */
  public static List<TextEmbedderProvider> installed() {
    return installed(contextLoader());
  }

  /**
   * Lists the providers visible to a class loader, in registration order. A registration
   * that cannot be loaded or instantiated is skipped with a warning, so one broken provider
   * jar does not hide the others.
   *
   * @param loader The service class loader. Must not be {@code null}.
   * @return The installed providers; empty when none is registered. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code loader} is {@code null}.
   * @throws IllegalStateException Thrown if a provider declares a null or blank name.
   */
  public static List<TextEmbedderProvider> installed(ClassLoader loader) {
    if (loader == null) {
      throw new IllegalArgumentException("loader must not be null");
    }
    List<TextEmbedderProvider> providers = new ArrayList<>();
    Iterator<ServiceLoader.Provider<TextEmbedderProvider>> registrations =
        ServiceLoader.load(TextEmbedderProvider.class, loader).stream().iterator();
    int skipped = 0;
    while (skipped < MAX_SKIPPED_REGISTRATIONS) {
      TextEmbedderProvider provider;
      try {
        if (!registrations.hasNext()) {
          break;
        }
        provider = registrations.next().get();
      } catch (ServiceConfigurationError e) {
        skipped++;
        logger.warn("Skipping a text embedder provider registration: {}", e.getMessage());
        continue;
      }
      if (provider.name() == null || provider.name().isBlank()) {
        throw new IllegalStateException("Text embedder provider declares a blank name: "
            + provider.getClass().getName());
      }
      providers.add(provider);
    }
    return Collections.unmodifiableList(providers);
  }

  /**
   * Selects a provider by name using the thread context class loader, or this class's loader
   * when absent.
   *
   * @param name The case-sensitive provider name. Must not be null or blank.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if the name is null, blank, or not installed.
   * @throws IllegalStateException Thrown if no provider of that name is available, or if two
   *                               available providers of that name share the highest priority.
   */
  public static TextEmbedderProvider get(String name) {
    return get(name, contextLoader());
  }

  /**
   * Selects a provider by name among those visible to a class loader. When several providers
   * declare the name, the available one with the highest priority wins, so a module can
   * replace another module's provider by registering the same name with a larger priority.
   * Selection never depends on registration order.
   *
   * @param name The case-sensitive provider name. Must not be null or blank.
   * @param loader The service class loader. Must not be {@code null}.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is invalid or the name is not installed.
   * @throws IllegalStateException Thrown if no provider of that name is available, or if two
   *                               available providers of that name share the highest priority.
   */
  public static TextEmbedderProvider get(String name, ClassLoader loader) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    List<TextEmbedderProvider> installed = installed(loader);
    List<TextEmbedderProvider> named = new ArrayList<>();
    for (TextEmbedderProvider provider : installed) {
      if (name.equals(provider.name())) {
        named.add(provider);
      }
    }
    if (named.isEmpty()) {
      throw new IllegalArgumentException("Text embedder provider is not installed: " + name
          + " (installed: " + names(installed) + ")");
    }
    List<TextEmbedderProvider> available = available(named);
    if (available.isEmpty()) {
      throw new IllegalStateException("Text embedder provider is installed but not available: "
          + name + " (" + classes(named) + ")");
    }
    return highest(available, "Text embedder providers named " + name
        + " share the highest priority: ");
  }

  /**
   * Selects the provider for a model using the thread context class loader, or this class's
   * loader when absent. See {@link #select(Path, Map, ClassLoader)}.
   *
   * @param model The model file or directory. Must not be {@code null}.
   * @param options Provider-specific options. Must not be {@code null}.
   * @return The provider to load the model with. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, if no installed and
   *                                  available provider supports the request, or if the pinned
   *                                  provider is not installed.
   * @throws IllegalStateException Thrown if two supporting providers share the highest priority,
   *                               or if the pinned provider is not available.
   */
  public static TextEmbedderProvider select(Path model, Map<String, String> options) {
    return select(model, options, contextLoader());
  }

  /**
   * Selects the provider for a model among those visible to a class loader. When the system
   * property {@link #PROVIDER_PROPERTY} is set, the provider of that name is returned as by
   * {@link #get(String, ClassLoader)}. Otherwise the candidates are the installed providers
   * that are available and support the request, and the one with the highest priority wins.
   * Selection never depends on registration order.
   *
   * @param model The model file or directory. Must not be {@code null}.
   * @param options Provider-specific options. Must not be {@code null}.
   * @param loader The service class loader. Must not be {@code null}.
   * @return The provider to load the model with. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, if no installed and
   *                                  available provider supports the request, or if the pinned
   *                                  provider is not installed.
   * @throws IllegalStateException Thrown if two supporting providers share the highest priority,
   *                               or if the pinned provider is not available.
   */
  public static TextEmbedderProvider select(Path model, Map<String, String> options,
                                            ClassLoader loader) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    if (options == null) {
      throw new IllegalArgumentException("options must not be null");
    }
    String pinned = System.getProperty(PROVIDER_PROPERTY);
    if (pinned != null && !pinned.isBlank()) {
      return get(pinned.strip(), loader);
    }
    List<TextEmbedderProvider> installed = installed(loader);
    List<TextEmbedderProvider> candidates = new ArrayList<>();
    for (TextEmbedderProvider provider : available(installed)) {
      if (provider.supports(model, options)) {
        candidates.add(provider);
      }
    }
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("No installed text embedder provider supports " + model
          + " (installed: " + names(installed) + ")");
    }
    return highest(candidates, "Text embedder providers for " + model
        + " share the highest priority, set -D" + PROVIDER_PROPERTY + "=<name> to pin one: ");
  }

  private static ClassLoader contextLoader() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    return loader == null ? TextEmbedderProviders.class.getClassLoader() : loader;
  }

  /** Keeps the providers whose availability check passes; a failing check counts as absent. */
  private static List<TextEmbedderProvider> available(List<TextEmbedderProvider> providers) {
    List<TextEmbedderProvider> available = new ArrayList<>();
    for (TextEmbedderProvider provider : providers) {
      boolean usable;
      try {
        usable = provider.isAvailable();
      } catch (RuntimeException | LinkageError e) {
        logger.warn("Text embedder provider {} failed its availability check: {}",
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
  private static TextEmbedderProvider highest(List<TextEmbedderProvider> candidates,
                                              String ambiguous) {
    TextEmbedderProvider best = null;
    boolean tie = false;
    for (TextEmbedderProvider candidate : candidates) {
      if (best == null || candidate.priority() > best.priority()) {
        best = candidate;
        tie = false;
      } else if (candidate.priority() == best.priority()) {
        tie = true;
      }
    }
    if (tie) {
      List<TextEmbedderProvider> tied = new ArrayList<>();
      for (TextEmbedderProvider candidate : candidates) {
        if (candidate.priority() == best.priority()) {
          tied.add(candidate);
        }
      }
      throw new IllegalStateException(ambiguous + classes(tied));
    }
    return best;
  }

  private static String names(List<TextEmbedderProvider> providers) {
    List<String> names = new ArrayList<>();
    for (TextEmbedderProvider provider : providers) {
      names.add(provider.name());
    }
    return names.toString();
  }

  private static String classes(List<TextEmbedderProvider> providers) {
    List<String> classes = new ArrayList<>();
    for (TextEmbedderProvider provider : providers) {
      classes.add(provider.getClass().getName());
    }
    return classes.toString();
  }
}
