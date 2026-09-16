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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextEmbedderProvidersTest {

  private static final String SERVICE = "META-INF/services/" + TextEmbedderProvider.class.getName();
  private static final Map<String, String> NO_OPTIONS = Map.of();

  @AfterEach
  void clearPin() {
    System.clearProperty(TextEmbedderProviders.PROVIDER_PROPERTY);
  }

  @Test
  void selectsProvidersByNameAndOwnsModelsIndependently(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, Second.class)) {
      TextEmbedderProvider first = TextEmbedderProviders.get("first", loader);
      TextEmbedderProvider second = TextEmbedderProviders.get("second", loader);
      try (TextEmbedder a = first.load(dir, NO_OPTIONS);
           TextEmbedder b = first.load(dir, NO_OPTIONS);
           TextEmbedder c = second.load(dir, NO_OPTIONS)) {
        assertNotSame(a, b);
        assertEquals(1, a.dimension());
        assertEquals(2, c.dimension());
        a.close();
        assertEquals(1, b.embed("text")[0]);
        assertThrows(IllegalStateException.class, () -> a.embed("text"));
      }
    }
  }

  @Test
  void listsInstalledProvidersInRegistrationOrder(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Second.class, First.class)) {
      List<TextEmbedderProvider> installed = TextEmbedderProviders.installed(loader);
      assertEquals(2, installed.size());
      assertEquals("second", installed.get(0).name());
      assertEquals("first", installed.get(1).name());
      assertThrows(UnsupportedOperationException.class, () -> installed.remove(0));
    }
  }

  @Test
  void rejectsMissingNameAndInvalidArguments(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class)) {
      IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get("missing", loader));
      assertTrue(missing.getMessage().contains("missing"));
      assertTrue(missing.getMessage().contains("[first]"), missing.getMessage());
      assertThrows(IllegalArgumentException.class, () -> TextEmbedderProviders.get(null, loader));
      assertThrows(IllegalArgumentException.class, () -> TextEmbedderProviders.get(" ", loader));
      assertThrows(IllegalArgumentException.class, () -> TextEmbedderProviders.get("first", null));
      assertThrows(IllegalArgumentException.class, () -> TextEmbedderProviders.installed(null));
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(null, NO_OPTIONS, loader));
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(dir, null, loader));
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(dir, NO_OPTIONS, null));
    }
  }

  @Test
  void worksWithoutAnyProvider(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir)) {
      assertTrue(TextEmbedderProviders.installed(loader).isEmpty());
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get("any", loader));
      IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
      assertTrue(none.getMessage().contains("[]"), none.getMessage());
    }
  }

  /** Two registrations of one name: the higher priority replaces the lower one. */
  @Test
  void sameNameWithHigherPriorityWins(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, FirstReplacement.class)) {
      assertInstanceOf(FirstReplacement.class, TextEmbedderProviders.get("first", loader));
    }
    try (URLClassLoader loader = providers(dir, FirstReplacement.class, First.class)) {
      assertInstanceOf(FirstReplacement.class, TextEmbedderProviders.get("first", loader));
    }
  }

  @Test
  void sameNameWithSamePriorityIsAmbiguous(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, Duplicate.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> TextEmbedderProviders.get("first", loader));
      assertTrue(e.getMessage().contains(First.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains(Duplicate.class.getName()), e.getMessage());
    }
  }

  @Test
  void unavailableProviderOfThatNameIsReportedAsSuch(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Unavailable.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> TextEmbedderProviders.get("unavailable", loader));
      assertTrue(e.getMessage().contains("not available"), e.getMessage());
    }
  }

  /** Selection routes by capability: the provider that claims the request wins. */
  @Test
  void selectPicksTheSupportingProviderWithTheHighestPriority(@TempDir Path dir)
      throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, OnlyText.class)) {
      assertInstanceOf(OnlyText.class,
          TextEmbedderProviders.select(dir.resolve("model.txt"), NO_OPTIONS, loader));
      assertInstanceOf(First.class,
          TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
    }
  }

  /** A provider whose runtime is absent takes no part in selection, whatever its priority. */
  @Test
  void selectSkipsUnavailableProviders(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Unavailable.class, First.class)) {
      assertInstanceOf(First.class,
          TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
    }
    try (URLClassLoader loader = providers(dir, Unavailable.class, Failing.class)) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
      assertTrue(e.getMessage().contains("[unavailable, failing]"), e.getMessage());
    }
  }

  @Test
  void selectWithoutSupportingProviderNamesTheInstalledOnes(@TempDir Path dir)
      throws Exception {
    try (URLClassLoader loader = providers(dir, OnlyText.class)) {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
      assertTrue(e.getMessage().contains("model.bin"), e.getMessage());
      assertTrue(e.getMessage().contains("[onlytext]"), e.getMessage());
    }
  }

  @Test
  void selectWithTiedPriorityIsAmbiguousAndNamesThePinProperty(@TempDir Path dir)
      throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, Second.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
      assertTrue(e.getMessage().contains(TextEmbedderProviders.PROVIDER_PROPERTY), e.getMessage());
      assertTrue(e.getMessage().contains(First.class.getName()), e.getMessage());
      assertTrue(e.getMessage().contains(Second.class.getName()), e.getMessage());
    }
  }

  /** The pin property overrides capability and priority, and a pinned name must exist. */
  @Test
  void pinnedProviderOverridesSelection(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, OnlyText.class)) {
      System.setProperty(TextEmbedderProviders.PROVIDER_PROPERTY, "first");
      assertInstanceOf(First.class,
          TextEmbedderProviders.select(dir.resolve("model.txt"), NO_OPTIONS, loader));
      System.setProperty(TextEmbedderProviders.PROVIDER_PROPERTY, " onlytext ");
      assertInstanceOf(OnlyText.class,
          TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
      System.setProperty(TextEmbedderProviders.PROVIDER_PROPERTY, "missing");
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.select(dir.resolve("model.bin"), NO_OPTIONS, loader));
      System.setProperty(TextEmbedderProviders.PROVIDER_PROPERTY, "  ");
      assertInstanceOf(OnlyText.class,
          TextEmbedderProviders.select(dir.resolve("model.txt"), NO_OPTIONS, loader));
    }
  }

  /** One broken registration must not hide the working providers next to it. */
  @Test
  void brokenRegistrationsAreSkipped(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, NotAProvider.class, First.class,
        NoPublicConstructor.class, Second.class)) {
      List<TextEmbedderProvider> installed = TextEmbedderProviders.installed(loader);
      assertEquals(2, installed.size());
      assertInstanceOf(First.class, installed.get(0));
      assertInstanceOf(Second.class, installed.get(1));
    }
    Path descriptor = dir.resolve(SERVICE);
    Files.writeString(descriptor, "opennlp.tools.embeddings.DoesNotExist\n"
        + First.class.getName() + "\n");
    try (URLClassLoader loader = serviceLoader(dir)) {
      assertEquals(1, TextEmbedderProviders.installed(loader).size());
    }
  }

  @Test
  void blankProviderNameIsRejected(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Blank.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> TextEmbedderProviders.installed(loader));
      assertTrue(e.getMessage().contains(Blank.class.getName()), e.getMessage());
    }
  }

  @Test
  void selectedProviderIsTheSameInstanceListed(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class)) {
      TextEmbedderProvider listed = TextEmbedderProviders.installed(loader).get(0);
      assertEquals(listed.getClass(), TextEmbedderProviders.get("first", loader).getClass());
      assertSame(listed.getClass(),
          TextEmbedderProviders.select(dir.resolve("a"), NO_OPTIONS, loader).getClass());
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

  public static class First implements TextEmbedderProvider {
    @Override
    public String name() {
      return "first";
    }

    @Override
    public boolean supports(Path model, Map<String, String> options) {
      return true;
    }

    @Override
    public TextEmbedder load(Path model, Map<String, String> options) {
      return new Model(1);
    }
  }

  public static class Second extends First {
    @Override
    public String name() {
      return "second";
    }

    @Override
    public TextEmbedder load(Path model, Map<String, String> options) {
      return new Model(2);
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

  /** Claims only {@code .txt} models, ranked above {@link First}. */
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
    public boolean supports(Path model, Map<String, String> options) {
      return model.getFileName().toString().endsWith(".txt");
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

  /** Registered in the service file but does not implement the interface. */
  public static class NotAProvider {
  }

  /** Implements the interface but cannot be instantiated by the service loader. */
  public static class NoPublicConstructor extends First {
    private NoPublicConstructor() {
    }
  }

  private static class Model implements TextEmbedder {
    private final int dimension;
    private boolean closed;

    Model(int dimension) {
      this.dimension = dimension;
    }

    @Override
    public float[] embed(CharSequence text) {
      if (closed) {
        throw new IllegalStateException("closed");
      }
      float[] vector = new float[dimension];
      vector[0] = dimension;
      return vector;
    }

    @Override
    public int dimension() {
      return dimension;
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}
