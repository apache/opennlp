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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.StringUtil;

/**
 * Resolves the {@link Provider providers} of one SPI.
 * <p>
 * The providers are looked up with {@link ServiceLoader} when an instance is created, so
 * {@link #of(Class)} belongs into the startup of an application rather than into a request, and a
 * new instance is needed to see providers added later. Availability, support for a spec and the
 * configuration are read on every call. Instances are thread-safe; the only state that changes is
 * the set of failures already reported.
 * <p>
 * Keep the instance in a field owned by the application and not in a {@code static} field, since
 * OpenNLP can be deployed in a class loader shared by several applications.
 * <p>
 * Resolution is steered by name through {@link #configurationKey()}, {@link #disabledKey()} and
 * {@link #allowedKey()}, and by the provider class itself through
 * {@link #ofTrusted(Class, ClassLoader, Predicate)}.
 *
 * @param <P> The SPI, a sub-interface of {@link Provider}.
 * @see Provider
 * @see ProviderSpec
 * @see UnsatisfiedProviderException
 * @see AmbiguousProviderException
 * @since 3.0.0
 */
public final class Providers<P extends Provider<?>> {

  /**
   * The prefix of the configuration key that selects a provider by name for {@link #select}.
   * The full key is the prefix followed by the fully qualified name of the SPI, see
   * {@link #configurationKey()}.
   */
  public static final String CONFIGURATION_KEY_PREFIX = "opennlp.provider.";

  /**
   * The suffix of the configuration key that disables providers by name, see
   * {@link #disabledKey()}.
   */
  public static final String DISABLED_KEY_SUFFIX = ".disabled";

  /**
   * The suffix of the configuration key that limits which provider classes may be used, see
   * {@link #allowedKey()}.
   */
  public static final String ALLOWED_KEY_SUFFIX = ".allowed";

  /**
   * The resource read for configuration keys, once per lookup and class loader, encoded in
   * UTF-8. Several files are merged by their {@value #ORDINAL_KEY}, so the highest one wins, and
   * a system property of the same name wins over all of them. Every jar of the class loader can
   * carry this file. In a native image it is read only when it is registered as a resource.
   */
  public static final String CONFIGURATION_FILE = "META-INF/opennlp/providers.properties";

  /** The key that orders several {@value #CONFIGURATION_FILE} files, {@code 0} by default. */
  public static final String ORDINAL_KEY = "configuration.ordinal";

  private static final Logger logger = LoggerFactory.getLogger(Providers.class);

  private static final char NAME_SEPARATOR = ',';
  private static final String SERVICE_PREFIX = "META-INF/services/";
  private static final char PACKAGE_SEPARATOR_PATH = '/';
  private static final String CLASS_SUFFIX = ".class";
  /** Stands for an allow list that holds no entry, which allows no provider at all. */
  private static final String NOTHING_ALLOWED = "\u0000";
  private static final String PACKAGE_SEPARATOR = ".";
  private static final char BYTE_ORDER_MARK = '\uFEFF';
  private static final int MAX_NAME_LENGTH = 64;
  private static final String LOOKUP_FAILED = "Cannot look up %s providers with %s";
  private static final String SKIPPED_ENTRY = "Skipping a {} provider registered with {}: {}";
  private static final String SKIPPED_CALL = "Skipping {} provider {}: {}() failed: {}";
  private static final String NAME_METHOD = "name";
  private static final String PRIORITY_METHOD = "priority";
  private static final String AVAILABLE_METHOD = "isAvailable";
  private static final String SUPPORTS_METHOD = "supports";
  private static final String SAME_PRIORITY = " have the same priority: ";
  private static final String INVALID_NAME = "Skipping {} provider {}: name() is not usable, it "
      + "must be at most {} characters of ASCII letters, digits, '.', '_' or '-'";

  /**
   * Bounds a lookup whose iterator neither yields a provider nor reaches its end, which
   * {@link ServiceLoader} does when it cannot list the service files at all. The bound is far
   * above the number of broken registrations a class path can hold, so a lookup that only skips
   * unusable providers is not cut short.
   */
  private static final int MAX_CONSECUTIVE_FAILURES = 1000;

  private final Class<P> spi;
  private final String configurationKey;
  private final String disabledKey;
  private final String allowedKey;
  private final UnaryOperator<String> configuration;
  private final Predicate<Class<?>> trusted;
  private final String loaderName;
  private final List<Registration<P>> registrations;
  private final List<P> installed;
  private final int skipped;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();
  private volatile Parsed disabled;

