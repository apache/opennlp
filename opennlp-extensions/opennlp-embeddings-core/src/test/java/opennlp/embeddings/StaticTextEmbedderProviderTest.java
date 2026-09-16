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
package opennlp.embeddings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProviders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The capability, availability and registration of the static provider. */
class StaticTextEmbedderProviderTest {

  private final StaticTextEmbedderProvider provider = new StaticTextEmbedderProvider();

  @Test
  void testIdentityAndAvailability() {
    assertEquals("static", provider.name());
    assertEquals(0, provider.priority());
    assertTrue(provider.isAvailable());
  }

  @Test
  void testSupportsAModelDirectoryWithoutOptions(@TempDir Path dir) throws Exception {
    EmbeddingTestFixtures.writeSearchDirectory(dir);
    assertTrue(provider.supports(dir, Map.of()));
    assertFalse(provider.supports(dir, Map.of("vocabulary", "vocab.txt")));
    assertFalse(provider.supports(dir, null));
    assertFalse(provider.supports(null, Map.of()));
  }

  @Test
  void testDoesNotSupportFilesOrIncompleteDirectories(@TempDir Path dir) throws Exception {
    assertFalse(provider.supports(dir, Map.of()), "empty directory");
    Files.createFile(dir.resolve("model.safetensors"));
    assertFalse(provider.supports(dir, Map.of()), "no config.json");
    Files.createFile(dir.resolve("config.json"));
    assertTrue(provider.supports(dir, Map.of()));
    assertFalse(provider.supports(dir.resolve("model.safetensors"), Map.of()), "a file");
    assertFalse(provider.supports(dir.resolve("model.onnx"), Map.of()), "an ONNX file");
  }

  /** The provider is registered in this module and is the one selected for a model directory. */
  @Test
  void testRegisteredAndSelectedForModelDirectories(@TempDir Path dir) throws Exception {
    EmbeddingTestFixtures.writeSearchDirectory(dir);
    assertInstanceOf(StaticTextEmbedderProvider.class, TextEmbedderProviders.get("static"));
    assertInstanceOf(StaticTextEmbedderProvider.class,
        TextEmbedderProviders.select(dir, Map.of()));
    StaticEmbeddingModel expected = StaticEmbeddingModel.load(dir);
    try (TextEmbedder actual = TextEmbedderProviders.select(dir, Map.of()).load(dir, Map.of())) {
      assertArrayEquals(expected.embed("king"), actual.embed("king"));
    }
  }

  @Test
  void testLoadRejectsOptionsAndNull(@TempDir Path dir) {
    assertThrows(IllegalArgumentException.class, () -> provider.load(null, Map.of()));
    assertThrows(IllegalArgumentException.class, () -> provider.load(dir, null));
    assertThrows(IllegalArgumentException.class,
        () -> provider.load(dir, Map.of("lowerCase", "true")));
  }
}
