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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProvider;
import opennlp.tools.util.ext.ProviderSpec;
import opennlp.tools.util.ext.Providers;

import static opennlp.dl.vectors.OnnxTextEmbedderProvider.LOWER_CASE_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.MAX_LENGTH_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.NORMALIZE_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.POOLING_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.VOCABULARY_OPTION;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link TextEmbedder} adapter driven through a real ONNX session. The bundled
 * {@code tiny-vectors.onnx} (see {@code gen_tiny_vectors_model.py} next to it) computes
 * {@code output[b][t] = float(input_ids[b][t]) * W} with {@code W = [0.5, -1, 2]}, so every
 * expected vector is hand-computable from the vocabulary ids: mean pooling gives the mean id
 * times {@code W}, {@code [CLS]} pooling gives {@code 7 * W}, and unit length gives
 * {@code W / |W|} for every input. {@code tiny-pooled.onnx} (see
 * {@code gen_tiny_pooled_model.py}) adds a {@code sentence_embedding} output holding the sum of
 * the ids times {@code W}.
 */
class SentenceVectorsDLEmbedderTest {

  private static final float DELTA = 1e-5f;

  // W / |W|, the unit-length vector of every input of the tiny models.
  private static final float[] UNIT_VECTOR = scale(1 / (float) Math.sqrt(5.25));

  // "hello world" = [CLS]=7 hello=4 world=5 [SEP]=3
  private static final float[] HELLO_WORLD_MEAN = scale(19 / 4f);
  private static final float[] HELLO_WORLD_SUM = scale(19);

  // "hello" = [CLS]=7 hello=4 [SEP]=3
  private static final float[] HELLO_MEAN = scale(14 / 3f);

  private static final float[] CLS_VECTOR = scale(7);

  private static float[] scale(final float factor) {
    return new float[] {0.5f * factor, -1f * factor, 2f * factor};
  }

  // Copy the model out of the classpath rather than resolving it in place: when this test runs
  // from the opennlp-dl test-jar (as it does in opennlp-dl-gpu) the resource URI is inside a jar
  // and is not hierarchical, so new File(uri) would fail.
  private static File model(final Path dir, final String name) throws IOException {
    final Path file = dir.resolve(name);
    try (InputStream is = Objects.requireNonNull(SentenceVectorsDLEmbedderTest.class
        .getResourceAsStream("/opennlp/dl/vectors/" + name))) {
      Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return file.toFile();
  }

  private static File model(final Path dir) throws IOException {
    return model(dir, "tiny-vectors.onnx");
  }

  private static File vocab(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    // Line number = id: [UNK]=2, [SEP]=3, hello=4, world=5, [CLS]=7.
    Files.write(file, List.of("[PAD]", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    return file.toFile();
  }

  private static SentenceVectorsDL meanVectors(final Path dir, final int maxLength)
      throws Exception {
    return new SentenceVectorsDL(model(dir), vocab(dir), true, Pooling.MEAN, false, maxLength);
  }

  @Test
  void testSelectedProviderPreservesInference(@TempDir final Path dir) throws Exception {
    final Path graph = model(dir).toPath();
    vocab(dir);
    final Map<String, String> options = Map.of(VOCABULARY_OPTION, "vocab.txt");
    final ProviderSpec spec = ProviderSpec.of(graph, options);
    final TextEmbedderProvider provider = Providers.of(TextEmbedderProvider.class).select(spec);
    assertEquals(OnnxTextEmbedderProvider.NAME, provider.name());
    try (TextEmbedder first = provider.create(spec);
         TextEmbedder second = provider.create(spec)) {
      assertArrayEquals(UNIT_VECTOR, first.embed("hello world"), DELTA);
      first.close();
      assertArrayEquals(UNIT_VECTOR, second.embed("hello"), DELTA);
      assertArrayEquals(UNIT_VECTOR, second.embedAll(List.of("hello", "world"))[1], DELTA);
    }
    final ProviderSpec clsSpec = ProviderSpec.of(graph, Map.of(VOCABULARY_OPTION, "vocab.txt",
        POOLING_OPTION, "cls", NORMALIZE_OPTION, "false", MAX_LENGTH_OPTION, "8"));
    try (TextEmbedder cls = provider.create(clsSpec)) {
      assertArrayEquals(CLS_VECTOR, cls.embed("hello world"), DELTA);
    }
    assertThrows(IllegalArgumentException.class, () -> provider.create(null));
    assertThrows(IllegalArgumentException.class,
        () -> provider.create(ProviderSpec.of(dir, options)), "a directory");
    assertThrows(IllegalArgumentException.class,
        () -> provider.create(ProviderSpec.of(dir.resolve("missing.onnx"), options)),
        "a missing model");
    assertThrows(IllegalArgumentException.class, () -> provider.create(ProviderSpec.of(graph)),
        "no vocabulary");
    for (final Map.Entry<String, String> invalid : List.of(Map.entry(LOWER_CASE_OPTION, "invalid"),
        Map.entry(NORMALIZE_OPTION, "yes"), Map.entry(POOLING_OPTION, "max"),
        Map.entry(POOLING_OPTION, "MEAN"), Map.entry(MAX_LENGTH_OPTION, "1"),
        Map.entry(MAX_LENGTH_OPTION, "many"), Map.entry("typo", "true"))) {
      assertThrows(IllegalArgumentException.class, () -> provider.create(ProviderSpec.of(graph,
          Map.of(VOCABULARY_OPTION, "vocab.txt", invalid.getKey(), invalid.getValue()))),
          invalid.toString());
    }
  }

  @Test
  void testEmbedderContractOverARealSession(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir), vocab(dir))) {

      // The primary entry point, against which the adapter below is compared.
      assertArrayEquals(UNIT_VECTOR, vectors.getVectors("hello world"), DELTA);

      final TextEmbedder embedder = vectors;

      // The dimension comes from the model's declared output metadata, no inference needed.
      assertEquals(3, embedder.dimension());

      // The interface produces the same vector as the original entry point, for String and
      // non-String inputs alike.
      assertArrayEquals(UNIT_VECTOR, embedder.embed("hello world"), DELTA);
      assertArrayEquals(UNIT_VECTOR, embedder.embed(new StringBuilder("hello world")), DELTA);

      final float[][] batch = embedder.embedAll(List.of("hello world", "hello"));
      assertEquals(2, batch.length);
      assertArrayEquals(UNIT_VECTOR, batch[0], DELTA);
      assertArrayEquals(UNIT_VECTOR, batch[1], DELTA);

      // Every call returns an array of its own.
      assertNotSame(embedder.embed("hello"), embedder.embed("hello"));

      assertEquals("text must not be null", assertThrows(IllegalArgumentException.class,
          () -> embedder.embed(null)).getMessage());
      assertEquals("sentence must not be null", assertThrows(IllegalArgumentException.class,
          () -> vectors.getVectors(null)).getMessage());
      assertEquals("texts must not be null", assertThrows(IllegalArgumentException.class,
          () -> embedder.embedAll(null)).getMessage());
    }
  }