  /**
   * @param spi The SPI. Must be a sub-interface of {@link Provider}.
   * @param loader The class loader to look providers up with, or {@code null} to resolve one.
   * @param configuration The source of the configuration keys, or {@code null} for the default.
   * @param trusted Accepts the provider classes that may be used, or {@code null} for all.
   * @throws IllegalArgumentException Thrown if {@code spi} is {@code null} or not a
   *                                  sub-interface of {@link Provider}.
   * @throws ProviderResolutionException Thrown if the configuration or the service files cannot
   *                                     be read, or if {@code trusted} fails.
   */
  private Providers(final Class<P> spi, final ClassLoader loader,
                    final UnaryOperator<String> configuration,
                    final Predicate<Class<?>> trusted) {
    if (spi == null) {
      throw new IllegalArgumentException("spi must not be null");
    }
    if (!spi.isInterface() || Provider.class.equals(spi) || !Provider.class.isAssignableFrom(spi)) {
      throw new IllegalArgumentException("spi must be a sub-interface of "
          + Provider.class.getName() + ": " + spi.getName());
    }
    this.spi = spi;
    this.configurationKey = CONFIGURATION_KEY_PREFIX + spi.getName();
    this.disabledKey = this.configurationKey + DISABLED_KEY_SUFFIX;
    this.allowedKey = this.configurationKey + ALLOWED_KEY_SUFFIX;
    final ClassLoader lookupLoader = resolveLoader(loader);
    this.loaderName = lookupLoader.getClass().getName() + '@'
        + Integer.toHexString(System.identityHashCode(lookupLoader));
    this.configuration = configuration != null ? configuration : fileConfiguration(lookupLoader);
    this.trusted = trusted;
    final Lookup<P> lookup = lookup(lookupLoader);
    this.registrations = lookup.registrations();
    this.skipped = lookup.skipped();
    final List<P> providers = new ArrayList<>(registrations.size());
    for (final Registration<P> registration : registrations) {
      providers.add(registration.provider());
    }
    this.installed = Collections.unmodifiableList(providers);
  }

  /**
   * Looks the {@link Provider providers} of an SPI up with the thread context class loader, or
   * with the class loader of {@code spi} if the thread has none or if it cannot see {@code spi}.
   * On a thread whose context class loader is not the application's, such as a thread of a pool
   * or of a container, this finds the providers of the SPI's own class loader only, so a library
   * or a framework integration captures the class loader at startup and calls
   * {@link #of(Class, ClassLoader)}.
   *
   * @param spi The SPI. Must be a sub-interface of {@link Provider}.
   * @param <P> The SPI.
   * @return The providers of {@code spi}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code spi} is {@code null} or not a
   *                                  sub-interface of {@link Provider}.
   * @throws ProviderResolutionException Thrown if the configuration or the service files cannot
   *                                     be read.
   */
  public static <P extends Provider<?>> Providers<P> of(final Class<P> spi) {
    return new Providers<>(spi, null, null, null);
  }

  /**
   * Looks the {@link Provider providers} of an SPI up with a class loader.
   *
   * @param spi The SPI. Must be a sub-interface of {@link Provider}.
   * @param loader The class loader to look providers up with. Must not be {@code null}.
   * @param <P> The SPI.
   * @return The providers of {@code spi}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, or if {@code spi} is
   *                                  not a sub-interface of {@link Provider}.
   * @throws ProviderResolutionException Thrown if the configuration or the service files cannot
   *                                     be read.
   */
  public static <P extends Provider<?>> Providers<P> of(final Class<P> spi,
                                                        final ClassLoader loader) {
    requireArguments(List.of("spi", "loader"), spi, loader);
    return new Providers<>(spi, loader, null, null);
  }

  /**
   * Looks the {@link Provider providers} of an SPI up with a class loader and reads the
   * configuration keys from an application's own source instead of {@value #CONFIGURATION_FILE}
   * and the system properties. Applications that share a class loader hierarchy, such as several
   * deployments in one server, use this to configure resolution per application.
   *
   * @param spi The SPI. Must be a sub-interface of {@link Provider}.
   * @param loader The class loader to look providers up with. Must not be {@code null}.
   * @param configuration Answers {@link #configurationKey()}, {@link #disabledKey()} and
   *                      {@link #allowedKey()} with the configured value or {@code null}. Must
   *                      not be {@code null}, and must be thread-safe, cheap and free of side
   *                      effects, as it is called on every resolution.
   * @param <P> The SPI.
   * @return The providers of {@code spi}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, or if {@code spi} is
   *                                  not a sub-interface of {@link Provider}.
   * @throws ProviderResolutionException Thrown if the service files cannot be read.
   */
  public static <P extends Provider<?>> Providers<P> of(final Class<P> spi,
                                                        final ClassLoader loader,
                                                        final UnaryOperator<String> configuration) {
    requireArguments(List.of("spi", "loader", "configuration"), spi, loader, configuration);
    return new Providers<>(spi, loader, configuration, null);
  }

