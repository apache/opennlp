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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.dl.InferenceOptions;
import opennlp.dl.Tokens;

import static opennlp.dl.vectors.TinyVectorModels.DELTA;
import static opennlp.dl.vectors.TinyVectorModels.TINY_MASKED;
import static opennlp.dl.vectors.TinyVectorModels.TINY_POOLED;
import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.UNIT_VECTOR;
import static opennlp.dl.vectors.TinyVectorModels.assertSameEmbeddings;
import static opennlp.dl.vectors.TinyVectorModels.gpuOrAbort;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static opennlp.dl.vectors.TinyVectorModels.scale;
import static opennlp.dl.vectors.TinyVectorModels.sizes;
import static opennlp.dl.vectors.TinyVectorModels.vocab;
import static opennlp.dl.vectors.TinyVectorModels.vocabPad6;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Edge cases of the padding strategies of {@link SentenceVectorsDL#embedAll(List)}: the limit of
 * token positions per inference under padding, other vocabularies, pooling and normalization of
 * padded rows, a model that ignores the mask, and a GPU.
 *
 * <p>With the test vocabulary "hello" encodes to 3 tokens and "hello world" to 4.</p>
 */
class SentenceVectorsDLPaddingEdgesTest {

  private static final int LIMIT = SentenceVectorsDL.MAX_BATCH_TOKEN_POSITIONS;

  private static File write(final Path dir, final String name, final List<String> tokens)
      throws IOException {
    final Path file = dir.resolve(name);
    Files.write(file, tokens);
    return file.toFile();
  }

  private static SentenceVectorsDL embedder(final File model, final File vocabulary,
      final Pooling pooling, final boolean normalize, final int maxLength,
      final PaddingStrategy padding) throws Exception {
    return new SentenceVectorsDL(model, vocabulary, true,
        new SentenceVectorsOptions(pooling, normalize, maxLength, padding),
        new InferenceOptions());
  }

  private static Tokens[] tokens(final int length, final int count) {
    return Collections.nCopies(count, TinyVectorModels.tokens(length)).toArray(new Tokens[0]);
  }

  // The plan tests do not run a text, so one embedder per strategy is enough.
  private static SentenceVectorsDL planner(final Path dir, final PaddingStrategy padding,
      final int maxLength) throws Exception {
    return embedder(model(dir, TINY_VECTORS), vocab(dir), Pooling.MEAN, false, maxLength,
        padding);
  }

  /**
   * LONGEST fills an inference to exactly the limit, splits at one row more, and runs a row wider
   * than the limit on its own.
   */
  @Test
  void testLongestAtTheLimitAndOverIt(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL longest = planner(dir, PaddingStrategy.LONGEST, 8)) {
      assertEquals(List.of(LIMIT / 4), sizes(longest.batches(tokens(4, LIMIT / 4))));
      assertEquals(List.of(LIMIT / 4, 1), sizes(longest.batches(tokens(4, LIMIT / 4 + 1))));
      assertEquals(List.of(LIMIT / 2, 1), sizes(longest.batches(tokens(2, LIMIT / 2 + 1))));
      assertEquals(List.of(List.of(0)), longest.batches(tokens(LIMIT + 1, 1)));
      final Tokens[] mixed = {tokens(LIMIT + 1, 1)[0], tokens(3, 1)[0], tokens(LIMIT + 1, 1)[0]};
      assertEquals(List.of(List.of(1), List.of(0), List.of(2)), longest.batches(mixed));
    }
  }

  /** MAX_LENGTH with a length above the limit runs one row per inference. */
  @Test
  void testMaxLengthAboveTheLimitRunsOneRowEach(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL fixed = planner(dir, PaddingStrategy.MAX_LENGTH, LIMIT + 5)) {
      assertEquals(List.of(1, 1, 1), sizes(fixed.batches(tokens(3, 3))));
    }
  }

  /** MAX_LENGTH with lengths that divide the limit evenly, or equal it. */
  @ParameterizedTest
  @MethodSource("maxLengthPlans")
  void testMaxLengthThatDividesTheLimit(final int maxLength, final int count,
      final List<Integer> expected, @TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL fixed = planner(dir, PaddingStrategy.MAX_LENGTH, maxLength)) {
      assertEquals(expected, sizes(fixed.batches(tokens(3, count))));
    }
  }

  static Stream<Arguments> maxLengthPlans() {
    return Stream.of(
        Arguments.of(8, 2048, List.of(2048)),
        Arguments.of(8, 2049, List.of(2048, 1)),
        Arguments.of(8, 4097, List.of(2048, 2048, 1)),
        Arguments.of(LIMIT, 2, List.of(1, 1)),
        Arguments.of(LIMIT / 2, 3, List.of(2, 1)));
  }

  /**
   * An input as long as a MAX_LENGTH wider than the limit still embeds, in call order, next to
   * short inputs.
   */
  @Test
  void testMaxLengthAboveTheLimitEmbeds(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL fixed = embedder(model(dir, TINY_MASKED), vocabPad6(dir),
        Pooling.MEAN, false, LIMIT + 5, PaddingStrategy.MAX_LENGTH)) {
      final float[][] batch = fixed.embedAll(List.of("hello world", "hello", "world"));
      assertArrayEquals(scale(19), batch[0], DELTA);
      assertArrayEquals(scale(14), batch[1], DELTA);
      assertArrayEquals(scale(15), batch[2], DELTA);
    }
  }

  /** Alternating lengths with duplicates come back in input order after LONGEST sorts them. */
  @Test
  void testInterleavedLengthsKeepInputOrder(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL longest = embedder(model(dir, TINY_MASKED), vocabPad6(dir),
        Pooling.MEAN, false, 8, PaddingStrategy.LONGEST)) {
      final List<String> texts = new ArrayList<>();
      for (int i = 0; i < LIMIT / 4 + 1; i++) {
        texts.add("hello world");
        texts.add("hello");
        texts.add("hello world");
        texts.add("world");
      }
      final float[][] batch = longest.embedAll(texts);
      assertEquals(texts.size(), batch.length);
      for (int i = 0; i < batch.length; i++) {
        final String text = texts.get(i);
        final float sum = "hello world".equals(text) ? 19 : "hello".equals(text) ? 14 : 15;
        assertArrayEquals(scale(sum), batch[i], DELTA, "row " + i);
      }
    }
  }

  /**
   * A RoBERTa vocabulary with {@code <pad>} pads with that token: {@code tiny-masked.onnx} sums
   * the ids times the mask, so padded rows give the sum of their own ids.
   */
  @ParameterizedTest
  @EnumSource(value = PaddingStrategy.class, names = {"LONGEST", "MAX_LENGTH"})
  void testRobertaVocabularyPads(final PaddingStrategy padding, @TempDir final Path dir)
      throws Exception {
    // <s>=0 <pad>=1 </s>=2 <unk>=3 hello=4 world=5
    final File roberta = write(dir, "roberta.txt",
        List.of("<s>", "<pad>", "</s>", "<unk>", "hello", "world"));
    try (SentenceVectorsDL vectors = embedder(model(dir, TINY_MASKED), roberta,
        Pooling.MEAN, false, 8, padding)) {
      final float[][] batch = vectors.embedAll(List.of("hello world", "hello"));
      assertArrayEquals(scale(11), batch[0], DELTA);
      assertArrayEquals(scale(6), batch[1], DELTA);
    }
  }

  /** A padding id of 0, the usual case, gives the same masked sums as the id 6 fixture. */
  @ParameterizedTest
  @EnumSource(value = PaddingStrategy.class, names = {"LONGEST", "MAX_LENGTH"})
  void testPaddingIdZero(final PaddingStrategy padding, @TempDir final Path dir)
      throws Exception {
    try (SentenceVectorsDL vectors = embedder(model(dir, TINY_MASKED), vocab(dir),
        Pooling.MEAN, false, 8, padding)) {
      final float[][] batch = vectors.embedAll(List.of("hello world", "hello", "world", ""));
      assertArrayEquals(scale(19), batch[0], DELTA);
      assertArrayEquals(scale(14), batch[1], DELTA);
      assertArrayEquals(scale(15), batch[2], DELTA);
      assertArrayEquals(scale(10), batch[3], DELTA);
    }
  }

  static Stream<Arguments> poolingCases() {
    final List<Arguments> cases = new ArrayList<>();
    for (final PaddingStrategy padding : List.of(PaddingStrategy.LONGEST,
        PaddingStrategy.MAX_LENGTH)) {
      cases.add(Arguments.of(padding, Pooling.CLS, false, scale(7), scale(7)));
      cases.add(Arguments.of(padding, Pooling.CLS, true, UNIT_VECTOR, UNIT_VECTOR));
      cases.add(Arguments.of(padding, Pooling.MEAN, true, UNIT_VECTOR, UNIT_VECTOR));
    }
    return cases.stream();
  }

  /**
   * CLS pooling reads the first position, which padding never moves, and unit length scaling
   * applies per row, for padded rows too.
   */
  @ParameterizedTest
  @MethodSource("poolingCases")
  void testClsAndNormalizeUnderPadding(final PaddingStrategy padding, final Pooling pooling,
      final boolean normalize, final float[] helloWorld, final float[] hello,
      @TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = embedder(model(dir, TINY_VECTORS), vocabPad6(dir),
        pooling, normalize, 8, padding)) {
      final float[][] batch = vectors.embedAll(List.of("hello world", "hello", "hello world"));
      assertArrayEquals(helloWorld, batch[0], DELTA);
      assertArrayEquals(hello, batch[1], DELTA);
      assertArrayEquals(helloWorld, batch[2], DELTA);
    }
  }

  /**
   * The pooled-output model ignores the mask: a padded row also sums the padding id, so a batch
   * differs from the single-input result under LONGEST, and every call pads to the maximum length
   * under MAX_LENGTH, single calls included. This pins the documented caveat.
   */
  @Test
  void testPooledOutputModelSumsThePadding(@TempDir final Path dir) throws Exception {
    final File pooled = model(dir, TINY_POOLED);
    final File vocabulary = vocabPad6(dir);
    try (SentenceVectorsDL longest = embedder(pooled, vocabulary, Pooling.MEAN, false, 8,
        PaddingStrategy.LONGEST);
         SentenceVectorsDL fixed = embedder(pooled, vocabulary, Pooling.MEAN, false, 8,
             PaddingStrategy.MAX_LENGTH)) {
      final float[][] batch = longest.embedAll(List.of("hello world", "hello"));
      assertArrayEquals(scale(19), batch[0], DELTA);
      assertArrayEquals(scale(14 + 6), batch[1], DELTA);
      assertFalse(Arrays.equals(longest.embed("hello"), batch[1]));
      assertArrayEquals(scale(14), longest.embed("hello"), DELTA);
      assertArrayEquals(scale(14 + 5 * 6), fixed.embed("hello"), DELTA);
      assertArrayEquals(scale(19 + 4 * 6), fixed.embed("hello world"), DELTA);
    }
  }

  /**
   * A single {@code embed} under MAX_LENGTH pads to the maximum length: a model that ignores the
   * mask shows the padding ids, a model that reads it gives the unpadded sum.
   */
  @Test
  void testSingleEmbedUnderMaxLengthIsPadded(@TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL ignoring = embedder(model(dir, TINY_POOLED), vocabPad6(dir),
        Pooling.MEAN, false, 6, PaddingStrategy.MAX_LENGTH);
         SentenceVectorsDL masked = embedder(model(dir, TINY_MASKED), vocabPad6(dir),
             Pooling.MEAN, false, 6, PaddingStrategy.MAX_LENGTH)) {
      assertArrayEquals(scale(14 + 3 * 6), ignoring.embed("hello"), DELTA);
      assertArrayEquals(scale(14), masked.embed("hello"), DELTA);
      assertEquals(3, masked.dimension());
    }
  }

  static Stream<Arguments> boundaryPlans() {
    final List<Arguments> cases = new ArrayList<>();
    for (final PaddingStrategy padding : PaddingStrategy.values()) {
      cases.add(Arguments.of(padding, 4, LIMIT / 4, List.of(LIMIT / 4)));
      cases.add(Arguments.of(padding, 4, LIMIT / 4 + 1, List.of(LIMIT / 4, 1)));
      cases.add(Arguments.of(padding, 2, LIMIT / 2, List.of(LIMIT / 2)));
      cases.add(Arguments.of(padding, 2, 0, List.of()));
    }
    return cases.stream();
  }

  /**
   * A batch holds at most LIMIT token positions under every strategy: rows of one width fill the
   * inference at LIMIT / width rows and split at one row more. The maximum length is set to the
   * width of the rows, so MAX_LENGTH plans the same inferences.
   */
  @ParameterizedTest
  @MethodSource("boundaryPlans")
  void testRowCountBoundariesUnderEveryStrategy(final PaddingStrategy padding, final int width,
      final int count, final List<Integer> expected, @TempDir final Path dir) throws Exception {
    try (SentenceVectorsDL vectors = planner(dir, padding, width)) {
      assertEquals(expected, sizes(vectors.batches(tokens(width, count))));
    }
  }

  /** A row as wide as the limit runs alone under every strategy, as does a wider one. */
  @ParameterizedTest
  @EnumSource(PaddingStrategy.class)
  void testRowAtTheLimitRunsAlone(final PaddingStrategy padding, @TempDir final Path dir)
      throws Exception {
    try (SentenceVectorsDL vectors = planner(dir, padding, LIMIT)) {
      assertEquals(List.of(1, 1, 1), sizes(vectors.batches(tokens(LIMIT, 3))));
      assertEquals(List.of(1, 1), sizes(vectors.batches(tokens(LIMIT + 1, 2))));
    }
  }

  /**
   * LONGEST and MAX_LENGTH give the vectors of the CPU session on a CUDA device 0 for a call that
   * splits, on the model that reads the attention mask; skipped where there is no CUDA device.
   */
  @ParameterizedTest
  @EnumSource(value = PaddingStrategy.class, names = {"LONGEST", "MAX_LENGTH"})
  void testGpuMatchesTheCpuUnderPadding(final PaddingStrategy padding, @TempDir final Path dir)
      throws Exception {
    final File model = model(dir, TINY_MASKED);
    final File vocabulary = vocabPad6(dir);
    final List<String> texts = new ArrayList<>(Collections.nCopies(LIMIT / 4 + 1, "hello world"));
    texts.add("hello");
    texts.add("world");
    try (SentenceVectorsDL gpu = gpuOrAbort(model, vocabulary, Pooling.MEAN, false, 8, padding);
         SentenceVectorsDL cpu = embedder(model, vocabulary, Pooling.MEAN, false, 8, padding)) {
      assertSameEmbeddings(cpu, gpu, texts);
    }
  }
}
