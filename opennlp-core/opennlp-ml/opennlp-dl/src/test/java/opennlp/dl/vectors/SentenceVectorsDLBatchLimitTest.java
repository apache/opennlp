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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.Tokens;

import static opennlp.dl.vectors.TinyVectorModels.CLS_VECTOR;
import static opennlp.dl.vectors.TinyVectorModels.DELTA;
import static opennlp.dl.vectors.TinyVectorModels.HELLO;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_MEAN;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_SUM;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_WORLD;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_WORLD_MEAN;
import static opennlp.dl.vectors.TinyVectorModels.HELLO_WORLD_SUM;
import static opennlp.dl.vectors.TinyVectorModels.TINY_POOLED;
import static opennlp.dl.vectors.TinyVectorModels.TINY_VECTORS;
import static opennlp.dl.vectors.TinyVectorModels.UNIT_VECTOR;
import static opennlp.dl.vectors.TinyVectorModels.alternating;
import static opennlp.dl.vectors.TinyVectorModels.model;
import static opennlp.dl.vectors.TinyVectorModels.tokens;
import static opennlp.dl.vectors.TinyVectorModels.vocab;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the split of {@link SentenceVectorsDL#embedAll(List)} into inferences of at most
 * {@value SentenceVectorsDL#MAX_BATCH_TOKEN_POSITIONS} token positions: the boundaries of the
 * batches, and the vectors that come back through a split group. "hello" encodes to 3 tokens and
 * "hello world" to 4.
 */
class SentenceVectorsDLBatchLimitTest {

  private static final int LIMIT = SentenceVectorsDL.MAX_BATCH_TOKEN_POSITIONS;

  /**
   * {@return a session on the CPU over the given tiny model}
   *
   * @param dir The directory to copy the model into.
   * @param model {@link TinyVectorModels#TINY_VECTORS} or {@link TinyVectorModels#TINY_POOLED}.
   * @param pooling The pooling.
   * @param normalize {@code true} to scale every vector to unit length.
   * @param maxLength The maximum number of tokens per input.
   */
  private static SentenceVectorsDL vectors(final Path dir, final String model,
      final Pooling pooling, final boolean normalize, final int maxLength) throws Exception {
    return new SentenceVectorsDL(model(dir, model), vocab(dir), true, pooling, normalize,
        maxLength);
  }

  /** The directory of the shared embedder. */
  @TempDir
  static Path shared;

  /** An embedder that does not pad, whose batches the tests below check. */
  private static SentenceVectorsDL exact;

  /**
   * Opens the shared embedder.
   *
   * @throws Exception Thrown if the model cannot be loaded.
   */
  @BeforeAll
  static void openExact() throws Exception {
    exact = vectors(shared, TINY_VECTORS, Pooling.MEAN, false,
        SentenceVectorsDL.DEFAULT_MAX_LENGTH);
  }

  /** Closes the shared embedder. */
  @AfterAll
  static void closeExact() {
    exact.close();
  }

  /**
   * {@return the sizes of the batches of {@code count} encodings of {@code length} tokens}
   *
   * @param length The number of tokens of every encoding.
   * @param count The number of encodings.
   */
  private static List<Integer> sizes(final int length, final int count) {
    final Tokens[] encoded = Collections.nCopies(count, tokens(length)).toArray(new Tokens[0]);
    return TinyVectorModels.sizes(exact.batches(encoded));
  }

  /**
   * {@return the length, the count and the expected batch sizes at the edges of the row count}
   */
  static Stream<Arguments> rowCountBoundaries() {
    final int rows4 = LIMIT / 4;
    final int rows2 = LIMIT / 2;
    final int rows3 = LIMIT / 3;
    return Stream.of(
        Arguments.of(4, rows4, List.of(rows4)),
        Arguments.of(4, rows4 + 1, List.of(rows4, 1)),
        Arguments.of(4, rows4 - 1, List.of(rows4 - 1)),
        Arguments.of(2, rows2, List.of(rows2)),
        Arguments.of(2, rows2 + 1, List.of(rows2, 1)),
        Arguments.of(3, rows3, List.of(rows3)),
        Arguments.of(3, rows3 + 1, List.of(rows3, 1)),
        Arguments.of(3, 2 * rows3, List.of(rows3, rows3)),
        Arguments.of(3, 2 * rows3 + 1, List.of(rows3, rows3, 1)),
        Arguments.of(1, LIMIT, List.of(LIMIT)),
        Arguments.of(1, LIMIT + 1, List.of(LIMIT, 1)));
  }

  /**
   * Checks a group that fills one inference exactly, one row more, one row fewer, and lengths
   * that do or do not divide the cap evenly.
   */
  @ParameterizedTest
  @MethodSource("rowCountBoundaries")
  void testRowCountBoundaries(final int length, final int count, final List<Integer> expected) {
    assertEquals(expected, sizes(length, count));
  }

  /** An input as long as the cap, or longer, runs in a batch of its own. */
  @ParameterizedTest
  @ValueSource(ints = {LIMIT, LIMIT + 1})
  void testLengthAtAndOverTheLimitRunsOneRowPerBatch(final int length) {
    assertEquals(List.of(1, 1, 1), sizes(length, 3));
  }

  /** An empty call has no batches. */
  @Test
  void testEmptyCallHasNoBatches() {
    assertTrue(exact.batches(new Tokens[0]).isEmpty());
  }

  /** Inputs of different lengths run one batch per length, in the order each length appears. */
  @Test
  void testGroupsByLengthInCallOrder() {
    assertEquals(List.of(List.of(0, 3), List.of(1, 2, 4)), exact.batches(
        new Tokens[] {tokens(4), tokens(3), tokens(3), tokens(4), tokens(3)}));
  }

  /**
   * Checks that only the group that is too large is split: the 4-token inputs need two
   * inferences, the 3-token inputs between them stay in one.
   */
  @Test
  void testOnlyTheLargeGroupIsSplit() {
    final Tokens[] encoded = new Tokens[LIMIT / 4 + 3];
    Arrays.fill(encoded, tokens(4));
    encoded[1] = tokens(3);
    encoded[2] = tokens(3);
    final List<List<Integer>> batches = exact.batches(encoded);
    assertEquals(3, batches.size());
    assertEquals(LIMIT / 4, batches.get(0).size());
    assertEquals(List.of(0, 3), batches.get(0).subList(0, 2));
    assertEquals(1, batches.get(1).size());
    assertEquals(List.of(1, 2), batches.get(2));
  }

  /**
   * Runs the same mix through the session: one group splits, the other does not. The rows at
   * the start and end of each inference, and the two short texts, equal the vector of their own
   * text.
   */
  @Test
  void testMixedGroupsThroughTheSession(@TempDir final Path dir) throws Exception {
    final List<String> texts = new ArrayList<>(Collections.nCopies(LIMIT / 4 + 1, HELLO_WORLD));
    texts.add(1, HELLO);
    texts.add(7, HELLO);
    try (SentenceVectorsDL v = vectors(dir, TINY_VECTORS, Pooling.MEAN, false,
        SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      final float[][] batch = v.embedAll(texts);
      assertEquals(texts.size(), batch.length);
      // The first inference ends with the (LIMIT / 4)-th "hello world", at index LIMIT / 4 + 1.
      final int lastOfFirst = LIMIT / 4 + 1;
      for (final int i : new int[] {0, 1, 2, 7, lastOfFirst, lastOfFirst + 1, texts.size() - 1}) {
        assertArrayEquals(v.embed(texts.get(i)), batch[i], DELTA, "row " + i);
      }
    }
  }

  /** An input as wide as the cap runs next to short inputs and comes back in call order. */
  @Test
  void testInputAtTheLimitThroughTheSession(@TempDir final Path dir) throws Exception {
    final String wide = String.join(" ", Collections.nCopies(LIMIT - 2, HELLO));
    try (SentenceVectorsDL v = vectors(dir, TINY_VECTORS, Pooling.MEAN, false, LIMIT)) {
      final List<String> texts = List.of(HELLO, wide, HELLO_WORLD, wide);
      final float[][] batch = v.embedAll(texts);
      for (int i = 0; i < texts.size(); i++) {
        assertArrayEquals(v.embed(texts.get(i)), batch[i], DELTA, "row " + i);
      }
    }
  }

  /**
   * {@return the model, pooling, unit length, and the expected vectors for "hello world" and
   * "hello"}
   */
  static Stream<Arguments> pathCases() {
    return Stream.of(
        Arguments.of(TINY_VECTORS, Pooling.MEAN, false, HELLO_WORLD_MEAN, HELLO_MEAN),
        Arguments.of(TINY_VECTORS, Pooling.CLS, false, CLS_VECTOR, CLS_VECTOR),
        Arguments.of(TINY_VECTORS, Pooling.CLS, true, UNIT_VECTOR, UNIT_VECTOR),
        Arguments.of(TINY_VECTORS, Pooling.MEAN, true, UNIT_VECTOR, UNIT_VECTOR),
        Arguments.of(TINY_POOLED, Pooling.CLS, false, HELLO_WORLD_SUM, HELLO_SUM),
        Arguments.of(TINY_POOLED, Pooling.MEAN, true, UNIT_VECTOR, UNIT_VECTOR));
  }

  /**
   * Checks every path after the inference through split groups of alternating lengths, with the
   * results in input order.
   */
  @ParameterizedTest
  @MethodSource("pathCases")
  void testPathsThroughASplitGroup(final String model, final Pooling pooling,
      final boolean normalize, final float[] helloWorld, final float[] hello,
      @TempDir final Path dir) throws Exception {
    final List<String> texts = alternating(LIMIT / 3 + 1);
    try (SentenceVectorsDL v = vectors(dir, model, pooling, normalize,
        SentenceVectorsDL.DEFAULT_MAX_LENGTH)) {
      final float[][] batch = v.embedAll(texts);
      assertEquals(texts.size(), batch.length);
      for (int i = 0; i < batch.length; i++) {
        assertArrayEquals(i % 2 == 0 ? helloWorld : hello, batch[i], DELTA, "row " + i);
      }
    }
  }
}
