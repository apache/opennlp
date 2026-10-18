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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import ai.onnxruntime.OrtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.dl.InferenceOptions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link InferenceOptions} constructor of {@link SentenceVectorsDL} with
 * {@code tiny-vectors.onnx} (see {@code gen_tiny_vectors_model.py}), whose output vector of a
 * token is its vocabulary id times {@code [0.5, -1, 2]}.
 */
class SentenceVectorsDLInferenceOptionsTest {

  /** A CUDA device id that no machine has. */
  private static final int UNUSABLE_DEVICE_ID = 99;

  private static final String SENTENCE = "the dog";

  @TempDir
  Path dir;

  /** Without a GPU request the options constructor computes the vectors of the default one. */
  @Test
  void testCpuOptionsMatchTheDefaultConstructor() throws IOException, OrtException {
    final File model = copy("/opennlp/dl/vectors/tiny-vectors.onnx", "tiny-vectors.onnx");
    final File vocab = copy("/opennlp/dl/vocab-plain.txt", "vocab.txt");
    try (SentenceVectorsDL defaults = new SentenceVectorsDL(model, vocab);
         SentenceVectorsDL options = new SentenceVectorsDL(model, vocab, new InferenceOptions())) {
      final float[] expected = defaults.getVectors(SENTENCE);
      assertEquals(3, expected.length);
      assertArrayEquals(expected, options.getVectors(SENTENCE));
    }
  }

  /** A GPU request fails when CUDA or the device is not available instead of using the CPU. */
  @Test
  void testGpuRequestOnAnUnusableDeviceFails() throws IOException {
    final File model = copy("/opennlp/dl/vectors/tiny-vectors.onnx", "tiny-vectors.onnx");
    final File vocab = copy("/opennlp/dl/vocab-plain.txt", "vocab.txt");
    final InferenceOptions options = new InferenceOptions();
    options.setGpu(true);
    options.setGpuDeviceId(UNUSABLE_DEVICE_ID);
    assertThrows(OrtException.class, () -> new SentenceVectorsDL(model, vocab, options).close());
  }

  /**
   * Copies a test resource into the temporary directory.
   *
   * @param resource The classpath resource.
   * @param name The file name in the temporary directory.
   * @return The copied file.
   * @throws IOException Thrown if the resource cannot be read or written.
   */
  private File copy(final String resource, final String name) throws IOException {
    final Path target = dir.resolve(name);
    try (InputStream in = getClass().getResourceAsStream(resource)) {
      Files.copy(in, target);
    }
    return target.toFile();
  }
}