  /**
   * Looks the {@link Provider providers} of an SPI up with a class loader, using only the
   * provider classes a predicate accepts, which lets an application decide by the code source,
   * the module or the signers of a class instead of by its name.
   * <p>
   * The predicate is applied to each provider class the configuration permits, after the class
   * has been loaded and before it is initialized and instantiated, so a rejected provider runs
   * neither its static initializer nor its constructor. The predicate must not initialize the
   * class either. An exception from it ends the lookup.
   *
   * @param spi The SPI. Must be a sub-interface of {@link Provider}.
   * @param loader The class loader to look providers up with. Must not be {@code null}.
   * @param trusted Accepts the provider classes that may be used. Must not be {@code null}.
   * @param <P> The SPI.
   * @return The providers of {@code spi}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, or if {@code spi} is
   *                                  not a sub-interface of {@link Provider}.
   * @throws ProviderResolutionException Thrown if the configuration or the service files cannot
   *                                     be read, or if {@code trusted} throws.
   */
  public static <P extends Provider<?>> Providers<P> ofTrusted(final Class<P> spi,
                                                               final ClassLoader loader,
                                                               final Predicate<Class<?>> trusted) {
    requireArguments(List.of("spi", "loader", "trusted"), spi, loader, trusted);
    return new Providers<>(spi, loader, null, trusted);
  }

  /**
   * Combines {@link #of(Class, ClassLoader, UnaryOperator)} and
   * {@link #ofTrusted(Class, ClassLoader, Predicate)}.
   *
   * @param spi The SPI. Must be a sub-interface of {@link Provider}.
   * @param loader The class loader to look providers up with. Must not be {@code null}.
   * @param configuration Answers the configuration keys. Must not be {@code null}.
   * @param trusted Accepts the provider classes that may be used. Must not be {@code null}.
   * @param <P> The SPI.
   * @return The providers of {@code spi}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, or if {@code spi} is
   *                                  not a sub-interface of {@link Provider}.
   * @throws ProviderResolutionException Thrown if the service files cannot be read, or if
   *                                     {@code trusted} throws.
   */
  public static <P extends Provider<?>> Providers<P> of(final Class<P> spi,
                                                        final ClassLoader loader,
                                                        final UnaryOperator<String> configuration,
                                                        final Predicate<Class<?>> trusted) {
    requireArguments(List.of("spi", "loader", "configuration", "trusted"), spi, loader,
        configuration, trusted);
    return new Providers<>(spi, loader, configuration, trusted);
  }

