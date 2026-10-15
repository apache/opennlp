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

package opennlp.tools.depparse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static opennlp.tools.depparse.DependencyTestSamples.CACHE_BYTES;
import static opennlp.tools.depparse.DependencyTestSamples.MINIMAL_TRANSITIONS;
import static opennlp.tools.depparse.DependencyTestSamples.minimalNetwork;
import static opennlp.tools.depparse.DependencyTestSamples.model;
import static opennlp.tools.depparse.DependencyTestSamples.networkBytes;
import static opennlp.tools.depparse.DependencyTestSamples.score;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks numerical precision of scoring and the byte budget and concurrency of the
 * {@link ContributionCache}.
 */
public class FeedforwardDependencyScoringTest {

  /** The number of features of one configuration. */
  private static final int FEATURES = FeedforwardContext.FEATURE_COUNT;

  /** The embedding rows of the hand-built networks: the reserved symbols only. */
  private static final int VOCABULARY_ROWS = FeedforwardNetwork.RESERVED_ROWS;

  /** A two-token sentence whose parse the known-score tests pin. */
  private static final String[] TOKENS = {"birds", "sing"};

  /** The tags of {@link #TOKENS}. */
  private static final String[] TAGS = {"NOUN", "VERB"};

  /** The number of threads contending for the cache. */
  private static final int WORKERS = 8;

  /** The number of independent runs of each contention test. */
  private static final int ROUNDS = 8;

  /** The time a worker waits for the others before it gives up. */
  private static final long WAIT_SECONDS = 10L;

  /** The hidden width of the contention network. */
  private static final int CONTENTION_HIDDEN = 256;

  /** The embedding width of the contention network. */
  private static final int CONTENTION_WIDTH = 128;

  /**
   * A hidden-layer calculation with a known result.
   *
   * @param name The arithmetic condition.
   * @param inputs The embedding values.
   * @param weights The hidden-layer weights.
   * @param bias The hidden bias.
   * @param outputScale The output weight magnitude.
   * @param hidden The expected hidden sum before activation.
   */
  private record Calculation(String name, float[] inputs, float[] weights,
      float bias, float outputScale, double hidden) {
  }

  /** {@return finite products and sums covering overflow, underflow and cancellation} */
  private static Stream<Arguments> calculations() {
    final float[] inputs = new float[256];
    final float[] weights = new float[256];
    Arrays.fill(inputs, Math.scalb(1f, 80));
    Arrays.fill(weights, Math.scalb(1f, 40));
    return Stream.of(
        new Calculation("product overflow", new float[] {Math.scalb(1f, 80)},
            new float[] {Math.scalb(1f, 80)}, 0, Math.scalb(1f, -120), Math.scalb(1d, 160)),
        new Calculation("product underflow", new float[] {Math.scalb(1f, -80)},
            new float[] {Math.scalb(1f, -80)}, 0, Math.scalb(1f, 120), Math.scalb(1d, -160)),
        new Calculation("product cancellation", new float[] {Math.nextUp(1f), 1f},
            new float[] {Math.nextUp(1f), -(1f + Math.scalb(1f, -22))}, 0,
            Math.scalb(1f, 120), Math.scalb(1d, -46)),
        new Calculation("cache range", inputs, weights, 0, Math.scalb(1f, -120),
            Math.scalb(1d, 128)),
        new Calculation("cache cancellation", new float[] {1f, Math.scalb(1f, -24)},
            new float[] {1f, 1f}, -1f, Math.scalb(1f, 80), Math.scalb(1d, -24)))
        .flatMap(calculation -> Stream.of(false, true).map(negative ->
            Arguments.of(calculation.name(), calculation, negative)));
  }

