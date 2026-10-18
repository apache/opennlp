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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ai.onnxruntime.OrtException;

import static opennlp.dl.OnnxTestSupport.copy;
import static opennlp.dl.OnnxTestSupport.gpu;
import static opennlp.dl.OnnxTestSupport.hasCudaProvider;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test fixtures for the bundled tiny ONNX models. {@code tiny-vectors.onnx} (see
 * {@code gen_tiny_vectors_model.py}) computes {@code output[b][t] = float(input_ids[b][t]) * W}
 * with {@code W = [0.5, -1, 2]}; {@code tiny-pooled.onnx} (see {@code gen_tiny_pooled_model.py})
 * adds a {@code sentence_embedding} output holding the sum of the ids times {@code W}.
 */
public final class TinyVectorModels {

  /** The model with a token output only. */
  public static final String TINY_VECTORS = "tiny-vectors.onnx";

  /** The model with an additional {@code sentence_embedding} output. */
  public static final String TINY_POOLED = "tiny-pooled.onnx";

  /** The tolerance for comparing vectors of the tiny models. */
  public static final float DELTA = 1e-5f;

  private static final String VECTORS_RESOURCES = "/opennlp/dl/vectors/";

  private TinyVectorModels() {
  }

  /**
   * Copies a tiny model into a directory.
   *
   * @param dir The directory to copy into.
   * @param name {@link #TINY_VECTORS} or {@link #TINY_POOLED}.
   * @return The model file.
   * @throws IOException Thrown if the model cannot be copied.
   */
  public static File model(final Path dir, final String name) throws IOException {
    return copy(dir, VECTORS_RESOURCES + name, name);
  }

  /**
   * Writes the vocabulary of the tiny models, where the line number is the id:
   * {@code [UNK]=2, [SEP]=3, hello=4, world=5, [CLS]=7}.
   *
   * @param dir The directory to write into.
   * @return The vocabulary file.
   * @throws IOException Thrown if the vocabulary cannot be written.
   */
  public static File vocab(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    Files.write(file, List.of("[PAD]", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    return file.toFile();
  }

  /**
   * {@return {@code W} times {@code factor}, the expected vector for a mean or summed id}
   *
   * @param factor The mean or summed token id.
   */
  public static float[] scale(final float factor) {
    return new float[] {0.5f * factor, -1f * factor, 2f * factor};
  }

  /**
   * Opens a session on CUDA device {@code 0}, or aborts the calling test where the ONNX Runtime
   * on the classpath has no CUDA execution provider or the device is not usable.
   *
   * @param model The model file.
   * @param vocab The vocabulary file.
   * @param pooling The pooling.
   * @param normalize {@code true} to scale every vector to unit length.
   * @return The session on the GPU.
   * @throws IOException Thrown if the model or vocabulary cannot be read.
   */
  public static SentenceVectorsDL gpuOrAbort(final File model, final File vocab,
      final Pooling pooling, final boolean normalize) throws IOException {
    assumeTrue(hasCudaProvider(), "the ONNX Runtime on the classpath has no CUDA execution provider");
    try {
      return new SentenceVectorsDL(model, vocab, true, pooling, normalize,
          SentenceVectorsDL.DEFAULT_MAX_LENGTH, gpu(0));
    } catch (OrtException e) {
      return abort("no usable CUDA device 0: " + e.getMessage());
    }
  }
}
