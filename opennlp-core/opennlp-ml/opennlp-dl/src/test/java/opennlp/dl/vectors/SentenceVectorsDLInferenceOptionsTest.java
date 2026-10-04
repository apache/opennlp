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
 * {@link SentenceVectorsDL} takes {@link InferenceOptions}, so a GPU can be requested.
 *
 * <p>Before this constructor existed the class built its session from default options and could
 * only run on the CPU, although {@code opennlp-dl-gpu} documents GPU support. A request for a
 * device id no machine has proves the options reach ONNX Runtime: the CPU-only build rejects
 * the CUDA provider while the options are built, and the GPU build rejects the CUDA libraries or
 * the device. Either way it throws {@link OrtException}, and it could not unless the options
 * were passed through. ONNX Runtime does not fall back to the CPU in any of these cases.</p>
 */
class SentenceVectorsDLInferenceOptionsTest {

  private static final int UNUSABLE_DEVICE_ID = 99;

  private static final float DELTA = 1e-6f;

  private static final String TEXT = "hello world";

  // Copied out of the classpath, because this test also runs from the opennlp-dl test-jar in
  // opennlp-dl-gpu, where the resource URI is not hierarchical.
  private static File model(final Path dir) throws IOException {
    final Path file = dir.resolve("tiny-vectors.onnx");
    try (InputStream is = Objects.requireNonNull(SentenceVectorsDLInferenceOptionsTest.class
        .getResourceAsStream("/opennlp/dl/vectors/tiny-vectors.onnx"))) {
      Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return file.toFile();
  }

  private static File vocab(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    Files.write(file, List.of("[PAD]", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    return file.toFile();
  }

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

  @Test
  void testNullOptionsAreRejected(@TempDir Path dir) throws IOException {
    final File model = model(dir);
    final File vocab = vocab(dir);
    assertThrows(IllegalArgumentException.class, () -> new SentenceVectorsDL(model, vocab, true,
        Pooling.MEAN, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH, null).close());
  }
}
