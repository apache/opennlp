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

import opennlp.embeddings.spi.TeacherEncoderProviders;
import opennlp.tools.embeddings.TextEmbedderProviders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks the dependencies supplied by the default embedding bundle. */
class DefaultEmbeddingProvidersTest {

  @Test
  void includesBothProvidersButNoCli(@TempDir Path dir) throws Exception {
    assertEquals("onnx", TextEmbedderProviders.get("onnx").name());
    assertEquals("static", TextEmbedderProviders.get("static").name());
    assertEquals("onnx", TeacherEncoderProviders.get("onnx").name());
    assertEquals(2, TextEmbedderProviders.installed().size());
    assertEquals(1, TeacherEncoderProviders.installed().size());
    // routing by model shape: an ONNX file goes to onnx, a static model directory to static
    assertEquals("onnx", TextEmbedderProviders.select(dir.resolve("model.onnx"),
        Map.of("vocabulary", "vocab.txt")).name());
    Files.createFile(dir.resolve("model.safetensors"));
    Files.createFile(dir.resolve("config.json"));
    assertEquals("static", TextEmbedderProviders.select(dir, Map.of()).name());
    assertEquals("onnx", TeacherEncoderProviders.select(dir.resolve("model.onnx")).name());
    assertThrows(ClassNotFoundException.class,
        () -> Class.forName("opennlp.tools.cmdline.CmdLineTool", false, getClass().getClassLoader()));
  }
}
