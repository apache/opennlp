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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.dl.InferenceOptions;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.embeddings.TextEmbedderProvider;
import opennlp.tools.util.ext.ProviderSpec;
import opennlp.tools.util.ext.Providers;

import static opennlp.dl.vectors.OnnxTextEmbedderProvider.LOWER_CASE_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.MAX_LENGTH_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.NORMALIZE_OPTION;
import static opennlp.dl.vectors.OnnxTextEmbedderProvider.PADDING_OPTION;
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

  private static SentenceVectorsDL meanVectors(final Path dir, final int maxLength,
      final PaddingStrategy padding) throws Exception {
    return new SentenceVectorsDL(model(dir), vocab(dir), true, Pooling.MEAN, false, maxLength,
        padding, new InferenceOptions());
  }

  // [PAD] at id 6 instead of 0; the other ids are those of vocab().
  private static File vocabPad6(final Path dir) throws IOException {
    final Path file = dir.resolve("vocab-pad6.txt");
    Files.write(file, List.of("unused0", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "[PAD]", "[CLS]"));
    return file.toFile();
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
    for (final String padding : List.of("exact_length", "longest", "max_length")) {
      try (TextEmbedder padded = provider.create(ProviderSpec.of(graph,
          Map.of(VOCABULARY_OPTION, "vocab.txt", PADDING_OPTION, padding)))) {
        assertArrayEquals(UNIT_VECTOR, padded.embedAll(List.of("hello world", "hello"))[1],
            DELTA, padding);
      }
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
        Map.entry(MAX_LENGTH_OPTION, "many"), Map.entry(PADDING_OPTION, "none"),
        Map.entry(PADDING_OPTION, "LONGEST"), Map.entry("typo", "true"))) {
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
   * Asserts that {@code embedAll} splits a large group of same-length inputs into inferences of
   * at most {@value SentenceVectorsDL#MAX_BATCH_TOKEN_POSITIONS} token positions, and that every
   * row still equals its single-input vector. "hello world" encodes to 4 tokens, so one
   * inference holds at most 4096 rows.
   */
  @Test
  void testEmbedAllCapsTokenPositionsPerInference(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = meanVectors(dir, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      final List<String> texts = Collections.nCopies(5000, "hello world");
      assertArrayEquals(new int[][] {{4096, 4}, {904, 4}}, vectors.batchShapes(texts));
      final float[][] batch = vectors.embedAll(texts);
      assertEquals(texts.size(), batch.length);
      for (final float[] row : batch) {
        assertArrayEquals(HELLO_WORLD_MEAN, row, DELTA);
      }
    }
  }

  /**
   * Asserts that inputs of different lengths still run one inference per length, in the order
   * each length first appears in the call.
   */
  @Test
  void testEmbedAllGroupsByLengthInCallOrder(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = meanVectors(dir, SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      assertArrayEquals(new int[][] {{2, 4}, {3, 3}}, vectors.batchShapes(
          List.of("hello world", "hello", "world", "hello world", "hello")));
    }
  }

  /**
   * Asserts that an input wider than the cap on its own still runs, as a batch of one.
   */
  @Test
  void testEmbedAllRunsAnInputWiderThanTheCapAlone(@TempDir final Path dir) throws Exception {
    final int width = SentenceVectorsDL.MAX_BATCH_TOKEN_POSITIONS + 1;
    try (SentenceVectorsDL vectors = meanVectors(dir, width)) {
      final String text = String.join(" ", Collections.nCopies(width, "hello"));
      assertArrayEquals(new int[][] {{1, width}, {1, width}},
          vectors.batchShapes(List.of(text, text)));
    }
  }

  /**
   * Asserts that every padding strategy returns, for each input of a mixed-length call, the
   * vector that input gets on its own: padded positions are masked out of the pooling.
   */
  @ParameterizedTest
  @EnumSource(PaddingStrategy.class)
  void testPaddingKeepsSingleInputVectors(final PaddingStrategy padding, @TempDir final Path dir)
      throws Exception {
    try (SentenceVectorsDL vectors = meanVectors(dir, 6, padding)) {
      final List<String> texts = List.of("hello world", "hello", "world", "hello world",
          "hello", "");
      final float[][] batch = vectors.embedAll(texts);
      for (int i = 0; i < texts.size(); i++) {
        assertArrayEquals(vectors.embed(texts.get(i)), batch[i], DELTA, texts.get(i));
      }
      assertArrayEquals(HELLO_WORLD_MEAN, batch[0], DELTA);
      assertArrayEquals(HELLO_MEAN, batch[1], DELTA);
      // "world" = [CLS]=7 world=5 [SEP]=3, mean 5.
      assertArrayEquals(scale(5), batch[2], DELTA);
      assertArrayEquals(HELLO_WORLD_MEAN, batch[3], DELTA);
      assertArrayEquals(HELLO_MEAN, batch[4], DELTA);
      // "" = [CLS]=7 [SEP]=3, mean 5.
      assertArrayEquals(scale(5), batch[5], DELTA);
    }
  }

  /**
   * Asserts the inference shapes of each strategy for one mixed-length call: one inference per
   * length without padding, one inference at the longest row, or one at the maximum length.
   */
  @Test
  void testPaddingShapes(@TempDir final Path dir) throws Exception {
    final List<String> texts = List.of("hello world", "hello", "world", "hello world", "hello");
    try (SentenceVectorsDL exact = meanVectors(dir, 6, PaddingStrategy.EXACT_LENGTH);
         SentenceVectorsDL longest = meanVectors(dir, 6, PaddingStrategy.LONGEST);
         SentenceVectorsDL fixed = meanVectors(dir, 6, PaddingStrategy.MAX_LENGTH)) {
      assertArrayEquals(new int[][] {{2, 4}, {3, 3}}, exact.batchShapes(texts));
      assertArrayEquals(new int[][] {{5, 4}}, longest.batchShapes(texts));
      assertArrayEquals(new int[][] {{5, 6}}, fixed.batchShapes(texts));
    }
  }

  /**
   * Asserts that padded batches also respect the token position cap. LONGEST orders the inputs
   * by length, so the one short input shares the first batch with the long ones.
   */
  @Test
  void testPaddedBatchesRespectTheCap(@TempDir final Path dir) throws Exception {
    final List<String> texts = new ArrayList<>(Collections.nCopies(5000,
        "hello world"));
    texts.add("hello");
    try (SentenceVectorsDL longest = meanVectors(dir, 8, PaddingStrategy.LONGEST);
         SentenceVectorsDL fixed = meanVectors(dir, 8, PaddingStrategy.MAX_LENGTH)) {
      assertArrayEquals(new int[][] {{4096, 4}, {905, 4}}, longest.batchShapes(texts));
      assertArrayEquals(new int[][] {{2048, 8}, {2048, 8}, {905, 8}}, fixed.batchShapes(texts));
      final float[][] batch = longest.embedAll(texts);
      assertArrayEquals(HELLO_WORLD_MEAN, batch[4999], DELTA);
      assertArrayEquals(HELLO_MEAN, batch[5000], DELTA);
    }
  }

  /**
   * Asserts that the padding id comes from the vocabulary. {@code tiny-pooled.onnx} sums the ids
   * of every position without reading the mask, so a padded row shows the id that was placed.
   */
  @Test
  void testPaddingIdIsReadFromTheVocabulary(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, "tiny-pooled.onnx"),
        vocabPad6(dir), true, Pooling.MEAN, false, 8, PaddingStrategy.LONGEST,
        new InferenceOptions())) {
      final float[][] batch = vectors.embedAll(List.of("hello world", "hello"));
      assertArrayEquals(HELLO_WORLD_SUM, batch[0], DELTA);
      assertArrayEquals(scale(14 + 6), batch[1], DELTA);
    }
  }

  /**
   * Asserts that padded positions are {@code 0} in the attention mask. {@code tiny-masked.onnx}
   * sums the ids times the mask, so a padded row gives the sum of its own ids only; a mask of
   * {@code 1} at the padded position would add the padding id {@code 6}.
   */
  @ParameterizedTest
  @EnumSource(value = PaddingStrategy.class, names = {"LONGEST", "MAX_LENGTH"})
  void testPaddedPositionsAreMaskedOut(final PaddingStrategy padding, @TempDir final Path dir)
      throws Exception {
    try (SentenceVectorsDL vectors = new SentenceVectorsDL(model(dir, "tiny-masked.onnx"),
        vocabPad6(dir), true, Pooling.MEAN, false, 8, padding, new InferenceOptions())) {
      final float[][] batch = vectors.embedAll(List.of("hello world", "hello"));
      assertArrayEquals(HELLO_WORLD_SUM, batch[0], DELTA);
      assertArrayEquals(scale(14), batch[1], DELTA);
    }
  }

  /**
   * Asserts that a padding strategy needs a padding token, and that no padding does not.
   */
  @Test
  void testPaddingRequiresAPaddingToken(@TempDir final Path dir) throws Exception {
    final Path vocabulary = dir.resolve("vocab-nopad.txt");
    Files.write(vocabulary, List.of("unused0", "unused1", "[UNK]", "[SEP]", "hello", "world",
        "unused2", "[CLS]"));
    for (final PaddingStrategy padding : List.of(PaddingStrategy.LONGEST,
        PaddingStrategy.MAX_LENGTH)) {
      assertThrows(IllegalArgumentException.class, () -> new SentenceVectorsDL(model(dir),
          vocabulary.toFile(), true, Pooling.MEAN, false, 8, padding, new InferenceOptions()),
          padding.name());
    }
    try (SentenceVectorsDL exact = new SentenceVectorsDL(model(dir), vocabulary.toFile(), true,
        Pooling.MEAN, false, 8, PaddingStrategy.EXACT_LENGTH, new InferenceOptions())) {
      assertArrayEquals(HELLO_MEAN, exact.embed("hello"), DELTA);
    }
    assertEquals("padding must not be null", assertThrows(IllegalArgumentException.class,
        () -> meanVectors(dir, 8, null)).getMessage());
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
