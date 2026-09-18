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
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvidersTest {

  private static final String META_INF = "META-INF/";
  private static final String SERVICES = META_INF + "services/";
  private static final String SOURCE_KEY =
      Providers.CONFIGURATION_KEY_PREFIX + SourceProvider.class.getName();
  private static final String SINK_KEY =
      Providers.CONFIGURATION_KEY_PREFIX + SinkProvider.class.getName();
  private static final String SOURCE_DISABLED_KEY = SOURCE_KEY + Providers.DISABLED_KEY_SUFFIX;
  private static final String SOURCE_ALLOWED_KEY = SOURCE_KEY + Providers.ALLOWED_KEY_SUFFIX;
  private static final String KIND_OPTION = "kind";
  private static final String TEXT_KIND = "text";
  private static final ProviderSpec ANY = ProviderSpec.empty();
  private static final ProviderSpec TEXT = ProviderSpec.of(Map.of(KIND_OPTION, TEXT_KIND));
  private static final ProviderSpec BINARY = ProviderSpec.of(Map.of(KIND_OPTION, "binary"));

  @TempDir
  private Path dir;

  @AfterEach
  void clearConfiguration() {
    System.clearProperty(SOURCE_KEY);
    System.clearProperty(SINK_KEY);
    System.clearProperty(SOURCE_DISABLED_KEY);
    System.clearProperty(SOURCE_ALLOWED_KEY);
    System.clearProperty(SINK_KEY + Providers.DISABLED_KEY_SUFFIX);
  }

  @Test
  void testConfigurationKeysUseFullyQualifiedSpiName() throws IOException {
    try (URLClassLoader loader = loader(Map.of())) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals("opennlp.provider.opennlp.tools.util.ext.ProvidersTest$SourceProvider",
          providers.configurationKey(), "configuration key");
      assertEquals(providers.configurationKey() + ".disabled", providers.disabledKey(),
          "disabled key");
      assertNotEquals(Providers.of(First.SameName.class, loader).configurationKey(),
          Providers.of(Second.SameName.class, loader).configurationKey(),
          "SPIs sharing a simple name must not share a key");
    }
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void testRejectsInvalidArguments() throws IOException {
    assertThrows(IllegalArgumentException.class, () -> Providers.of(null), "null spi");
    assertThrows(IllegalArgumentException.class, () -> Providers.of((Class) Provider.class),
        "Provider itself");
    assertThrows(IllegalArgumentException.class, () -> Providers.of((Class) Alpha.class),
        "an implementation class");
    assertThrows(IllegalArgumentException.class, () -> Providers.of((Class) Source.class),
        "a service interface");
    assertThrows(IllegalArgumentException.class,
        () -> Providers.of(SourceProvider.class, null), "null loader");
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(Alpha.class)))) {
      assertThrows(IllegalArgumentException.class,
          () -> Providers.of(SourceProvider.class, loader, null), "null configuration");
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertThrows(IllegalArgumentException.class, () -> providers.select(null), "null spec");
      assertThrows(IllegalArgumentException.class, () -> providers.supporting(null), "null spec");
      assertThrows(IllegalArgumentException.class, () -> providers.byName(null), "null name");
      assertThrows(IllegalArgumentException.class, () -> providers.byName(" "), "blank name");
    }
  }

  @Test
  void testInstalledListsOnlyProvidersOfTheSpiInLookupOrder() throws IOException {
    CountingSink.INSTANCES.set(0);
    try (URLClassLoader loader = loader(Map.of(
        SourceProvider.class, List.of(Beta.class, Alpha.class),
        SinkProvider.class, List.of(CountingSink.class)))) {
      final List<SourceProvider> installed = Providers.of(SourceProvider.class, loader).installed();
      assertEquals(2, installed.size(), "installed sources");
      assertInstanceOf(Beta.class, installed.get(0), "lookup order");
      assertInstanceOf(Alpha.class, installed.get(1), "lookup order");
      assertThrows(UnsupportedOperationException.class, () -> installed.remove(0), "unmodifiable");
      assertEquals(0, CountingSink.INSTANCES.get(), "a sink provider was instantiated");
      assertEquals(1, Providers.of(SinkProvider.class, loader).installed().size(), "installed sinks");
      assertEquals(1, CountingSink.INSTANCES.get(), "sink provider instances");
    }
  }

  @Test
  void testSelectsHighestPrioritySupportingAvailableProvider() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class, Unavailable.class, AvailabilityThrows.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(4, providers.installed().size(), "installed");
      final List<SourceProvider> forText = providers.supporting(TEXT);
      assertEquals(2, forText.size(), "supporting text");
      assertInstanceOf(TextOnly.class, forText.get(0), "highest priority first");
      assertInstanceOf(Alpha.class, forText.get(1), "lower priority second");
      assertThrows(UnsupportedOperationException.class, () -> forText.remove(0), "unmodifiable");
      assertInstanceOf(TextOnly.class, providers.select(TEXT), "selected for text");
      assertEquals(1, providers.supporting(BINARY).size(), "supporting binary");
      assertInstanceOf(Alpha.class, providers.select(BINARY), "selected for binary");
    }
  }

  @Test
  void testEqualPriorityKeepsLookupOrder() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Beta.class, Alpha.class, TextOnly.class)))) {
      final List<SourceProvider> supporting =
          Providers.of(SourceProvider.class, loader).supporting(TEXT);
      assertEquals(List.of("text", "beta", "alpha"), names(supporting),
          "equal priority keeps lookup order below the higher priority");
    }
  }

  @Test
  void testUnsatisfiedSelectionNamesInstalledProviders() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(TextOnly.class)))) {
      final UnsatisfiedProviderException e = assertThrows(UnsatisfiedProviderException.class,
          () -> Providers.of(SourceProvider.class, loader).select(BINARY));
      assertTrue(e.getMessage().contains("Installed: [text]"), e.getMessage());
    }
  }

  @Test
  void testTiedPriorityIsAmbiguous() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, Beta.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      final AmbiguousProviderException e =
          assertThrows(AmbiguousProviderException.class, () -> providers.select(ANY));
      assertTrue(e.getMessage().contains(Alpha.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains(Beta.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains(SOURCE_KEY + "=<name>"), e.getMessage());
      assertEquals(2, providers.supporting(ANY).size(), "both remain candidates");
    }
  }

  @Test
  void testAmbiguityUnderASelectedNameAdvisesDisabling() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, AlphaDuplicate.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      System.setProperty(SOURCE_KEY, "alpha");
      final AmbiguousProviderException e =
          assertThrows(AmbiguousProviderException.class, () -> providers.select(ANY));
      assertTrue(e.getMessage().contains(SOURCE_DISABLED_KEY), e.getMessage());
      assertFalse(e.getMessage().contains(SOURCE_KEY + "=<name>"),
          "selecting by name cannot break a tie between providers of that name");
    }
  }

  @Test
  void testByNameRanksProvidersOfTheSameName() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, AlphaReplacement.class, Unavailable.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertInstanceOf(AlphaReplacement.class, providers.byName("alpha").orElseThrow(),
          "the higher priority replaces the other provider of that name");
      assertInstanceOf(AlphaReplacement.class, providers.select(ANY), "selected");
      assertTrue(providers.byName("unavailable").isEmpty(), "unavailable");
      assertTrue(providers.byName("missing").isEmpty(), "not installed");
      assertTrue(providers.byName("ALPHA").isEmpty(), "names are case-sensitive");
    }
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, AlphaDuplicate.class)))) {
      final AmbiguousProviderException e = assertThrows(AmbiguousProviderException.class,
          () -> Providers.of(SourceProvider.class, loader).byName("alpha"));
      assertTrue(e.getMessage().contains(AlphaDuplicate.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains(SOURCE_DISABLED_KEY), e.getMessage());
    }
  }

  @Test
  void testSelectedNameNarrowsCandidates() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      System.setProperty(SOURCE_KEY, "alpha");
      assertInstanceOf(Alpha.class, providers.select(TEXT), "the selected name wins");
      assertEquals(2, providers.supporting(TEXT).size(), "supporting ignores the selection");
      System.setProperty(SOURCE_KEY, " text ");
      assertInstanceOf(TextOnly.class, providers.select(TEXT), "surrounding whitespace is ignored");
      System.setProperty(SOURCE_KEY, " ");
      assertInstanceOf(TextOnly.class, providers.select(TEXT), "a blank value selects nothing");
    }
  }

  @Test
  void testSelectedNameMustSupportTheSpec() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      System.setProperty(SOURCE_KEY, "text");
      final UnsatisfiedProviderException unsupported =
          assertThrows(UnsatisfiedProviderException.class, () -> providers.select(BINARY));
      assertTrue(unsupported.getMessage().contains("named 'text'"), unsupported.getMessage());
      assertTrue(unsupported.getMessage().contains(SOURCE_KEY), unsupported.getMessage());
      System.setProperty(SOURCE_KEY, "missing");
      assertThrows(UnsatisfiedProviderException.class, () -> providers.select(TEXT),
          "a name nobody has");
    }
  }

  @Test
  void testConfigurationAppliesToItsSpiOnly() throws IOException {
    try (URLClassLoader loader = loader(Map.of(
        SourceProvider.class, List.of(Alpha.class),
        SinkProvider.class, List.of(CountingSink.class)))) {
      System.setProperty(SINK_KEY + Providers.DISABLED_KEY_SUFFIX, "sink");
      assertInstanceOf(Alpha.class, Providers.of(SourceProvider.class, loader).select(ANY),
          "another SPI is unaffected");
      assertThrows(UnsatisfiedProviderException.class,
          () -> Providers.of(SinkProvider.class, loader).select(ANY), "disabled sink");
    }
  }

  @Test
  void testDisabledNamesAreNotResolved() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      System.setProperty(SOURCE_DISABLED_KEY, " text , , beta ");
      assertEquals(2, providers.installed().size(), "a disabled name stays installed");
      assertTrue(providers.byName("text").isEmpty(), "disabled by name");
      assertInstanceOf(Alpha.class, providers.byName("alpha").orElseThrow(), "not disabled");
      assertEquals(1, providers.supporting(TEXT).size(), "supporting without the disabled one");
      assertInstanceOf(Alpha.class, providers.select(TEXT), "selected");
      System.setProperty(SOURCE_KEY, "text");
      final UnsatisfiedProviderException e =
          assertThrows(UnsatisfiedProviderException.class, () -> providers.select(TEXT));
      assertTrue(e.getMessage().contains(SOURCE_DISABLED_KEY), e.getMessage());
      System.clearProperty(SOURCE_KEY);
      System.setProperty(SOURCE_DISABLED_KEY, " ");
      assertInstanceOf(TextOnly.class, providers.select(TEXT), "a blank value disables nothing");
    }
  }

  /** A provider disabled by class name must not be loaded at all. */
  @Test
  void testDisabledClassIsNotLoaded() throws IOException {
    NotLoaded.INSTANCES.set(0);
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(NotLoaded.class, Alpha.class)))) {
      System.setProperty(SOURCE_DISABLED_KEY, NotLoaded.class.getName());
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(1, providers.installed().size(), "only the enabled provider is installed");
      assertEquals(0, NotLoaded.INSTANCES.get(), "the disabled class was instantiated");
      assertInstanceOf(Alpha.class, providers.select(ANY), "selected");
    }
  }

  /** Only the allowed provider classes are used, and the others are not even instantiated. */
  @Test
  void testAllowedProviderClasses() throws IOException {
    NotLoaded.INSTANCES.set(0);
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(NotLoaded.class, Alpha.class, TextOnly.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(SOURCE_KEY + ".allowed", providers.allowedKey(), "allowed key");
      assertEquals(3, providers.installed().size(), "an unset key allows every provider");

      NotLoaded.INSTANCES.set(0);
      System.setProperty(SOURCE_ALLOWED_KEY, " " + Alpha.class.getName() + " , "
          + TextOnly.class.getName() + " ");
      final Providers<SourceProvider> byClass = Providers.of(SourceProvider.class, loader);
      assertEquals(List.of("alpha", "text"), names(byClass.installed()), "allowed by class name");
      assertEquals(0, NotLoaded.INSTANCES.get(), "a provider outside the list was instantiated");
      assertInstanceOf(TextOnly.class, byClass.select(TEXT), "selected");

      System.setProperty(SOURCE_ALLOWED_KEY, "opennlp.tools.util.ext.Nothing.");
      assertTrue(Providers.of(SourceProvider.class, loader).installed().isEmpty(),
          "a package prefix nobody matches");

      System.setProperty(SOURCE_ALLOWED_KEY, ProvidersTest.class.getName() + "$");
      assertEquals(0, Providers.of(SourceProvider.class, loader).installed().size(),
          "a prefix that does not end with a dot is a class name");

      System.setProperty(SOURCE_ALLOWED_KEY, "opennlp.tools.util.ext.");
      assertEquals(3, Providers.of(SourceProvider.class, loader).installed().size(),
          "allowed by package prefix");

      System.setProperty(SOURCE_ALLOWED_KEY, "  ");
      assertEquals(3, Providers.of(SourceProvider.class, loader).installed().size(),
          "a blank key allows every provider");
    }
  }

  @Test
  void testAllowedProviderClassesAreReported() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(Alpha.class)))) {
      System.setProperty(SOURCE_ALLOWED_KEY, "com.example.");
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      final UnsatisfiedProviderException e =
          assertThrows(UnsatisfiedProviderException.class, () -> providers.select(ANY));
      assertTrue(e.getMessage().contains(SOURCE_ALLOWED_KEY), e.getMessage());
      assertTrue(e.getMessage().contains("Skipped during lookup: 1"), e.getMessage());
    }
  }

  /** The allow list decides what may run, so a file of the class path must not set it. */
  @Test
  void testAllowedProviderClassesAreNotReadFromTheConfigurationFile() throws IOException {
    register(dir, SourceProvider.class, List.of(Alpha.class, TextOnly.class));
    configure(dir, SOURCE_ALLOWED_KEY + " = " + Alpha.class.getName() + "\n"
        + SOURCE_DISABLED_KEY + " = text\n");
    try (URLClassLoader loader = isolated(dir)) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(2, providers.installed().size(), "the allow list of a file is ignored");
      assertTrue(providers.byName("text").isEmpty(), "the disabled key of a file is honoured");
    }
  }

  @Test
  void testAllowedProviderClassesWithoutEntriesAllowNothing() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(Alpha.class)))) {
      System.setProperty(SOURCE_ALLOWED_KEY, " , , ");
      assertTrue(Providers.of(SourceProvider.class, loader).installed().isEmpty(),
          "a value that holds only separators allows no provider");
    }
  }

  /** A provider the trust predicate rejects is neither initialized nor instantiated. */
  @Test
  void testNotTrustedProvidersAreNotUsed() throws IOException {
    Tracker.INITIALIZED.set(false);
    Tracker.INSTANCES.set(0);
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Initializing.class, Alpha.class)))) {
      final Providers<SourceProvider> providers = Providers.ofTrusted(SourceProvider.class, loader,
          (final Class<?> provided) -> Alpha.class.equals(provided));
      assertEquals(List.of("alpha"), names(providers.installed()), "only the trusted provider");
      assertFalse(Tracker.INITIALIZED.get(), "the rejected class was initialized");
      assertEquals(0, Tracker.INSTANCES.get(), "the rejected class was instantiated");
      final Providers<SourceProvider> rejecting = Providers.ofTrusted(SourceProvider.class, loader,
          (final Class<?> provided) -> false);
      assertTrue(rejecting.installed().isEmpty(), "no provider is trusted");
      final UnsatisfiedProviderException e = assertThrows(UnsatisfiedProviderException.class,
          () -> rejecting.select(TEXT));
      assertTrue(e.getMessage().contains("Skipped during lookup: 2"), e.getMessage());
      assertFalse(Tracker.INITIALIZED.get(), "the rejected class was initialized");
    }
  }

  @Test
  void testTrustedProvidersOfACodeSource() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final URL application = getClass().getProtectionDomain().getCodeSource().getLocation();
      final Providers<SourceProvider> providers = Providers.ofTrusted(SourceProvider.class, loader,
          (final Class<?> provided) -> application.equals(
              provided.getProtectionDomain().getCodeSource().getLocation()));
      assertEquals(2, providers.installed().size(), "both providers share the code source");
      final Providers<SourceProvider> foreign = Providers.ofTrusted(SourceProvider.class, loader,
          (final Class<?> provided) -> !application.equals(
              provided.getProtectionDomain().getCodeSource().getLocation()));
      assertTrue(foreign.installed().isEmpty(), "no provider comes from another code source");
    }
  }

  /** The name based keys filter first, so the predicate only sees what they leave. */
  @Test
  void testTrustIsCheckedAfterTheNameBasedKeys() throws IOException {
    final List<String> asked = Collections.synchronizedList(new ArrayList<>());
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class, Beta.class)))) {
      System.setProperty(SOURCE_ALLOWED_KEY, Alpha.class.getName() + "," + Beta.class.getName());
      System.setProperty(SOURCE_DISABLED_KEY, Beta.class.getName());
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(List.of("alpha"), names(providers.installed()), "the keys leave one provider");
      final Providers<SourceProvider> trusting = Providers.ofTrusted(SourceProvider.class, loader,
          (final Class<?> provided) -> {
            asked.add(provided.getName());
            return true;
          });
      assertEquals(List.of(Alpha.class.getName()), asked,
          "the predicate is asked for the classes the keys leave, and only for those");
      assertEquals(1, trusting.installed().size(), "installed");
    }
  }

  @Test
  void testFailingTrustCheckEndsTheLookup() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(Alpha.class)))) {
      final ProviderResolutionException e = assertThrows(ProviderResolutionException.class,
          () -> Providers.ofTrusted(SourceProvider.class, loader,
              (final Class<?> provided) -> {
                throw new IllegalStateException("cannot decide");
              }), "a trust check that fails must not widen the policy");
      assertTrue(e.getMessage().contains(Alpha.class.getName()), e.getMessage());
      assertInstanceOf(IllegalStateException.class, e.getCause(), "cause");
    }
  }

  @Test
  void testTrustedFactoriesRejectInvalidArguments() throws IOException {
    try (URLClassLoader loader = loader(Map.of())) {
      assertThrows(IllegalArgumentException.class,
          () -> Providers.ofTrusted(SourceProvider.class, null, provided -> true), "null loader");
      assertThrows(IllegalArgumentException.class,
          () -> Providers.ofTrusted(SourceProvider.class, loader, null), "null predicate");
      assertThrows(IllegalArgumentException.class,
          () -> Providers.of(SourceProvider.class, loader, null, provided -> true),
          "null configuration");
      assertThrows(IllegalArgumentException.class,
          () -> Providers.of(SourceProvider.class, loader, key -> null, null), "null predicate");
    }
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Providers.ofTrusted(SourceProvider.class, null, null), "null loader and predicate");
    assertEquals("loader, trusted must not be null", e.getMessage(), "every missing argument");
  }

  @Test
  void testConfigurationOfAnApplicationReplacesTheSystemProperties() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final Map<String, String> application = Map.of(SOURCE_KEY, "alpha");
      final UnaryOperator<String> configuration = application::get;
      System.setProperty(SOURCE_KEY, "text");
      final Providers<SourceProvider> providers =
          Providers.of(SourceProvider.class, loader, configuration);
      assertInstanceOf(Alpha.class, providers.select(TEXT), "the application's own selection");
      assertInstanceOf(TextOnly.class, Providers.of(SourceProvider.class, loader).select(TEXT),
          "another application is configured on its own");
    }
  }

  @Test
  void testConfigurationFileOfTheClassLoader() throws IOException {
    final Path application = Files.createDirectories(dir.resolve("application"));
    final Path library = Files.createDirectories(dir.resolve("library"));
    register(library, SourceProvider.class, List.of(Alpha.class, TextOnly.class));
    configure(library, Providers.ORDINAL_KEY + " = 10\n" + SOURCE_KEY + " = text\n");
    configure(application, SOURCE_KEY + " = alpha\n");
    try (URLClassLoader loader = isolated(library)) {
      assertInstanceOf(TextOnly.class, Providers.of(SourceProvider.class, loader).select(TEXT),
          "the value of the configuration file");
      System.setProperty(SOURCE_KEY, "alpha");
      assertInstanceOf(Alpha.class, Providers.of(SourceProvider.class, loader).select(TEXT),
          "a system property wins over the file");
    }
    System.clearProperty(SOURCE_KEY);
    try (URLClassLoader loader = isolated(library, application)) {
      assertInstanceOf(TextOnly.class, Providers.of(SourceProvider.class, loader).select(TEXT),
          "the higher ordinal wins");
    }
    configure(application, Providers.ORDINAL_KEY + " = 20\n" + SOURCE_KEY + " = alpha\n");
    try (URLClassLoader loader = isolated(library, application)) {
      assertInstanceOf(Alpha.class, Providers.of(SourceProvider.class, loader).select(TEXT),
          "the higher ordinal wins");
    }
    configure(application, Providers.ORDINAL_KEY + " = x\n");
    try (URLClassLoader loader = isolated(library, application)) {
      assertThrows(ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader), "an ordinal that is not a number");
    }
  }

  @Test
  void testBrokenProvidersAreSkipped() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(
        NotAProvider.class, Alpha.class, NoPublicConstructor.class, ConstructorThrows.class,
        NameThrows.class, NameErrors.class, NullName.class, BlankName.class, CommaName.class,
        PriorityThrows.class, SupportsErrors.class, TextOnly.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(List.of("alpha", "supports", "text"), names(providers.installed()),
          "only the usable providers are installed");
      assertInstanceOf(TextOnly.class, providers.select(TEXT), "selected for text");
      assertInstanceOf(Alpha.class, providers.select(BINARY),
          "a provider whose supports() throws must not hide the others");
      assertTrue(providers.byName("a,b").isEmpty(), "a name with the separator is skipped");
    }
  }

  @Test
  void testUnsatisfiedSelectionReportsSkippedProviders() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(NotAProvider.class, TextOnly.class)))) {
      final UnsatisfiedProviderException e = assertThrows(UnsatisfiedProviderException.class,
          () -> Providers.of(SourceProvider.class, loader).select(BINARY));
      assertTrue(e.getMessage().contains("Skipped during lookup: 1"), e.getMessage());
    }
  }

  @Test
  void testMissingProviderClassIsSkipped() throws IOException {
    write(dir, SERVICES + SourceProvider.class.getName(),
        "opennlp.tools.util.ext.DoesNotExist\n" + Alpha.class.getName() + "\n");
    try (URLClassLoader loader = isolated(dir)) {
      assertEquals(1, Providers.of(SourceProvider.class, loader).installed().size(),
          "the provider behind the missing class");
    }
  }

  @Test
  @Timeout(30)
  void testLookupFailsIfServiceFilesCannotBeRead() throws IOException {
    try (URLClassLoader loader = failing(new IOException("unreadable"))) {
      final ProviderResolutionException e = assertThrows(ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader));
      assertTrue(e.getMessage().contains(SourceProvider.class.getName()), e.getMessage());
    }
  }

  /** A class loader that keeps failing without a cause must not be retried forever. */
  @Test
  @Timeout(30)
  void testLookupFailsIfItDoesNotAdvance() throws IOException {
    try (URLClassLoader loader = failing(new NoClassDefFoundError("class loader is stopped"))) {
      assertThrows(ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader), "a repeated error without a cause");
    }
    try (URLClassLoader loader = failing(new IllegalStateException("web application stopped"))) {
      final ProviderResolutionException e = assertThrows(ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader), "a stopped web application");
      assertInstanceOf(IllegalStateException.class, e.getCause(), "cause");
    }
  }

  /** A module or bundle class loader delegates without a parent chain and must still be used. */
  @Test
  void testUsesADelegatingContextClassLoader() throws IOException {
    final Thread thread = Thread.currentThread();
    final ClassLoader previous = thread.getContextClassLoader();
    final ClassLoader application = getClass().getClassLoader();
    register(dir, SourceProvider.class, List.of(Alpha.class));
    final URL services = dir.toUri().toURL();
    try (URLClassLoader delegating = new URLClassLoader(new URL[] {services}, null) {
      @Override
      public Class<?> loadClass(final String name) throws ClassNotFoundException {
        return application.loadClass(name);
      }

      @Override
      public URL getResource(final String name) {
        final URL own = findResource(name);
        return own != null ? own : application.getResource(name);
      }
    }) {
      thread.setContextClassLoader(delegating);
      assertInstanceOf(Alpha.class, Providers.of(SourceProvider.class).select(ANY),
          "a context class loader that delegates without a parent chain");
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  @Test
  void testSkippedRegistrationsDoNotHideAProviderBehindThem() throws IOException {
    final StringBuilder registrations = new StringBuilder();
    for (int broken = 0; broken < 100; broken++) {
      registrations.append("opennlp.tools.util.ext.Missing").append(broken).append('\n');
    }
    registrations.append(Alpha.class.getName()).append('\n');
    write(dir, SERVICES + SourceProvider.class.getName(), registrations.toString());
    try (URLClassLoader loader = isolated(dir)) {
      assertEquals(1, Providers.of(SourceProvider.class, loader).installed().size(),
          "a provider listed after a hundred missing ones");
    }
  }

  /** Two service files that cannot be read must not hide the providers of a third one. */
  @Test
  @Timeout(30)
  void testUnreadableServiceFilesDoNotHideAProvider() throws IOException {
    final List<URL> torn = new ArrayList<>();
    for (int file = 0; file < 2; file++) {
      final Path missing = dir.resolve("torn" + file).resolve(SERVICES
          + SourceProvider.class.getName());
      Files.createDirectories(missing.getParent());
      torn.add(missing.toUri().toURL());
    }
    register(dir, SourceProvider.class, List.of(Alpha.class));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()},
        getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException {
        if (!name.startsWith(SERVICES)) {
          return name.startsWith(META_INF) ? findResources(name) : super.getResources(name);
        }
        final List<URL> resources = new ArrayList<>(torn);
        resources.addAll(Collections.list(findResources(name)));
        return Collections.enumeration(resources);
      }
    }) {
      assertEquals(1, Providers.of(SourceProvider.class, loader).installed().size(),
          "the provider of the readable service file");
    }
  }

  @Test
  void testFatalErrorOfAConstructorIsRethrown() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(ConstructorRunsOutOfMemory.class, Alpha.class)))) {
      assertThrows(OutOfMemoryError.class, () -> Providers.of(SourceProvider.class, loader),
          "an error wrapped by the service loader is not skipped");
    }
  }

  @Test
  void testRejectsUnusableNames() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(ControlCharacterName.class, LongName.class, Alpha.class)))) {
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader);
      assertEquals(List.of("alpha"), names(providers.installed()),
          "a name with a control character or above the length limit is rejected");
    }
  }

  @Test
  void testConfigurationFailureIsReported() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class, List.of(Alpha.class)))) {
      final ProviderResolutionException duringLookup = assertThrows(
          ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader, (final String key) -> {
            throw new IllegalStateException("context destroyed");
          }), "a configuration that fails while the providers are looked up");
      assertInstanceOf(IllegalStateException.class, duringLookup.getCause(), "cause");
      final Providers<SourceProvider> providers = Providers.of(SourceProvider.class, loader,
          (final String key) -> {
            if (SOURCE_KEY.equals(key)) {
              throw new IllegalStateException("context destroyed");
            }
            return null;
          });
      final ProviderResolutionException duringSelect =
          assertThrows(ProviderResolutionException.class, () -> providers.select(ANY),
              "a configuration that fails while a provider is selected");
      assertInstanceOf(IllegalStateException.class, duringSelect.getCause(), "cause");
    }
  }

  @Test
  void testDisablingThroughTheConfigurationOfAnApplication() throws IOException {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final Map<String, String> application = Map.of(SOURCE_DISABLED_KEY, "text");
      final Providers<SourceProvider> providers =
          Providers.of(SourceProvider.class, loader, application::get);
      assertInstanceOf(Alpha.class, providers.select(TEXT), "the disabled provider is left out");
      assertTrue(providers.byName("text").isEmpty(), "disabled by the application");
    }
  }

  @Test
  void testConfigurationFileIsValidatedOnItsOwn() throws IOException {
    register(dir, SourceProvider.class, List.of(Alpha.class));
    configure(dir, Providers.ORDINAL_KEY + " = twenty\n");
    try (URLClassLoader loader = isolated(dir)) {
      final ProviderResolutionException e = assertThrows(ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader), "a single malformed file");
      assertTrue(e.getMessage().contains(Providers.CONFIGURATION_FILE), e.getMessage());
    }
  }

  @Test
  void testConfigurationFileWithByteOrderMark() throws IOException {
    register(dir, SourceProvider.class, List.of(Alpha.class, TextOnly.class));
    configure(dir, '\uFEFF' + SOURCE_KEY + " = alpha\n");
    try (URLClassLoader loader = isolated(dir)) {
      assertInstanceOf(Alpha.class, Providers.of(SourceProvider.class, loader).select(TEXT),
          "a byte order mark must not hide the first key");
    }
  }

  @Test
  void testConfigurationFileMustBeUtf8() throws IOException {
    register(dir, SourceProvider.class, List.of(Alpha.class));
    final Path file = dir.resolve(Providers.CONFIGURATION_FILE);
    Files.createDirectories(file.getParent());
    Files.write(file, (SOURCE_KEY + " = pr\u00fcfer\n").getBytes(StandardCharsets.ISO_8859_1));
    try (URLClassLoader loader = isolated(dir)) {
      final ProviderResolutionException e = assertThrows(ProviderResolutionException.class,
          () -> Providers.of(SourceProvider.class, loader), "a file that is not UTF-8");
      assertTrue(e.getMessage().contains(Providers.CONFIGURATION_FILE), e.getMessage());
    }
  }

  @Test
  void testUsesContextClassLoaderOnlyIfItSeesTheSpi() throws IOException {
    final Thread thread = Thread.currentThread();
    final ClassLoader previous = thread.getContextClassLoader();
    final AtomicInteger lookups = new AtomicInteger();
    try (URLClassLoader context = loader(Map.of(SourceProvider.class, List.of(Alpha.class)));
         URLClassLoader unrelated = counting(lookups)) {
      thread.setContextClassLoader(context);
      assertInstanceOf(Alpha.class, Providers.of(SourceProvider.class).select(ANY),
          "a context class loader that sees the SPI is used");
      thread.setContextClassLoader(unrelated);
      assertTrue(Providers.of(SourceProvider.class).installed().isEmpty(),
          "the class loader of the SPI has no providers registered");
      assertEquals(0, lookups.get(),
          "a context class loader that cannot see the SPI must not be used");
      thread.setContextClassLoader(null);
      assertTrue(Providers.of(SourceProvider.class).installed().isEmpty(), "without a context");
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  /** Nothing static may keep the class loader of a lookup alive, not even a failing one. */
  @Test
  void testLookupDoesNotRetainTheClassLoader() throws Exception {
    assertNull(collect(lookUpAndRelease()), "the class loader of a lookup was retained");
  }

  /**
   * @return A reference to the class loader of a lookup that is over when this method returns.
   */
  private WeakReference<ClassLoader> lookUpAndRelease() throws IOException {
    final URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, NameThrows.class)));
    assertEquals(1, Providers.of(SourceProvider.class, loader).installed().size(),
        "one provider is usable, the other one is skipped and logged");
    loader.close();
    return new WeakReference<>(loader);
  }

  /** The configuration of an instance is independent of the system properties another one uses. */
  @Test
  void testConcurrentSelection() throws Exception {
    try (URLClassLoader loader = loader(Map.of(SourceProvider.class,
        List.of(Alpha.class, TextOnly.class)))) {
      final Map<String, String> application = Map.of(SOURCE_KEY, "alpha");
      final Providers<SourceProvider> pinned =
          Providers.of(SourceProvider.class, loader, application::get);
      final Providers<SourceProvider> configured = Providers.of(SourceProvider.class, loader);
      final int threads = 8;
      final ExecutorService executor = Executors.newFixedThreadPool(threads + 1);
      try {
        final AtomicBoolean running = new AtomicBoolean(true);
        final Future<?> toggle = executor.submit(() -> {
          while (running.get()) {
            System.setProperty(SOURCE_KEY, "alpha");
            System.clearProperty(SOURCE_KEY);
            Thread.onSpinWait();
          }
        });
        final List<Callable<Void>> tasks = new ArrayList<>();
        for (int task = 0; task < threads; task++) {
          tasks.add(() -> {
            for (int i = 0; i < 500; i++) {
              assertInstanceOf(Alpha.class, pinned.select(TEXT), "the pinned configuration");
              assertEquals("alpha", pinned.select(TEXT).create(TEXT).id(), "created by alpha");
              final SourceProvider selected = configured.select(TEXT);
              assertTrue(selected instanceof Alpha || selected instanceof TextOnly,
                  "the system property selects one of both");
              assertInstanceOf(Alpha.class, configured.select(BINARY),
                  "only alpha supports a binary spec");
            }
            return null;
          });
        }
        for (final Future<Void> result : executor.invokeAll(tasks)) {
          result.get();
        }
        running.set(false);
        toggle.get();
      } finally {
        executor.shutdownNow();
      }
    }
  }

  /**
   * @param reference The reference to clear.
   * @return The referenced class loader, or {@code null} if it was collected.
   */
  private ClassLoader collect(final WeakReference<ClassLoader> reference) throws Exception {
    for (int attempt = 0; attempt < 50 && reference.get() != null; attempt++) {
      System.gc();
      TimeUnit.MILLISECONDS.sleep(50);
    }
    return reference.get();
  }

  /**
   * @param providers The providers to name.
   * @return The names of {@code providers} in order.
   */
  private List<String> names(final List<SourceProvider> providers) {
    final List<String> names = new ArrayList<>(providers.size());
    for (final SourceProvider provider : providers) {
      names.add(provider.name());
    }
    return names;
  }

  /**
   * @param registrations The provider classes to register per SPI.
   * @return A class loader that finds service files only in the temporary directory.
   */
  private URLClassLoader loader(final Map<Class<?>, List<Class<?>>> registrations)
      throws IOException {
    for (final Map.Entry<Class<?>, List<Class<?>>> registration : registrations.entrySet()) {
      register(dir, registration.getKey(), registration.getValue());
    }
    return isolated(dir);
  }

  /**
   * @param root The directory to write the service file into.
   * @param spi The SPI to register providers for.
   * @param providers The provider classes to register.
   */
  private void register(final Path root, final Class<?> spi, final List<Class<?>> providers)
      throws IOException {
    write(root, SERVICES + spi.getName(), lines(providers));
  }

  /**
   * @param providers The provider classes to register.
   * @return The content of a service file listing {@code providers}.
   */
  private String lines(final List<Class<?>> providers) {
    final StringBuilder names = new StringBuilder();
    for (final Class<?> provider : providers) {
      names.append(provider.getName()).append('\n');
    }
    return names.toString();
  }

  /**
   * @param root The directory to write the configuration file into.
   * @param content The content of the configuration file.
   */
  private void configure(final Path root, final String content) throws IOException {
    write(root, Providers.CONFIGURATION_FILE, content);
  }

  /**
   * @param root The directory to write into.
   * @param resource The path of the resource below {@code root}.
   * @param content The content of the resource.
   */
  private void write(final Path root, final String resource, final String content)
      throws IOException {
    final Path file = root.resolve(resource);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  /**
   * @param roots The directories to find resources in.
   * @return A class loader that finds the resources of OpenNLP only in {@code roots}, and classes
   *         in its parent.
   */
  private URLClassLoader isolated(final Path... roots) throws IOException {
    final URL[] urls = new URL[roots.length];
    for (int root = 0; root < roots.length; root++) {
      urls[root] = roots[root].toUri().toURL();
    }
    return new URLClassLoader(urls, getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException {
        return name.startsWith(META_INF) ? findResources(name) : super.getResources(name);
      }
    };
  }

  /**
   * @param failure The failure to raise for every resource lookup.
   * @return A class loader that cannot list resources.
   */
  private URLClassLoader failing(final Throwable failure) {
    return new URLClassLoader(new URL[0], getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException {
        if (!name.startsWith(META_INF)) {
          return super.getResources(name);
        }
        if (failure instanceof IOException io) {
          throw io;
        }
        if (failure instanceof Error error) {
          throw error;
        }
        throw (RuntimeException) failure;
      }
    };
  }

  /**
   * @param lookups Counts the resource lookups of OpenNLP.
   * @return A class loader that cannot see the SPI, because it has no parent.
   */
  private URLClassLoader counting(final AtomicInteger lookups) {
    return new URLClassLoader(new URL[0], null) {
      @Override
      public Enumeration<URL> getResources(final String name) throws IOException {
        if (name.startsWith(META_INF)) {
          lookups.incrementAndGet();
          return Collections.emptyEnumeration();
        }
        return super.getResources(name);
      }
    };
  }

  interface Source {
    String id();
  }

  interface Sink {
  }

  public interface SourceProvider extends Provider<Source> {
  }

  public interface SinkProvider extends Provider<Sink> {
  }

  static final class First {
    public interface SameName extends Provider<Source> {
    }
  }

  static final class Second {
    public interface SameName extends Provider<Source> {
    }
  }

  public static class Alpha implements SourceProvider {
    @Override
    public String name() {
      return "alpha";
    }

    @Override
    public boolean supports(final ProviderSpec spec) {
      return true;
    }

    @Override
    public Source create(final ProviderSpec spec) {
      final String id = name();
      return () -> id;
    }
  }

  public static class Beta extends Alpha {
    @Override
    public String name() {
      return "beta";
    }
  }

  /** The same name and priority as {@link Alpha}. */
  public static class AlphaDuplicate extends Alpha {
  }

  /** The same name as {@link Alpha} with a higher priority. */
  public static class AlphaReplacement extends Alpha {
    @Override
    public int priority() {
      return 10;
    }
  }

  /** Supports only a text spec, ranked above {@link Alpha}. */
  public static class TextOnly extends Alpha {
    @Override
    public String name() {
      return "text";
    }

    @Override
    public int priority() {
      return 5;
    }

    @Override
    public boolean supports(final ProviderSpec spec) {
      return TEXT_KIND.equals(spec.option(KIND_OPTION, TEXT_KIND));
    }
  }

  public static class Unavailable extends Alpha {
    @Override
    public String name() {
      return "unavailable";
    }

    @Override
    public int priority() {
      return 100;
    }

    @Override
    public boolean isAvailable() {
      return false;
    }
  }

  public static class AvailabilityThrows extends Unavailable {
    @Override
    public boolean isAvailable() {
      throw new NoClassDefFoundError("missing runtime");
    }
  }

  public static class NameThrows extends Alpha {
    @Override
    public String name() {
      throw new IllegalStateException("no name");
    }
  }

  public static class NameErrors extends Alpha {
    @Override
    public String name() {
      throw new AssertionError("no name");
    }
  }

  public static class NullName extends Alpha {
    @Override
    public String name() {
      return null;
    }
  }

  public static class BlankName extends Alpha {
    @Override
    public String name() {
      return " ";
    }
  }

  /** A name holding the separator of the disabled names. */
  public static class CommaName extends Alpha {
    @Override
    public String name() {
      return "a,b";
    }
  }

  /** A name that would forge a log line. */
  public static class ControlCharacterName extends Alpha {
    @Override
    public String name() {
      return "acme\r\nforged entry";
    }
  }

  /** A name beyond the length a configuration value may hold. */
  public static class LongName extends Alpha {
    @Override
    public String name() {
      return "n".repeat(200);
    }
  }

  public static class PriorityThrows extends Alpha {
    @Override
    public int priority() {
      throw new IllegalStateException("no priority");
    }
  }

  /** Available and ranked highest, but cannot decide whether it supports a spec. */
  public static class SupportsErrors extends Unavailable {
    @Override
    public String name() {
      return "supports";
    }

    @Override
    public boolean isAvailable() {
      return true;
    }

    @Override
    public boolean supports(final ProviderSpec spec) {
      throw new AssertionError("cannot decide");
    }
  }

  public static class ConstructorThrows extends Alpha {
    public ConstructorThrows() {
      throw new IllegalStateException("cannot construct");
    }
  }

  public static class ConstructorRunsOutOfMemory extends Alpha {
    public ConstructorRunsOutOfMemory() {
      throw new OutOfMemoryError("cannot construct");
    }
  }

  public static class NoPublicConstructor extends Alpha {
    private NoPublicConstructor() {
    }
  }

  public static class NotAProvider {
  }

  /**
   * Tracks {@link Initializing}. A type of its own, so a test can read the counters without
   * initializing {@link Initializing}, which happens only once per class loader.
   */
  static final class Tracker {

    static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    static final AtomicInteger INSTANCES = new AtomicInteger();

    private Tracker() {
    }
  }

  /** Tracks its initialization and its instances through {@link Tracker}. */
  public static class Initializing extends Alpha {

    static {
      Tracker.INITIALIZED.set(true);
    }

    public Initializing() {
      Tracker.INSTANCES.incrementAndGet();
    }

    @Override
    public String name() {
      return "initializing";
    }
  }

  /** Counts its instances, to show that a disabled class is not loaded. */
  public static class NotLoaded extends Alpha {

    static final AtomicInteger INSTANCES = new AtomicInteger();

    public NotLoaded() {
      INSTANCES.incrementAndGet();
    }

    @Override
    public String name() {
      return "notloaded";
    }
  }

  public static class CountingSink implements SinkProvider {

    static final AtomicInteger INSTANCES = new AtomicInteger();

    public CountingSink() {
      INSTANCES.incrementAndGet();
    }

    @Override
    public String name() {
      return "sink";
    }

    @Override
    public boolean supports(final ProviderSpec spec) {
      return true;
    }

    @Override
    public Sink create(final ProviderSpec spec) {
      return new Sink() {
      };
    }
  }
}