  /**
   * Direct, cached, copied and reloaded networks retain the calculated values.
   *
   * @param name The arithmetic condition.
   * @param calculation The input values and expected result.
   * @param negative Whether to negate the hidden sum.
   * @throws IOException Thrown if serialization or loading fails.
   */
  @ParameterizedTest(name = "{0}, negative={2}")
  @MethodSource("calculations")
  void testKnownScores(String name, Calculation calculation, boolean negative) throws IOException {
    final FeedforwardNetwork network = calculationNetwork(calculation, negative);
    final double hidden = negative ? -calculation.hidden() : calculation.hidden();
    final double score = hidden * hidden * hidden * calculation.outputScale();
    final double[] expected = {0, -score, score};
    final int[] features = new int[FEATURES];
    assertArrayEquals(expected, score(network, features, null), name);
    final byte[] serialized = networkBytes(network);
    final ContributionCache cache = new ContributionCache(network, CACHE_BYTES);
    assertArrayEquals(expected, score(network, features, cache));
    assertArrayEquals(expected, score(network, features, cache));
    assertArrayEquals(expected, score(network.copy(), features, null));
    assertArrayEquals(serialized, networkBytes(network));
    final FeedforwardNetwork loaded = new FeedforwardNetwork.Serializer().create(
        new ByteArrayInputStream(serialized));
    assertArrayEquals(expected, score(loaded, features, null));
    final DependencyTree expectedTree = negative
        ? DependencyTree.of(new int[] {1, -1}, new String[] {"dep", "root"})
        : DependencyTree.of(new int[] {-1, 0}, new String[] {"root", "root"});
    assertEquals(expectedTree,
        new FeedforwardDependencyParser(model(loaded)).parse(TOKENS, TAGS));
  }

  /**
   * Builds one active input block with opposite arc output weights.
   *
   * @param calculation The input values.
   * @param negative Whether to negate weights and bias.
   * @return The network.
   */
  private FeedforwardNetwork calculationNetwork(Calculation calculation, boolean negative) {
    final int width = calculation.inputs().length;
    final float[][] embeddings = new float[VOCABULARY_ROWS][width];
    for (int row = 0; row < embeddings.length; row++) {
      embeddings[row] = calculation.inputs().clone();
    }
    final float[][] weights = new float[1][FEATURES * width];
    for (int i = 0; i < width; i++) {
      weights[0][i] = negative ? -calculation.weights()[i] : calculation.weights()[i];
    }
    return minimalNetwork(embeddings, weights,
        new float[] {negative ? -calculation.bias() : calculation.bias()},
        new float[][] {{0}, {-calculation.outputScale()}, {calculation.outputScale()}});
  }

  /** {@return invalid values in each output for greedy and beam parsing} */
  private static Stream<Arguments> invalidScores() {
    return Stream.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        .flatMap(value -> IntStream.range(0, MINIMAL_TRANSITIONS.length).boxed().flatMap(index ->
            Stream.of(1, 4).map(beam -> Arguments.of(value, index, beam))));
  }