  /**
   * Checks the arguments of a factory method at once, so that a caller sees every missing one.
   *
   * @param names The names of the arguments, in the order of {@code arguments}.
   * @param arguments The arguments to check.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, naming all of them.
   */
  private static void requireArguments(final List<String> names, final Object... arguments) {
    final List<String> missing = new ArrayList<>();
    for (int argument = 0; argument < arguments.length; argument++) {
      if (arguments[argument] == null) {
        missing.add(names.get(argument));
      }
    }
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(String.join(", ", missing) + " must not be null");
    }
  }

  /**
   * @return The configuration key that selects a provider by name for {@link #select}:
   *         {@value #CONFIGURATION_KEY_PREFIX} followed by the fully qualified name of the SPI.
   *         Never {@code null}.
   */
  public String configurationKey() {
    return configurationKey;
  }

  /**
   * @return The configuration key that disables providers of this SPI, a comma separated list of
   *         provider names and provider class names: {@link #configurationKey()} followed by
   *         {@value #DISABLED_KEY_SUFFIX}. A provider disabled by name is not resolved but stays
   *         listed by {@link #installed()}. A provider disabled by class name is not instantiated
   *         at all, which needs the class name to be configured before the providers are looked
   *         up. Never {@code null}.
   */
  public String disabledKey() {
    return disabledKey;
  }

  /**
   * @return The configuration key that limits which provider classes of this SPI may be used:
   *         {@link #configurationKey()} followed by {@value #ALLOWED_KEY_SUFFIX}. The value is a
   *         comma separated list of provider class names and of package prefixes, which end with
   *         a dot; an unset or blank value allows every provider, a value that holds only
   *         separators allows none. It matches class names, not their origin. Use
   *         {@link #ofTrusted(Class, ClassLoader, Predicate)} to validate provider classes.
   *         Unlike the other keys it is read from a system property or from the source an
   *         application passes only, never from {@value #CONFIGURATION_FILE}, which any jar can carry.
   *         Providers are checked against it before they are instantiated, so it has to be
   *         configured before the lookup. Never {@code null}.
   */
  public String allowedKey() {
    return allowedKey;
  }

  /**
   * @return The providers in lookup order, including unavailable ones and those disabled by
   *         name. Unmodifiable and never {@code null}.
   */
  public List<P> installed() {
    return installed;
  }

  /**
   * Resolves an available {@link Provider provider} by name, unless the name is disabled. If
   * several available providers have the name, the one with the highest priority is returned.
   * The key from {@link #configurationKey()} is not applied.
   *
   * @param name The case-sensitive provider name. Must not be {@code null} or blank.
   * @return The provider, or {@link Optional#empty()} if the name is disabled or no available
   *         provider has it.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null} or blank.
   * @throws AmbiguousProviderException Thrown if several providers of that name have the highest
   *                                    priority.
   */
  public Optional<P> byName(final String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    if (disabledNames().contains(name)) {
      return Optional.empty();
    }
    final List<Registration<P>> named = new ArrayList<>();
    for (final Registration<P> registration : registrations) {
      if (registration.name().equals(name) && isAvailable(registration)) {
        named.add(registration);
      }
    }
    if (named.isEmpty()) {
      return Optional.empty();
    }
    named.sort(Registration.BY_PRIORITY);
    if (isTied(named)) {
      throw new AmbiguousProviderException(spi.getName() + " providers named '" + name + "'"
          + SAME_PRIORITY + tiedClasses(named) + ". " + hint(name));
    }
    return Optional.of(named.get(0).provider());
  }

  /**
   * Lists the available {@link Provider providers} that support a {@link ProviderSpec spec},
   * without the disabled ones. The key from
   * {@link #configurationKey()} is not applied.
   *
   * @param spec The spec. Must not be {@code null}.
   * @return The providers ordered by descending priority, providers of equal priority in lookup
   *         order. Unmodifiable and never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code spec} is {@code null}.
   */
  public List<P> supporting(final ProviderSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    final List<Registration<P>> candidates = candidates(spec, disabledNames(), null);
    final List<P> providers = new ArrayList<>(candidates.size());
    for (final Registration<P> candidate : candidates) {
      providers.add(candidate.provider());
    }
    return Collections.unmodifiableList(providers);
  }

  /**
   * Resolves the {@link Provider provider} for a {@link ProviderSpec spec}. The candidates are
   * the available providers that support {@code spec} and are not disabled. If the key from
   * {@link #configurationKey()} holds a nonblank value, only candidates with that name,
   * surrounding whitespace ignored, remain. The candidate with the highest priority is returned.
   *
   * @param spec The spec. Must not be {@code null}.
   * @return The provider. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code spec} is {@code null}.
   * @throws UnsatisfiedProviderException Thrown if no candidate remains.
   * @throws AmbiguousProviderException Thrown if several candidates have the highest priority.
   */
  public P select(final ProviderSpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("spec must not be null");
    }
    final Set<String> disabled = disabledNames();
    final String selected = configuredName();
    final List<Registration<P>> candidates = candidates(spec, disabled, selected);
    if (candidates.isEmpty()) {
      throw new UnsatisfiedProviderException(unsatisfiedMessage(spec, selected, disabled));
    }
    if (isTied(candidates)) {
      throw new AmbiguousProviderException(spi.getName() + " providers for " + spec + SAME_PRIORITY
          + tiedClasses(candidates) + ". " + hint(selected));
    }
    return candidates.get(0).provider();
  }

  /**
   * @param spec The spec. Must not be {@code null}.
   * @param disabled The disabled provider names and class names. Must not be {@code null}.
   * @param selected The selected name, or {@code null} to keep every name.
   * @return The available registrations that support {@code spec}, ordered by descending
   *         priority. A new, modifiable list.
   */
  private List<Registration<P>> candidates(final ProviderSpec spec, final Set<String> disabled,
                                           final String selected) {
    final List<Registration<P>> candidates = new ArrayList<>();
    for (final Registration<P> registration : registrations) {
      if ((selected == null || selected.equals(registration.name()))
          && !disabled.contains(registration.name()) && isAvailable(registration)
          && supports(registration, spec)) {
        candidates.add(registration);
      }
    }
    candidates.sort(Registration.BY_PRIORITY);
    return candidates;
  }

  private boolean isAvailable(final Registration<P> registration) {
    try {
      return registration.provider().isAvailable();
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      warn(registration.provider(), AVAILABLE_METHOD, e.toString());
      return false;
    }
  }

  private boolean supports(final Registration<P> registration, final ProviderSpec spec) {
    try {
      return registration.provider().supports(spec);
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      warn(registration.provider(), SUPPORTS_METHOD, e.toString());
      return false;
    }
  }

  private boolean isTied(final List<Registration<P>> sorted) {
    return sorted.size() > 1 && sorted.get(0).priority() == sorted.get(1).priority();
  }

  private List<String> tiedClasses(final List<Registration<P>> sorted) {
    final int highest = sorted.get(0).priority();
    final List<String> classes = new ArrayList<>();
    for (final Registration<P> registration : sorted) {
      if (registration.priority() == highest) {
        classes.add(registration.provider().getClass().getName());
      }
    }
    return classes;
  }

  /**
   * @param selected The selected name, or {@code null} if none is configured.
   * @return The advice added to an {@link AmbiguousProviderException}.
   */
  private String hint(final String selected) {
    if (selected == null) {
      return "Set " + configurationKey + "=<name> to select one.";
    }
    return "Add one of them to " + disabledKey + ", or give it another priority.";
  }

  /**
   * @param spec The spec no provider was resolved for.
   * @param selected The selected name, or {@code null} if none is configured.
   * @param disabled The disabled provider names and class names.
   * @return The message of an {@link UnsatisfiedProviderException}.
   */
  private String unsatisfiedMessage(final ProviderSpec spec, final String selected,
                                    final Set<String> disabled) {
    final List<String> names = new ArrayList<>(registrations.size());
    for (final Registration<P> registration : registrations) {
      names.add(registration.name());
    }
    final StringBuilder message = new StringBuilder("No available ").append(spi.getName())
        .append(" provider");
    if (selected != null) {
      message.append(" named '").append(selected).append("' (").append(configurationKey)
          .append(')');
    }
    message.append(" supports ").append(spec).append(". Installed: ").append(names);
    if (!disabled.isEmpty()) {
      message.append(". Disabled by ").append(disabledKey).append(": ").append(disabled);
    }
    final Set<String> allowed = allowedClasses();
    if (!allowed.isEmpty()) {
      message.append(". Allowed by ").append(allowedKey).append(": ").append(allowed);
    }
    if (skipped > 0) {
      message.append(". Skipped during lookup: ").append(skipped).append(" (see the log)");
    }
    return message.toString();
  }

  /**
   * @return The name the configuration selects, without surrounding whitespace, or {@code null}
   *         if none is configured.
   */
  private String configuredName() {
    final String value = configured(configurationKey);
    return value == null || value.isBlank() ? null : value.strip();
  }

  /**
   * Reads a configuration key through the source of this instance.
   *
   * @param key The key to read. Must not be {@code null}.
   * @return The configured value, or {@code null} if the key is not configured.
   * @throws ProviderResolutionException Thrown if the configuration source fails.
   */
  private String configured(final String key) {
    try {
      return configuration.apply(key);
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      throw new ProviderResolutionException("Cannot read the configuration key " + key, e);
    }
  }

  /**
   * @return The provider names and class names the configuration disables. Never {@code null}.
   */
  private Set<String> disabledNames() {
    final String value = configured(disabledKey);
    final Parsed parsed = disabled;
    if (parsed != null && Objects.equals(parsed.value(), value)) {
      return parsed.names();
    }
    final Set<String> names = names(value);
    disabled = new Parsed(value, names);
    return names;
  }

  private Set<String> names(final String value) {
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    final Set<String> names = new HashSet<>();
    for (final String entry : StringUtil.split(value, NAME_SEPARATOR)) {
      final String name = entry.strip();
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    return names;
  }

  /**
   * @return The provider class names and package prefixes the configuration allows, or an empty
   *         set when every provider is allowed. Never {@code null}.
   */
  private Set<String> allowedClasses() {
    final String value = configured(allowedKey);
    if (value == null || value.isBlank()) {
      return Set.of();
    }
    final Set<String> allowed = names(value);
    return allowed.isEmpty() ? Set.of(NOTHING_ALLOWED) : allowed;
  }

  private boolean isAllowed(final Set<String> allowed, final String provided) {
    if (allowed.isEmpty()) {
      return true;
    }
    if (allowed.contains(provided)) {
      return true;
    }
    for (final String entry : allowed) {
      if (entry.endsWith(PACKAGE_SEPARATOR) && provided.startsWith(entry)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves the class loader to look providers up with: the thread context class loader when it
   * sees this SPI, the class loader of the SPI otherwise. A thread of the common
   * {@link java.util.concurrent.ForkJoinPool} carries the system class loader, which usually does
   * not see the SPI of an application.
   *
   * @param explicit The class loader an application passed, or {@code null}.
   * @return The class loader to look providers up with. Never {@code null}.
   */
  private ClassLoader resolveLoader(final ClassLoader explicit) {
    if (explicit != null) {
      return explicit;
    }
    final ClassLoader declaring = spi.getClassLoader() != null
        ? spi.getClassLoader() : ClassLoader.getSystemClassLoader();
    final ClassLoader context = Thread.currentThread().getContextClassLoader();
    if (context == null) {
      return declaring;
    }
    for (ClassLoader ancestor = context; ancestor != null; ancestor = ancestor.getParent()) {
      if (ancestor == declaring) {
        return context;
      }
    }
    // A class loader of a module system or of a bundle delegates without a parent chain, so ask
    // it whether it sees the SPI. Its resource is compared instead of loading the class, which
    // would run foreign code under that loader's lock.
    final String resource = spi.getName().replace('.', PACKAGE_SEPARATOR_PATH) + CLASS_SUFFIX;
    try {
      final String seen = String.valueOf(context.getResource(resource));
      return seen.equals(String.valueOf(declaring.getResource(resource))) ? context : declaring;
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      return declaring;
    }
  }

  /**
   * Reads {@value #CONFIGURATION_FILE} of a class loader, merged in ascending order of
   * {@value #ORDINAL_KEY}, and lets a system property of the same name win over it.
   *
   * @param loader The class loader to read the configuration with. Must not be {@code null}.
   * @return The configuration source. Never {@code null}.
   * @throws ProviderResolutionException Thrown if a configuration file cannot be read.
   */
  private UnaryOperator<String> fileConfiguration(final ClassLoader loader) {
    final Map<String, String> configured = readConfiguration(loader);
    final String allowed = allowedKey;
    // The allow list decides which classes may run at all, so it is not read from a file that
    // any jar of the class path can carry.
    return (final String key) -> allowed.equals(key)
        ? System.getProperty(key) : System.getProperty(key, configured.get(key));
  }

  /**
   * @param loader The class loader to read the configuration with. Must not be {@code null}.
   * @return The merged content of the configuration files. Never {@code null}.
   * @throws ProviderResolutionException Thrown if the class loader cannot list the configuration
   *                                     files or one of them cannot be read.
   */
  private Map<String, String> readConfiguration(final ClassLoader loader) {
    final List<ConfigurationFile> files = new ArrayList<>();
    try {
      final Enumeration<URL> resources = loader.getResources(CONFIGURATION_FILE);
      while (resources.hasMoreElements()) {
        files.add(read(resources.nextElement()));
      }
    } catch (final ProviderResolutionException e) {
      throw e;
    } catch (final IOException | RuntimeException | Error e) {
      rethrowFatal(e);
      throw lookupFailed(e);
    }
    files.sort(Comparator.comparingInt(ConfigurationFile::ordinal));
    final Map<String, String> configured = new LinkedHashMap<>();
    for (final ConfigurationFile file : files) {
      for (final String key : file.content().stringPropertyNames()) {
        configured.put(key, file.content().getProperty(key));
      }
      if (file.content().containsKey(configurationKey) || file.content().containsKey(disabledKey)
          || file.content().containsKey(allowedKey)) {
        // Any jar on the class path can carry this file, so record where a decision came from.
        logger.info("{} configures {} providers", printable(file.resource()), spi.getName());
      }
    }
    return configured;
  }

  /**
   * Reads one configuration file, without caching its jar, so that an application can be
   * undeployed and its files replaced.
   *
   * @param resource The configuration file to read. Must not be {@code null}.
   * @return The file. Never {@code null}.
   * @throws ProviderResolutionException Thrown if {@code resource} cannot be read, is not encoded
   *                                     in UTF-8 or holds an invalid {@value #ORDINAL_KEY}.
   */
  private ConfigurationFile read(final URL resource) {
    final Properties read = new Properties();
    try {
      final URLConnection connection = resource.openConnection();
      connection.setUseCaches(false);
      try (InputStream stream = connection.getInputStream();
           Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8.newDecoder())) {
        read.load(reader);
      }
    } catch (final IOException | IllegalArgumentException e) {
      throw new ProviderResolutionException("Cannot read " + printable(resource), e);
    }
    final Properties content = stripByteOrderMark(read);
    return new ConfigurationFile(resource, content, ordinal(resource, content));
  }

  private Properties stripByteOrderMark(final Properties content) {
    for (final String key : content.stringPropertyNames()) {
      if (!key.isEmpty() && key.charAt(0) == BYTE_ORDER_MARK) {
        final String stripped = key.substring(1);
        if (content.getProperty(stripped) == null) {
          content.setProperty(stripped, content.getProperty(key));
        }
        content.remove(key);
      }
    }
    return content;
  }

  /** @return The location without the parts that may hold credentials, for a log or a message. */
  private String printable(final URL resource) {
    try {
      return ProviderSpec.printable(resource.toURI());
    } catch (final URISyntaxException e) {
      return resource.getProtocol() + ":" + CONFIGURATION_FILE;
    }
  }

  /**
   * @return The value of {@value #ORDINAL_KEY}, or {@code 0} if the file has none.
   * @throws ProviderResolutionException Thrown if the value is not a number.
   */
  private int ordinal(final URL resource, final Properties content) {
    final String value = content.getProperty(ORDINAL_KEY);
    if (value == null || value.isBlank()) {
      return 0;
    }
    try {
      return Integer.parseInt(value.strip());
    } catch (final NumberFormatException e) {
      throw new ProviderResolutionException(ORDINAL_KEY + " of " + printable(resource)
          + " is not a number: " + value, e);
    }
  }

  /**
   * Looks the providers up, skipping the registrations that cannot be used.
   *
   * @param loader The class loader to look providers up with. Must not be {@code null}.
   * @return The registrations in lookup order and the number of skipped ones. Never
   *         {@code null}.
   * @throws ProviderResolutionException Thrown if the service files cannot be read or the lookup
   *                                     does not advance.
   */
  private Lookup<P> lookup(final ClassLoader loader) {
    try {
      // A class loader that cannot list the service files leaves the iterator of the service
      // loader where it is, so ask it once instead of reading that from a repeated failure.
      loader.getResources(SERVICE_PREFIX + spi.getName());
    } catch (final IOException | RuntimeException | Error e) {
      rethrowFatal(e);
      throw lookupFailed(e);
    }
    final Set<String> disabled = disabledNames();
    final Set<String> allowed = allowedClasses();
    final List<Registration<P>> found = new ArrayList<>();
    final Iterator<ServiceLoader.Provider<P>> iterator =
        ServiceLoader.load(spi, loader).stream().iterator();
    int failures = 0;
    int ignored = 0;
    while (true) {
      final ServiceLoader.Provider<P> entry;
      try {
        if (!iterator.hasNext()) {
          break;
        }
        entry = iterator.next();
      } catch (final RuntimeException e) {
        rethrowFatal(e);
        throw lookupFailed(e);
      } catch (final ServiceConfigurationError | LinkageError e) {
        rethrowFatal(e);
        // Every registration that fails advances the iterator and every entry it yields resets
        // the count, so only a failure that repeats beyond the bound can be one that does not.
        failures++;
        if (failures > MAX_CONSECUTIVE_FAILURES) {
          throw lookupFailed(e);
        }
        ignored++;
        logger.warn(SKIPPED_ENTRY, spi.getName(), loaderName, e.toString());
        continue;
      }
      failures = 0;
      final String provided = entry.type().getName();
      if (disabled.contains(provided) || !isAllowed(allowed, provided)) {
        ignored++;
        logger.info("Leaving out the {} provider {}, see {} and {}", spi.getName(), provided,
            disabledKey, allowedKey);
        continue;
      }
      logger.debug("The {} provider {} comes from {}", spi.getName(), provided,
          codeSource(entry.type()));
      if (!isTrusted(entry.type())) {
        ignored++;
        logger.info("Leaving out the not trusted {} provider {} from {}", spi.getName(), provided,
            codeSource(entry.type()));
        continue;
      }
      final P provider;
      try {
        provider = entry.get();
      } catch (final RuntimeException | Error e) {
        rethrowFatal(e);
        ignored++;
        logger.warn(SKIPPED_ENTRY, spi.getName(), loaderName, e.toString());
        continue;
      }
      final Optional<String> name = call(provider, NAME_METHOD, provider::name);
      if (name.isEmpty()) {
        ignored++;
        continue;
      }
      if (!isValidName(name.get())) {
        ignored++;
        logger.warn(INVALID_NAME, spi.getName(), provided, MAX_NAME_LENGTH);
        continue;
      }
      final Optional<Integer> priority = call(provider, PRIORITY_METHOD, provider::priority);
      if (priority.isEmpty()) {
        ignored++;
        continue;
      }
      found.add(new Registration<>(provider, name.get(), priority.get()));
    }
    warnAboutSharedNames(found);
    return new Lookup<>(Collections.unmodifiableList(found), ignored);
  }

  /** Reports providers that share a name, which can be a replacement or an unwanted override. */
  private void warnAboutSharedNames(final List<Registration<P>> found) {
    final Map<String, List<String>> classes = new LinkedHashMap<>();
    for (final Registration<P> registration : found) {
      classes.computeIfAbsent(registration.name(), name -> new ArrayList<>())
          .add(registration.provider().getClass().getName());
    }
    for (final Map.Entry<String, List<String>> shared : classes.entrySet()) {
      if (shared.getValue().size() > 1) {
        logger.info("{} providers {} share the name '{}'", spi.getName(), shared.getValue(),
            shared.getKey());
      }
    }
  }

  /**
   * @return {@code true} if the predicate accepts the class.
   * @throws ProviderResolutionException Thrown if the predicate throws.
   */
  private boolean isTrusted(final Class<?> provided) {
    if (trusted == null) {
      return true;
    }
    try {
      return trusted.test(provided);
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      throw new ProviderResolutionException("The trust check of the " + spi.getName()
          + " provider " + provided.getName() + " failed", e);
    }
  }

  /** @return The location the class was loaded from, or {@code "unknown"}. */
  private String codeSource(final Class<?> provided) {
    try {
      final ProtectionDomain domain = provided.getProtectionDomain();
      if (domain == null || domain.getCodeSource() == null
          || domain.getCodeSource().getLocation() == null) {
        return "unknown";
      }
      return printable(domain.getCodeSource().getLocation());
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      return "unknown";
    }
  }

  private ProviderResolutionException lookupFailed(final Throwable cause) {
    return new ProviderResolutionException(String.format(LOOKUP_FAILED, spi.getName(), loaderName),
        cause);
  }

  /**
   * Rethrows a {@link VirtualMachineError}, such as an {@link OutOfMemoryError} or a
   * {@link StackOverflowError}, that a provider or a class loader ran into. It leaves the JVM in a
   * state that skipping the provider does not recover from. {@link ServiceLoader} wraps what the
   * constructor of a provider throws in a {@link ServiceConfigurationError}, so the causes of
   * {@code failure} are checked as well.
   *
   * @param failure The failure to check. Must not be {@code null}.
   */
  private void rethrowFatal(final Throwable failure) {
    if (failure instanceof VirtualMachineError fatal) {
      throw fatal;
    }
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable cause = failure.getCause(); cause != null && seen.add(cause);
         cause = cause.getCause()) {
      if (cause instanceof VirtualMachineError fatal) {
        throw fatal;
      }
    }
  }

  /**
   * Calls a provider method and logs a failure instead of throwing it.
   *
   * @param provider The provider to call.
   * @param method The name of the called method, for the log message.
   * @param call The call.
   * @param <R> The result type.
   * @return The result, or {@link Optional#empty()} if the call returned {@code null} or threw.
   */
  private <R> Optional<R> call(final P provider, final String method, final Supplier<R> call) {
    try {
      final R result = call.get();
      if (result == null) {
        warn(provider, method, "returned null");
      }
      return Optional.ofNullable(result);
    } catch (final RuntimeException | Error e) {
      rethrowFatal(e);
      warn(provider, method, e.toString());
      return Optional.empty();
    }
  }

  /**
   * Reports a provider method that could not be used, once per provider and method at
   * {@code WARN} and at {@code DEBUG} afterwards, so a resolution on a request path does not
   * flood the log.
   *
   * @param provider The provider that failed.
   * @param method The name of the called method.
   * @param failure The failure to report.
   */
  private void warn(final P provider, final String method, final String failure) {
    final String provided = provider.getClass().getName();
    if (warned.add(provided + '#' + method)) {
      logger.warn(SKIPPED_CALL, spi.getName(), provided, method, failure);
    } else {
      logger.debug(SKIPPED_CALL, spi.getName(), provided, method, failure);
    }
  }

  /**
   * Accepts ASCII names only. A name is compared by {@link String#equals} with a configuration
   * value, and a non-ASCII name, such as one with an umlaut, can be written precomposed or
   * decomposed, or be changed by the platform encoding of a command line or an environment.
   *
   * @param name The name of a provider. Must not be {@code null}.
   * @return {@code true} if {@code name} can be used as a configuration value.
   */
  private boolean isValidName(final String name) {
    if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
      return false;
    }
    for (int character = 0; character < name.length(); character++) {
      final char current = name.charAt(character);
      final boolean ascii = current >= 'a' && current <= 'z' || current >= 'A' && current <= 'Z'
          || current >= '0' && current <= '9';
      if (!ascii && current != '.' && current != '_' && current != '-') {
        return false;
      }
    }
    return true;
  }

  /**
   * A provider with the name and priority read once at lookup.
   *
   * @param provider The provider.
   * @param name The name of the provider.
   * @param priority The priority of the provider.
   * @param <P> The SPI.
   */
  private record Registration<P>(P provider, String name, int priority) {

    /** Orders registrations by descending priority. */
    private static final Comparator<Registration<?>> BY_PRIORITY =
        Comparator.comparingInt((final Registration<?> registration) -> registration.priority())
            .reversed();
  }

  /**
   * The entries of a configuration value, kept to parse a value that does not change only once.
   *
   * @param value The configuration value, or {@code null} if the key is not configured.
   * @param names The entries of {@code value}.
   */
  private record Parsed(String value, Set<String> names) {
  }

  /**
   * A configuration file of a class loader.
   *
   * @param resource The location of the file.
   * @param content The content of the file.
   * @param ordinal The value of {@value #ORDINAL_KEY}, which orders several files.
   */
  private record ConfigurationFile(URL resource, Properties content, int ordinal) {
  }

  /**
   * The providers one {@link ServiceLoader} pass of the constructor found, and how many it left
   * out. The count is reported by an {@link UnsatisfiedProviderException}, since the reasons are
   * only logged.
   *
   * @param registrations The usable registrations in lookup order.
   * @param skipped The number of registrations left out as broken, disabled by class name, not
   *                allowed or not trusted.
   * @param <P> The SPI.
   */
  private record Lookup<P>(List<Registration<P>> registrations, int skipped) {
  }
}
