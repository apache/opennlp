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
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextEmbedderProvidersTest {

  private static final String SERVICE = "META-INF/services/" + TextEmbedderProvider.class.getName();

  @Test
  void selectsProvidersAndOwnsModelsIndependently(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, Second.class)) {
      TextEmbedderProvider first = TextEmbedderProviders.get("first", loader);
      TextEmbedderProvider second = TextEmbedderProviders.get("second", loader);
      try (TextEmbedder a = first.load(dir, Map.of());
           TextEmbedder b = first.load(dir, Map.of());
           TextEmbedder c = second.load(dir, Map.of())) {
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
  void rejectsMissingAndDuplicateProviders(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir, First.class, Duplicate.class)) {
      assertTrue(assertThrows(IllegalStateException.class,
          () -> TextEmbedderProviders.get("first", loader)).getMessage().contains("Duplicate"));
      assertTrue(assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get("missing", loader)).getMessage().contains("missing"));
    }
  }

  @Test
  void worksWithoutAnyBackend(@TempDir Path dir) throws Exception {
    try (URLClassLoader loader = providers(dir)) {
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get("onnx", loader));
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get(null, loader));
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get(" ", loader));
      assertThrows(IllegalArgumentException.class,
          () -> TextEmbedderProviders.get("onnx", null));
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

  public static class First implements TextEmbedderProvider {
    @Override
    public String name() {
      return "first";
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

  public static class Duplicate extends First {
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
