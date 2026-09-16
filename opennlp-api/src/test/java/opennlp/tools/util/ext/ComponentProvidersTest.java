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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discovery and selection of component providers registered in a temporary class loader. */
class ComponentProvidersTest {

  private static final String SERVICE = "META-INF/services/" + ComponentProvider.class.getName();
  private static final ComponentSpec ANY = ComponentSpec.empty();

  /** A contract under test; providers of it and of {@link Sink} share one service file. */
  interface Source {
    String id();
  }

  /** A second contract, to show that lookups are per contract. */
  interface Sink {
  }

  @AfterEach
  void clearPins() {
    System.clearProperty(ComponentProviders.pinProperty(Source.class));
    System.clearProperty(ComponentProviders.pinProperty(Sink.class));
  }

  @Test
  void testPinPropertyIsNamedAfterTheContract() {
    assertEquals("opennlp.provider.Source", ComponentProviders.pinProperty(Source.class));
    assertThrows(IllegalArgumentException.class, () -> ComponentProviders.pinProperty(null));
  }

  @Test
  void testListsProvidersOfOneContractInRegistrationOrder(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, SinkProvider.class, Second.class, First.class)) {
      List<ComponentProvider<Source>> sources = ComponentProviders.installed(Source.class, loader);
      assertEquals(2, sources.size());
      assertEquals("second", sources.get(0).name());
      assertEquals("first", sources.get(1).name());
      assertEquals(1, ComponentProviders.installed(Sink.class, loader).size());
      assertThrows(UnsupportedOperationException.class, () -> sources.remove(0));
    }
  }

  @Test
  void testCreatesIndependentComponents(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class)) {
      ComponentProvider<Source> first = ComponentProviders.get(Source.class, "first", loader);
      Source a = first.create(ANY);
      Source b = first.create(ANY);
      assertEquals("first", a.id());
      assertTrue(a != b);
    }
  }

  @Test
  void testRejectsMissingNameAndInvalidArguments(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class)) {
      IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.get(Source.class, "missing", loader));
      assertTrue(missing.getMessage().contains("Source"), missing.getMessage());
      assertTrue(missing.getMessage().contains("[first]"), missing.getMessage());
      assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.get(Source.class, " ", loader));
      assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.get(null, "first", loader));
      assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.get(Source.class, "first", null));
      assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.select(Source.class, null, loader));
      assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.supporting(Source.class, null, loader));
    }
  }

  @Test
  void testWorksWithoutAnyProvider(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir)) {
      assertTrue(ComponentProviders.installed(Source.class, loader).isEmpty());
      assertTrue(ComponentProviders.supporting(Source.class, ANY, loader).isEmpty());
      IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.select(Source.class, ANY, loader));
      assertTrue(none.getMessage().contains("[]"), none.getMessage());
    }
  }

  @Test
  void testSameNameIsRankedByPriorityAndTiesAreAmbiguous(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, FirstReplacement.class)) {
      assertInstanceOf(FirstReplacement.class,
          ComponentProviders.get(Source.class, "first", loader));
    }
    try (URLClassLoader loader = providers(dir, First.class, Duplicate.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> ComponentProviders.get(Source.class, "first", loader));
      assertTrue(e.getMessage().contains(Duplicate.class.getName()), e.getMessage());
    }
  }

  @Test
  void testUnavailableProviderOfThatNameIsReported(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Unavailable.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> ComponentProviders.get(Source.class, "unavailable", loader));
      assertTrue(e.getMessage().contains("not available"), e.getMessage());
    }
  }

  /** Selection routes by capability and lists every supporting provider, best first. */
  @Test
  void testSupportingAndSelectRouteByCapabilityAndPriority(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, OnlyText.class, Unavailable.class,
        Failing.class)) {
      ComponentSpec text = ComponentSpec.of(dir.resolve("model.txt"));
      ComponentSpec other = ComponentSpec.of(dir.resolve("model.bin"));
      List<ComponentProvider<Source>> forText =
          ComponentProviders.supporting(Source.class, text, loader);
      assertEquals(2, forText.size());
      assertInstanceOf(OnlyText.class, forText.get(0));
      assertInstanceOf(First.class, forText.get(1));
      assertInstanceOf(OnlyText.class, ComponentProviders.select(Source.class, text, loader));
      assertEquals(1, ComponentProviders.supporting(Source.class, other, loader).size());
      assertInstanceOf(First.class, ComponentProviders.select(Source.class, other, loader));
    }
  }

  @Test
  void testSelectWithoutSupportingProviderNamesTheInstalledOnes(@TempDir Path dir)
      throws Exception {
    try (URLClassLoader loader = providers(dir, OnlyText.class)) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.select(Source.class, ComponentSpec.of(dir.resolve("a.bin")),
              loader));
      assertTrue(e.getMessage().contains("a.bin"), e.getMessage());
      assertTrue(e.getMessage().contains("[onlytext]"), e.getMessage());
    }
  }

  @Test
  void testSelectWithTiedPriorityIsAmbiguousAndNamesThePinProperty(@TempDir Path dir)
      throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, Second.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> ComponentProviders.select(Source.class, ANY, loader));
      assertTrue(e.getMessage().contains("opennlp.provider.Source"), e.getMessage());
      assertTrue(e.getMessage().contains(First.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains(Second.class.getName()), e.getMessage());
      assertEquals(2, ComponentProviders.supporting(Source.class, ANY, loader).size());
    }
  }

  /** The pin property is per contract and overrides capability and priority. */
  @Test
  void testPinnedProviderOverridesSelectionForItsContractOnly(@TempDir Path dir)
      throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, OnlyText.class, SinkProvider.class)) {
      ComponentSpec text = ComponentSpec.of(dir.resolve("model.txt"));
      System.setProperty(ComponentProviders.pinProperty(Source.class), "first");
      assertInstanceOf(First.class, ComponentProviders.select(Source.class, text, loader));
      assertInstanceOf(SinkProvider.class, ComponentProviders.select(Sink.class, ANY, loader));
      System.setProperty(ComponentProviders.pinProperty(Source.class), " onlytext ");
      assertInstanceOf(OnlyText.class, ComponentProviders.select(Source.class, ANY, loader));
      System.setProperty(ComponentProviders.pinProperty(Source.class), "missing");
      assertThrows(IllegalArgumentException.class,
          () -> ComponentProviders.select(Source.class, text, loader));
      System.setProperty(ComponentProviders.pinProperty(Source.class), "  ");
      assertInstanceOf(OnlyText.class, ComponentProviders.select(Source.class, text, loader));
    }
  }

  /** One broken registration must not hide the working providers next to it. */
  @Test
  void testBrokenRegistrationsAreSkipped(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, NotAProvider.class, First.class,
        NoPublicConstructor.class, Second.class)) {
      List<ComponentProvider<Source>> installed =
          ComponentProviders.installed(Source.class, loader);
      assertEquals(2, installed.size());
      assertInstanceOf(First.class, installed.get(0));
      assertInstanceOf(Second.class, installed.get(1));
    }
    Files.writeString(dir.resolve(SERVICE), "opennlp.tools.util.ext.DoesNotExist\n"
        + First.class.getName() + "\n");
    try (URLClassLoader loader = serviceLoader(dir)) {
      assertEquals(1, ComponentProviders.installed(Source.class, loader).size());
    }
  }

  @Test
  void testBlankNameOrMissingContractIsRejected(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Blank.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> ComponentProviders.installed(Source.class, loader));
      assertTrue(e.getMessage().contains(Blank.class.getName()), e.getMessage());
    }
    try (URLClassLoader loader = providers(dir, NoContract.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> ComponentProviders.installed(Source.class, loader));
      assertTrue(e.getMessage().contains("no contract"), e.getMessage());
    }
  }

  private URLClassLoader providers(Path dir, Class<?>... types) throws IOException {
    Path descriptor = dir.resolve(SERVICE);
    Files.createDirectories(descriptor.getParent());
    StringBuilder names = new StringBuilder();
    for (Class<?> type : types) {
      names.append(type.getName()).append('\n');
    }
    Files.writeString(descriptor, names);
    return serviceLoader(dir);
  }

  /** A loader that answers the service lookup from {@code dir} only, hiding the parent's. */
  private URLClassLoader serviceLoader(Path dir) throws IOException {
    return new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return SERVICE.equals(name) ? findResources(name) : super.getResources(name);
      }
    };
  }

  public static class First implements ComponentProvider<Source> {
    @Override
    public Class<Source> type() {
      return Source.class;
    }

    @Override
    public String name() {
      return "first";
    }

    @Override
    public boolean supports(ComponentSpec spec) {
      return true;
    }

    @Override
    public Source create(ComponentSpec spec) {
      return this::name;
    }
  }

  public static class Second extends First {
    @Override
    public String name() {
      return "second";
    }
  }

  /** Same name and priority as {@link First}. */
  public static class Duplicate extends First {
  }

  /** Same name as {@link First} with a higher priority. */
  public static class FirstReplacement extends First {
    @Override
    public int priority() {
      return 10;
    }
  }

  /** Claims only {@code .txt} locations, ranked above {@link First}. */
  public static class OnlyText extends First {
    @Override
    public String name() {
      return "onlytext";
    }

    @Override
    public int priority() {
      return 5;
    }

    @Override
    public boolean supports(ComponentSpec spec) {
      return spec.locationEndsWith(".txt");
    }
  }

  /** Supports everything with the highest priority but reports its runtime as absent. */
  public static class Unavailable extends First {
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

  /** Throws from its availability check, which counts as unavailable. */
  public static class Failing extends First {
    @Override
    public String name() {
      return "failing";
    }

    @Override
    public boolean isAvailable() {
      throw new UnsupportedOperationException("probe failed");
    }
  }

  public static class Blank extends First {
    @Override
    public String name() {
      return " ";
    }
  }

  public static class NoContract extends First {
    @Override
    public Class<Source> type() {
      return null;
    }
  }

  public static class SinkProvider implements ComponentProvider<Sink> {
    @Override
    public Class<Sink> type() {
      return Sink.class;
    }

    @Override
    public String name() {
      return "sink";
    }

    @Override
    public boolean supports(ComponentSpec spec) {
      return true;
    }

    @Override
    public Sink create(ComponentSpec spec) {
      return new Sink() {
      };
    }
  }

  /** Registered in the service file but does not implement the interface. */
  public static class NotAProvider {
  }

  /** Implements the interface but cannot be instantiated by the service loader. */
  public static class NoPublicConstructor extends First {
    private NoPublicConstructor() {
    }
  }
}
