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
package opennlp.dl.vectors;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.embeddings.TextEmbedderProvider;
import opennlp.tools.embeddings.TextEmbedderProviders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The capability, availability and registration of the ONNX provider, without a model. */
class OnnxTextEmbedderProviderTest {

  private static final Map<String, String> OPTIONS = Map.of("vocabulary", "vocab.txt");

  private final OnnxTextEmbedderProvider provider = new OnnxTextEmbedderProvider();

  @Test
  void testIdentityAndAvailability() {
    assertEquals("onnx", provider.name());
    assertEquals(0, provider.priority());
    assertTrue(provider.isAvailable(), "onnxruntime is on the test classpath");
  }

  @ParameterizedTest
  @ValueSource(strings = {"model.onnx", "MODEL.ONNX", "dir/model.Onnx"})
  void testSupportsOnnxFilesWithItsOwnOptions(String model) {
    assertTrue(provider.supports(Path.of(model), OPTIONS));
    assertTrue(provider.supports(Path.of(model), Map.of()));
    assertTrue(provider.supports(Path.of(model),
        Map.of("vocabulary", "vocab.txt", "lowerCase", "false")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"model.bin", "model.onnx.bak", "onnx", "model.pt"})
  void testDoesNotSupportOtherModelFiles(String model) {
    assertFalse(provider.supports(Path.of(model), OPTIONS));
  }

  @Test
  void testDoesNotSupportForeignOptionsOrNullArguments() {
    assertFalse(provider.supports(Path.of("model.onnx"), Map.of("vocabulary", "v", "device", "gpu")));
    assertFalse(provider.supports(null, OPTIONS));
    assertFalse(provider.supports(Path.of("model.onnx"), null));
    assertFalse(provider.supports(Path.of("/"), OPTIONS));
  }

  /** The provider is registered in this module and is the one selected for an ONNX model. */
  @Test
  void testRegisteredAndSelectedForOnnxModels() {
    assertInstanceOf(OnnxTextEmbedderProvider.class, TextEmbedderProviders.get("onnx"));
    TextEmbedderProvider selected = TextEmbedderProviders.select(Path.of("model.onnx"), OPTIONS);
    assertInstanceOf(OnnxTextEmbedderProvider.class, selected);
  }
}
