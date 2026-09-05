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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The encoder's argument contract, checked before the ONNX runtime is touched.
 */
class OnnxTeacherEncoderTest {

  @Test
  void testRejectsNullFile() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(null));
    assertTrue(e.getMessage().contains("must not be null"), e.getMessage());
  }

  @Test
  void testRejectsMissingFile(@TempDir Path directory) {
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

  @Test
  void testRejectsNullAndEmptySequences(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeTinyOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      assertEquals("batch[0] must not be null", assertThrows(IllegalArgumentException.class,
          () -> encoder.encodeBatch(new long[][] {null})).getMessage());
      assertEquals("batch[1] must not be null", assertThrows(IllegalArgumentException.class,
          () -> encoder.encodeBatch(new long[][] {{1}, null})).getMessage());
      assertEquals("batch[0] must not be empty", assertThrows(IllegalArgumentException.class,
          () -> encoder.encodeBatch(new long[][] {new long[0]})).getMessage());
    }
  }

  @Test
  void testSupportsGraphWithoutAttentionMask(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeInputIdsOnlyOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      assertArrayEquals(new float[] {1f, -2f, 4f}, encoder.encodeBatch(new long[][] {{2}})[0]);
    }
  }

  @Test
  void testRejectsUnsupportedInput(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeUnsupportedInputOnnxModel(directory);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(model));
    assertTrue(exception.getMessage().contains("position_ids"), exception.getMessage());
  }

  @Test
  void testSupportsInt32InputIds(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeInt32InputOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      assertArrayEquals(new float[] {3f}, encoder.encodeBatch(new long[][] {{2, 4}})[0]);
      final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
          () -> encoder.encodeBatch(new long[][] {{(long) Integer.MAX_VALUE + 1}}));
      assertTrue(exception.getMessage().contains("input_ids[0][0]"), exception.getMessage());
      assertTrue(exception.getMessage().contains("INT32"), exception.getMessage());
    }
  }

  @Test
  void testRejectsRankOneInputIdsAtLoadTime(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeRankOneInputOnnxModel(directory);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(model));
    assertTrue(exception.getMessage().contains("input_ids"), exception.getMessage());
    assertTrue(exception.getMessage().contains("rank 2"), exception.getMessage());
  }

  @Test
  void testSupportsInt32AttentionMask(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeInt32AttentionMaskOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      assertArrayEquals(new float[] {3f}, encoder.encodeBatch(new long[][] {{2, 4}})[0]);
    }
  }

  @Test
  void testRejectsFloatInputIdsAtLoadTime(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeFloatInputOnnxModel(directory);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> OnnxTeacherEncoder.load(model));
    assertTrue(exception.getMessage().contains("input_ids"), exception.getMessage());
    assertTrue(exception.getMessage().contains("INT32 or INT64"), exception.getMessage());
  }

  @Test
  void testPrefersTheNamedLastHiddenStateOutput(@TempDir Path directory) throws Exception {
    final Path model = EmbeddingTestFixtures.writeMultipleOutputsOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      assertArrayEquals(new float[] {3f}, encoder.encodeBatch(new long[][] {{2, 4}})[0]);
    }
  }

  @Test
  void testRejectsOutputDimensionsThatDoNotMatchTheInput(@TempDir Path directory)
      throws Exception {
    final Path model = EmbeddingTestFixtures.writeFixedOutputOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      final IllegalArgumentException sequenceError = assertThrows(IllegalArgumentException.class,
          () -> encoder.encodeBatch(new long[][] {{2, 4}}));
      assertTrue(sequenceError.getMessage().contains("sequence dimension"),
          sequenceError.getMessage());

      final IllegalArgumentException batchError = assertThrows(IllegalArgumentException.class,
          () -> encoder.encodeBatch(new long[][] {{2}, {4}}));
      assertTrue(batchError.getMessage().contains("batch dimension"), batchError.getMessage());
    }
  }

  @Test
  void testMeanPoolingDoesNotOverflowFiniteHiddenStates(@TempDir Path directory)
      throws Exception {
    final Path model = EmbeddingTestFixtures.writeMaxFloatOnnxModel(directory);

    try (OnnxTeacherEncoder encoder = OnnxTeacherEncoder.load(model)) {
      assertArrayEquals(new float[] {Float.MAX_VALUE},
          encoder.encodeBatch(new long[][] {{1, 1}})[0]);
    }
  }
}
