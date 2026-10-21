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
import java.util.ArrayList;
import java.util.List;

import ai.onnxruntime.OrtException;

import opennlp.dl.Tokens;

import static opennlp.dl.OnnxTestSupport.copy;
import static opennlp.dl.OnnxTestSupport.gpu;
import static opennlp.dl.OnnxTestSupport.hasCudaProvider;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

  /** A text that encodes to 3 tokens: {@code [CLS]=7 hello=4 [SEP]=3}. */
  public static final String HELLO = "hello";

  /** A text that encodes to 4 tokens: {@code [CLS]=7 hello=4 world=5 [SEP]=3}. */
  public static final String HELLO_WORLD = "hello world";

  /** {@code W / |W|}, the unit-length vector of every input. */
  public static final float[] UNIT_VECTOR = scale(1 / (float) Math.sqrt(5.25));

  /** The mean pooled vector of {@link #HELLO_WORLD}. */
  public static final float[] HELLO_WORLD_MEAN = scale(19 / 4f);

  /** The summed vector of {@link #HELLO_WORLD}, the {@code sentence_embedding} of the pooled model. */
  public static final float[] HELLO_WORLD_SUM = scale(19);

  /** The mean pooled vector of {@link #HELLO}. */
  public static final float[] HELLO_MEAN = scale(14 / 3f);

  /** The summed vector of {@link #HELLO}, the {@code sentence_embedding} of the pooled model. */
  public static final float[] HELLO_SUM = scale(14);

  /** The {@code [CLS]} pooled vector of every input. */
  public static final float[] CLS_VECTOR = scale(7);

  /** The model whose output depends on the attention mask, see {@code gen_tiny_masked_model.py}. */
  public static final String TINY_MASKED = "tiny-masked.onnx";

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
   * Writes the vocabulary of {@link #vocab(Path)} with {@code [PAD]} at id 6 instead of 0, so a
   * padded position is visible as that id.
   *
   * @param dir The directory to write into.
   * @return The vocabulary file.
   * @throws IOException Thrown if the vocabulary cannot be written.
   */
  public static File vocabPad6(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab-pad6.txt");
    Files.write(file, List.of("unused0", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "[PAD]", "[CLS]"));
    return file.toFile();
  }

  /**
   * Writes the vocabulary of {@link #vocab(Path)} without a {@code [PAD]} token.
   *
   * @param dir The directory to write into.
   * @return The vocabulary file.
   * @throws IOException Thrown if the vocabulary cannot be written.
   */
  public static File vocabNoPad(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab-nopad.txt");
    Files.write(file, List.of("unused0", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    return file.toFile();
  }

  /**
   * {@return an encoding of {@code length} tokens; only its length matters for batching}
   *
   * @param length The number of tokens.
   */
  public static Tokens tokens(final int length) {
    return new Tokens(new String[length], new long[length], new long[length], new long[length]);
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
   * {@return {@code pairs} times {@link #HELLO_WORLD} followed by {@link #HELLO}, so the two
   * lengths alternate}
   *
   * @param pairs The number of pairs.
   */
  public static List<String> alternating(final int pairs) {
    final List<String> texts = new ArrayList<>(2 * pairs);
    for (int i = 0; i < pairs; i++) {
      texts.add(HELLO_WORLD);
      texts.add(HELLO);
    }
    return texts;
  }

  /**
   * {@return the number of rows of each batch}
   *
   * @param batches The batches, as returned by {@code SentenceVectorsDL.batches}.
   */
  public static List<Integer> sizes(final List<List<Integer>> batches) {
    return batches.stream().map(List::size).toList();
  }

  /**
   * Asserts that two sessions return the same vectors for {@code texts} in one
   * {@code embedAll} call, row by row.
   *
   * @param expected The reference session, usually on the CPU.
   * @param actual The session under test.
   * @param texts The texts of the call.
   */
  public static void assertSameEmbeddings(final SentenceVectorsDL expected,
      final SentenceVectorsDL actual, final List<String> texts) {
    final float[][] want = expected.embedAll(texts);
    final float[][] got = actual.embedAll(texts);
    assertEquals(want.length, got.length);
    for (int i = 0; i < want.length; i++) {
      assertArrayEquals(want[i], got[i], DELTA, "row " + i);
    }
  }

  /**
   * Opens a session on CUDA device {@code 0} with the default maximum length and the padding the
   * device picks, or aborts the calling test where there is no usable CUDA device.
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
    return gpuOrAbort(model, vocab, pooling, normalize, SentenceVectorsDL.DEFAULT_MAX_LENGTH,
        null);
  }

  /**
   * Opens a session on CUDA device {@code 0}, or aborts the calling test where the ONNX Runtime
   * on the classpath has no CUDA execution provider or the device is not usable.
   *
   * @param model The model file.
   * @param vocab The vocabulary file.
   * @param pooling The pooling.
   * @param normalize {@code true} to scale every vector to unit length.
   * @param maxLength The maximum number of tokens per input.
   * @param padding The padding strategy, or {@code null} for the one the device picks.
   * @return The session on the GPU.
   * @throws IOException Thrown if the model or vocabulary cannot be read.
   */
  public static SentenceVectorsDL gpuOrAbort(final File model, final File vocab,
      final Pooling pooling, final boolean normalize, final int maxLength,
      final PaddingStrategy padding) throws IOException {
    assumeTrue(hasCudaProvider(), "the ONNX Runtime on the classpath has no CUDA execution provider");
    try {
      return padding == null
          ? new SentenceVectorsDL(model, vocab, true, pooling, normalize, maxLength, gpu(0))
          : new SentenceVectorsDL(model, vocab, true, pooling, normalize, maxLength, padding,
              gpu(0));
    } catch (OrtException e) {
      return abort("no usable CUDA device 0: " + e.getMessage());
    }
  }
}
