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

/** Selection of teacher encoder providers registered in a temporary class loader. */
class TeacherEncoderProvidersTest {

  private static final String SERVICE =
      "META-INF/services/" + TeacherEncoderProvider.class.getName();

  @AfterEach
  void clearPin() {
    System.clearProperty(TeacherEncoderProviders.PROVIDER_PROPERTY);
  }

  @Test
  void listsAndSelectsByName(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Second.class, First.class)) {
      List<TeacherEncoderProvider> installed = TeacherEncoderProviders.installed(loader);
      assertEquals(2, installed.size());
      assertEquals("second", installed.get(0).name());
      assertInstanceOf(First.class, TeacherEncoderProviders.get("first", loader));
      IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
          () -> TeacherEncoderProviders.get("missing", loader));
      assertTrue(missing.getMessage().contains("[second, first]"), missing.getMessage());
      assertThrows(IllegalArgumentException.class, () -> TeacherEncoderProviders.get(" ", loader));
      assertThrows(IllegalArgumentException.class, () -> TeacherEncoderProviders.get("first", null));
      assertThrows(IllegalArgumentException.class,
          () -> TeacherEncoderProviders.select(null, loader));
    }
  }

  @Test
  void worksWithoutAnyProvider(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir)) {
      assertTrue(TeacherEncoderProviders.installed(loader).isEmpty());
      IllegalArgumentException none = assertThrows(IllegalArgumentException.class,
          () -> TeacherEncoderProviders.select(dir.resolve("model.onnx"), loader));
      assertTrue(none.getMessage().contains("[]"), none.getMessage());
    }
  }

  @Test
  void sameNameIsRankedByPriorityAndTiesAreAmbiguous(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, FirstReplacement.class)) {
      assertInstanceOf(FirstReplacement.class, TeacherEncoderProviders.get("first", loader));
    }
    try (URLClassLoader loader = providers(dir, First.class, Duplicate.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> TeacherEncoderProviders.get("first", loader));
      assertTrue(e.getMessage().contains(Duplicate.class.getName()), e.getMessage());
    }
  }

  @Test
  void selectRoutesByCapabilityAndSkipsUnavailable(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, Unavailable.class, First.class, OnlyBin.class)) {
      assertInstanceOf(OnlyBin.class,
          TeacherEncoderProviders.select(dir.resolve("model.bin"), loader));
      assertInstanceOf(First.class,
          TeacherEncoderProviders.select(dir.resolve("model.onnx"), loader));
    }
    try (URLClassLoader loader = providers(dir, First.class, Second.class)) {
      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> TeacherEncoderProviders.select(dir.resolve("model.onnx"), loader));
      assertTrue(e.getMessage().contains(TeacherEncoderProviders.PROVIDER_PROPERTY),
          e.getMessage());
    }
  }

  @Test
  void pinnedProviderOverridesSelection(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, OnlyBin.class)) {
      System.setProperty(TeacherEncoderProviders.PROVIDER_PROPERTY, "first");
      assertInstanceOf(First.class,
          TeacherEncoderProviders.select(dir.resolve("model.bin"), loader));
      System.setProperty(TeacherEncoderProviders.PROVIDER_PROPERTY, "missing");
      assertThrows(IllegalArgumentException.class,
          () -> TeacherEncoderProviders.select(dir.resolve("model.bin"), loader));
    }
  }

  @Test
  void brokenRegistrationsAreSkipped(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, NotAProvider.class, First.class)) {
      assertEquals(1, TeacherEncoderProviders.installed(loader).size());
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
    return new URLClassLoader(new URL[] {dir.toUri().toURL()}, getClass().getClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return SERVICE.equals(name) ? findResources(name) : super.getResources(name);
      }
    };
  }

  public static class First implements TeacherEncoderProvider {
    @Override
    public String name() {
      return "first";
    }

    @Override
    public boolean supports(Path model) {
      return true;
    }

    @Override
    public TeacherEncoder load(Path model) {
      throw new UnsupportedOperationException("not opened in this test");
    }
  }

  public static class Second extends First {
    @Override
    public String name() {
      return "second";
    }
  }

  public static class Duplicate extends First {
  }

  public static class FirstReplacement extends First {
    @Override
    public int priority() {
      return 10;
    }
  }

  public static class OnlyBin extends First {
    @Override
    public String name() {
      return "onlybin";
    }

    @Override
    public int priority() {
      return 5;
    }

    @Override
    public boolean supports(Path model) {
      return model.getFileName().toString().endsWith(".bin");
    }
  }

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

  public static class NotAProvider {
  }
}
