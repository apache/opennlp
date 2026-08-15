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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The encoder's argument contract, checked before the ONNX runtime is touched.
 */
class OnnxTeacherEncoderTest {

  @Test
  void testNullFileFailsLoudly() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(null));
    assertTrue(e.getMessage().contains("must not be null"), e.getMessage());
  }

  @Test
  void testMissingFileFailsLoudly(@TempDir Path directory) {
    final Path missing = directory.resolve("model.onnx");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(missing));
    assertTrue(e.getMessage().contains(missing.toString()), e.getMessage());
  }

  @Test
  void testDirectoryIsNotARegularFile(@TempDir Path directory) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(directory));
    assertTrue(e.getMessage().contains("regular file"), e.getMessage());
  }
}