  @Test
  void testPooling(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL mean = meanVectors(dir, SentenceVectorsDL.DEFAULT_MAX_LENGTH);
         SentenceVectorsDL cls = new SentenceVectorsDL(model(dir), vocab(dir), true,
             Pooling.CLS, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      assertArrayEquals(HELLO_WORLD_MEAN, mean.embed("hello world"), DELTA);
      assertArrayEquals(HELLO_MEAN, mean.embed("hello"), DELTA);
      assertArrayEquals(CLS_VECTOR, cls.embed("hello world"), DELTA);
      assertArrayEquals(CLS_VECTOR, cls.embed("hello"), DELTA);
    }
  }

  @Test
  void testTruncationKeepsTheFinalSeparator(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL three = meanVectors(dir, 3);
         SentenceVectorsDL two = meanVectors(dir, 2)) {
      // [CLS] hello world [SEP] becomes [CLS] hello [SEP].
      assertArrayEquals(HELLO_MEAN, three.embed("hello world"), DELTA);
      assertArrayEquals(HELLO_MEAN, three.embed("hello"), DELTA);
      // [CLS] [SEP] only: (7 + 3) / 2 = 5.
      assertArrayEquals(scale(5), two.embed("hello world"), DELTA);
      assertArrayEquals(three.embed("hello"), three.embedAll(List.of("hello world"))[0]);
    }
    assertThrows(IllegalArgumentException.class, () -> meanVectors(dir, 1));
    assertThrows(IllegalArgumentException.class, () -> new SentenceVectorsDL(model(dir),
        vocab(dir), true, null, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH));
  }

  @Test
  void testPooledOutputIsSelectedByName(@TempDir final Path dir) throws Exception {
    final File pooledModel = model(dir, "tiny-pooled.onnx");
    try (SentenceVectorsDL raw = new SentenceVectorsDL(pooledModel, vocab(dir), true,
             Pooling.CLS, false, SentenceVectorsDL.DEFAULT_MAX_LENGTH);
         SentenceVectorsDL unit = new SentenceVectorsDL(pooledModel, vocab(dir))) {
      assertEquals(3, raw.dimension());
      // The sentence_embedding output is used as it is; the pooling setting does not apply.
      assertArrayEquals(HELLO_WORLD_SUM, raw.embed("hello world"), DELTA);
      assertArrayEquals(HELLO_WORLD_SUM, raw.embedAll(List.of("hello", "hello world"))[1],
          DELTA);
      assertArrayEquals(UNIT_VECTOR, unit.embed("hello world"), DELTA);
    }
  }

  @Test
  void testEmbedAfterCloseThrows(@TempDir final Path dir) throws Exception {
    final SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir), vocab(dir));
    vectors.close();
    vectors.close();
    assertThrows(IllegalStateException.class, () -> vectors.embed("hello"));
    assertThrows(IllegalStateException.class, () -> vectors.embedAll(List.of("hello")));
    assertEquals(3, vectors.dimension());
  }

  /**
   * Drives the batched path over inputs of mixed tokenized lengths ("hello" encodes one
   * token shorter than "hello world") and asserts every row reproduces its single-input
   * vector exactly: the length-grouped batch never pads, so the computation per row is
   * the computation the single call performs.
   */
  @Test
  void testEmbedAllMatchesSingleEmbedsExactly(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = meanVectors(dir, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      final List<String> texts = List.of("hello", "hello world", "world", "hello world",
          "hello");
      final float[][] batch = vectors.embedAll(texts);
      assertEquals(texts.size(), batch.length);
      for (int i = 0; i < texts.size(); i++) {
        assertArrayEquals(vectors.embed(texts.get(i)), batch[i]);
      }
    }
  }

  /**
   * Asserts the batch contract edges: an empty input yields an empty batch, and a
   * {@code null} element is rejected rather than failing later inside the session.
   */
  @Test
  void testEmbedAllEdges(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir), vocab(dir))) {
      assertEquals(0, vectors.embedAll(List.of()).length);
      assertEquals("texts[1] must not be null", assertThrows(IllegalArgumentException.class,
          () -> vectors.embedAll(Arrays.asList("hello", null))).getMessage());
    }
  }
}
