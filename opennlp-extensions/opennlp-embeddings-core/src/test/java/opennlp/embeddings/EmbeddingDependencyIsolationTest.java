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

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.spi.TeacherEncoderProviders;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProviders;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies that inference and distillation contracts do not pull in a backend or CLI. */
class EmbeddingDependencyIsolationTest {

  @Test
  void loadsStaticModelWithoutAnyNativeBackend(@TempDir Path dir) throws Exception {
    EmbeddingTestFixtures.writeSearchDirectory(dir);
    StaticEmbeddingModel expected = StaticEmbeddingModel.load(dir);
    try (TextEmbedder actual = TextEmbedderProviders.get("static").load(dir, Map.of())) {
      assertArrayEquals(expected.embed("king"), actual.embed("king"));
    }
    assertThrows(IllegalArgumentException.class, TextEmbedderProviders::getDefault);
    assertThrows(IllegalArgumentException.class, TeacherEncoderProviders::getDefault);
  }

  @Test
  void doesNotRequireOnnxOrCli() {
    ClassLoader loader = getClass().getClassLoader();
    assertThrows(ClassNotFoundException.class,
        () -> Class.forName("ai.onnxruntime.OrtEnvironment", false, loader));
    assertThrows(ClassNotFoundException.class,
        () -> Class.forName("opennlp.tools.cmdline.CmdLineTool", false, loader));
  }
}