  /**
   * Corrupt in-memory scores fail at either decoding entry point.
   *
   * @param value The injected output bias.
   * @param index The affected transition.
   * @param beam The decoder beam size.
   */
  @ParameterizedTest
  @MethodSource("invalidScores")
  void testInvalidScores(float value, int index, int beam) {
    final FeedforwardNetwork network = minimalNetwork(new float[VOCABULARY_ROWS][1],
        new float[1][FEATURES], new float[1], new float[MINIMAL_TRANSITIONS.length][1]);
    network.outputBias()[index] = value;
    final FeedforwardDependencyParser parser =
        new FeedforwardDependencyParser(model(network), beam);
    final IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> parser.parse(TOKENS, TAGS));
    assertTrue(error.getMessage().contains("non-finite"), error.getMessage());
  }

  /** {@return common score offsets for log-softmax normalization} */
  private static Stream<Arguments> normalizationOffsets() {
    return DoubleStream.of(0, 1, -1, 1e20, -1e20, Float.MAX_VALUE, -Float.MAX_VALUE, 1e100)
        .boxed().map(Arguments::of);
  }

  /**
   * A common finite score offset leaves equal transition probabilities unchanged.
   *
   * @param offset The score shared by all transitions.
   */
  @ParameterizedTest
  @MethodSource("normalizationOffsets")
  void testEqualLogProbabilities(double offset) {
    final double[] scores = new double[MINIMAL_TRANSITIONS.length];
    Arrays.fill(scores, offset);
    final double[] expected = new double[MINIMAL_TRANSITIONS.length];
    Arrays.fill(expected, -Math.log(MINIMAL_TRANSITIONS.length));
    FeedforwardNetwork.logSoftmaxInPlace(scores);
    assertArrayEquals(expected, scores, 1e-15);
  }

  /**
   * Unequal probabilities retain their values and sum to one after a common shift.
   *
   * @param offset The common score shift.
   */
  @ParameterizedTest
  @ValueSource(doubles = {0, 1, -1, 1e12, -1e12, 0x1p48})
  void testUnequalLogProbabilities(double offset) {
    final double logSum = Math.log(Math.exp(-0.5) + Math.exp(-0.25) + 1);
    final double[] expected = {-0.5 - logSum, -0.25 - logSum, -logSum};
    final double[] actual = {offset, offset + 0.25, offset + 0.5};
    FeedforwardNetwork.logSoftmaxInPlace(actual);
    assertArrayEquals(expected, actual, 1e-15);
    assertEquals(1.0, Arrays.stream(actual).map(Math::exp).sum(), 1e-15);
  }

  /**
   * @return A network large enough that cached and direct scoring differ in cost, with
   *         one distinguishable row per worker. Never {@code null}.
   */
  private static FeedforwardNetwork contentionNetwork() {
    final float[][] embeddings = new float[VOCABULARY_ROWS][CONTENTION_WIDTH];
    for (int row = 0; row < embeddings.length; row++) {
      Arrays.fill(embeddings[row], (row + 1) * 0.125f);
    }
    final float[][] weights = new float[CONTENTION_HIDDEN][FEATURES * CONTENTION_WIDTH];
    for (float[] row : weights) {
      Arrays.fill(row, 0.125f);
    }
    final float[][] outputs = new float[MINIMAL_TRANSITIONS.length][CONTENTION_HIDDEN];
    outputs[0][0] = 1;
    return minimalNetwork(embeddings, weights, new float[CONTENTION_HIDDEN], outputs);
  }

  /**
   * @param network The network to cache.
   * @param entries The number of contribution vectors the budget admits.
   * @return A cache whose budget holds its tables plus exactly {@code entries} vectors.
   *         Never {@code null}.
   */
  private static ContributionCache cacheFor(FeedforwardNetwork network, int entries) {
    final long entryBytes = ContributionCache.ARRAY_HEADER_BYTES + 8L * network.hiddenSize();
    return new ContributionCache(network, tableBytes(network) + entries * entryBytes);
  }

  /**
   * @param network The network to cache.
   * @return The bytes the tables of a cache over {@code network} are charged up front.
   */
  private static long tableBytes(FeedforwardNetwork network) {
    return Long.MAX_VALUE - new ContributionCache(network, Long.MAX_VALUE).remainingBytes();
  }

  /**
   * Concurrent cache misses on one row store the entry once and refund the losers.
   *
   * @throws Exception Thrown if a worker fails.
   */
  @RepeatedTest(ROUNDS)
  void testConcurrentMissesOnASharedRow() throws Exception {
    final FeedforwardNetwork network = contentionNetwork();
    final ContributionCache cache = cacheFor(network, WORKERS);
    scoreConcurrently(network, cache, true);
    assertTrue(cache.entries() <= FEATURES, "stored " + cache.entries());
    assertEquals((WORKERS - cache.entries()) * cache.entryBytes(), cache.remainingBytes());
    assertEquals(tableBytes(network) + WORKERS * cache.entryBytes(),
        tableBytes(network) + cache.entries() * cache.entryBytes() + cache.remainingBytes(),
        "a lost compare-and-set must refund its reservation exactly once");
  }

  /**
   * Concurrent cache misses on distinct rows never exceed the budget.
   *
   * @throws Exception Thrown if a worker fails.
   */
  @RepeatedTest(ROUNDS)
  void testConcurrentMissesOnDistinctRows() throws Exception {
    final FeedforwardNetwork network = contentionNetwork();
    final ContributionCache cache = cacheFor(network, 1);
    scoreConcurrently(network, cache, false);
    assertEquals(1, cache.entries());
    assertEquals(0L, cache.remainingBytes());
    score(network, new int[FEATURES], cache);
    assertEquals(1, cache.entries());
  }

  /**
   * Scores one configuration per worker at the same time and checks every result.
   *
   * @param network The network.
   * @param cache The cache under contention.
   * @param shared Whether all workers use row zero or each its own row.
   * @throws Exception Thrown if a worker fails or times out.
   */
  private static void scoreConcurrently(FeedforwardNetwork network, ContributionCache cache,
      boolean shared) throws Exception {
    final CountDownLatch ready = new CountDownLatch(WORKERS);
    final CountDownLatch start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(WORKERS)) {
      final var futures = IntStream.range(0, WORKERS).mapToObj(index -> workers.submit(() -> {
        final int[] features = new int[FEATURES];
        Arrays.fill(features, shared ? 0 : index);
        ready.countDown();
        assertTrue(start.await(WAIT_SECONDS, TimeUnit.SECONDS), "workers did not start");
        return score(network, features, cache);
      })).toList();
      try {
        assertTrue(ready.await(WAIT_SECONDS, TimeUnit.SECONDS), "workers were not ready");
      } finally {
        start.countDown();
      }
      for (int i = 0; i < futures.size(); i++) {
        final double hidden = FEATURES * 2.0 * (shared ? 1 : i + 1);
        assertArrayEquals(new double[] {hidden * hidden * hidden, 0, 0},
            futures.get(i).get(WAIT_SECONDS, TimeUnit.SECONDS));
      }
    }
  }

  /**
   * Full caches keep their budget and agree with direct calculations on misses.
   *
   * @param entries The number of contribution vectors the budget admits.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, 8 * FEATURES})
  void testFullCache(int entries) {
    final float[][] embeddings = new float[VOCABULARY_ROWS][1];
    for (int row = 0; row < VOCABULARY_ROWS; row++) {
      embeddings[row][0] = (row + 1) * 0.125f;
    }
    final float[][] weights = new float[1][FEATURES];
    Arrays.fill(weights[0], 0.5f);
    final FeedforwardNetwork network = minimalNetwork(embeddings, weights, new float[1],
        new float[][] {{0}, {-1}, {1}});
    final ContributionCache cache = cacheFor(network, entries);
    for (int row = 0; row < VOCABULARY_ROWS; row++) {
      final int[] features = rowFeatures(network, row);
      double hidden = 0.0;
      for (final int feature : features) {
        hidden += 0.5 * embeddings[feature][0];
      }
      final double score = hidden * hidden * hidden;
      final double[] expected = {0, -score, score};
      assertArrayEquals(expected, score(network, features, cache));
      assertArrayEquals(expected, score(network, features, cache));
    }
    // the rows visit every slot of every position table once
    final long slots = slots(network);
    assertEquals(Math.min(entries, slots), cache.entries());
    assertEquals(Math.max(0, entries - slots) * cache.entryBytes(), cache.remainingBytes());
  }

  /**
   * @param network The network.
   * @return The table slots of a cache for {@code network}: one per feature position
   *         and row of the vocabulary block the position reads.
   */
  private static long slots(FeedforwardNetwork network) {
    return (long) FeedforwardContext.POSITIONS * network.wordIds().size()
        + (long) FeedforwardContext.POSITIONS * network.tagIds().size()
        + (long) FeedforwardContext.LABEL_POSITIONS * network.labelIds().size();
  }

  /** The cache sizes each position's table to its vocabulary block. */
  @Test
  void testTablesFollowTheVocabularyBlocks() {
    final FeedforwardNetwork network = contentionNetwork();
    final long tableBytes = FEATURES * ContributionCache.ARRAY_HEADER_BYTES
        + slots(network) * ContributionCache.REFERENCE_BYTES;
    assertEquals(0L, new ContributionCache(network, tableBytes).remainingBytes());
    assertThrows(IllegalArgumentException.class,
        () -> new ContributionCache(network, tableBytes - 1));
  }

  /**
   * Builds a configuration that reads one embedding row at every position of its
   * vocabulary, so the row's contribution is cached at each position it fits.
   *
   * @param network The network.
   * @param row The embedding row.
   * @return The features. Never {@code null}.
   */
  private static int[] rowFeatures(FeedforwardNetwork network, int row) {
    final int[] features = new int[FEATURES];
    for (int f = 0; f < FEATURES; f++) {
      if (f < FeedforwardContext.POSITIONS) {
        features[f] = network.firstWordRow() + row % network.wordIds().size();
      } else if (f < 2 * FeedforwardContext.POSITIONS) {
        features[f] = network.firstTagRow() + row % network.tagIds().size();
      } else {
        features[f] = network.firstLabelRow() + row % network.labelIds().size();
      }
    }
    return features;
  }
}
