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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.dl.InferenceOptions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link SentenceVectorsDL} constructor that takes {@link InferenceOptions}.
 */
class SentenceVectorsDLInferenceOptionsTest {

  private static final int UNUSABLE_DEVICE_ID = 99;

  private static final float DELTA = 1e-6f;

  private static final String TEXT = "hello world";

  /**
   * Copies the test model out of the classpath, because the test also runs from the
   * {@code opennlp-dl} test-jar in {@code opennlp-dl-gpu}, where the resource is not a file.
   *
   * @param dir The directory to copy the model into.
   * @return The model file.
   * @throws IOException Thrown if the model cannot be copied.
   */
  private static File model(final Path dir) throws IOException {
    final Path file = dir.resolve("tiny-vectors.onnx");
    try (InputStream is = Objects.requireNonNull(SentenceVectorsDLInferenceOptionsTest.class
        .getResourceAsStream("/opennlp/dl/vectors/tiny-vectors.onnx"))) {
      Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return file.toFile();
  }

  /**
   * Writes the eight-entry vocabulary of the test model.
   *
   * @param dir The directory to write the vocabulary into.
   * @return The vocabulary file.
   * @throws IOException Thrown if the vocabulary cannot be written.
   */
  private static File vocab(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    Files.write(file, List.of("[PAD]", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    return file.toFile();
  }

  /**
   * Requests a device id that no machine has and checks that ONNX Runtime rejects it, which
   * shows that the options reach the session; ONNX Runtime does not fall back to the CPU.
   */
  @Test
  void testGpuRequestReachesOnnxRuntime(@TempDir Path dir) throws IOException {
    final InferenceOptions options = new InferenceOptions();
    options.setGpu(true);
    options.setGpuDeviceId(UNUSABLE_DEVICE_ID);
    final File model = model(dir);
    final File vocab = vocab(dir);
    assertThrows(OrtException.class, () -> new SentenceVectorsDL(model, vocab, true, Pooling.MEAN,
        false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, options).close());
  }

  /**
   * Checks that default {@link InferenceOptions} give the same vectors as the constructor
   * without options.
   */
  @Test
  void testDefaultOptionsMatchTheDefaultSession(@TempDir Path dir) throws Exception {
    try (SentenceVectorsDL plain = new SentenceVectorsDL(model(dir), vocab(dir), true,
        Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH);
         SentenceVectorsDL withOptions = new SentenceVectorsDL(model(dir), vocab(dir), true,
             Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, new InferenceOptions())) {
      assertArrayEquals(plain.embed(TEXT), withOptions.embed(TEXT), DELTA);
    }
  }

  /**
   * The lower case setting in the options wins over the {@code lowerCase} argument: with lower
   * casing off, "HELLO" is not in the vocabulary and encodes as {@code [UNK]} (id 2), so the mean
   * of {@code [CLS]=7, [UNK]=2, [SEP]=3} times {@code W} is {@code 4 * W}; lower cased it would be
   * {@code hello} (id 4), the mean {@code 14 / 3 * W}.
   */
  @Test
  void testLowerCaseInOptionsWinsOverTheArgument(@TempDir Path dir) throws Exception {
    final InferenceOptions options = new InferenceOptions();
    options.setLowerCase(false);
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir), vocab(dir), true,
        Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, options)) {
      assertArrayEquals(new float[] {4 * 0.5f, 4 * -1f, 4 * 2f}, vectors.embed("HELLO"), DELTA);
      assertArrayEquals(new float[] {14 / 3f * 0.5f, 14 / 3f * -1f, 14 / 3f * 2f},
          vectors.embed("hello"), DELTA);
    }
  }

  /**
   * Checks that {@code null} options are rejected.
   */
  @Test
  void testNullOptionsAreRejected(@TempDir Path dir) throws IOException {
    final File model = model(dir);
    final File vocab = vocab(dir);
    assertThrows(IllegalArgumentException.class, () -> new SentenceVectorsDL(model, vocab, true,
        Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, null).close());
  }
}
