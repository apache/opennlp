/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.dl.vectors;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.InferenceOptions;

import static opennlp.dl.OnnxTestSupport.UNUSABLE_DEVICE_ID;
import static opennlp.dl.OnnxTestSupport.gpu;
import static opennlp.dl.vectors.TinyVectorModels.DELTA;
import static opennlp.dl.vectors.TinyVectorModels.TINY_POOLED;
import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.gpuOrAbort;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static opennlp.dl.vectors.TinyVectorModels.scale;
import static opennlp.dl.vectors.TinyVectorModels.vocab;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link SentenceVectorsDL} constructor that takes {@link InferenceOptions}.
 */
class SentenceVectorsDLInferenceOptionsTest {

  private static final String HELLO = "hello";

  private static final String TEXT = "hello world";

  private static final String LONGER_TEXT = "world hello world";

  /**
   * {@return the model, pooling and unit length combinations, one per path after the inference}
   */
  static Stream<Arguments> gpuParityCases() {
    return Stream.of(
        Arguments.of(TINY_VECTORS, Pooling.MEAN, false),
        Arguments.of(TINY_VECTORS, Pooling.MEAN, true),
        Arguments.of(TINY_VECTORS, Pooling.CLS, false),
        Arguments.of(TINY_VECTORS, Pooling.CLS, true),
        Arguments.of(TINY_POOLED, Pooling.CLS, false),
        Arguments.of(TINY_POOLED, Pooling.MEAN, true));
  }

  /**
   * Checks that every path gives the same vectors on CUDA device {@code 0} as on the CPU, where
   * a CUDA device exists; skipped elsewhere.
   */
  @ParameterizedTest
  @MethodSource("gpuParityCases")
  void testGpuVectorsMatchTheCpu(final String modelName, final Pooling pooling,
      final boolean normalize, @TempDir final Path dir) throws Exception {
    final File model = model(dir, modelName);
    final File vocab = vocab(dir);
    try (SentenceVectorsDL gpu = gpuOrAbort(model, vocab, pooling, normalize);
         SentenceVectorsDL cpu = new SentenceVectorsDL(model, vocab, true, pooling, normalize,
             SentenceVectorsDL.DEFAULT_MAX_LENGTH, new InferenceOptions())) {
      assertEquals(cpu.dimension(), gpu.dimension());
      assertArrayEquals(cpu.embed(TEXT), gpu.embed(TEXT), DELTA);
      final List<String> texts = List.of(TEXT, LONGER_TEXT, "");
      final float[][] cpuBatch = cpu.embedAll(texts);
      final float[][] gpuBatch = gpu.embedAll(texts);
      for (int i = 0; i < cpuBatch.length; i++) {
        assertArrayEquals(cpuBatch[i], gpuBatch[i], DELTA, "row " + i);
      }
    }
  }

  /**
   * Checks that a GPU request on a negative device id is rejected before ONNX Runtime is asked
   * for a device.
   */
  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE})
  void testNegativeDeviceIdsAreRejected(final int deviceId, @TempDir final Path dir)
      throws IOException {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = vocab(dir);
    final InferenceOptions options = gpu(deviceId);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new SentenceVectorsDL(model, vocab, true, Pooling.MEAN, false,
            SentenceVectorsDL.DEFAULT_MAX_LENGTH, options).close());
    assertEquals("gpuDeviceId must not be negative", e.getMessage());
  }

  /**
   * Checks that a GPU request on a device id that no machine has is rejected by ONNX Runtime
   * instead of running on the CPU.
   */
  @ParameterizedTest
  @ValueSource(ints = {UNUSABLE_DEVICE_ID, Integer.MAX_VALUE})
  void testUnusableDeviceIdsAreRejected(final int deviceId, @TempDir final Path dir)
      throws IOException {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = vocab(dir);
    final InferenceOptions options = gpu(deviceId);
    assertThrows(OrtException.class, () -> new SentenceVectorsDL(model, vocab, true, Pooling.MEAN,
        false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, options).close());
  }

  /**
   * {@return the lower case in the options ({@code null} for unset), the argument, and the
   * expected lower casing}
   */
  static Stream<Arguments> lowerCaseCases() {
    return Stream.of(
        Arguments.of(Boolean.TRUE, true, true),
        Arguments.of(Boolean.TRUE, false, true),
        Arguments.of(Boolean.FALSE, true, false),
        Arguments.of(Boolean.FALSE, false, false),
        Arguments.of(null, true, true),
        Arguments.of(null, false, false));
  }

  /**
   * Checks that the lower case setting is {@code options.getLowerCase()} when it is set and the
   * {@code lowerCase} argument when it is not. "HELLO" encodes as {@code [UNK]} (id 2) unless
   * lower cased to {@code hello} (id 4), so the mean of the ids is {@code 4} or {@code 14 / 3}.
   */
  @ParameterizedTest
  @MethodSource("lowerCaseCases")
  void testLowerCasePrecedence(final Boolean inOptions, final boolean argument,
      final boolean expectedLowerCase, @TempDir final Path dir) throws Exception {
    final InferenceOptions options = new InferenceOptions();
    if (inOptions != null) {
      options.setLowerCase(inOptions);
    }
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, TINY_VECTORS), vocab(dir),
        argument, Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, options)) {
      assertArrayEquals(scale(expectedLowerCase ? 14 / 3f : 4f), vectors.embed("HELLO"), DELTA);
    }
  }

  /**
   * Checks that default {@link InferenceOptions} give the same vectors as the constructor
   * without options.
   */
  @Test
  void testDefaultOptionsMatchTheDefaultSession(@TempDir final Path dir) throws Exception {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = vocab(dir);
    try (SentenceVectorsDL plain = new SentenceVectorsDL(model, vocab, true, Pooling.MEAN, false,
        SentenceVectorsDL.DEFAULT_MAX_LENGTH);
         SentenceVectorsDL withOptions = new SentenceVectorsDL(model, vocab, true, Pooling.MEAN,
             false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, new InferenceOptions())) {
      assertArrayEquals(plain.embed(TEXT), withOptions.embed(TEXT), DELTA);
    }
  }

  /**
   * Checks that {@code null} options are rejected with the parameter name.
   */
  @Test
  void testNullOptionsAreRejected(@TempDir final Path dir) throws IOException {
    final File model = model(dir, TINY_VECTORS);
    final File vocab = vocab(dir);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new SentenceVectorsDL(model, vocab, true, Pooling.MEAN, false,
            SentenceVectorsDL.DEFAULT_MAX_LENGTH, null).close());
    assertEquals("inferenceOptions must not be null", e.getMessage());
  }
}
