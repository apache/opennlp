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
package opennlp.embeddings.onnx;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.embeddings.spi.TeacherEncoderProviders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The capability, availability and registration of the ONNX teacher provider. */
class OnnxTeacherEncoderProviderTest {

  private final OnnxTeacherEncoderProvider provider = new OnnxTeacherEncoderProvider();

  @Test
  void testIdentityAndAvailability() {
    assertEquals("onnx", provider.name());
    assertEquals(0, provider.priority());
    assertTrue(provider.isAvailable(), "onnxruntime is on the test classpath");
  }

  @ParameterizedTest
  @ValueSource(strings = {"model.onnx", "MODEL.ONNX", "onnx/model.Onnx"})
  void testSupportsOnnxFiles(String model) {
    assertTrue(provider.supports(Path.of(model)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"model.bin", "model.onnx_data", "onnx", "model.safetensors"})
  void testDoesNotSupportOtherFiles(String model) {
    assertFalse(provider.supports(Path.of(model)));
  }

  @Test
  void testDoesNotSupportNullOrRoot() {
    assertFalse(provider.supports(null));
    assertFalse(provider.supports(Path.of("/")));
  }

  @Test
  void testRegisteredAndSelectedForOnnxModels() {
    assertInstanceOf(OnnxTeacherEncoderProvider.class, TeacherEncoderProviders.get("onnx"));
    assertInstanceOf(OnnxTeacherEncoderProvider.class,
        TeacherEncoderProviders.select(Path.of("onnx", "model.onnx")));
  }
}
