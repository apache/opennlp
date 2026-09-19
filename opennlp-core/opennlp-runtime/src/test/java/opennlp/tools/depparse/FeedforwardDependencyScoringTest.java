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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks numerical precision and concurrent cache use during dependency parsing. */
class FeedforwardDependencyScoringTest {

  private static final int CACHE_ENTRY_LIMIT = 32768;
  private static final int FEATURES = FeedforwardContext.FEATURE_COUNT;
  private static final int VOCABULARY_ROWS = 8;
  private static final String[] TRANSITIONS = {"SHIFT", "LEFT_ARC:dep", "RIGHT_ARC:root"};
  private static final String[] TOKENS = {"birds", "sing"};
  private static final String[] TAGS = {"NOUN", "VERB"};

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
   * Direct, cached, copied and reloaded models retain the calculated values.
   *
   * @param name The arithmetic condition.
   * @param calculation The input values and expected result.
   * @param negative Whether to negate the hidden sum.
   * @throws IOException If model serialization or loading fails.
   */
  @ParameterizedTest(name = "{0}, negative={2}")
  @MethodSource("calculations")
  void testKnownScores(String name, Calculation calculation, boolean negative) throws IOException {
    final FeedforwardDependencyModel model = calculationModel(calculation, negative);
    final double hidden = negative ? -calculation.hidden() : calculation.hidden();
    final double score = hidden * hidden * hidden * calculation.outputScale();
    final double[] expected = {0, -score, score};
    final int[] features = new int[FEATURES];
    assertArrayEquals(expected, model.score(features), name);
    final byte[] serialized = serialize(model);
    model.enableScoringCache();
    assertArrayEquals(expected, model.score(features));
    assertArrayEquals(expected, model.score(features));
    assertArrayEquals(expected, model.copy().score(features));
    assertArrayEquals(serialized, serialize(model));
    final FeedforwardDependencyModel loaded = FeedforwardDependencyModel.load(
        new ByteArrayInputStream(serialized));
    assertArrayEquals(expected, loaded.score(features));
    final DependencyGraph expectedGraph = negative
        ? DependencyGraph.of(new int[] {1, -1}, new String[] {"dep", "root"})
        : DependencyGraph.of(new int[] {-1, 0}, new String[] {"root", "root"});
    assertEquals(expectedGraph, new FeedforwardDependencyParser(loaded).parse(TOKENS, TAGS));
  }

  /**
   * Builds one active input block with opposite arc output weights.
   *
   * @param calculation The input values.
   * @param negative Whether to negate weights and bias.
   * @return The model.
   */
  private FeedforwardDependencyModel calculationModel(Calculation calculation, boolean negative) {
    final int width = calculation.inputs().length;
    final float[][] embeddings = new float[VOCABULARY_ROWS][width];
    for (int row = 0; row < embeddings.length; row++) {
      embeddings[row] = calculation.inputs().clone();
    }
    final float[][] weights = new float[1][FEATURES * width];
    for (int i = 0; i < width; i++) {
      weights[0][i] = negative ? -calculation.weights()[i] : calculation.weights()[i];
    }
    return model(embeddings, weights,
        new float[] {negative ? -calculation.bias() : calculation.bias()},
        new float[][] {{0}, {-calculation.outputScale()}, {calculation.outputScale()}});
  }

  /**
   * Builds a model with disjoint reserved vocabularies and valid transitions.
   *
   * @param embeddings The embedding matrix.
   * @param weights The hidden weights.
   * @param bias The hidden bias.
   * @param outputs The output weights.
   * @return The model.
   */
  private FeedforwardDependencyModel model(float[][] embeddings, float[][] weights,
      float[] bias, float[][] outputs) {
    return new FeedforwardDependencyModel(
        Map.of(FeedforwardDependencyModel.UNKNOWN, 0, FeedforwardDependencyModel.ABSENT, 1,
            FeedforwardDependencyModel.ROOT_SYMBOL, 2),
        Map.of(FeedforwardDependencyModel.UNKNOWN, 3, FeedforwardDependencyModel.ABSENT, 4,
            FeedforwardDependencyModel.ROOT_SYMBOL, 5),
        Map.of(FeedforwardDependencyModel.UNKNOWN, 6, FeedforwardDependencyModel.ABSENT, 7),
        TRANSITIONS.clone(), embeddings[0].length, embeddings, weights, bias, outputs,
        new float[TRANSITIONS.length]);
  }

  /** {@return invalid values in each output for greedy and beam parsing} */
  private static Stream<Arguments> invalidScores() {
    return Stream.of(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        .flatMap(value -> IntStream.range(0, TRANSITIONS.length).boxed().flatMap(index ->
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
    final FeedforwardDependencyModel model = model(new float[VOCABULARY_ROWS][1],
        new float[1][FEATURES], new float[1], new float[TRANSITIONS.length][1]);
    model.outputBias()[index] = value;
    final FeedforwardDependencyParser parser = new FeedforwardDependencyParser(model, beam);
    final IllegalStateException error = assertThrows(IllegalStateException.class,
        () -> parser.parse(TOKENS, TAGS));
    assertTrue(error.getMessage().contains("non-finite"), error.getMessage());
  }

  /** {@return common score offsets for parsing and refinement normalization} */
  private static Stream<Arguments> normalizationCases() {
    return DoubleStream.of(0, 1, -1, 1e20, -1e20, Float.MAX_VALUE, -Float.MAX_VALUE, 1e100)
        .boxed().flatMap(offset -> Stream.of(false, true)
            .map(refinement -> Arguments.of(offset, refinement)));
  }

  /**
   * A common finite score offset leaves equal transition probabilities unchanged.
   *
   * @param offset The score shared by all transitions.
   * @param refinement Whether to use the refinement optimizer's normalization.
   * @throws ReflectiveOperationException If the normalization method cannot be called.
   */
  @ParameterizedTest
  @MethodSource("normalizationCases")
  void testEqualLogProbabilities(double offset, boolean refinement)
      throws ReflectiveOperationException {
    final double[] scores = new double[TRANSITIONS.length];
    Arrays.fill(scores, offset);
    final double[] expected = new double[TRANSITIONS.length];
    Arrays.fill(expected, -Math.log(TRANSITIONS.length));
    assertArrayEquals(expected, normalizedScores(scores, refinement), 1e-15);
  }

  /** {@return offsets for three unequal, representable scores in both normalizers} */
  private static Stream<Arguments> unequalNormalizationCases() {
    return DoubleStream.of(0, 1, -1, 1e12, -1e12, Math.scalb(1d, 48)).boxed()
        .flatMap(offset -> Stream.of(false, true).map(refinement -> Arguments.of(offset, refinement)));
  }

  /**
   * Unequal probabilities retain their values and sum to one after a common shift.
   *
   * @param offset The common score shift.
   * @param refinement Whether to use refinement normalization.
   * @throws ReflectiveOperationException If the normalization method cannot be called.
   */
  @ParameterizedTest
  @MethodSource("unequalNormalizationCases")
  void testUnequalLogProbabilities(double offset, boolean refinement)
      throws ReflectiveOperationException {
    final double logSum = Math.log(Math.exp(-0.5) + Math.exp(-0.25) + 1);
    final double[] expected = {-0.5 - logSum, -0.25 - logSum, -logSum};
    final double[] actual = normalizedScores(new double[] {offset, offset + 0.25, offset + 0.5},
        refinement);
    assertArrayEquals(expected, actual, 1e-15);
    assertEquals(1.0, Arrays.stream(actual).map(Math::exp).sum(), 1e-15);
  }

  /**
   * Calls a private normalizer without exposing it through production API.
   *
   * @param scores The finite raw scores; refinement overwrites them.
   * @param refinement Whether to use refinement normalization.
   * @return The log probabilities.
   * @throws ReflectiveOperationException If the normalizer cannot be called.
   */
  private double[] normalizedScores(double[] scores, boolean refinement)
      throws ReflectiveOperationException {
    final FeedforwardDependencyModel model = model(
        new float[VOCABULARY_ROWS][1], new float[1][FEATURES], new float[1],
        new float[TRANSITIONS.length][1]);
    if (refinement) {
      final Class<?> type = Class.forName(
          FeedforwardDependencyTrainer.class.getName() + "$GlobalOptimizer");
      final var constructor = type.getDeclaredConstructor(FeedforwardDependencyModel.class,
          FeedforwardDependencyTrainer.Settings.class);
      constructor.setAccessible(true);
      final Object optimizer = constructor.newInstance(model,
          FeedforwardDependencyTrainer.Settings.defaults());
      final var method = type.getDeclaredMethod("logSoftmaxInPlace", double[].class);
      method.setAccessible(true);
      method.invoke(optimizer, (Object) scores);
      return scores;
    } else {
      final var method = FeedforwardDependencyParser.class.getDeclaredMethod(
          "logSoftmax", double[].class);
      method.setAccessible(true);
      return (double[]) method.invoke(new FeedforwardDependencyParser(model, 4), (Object) scores);
    }
  }

  /** {@return independent shared-key and distinct-key contention runs} */
  private static Stream<Arguments> contentionCases() {
    return IntStream.range(0, 8).boxed().flatMap(round ->
        Stream.of(false, true).map(shared -> Arguments.of(round, shared)));
  }

  /**
   * Concurrent cache misses respect the capacity and refund duplicate reservations.
   *
   * @param round The independent run.
   * @param shared Whether requests use the same embedding row.
   * @throws Exception If a worker or cache inspection fails.
   */
  @ParameterizedTest
  @MethodSource("contentionCases")
  void testConcurrentBudget(int round, boolean shared) throws Exception {
    final float[][] embeddings = new float[VOCABULARY_ROWS][128];
    for (int row = 0; row < embeddings.length; row++) {
      Arrays.fill(embeddings[row], (row + 1) * 0.125f);
    }
    final float[][] weights = new float[256][FEATURES * 128];
    for (float[] row : weights) {
      Arrays.fill(row, 0.125f);
    }
    final float[][] outputs = new float[TRANSITIONS.length][256];
    outputs[0][0] = 1;
    final FeedforwardDependencyModel model = model(embeddings, weights, new float[256], outputs);
    model.enableScoringCache();
    final Object cache = field(model, "cache");
    final AtomicInteger remaining = (AtomicInteger) field(cache, "remaining");
    final int budget = shared ? 8 : 1;
    remaining.set(budget);
    final CountDownLatch ready = new CountDownLatch(8);
    final CountDownLatch start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(8)) {
      final var futures = IntStream.range(0, 8).mapToObj(index -> workers.submit(() -> {
        final int[] features = new int[FEATURES];
        Arrays.fill(features, shared ? 0 : index);
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS), "workers did not start");
        return model.score(features);
      })).toList();
      try {
        assertTrue(ready.await(10, TimeUnit.SECONDS), "workers were not ready");
      } finally {
        start.countDown();
      }
      for (int i = 0; i < futures.size(); i++) {
        final double hidden = FEATURES * 2.0 * (shared ? 1 : i + 1);
        assertArrayEquals(new double[] {hidden * hidden * hidden, 0, 0},
            futures.get(i).get(10, TimeUnit.SECONDS));
      }
    }
    final int entries = cachedEntries(cache);
    assertTrue(entries <= budget,
        "round " + round + " stored " + entries + " entries with budget " + budget);
    assertEquals(budget - entries, remaining.get());
    model.score(new int[FEATURES]);
    assertEquals(budget, cachedEntries(cache));
    assertEquals(0, remaining.get());
  }

  /**
   * Concurrent parser constructors initialize one cache on a shared model.
   *
   * @param round The independent run.
   * @throws Exception If a worker or cache inspection fails.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3})
  void testConcurrentInitialization(int round) throws Exception {
    final FeedforwardDependencyModel model = model(new float[100000][1],
        new float[1][FEATURES], new float[1], new float[TRANSITIONS.length][1]);
    final CountDownLatch ready = new CountDownLatch(8);
    final CountDownLatch start = new CountDownLatch(1);
    final Callable<Object> initialize = () -> {
      ready.countDown();
      assertTrue(start.await(10, TimeUnit.SECONDS), "workers did not start");
      new FeedforwardDependencyParser(model);
      return field(model, "cache");
    };
    final Set<Object> caches = Collections.newSetFromMap(new IdentityHashMap<>());
    try (var workers = Executors.newFixedThreadPool(8)) {
      final var futures = IntStream.range(0, 8).mapToObj(index -> workers.submit(initialize)).toList();
      try {
        assertTrue(ready.await(10, TimeUnit.SECONDS), "workers were not ready");
      } finally {
        start.countDown();
      }
      for (var future : futures) {
        caches.add(future.get(10, TimeUnit.SECONDS));
      }
    }
    assertEquals(1, caches.size(), "cache identities in round " + round);
  }

  /**
   * Full caches retain their capacity and agree with direct calculations on misses.
   *
   * @param budget The cache capacity to exercise.
   * @throws ReflectiveOperationException If cache inspection fails.
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3, CACHE_ENTRY_LIMIT})
  void testFullCache(int budget) throws ReflectiveOperationException {
    final int rows = Math.max(VOCABULARY_ROWS, (budget + FEATURES - 1) / FEATURES + 2);
    final float[][] embeddings = new float[rows][1];
    for (int row = 0; row < rows; row++) {
      embeddings[row][0] = (row + 1) * 0.125f;
    }
    final float[][] weights = new float[1][FEATURES];
    Arrays.fill(weights[0], 0.5f);
    final FeedforwardDependencyModel model = model(embeddings, weights, new float[1],
        new float[][] {{0}, {-1}, {1}});
    model.enableScoringCache();
    final Object cache = field(model, "cache");
    final AtomicInteger remaining = (AtomicInteger) field(cache, "remaining");
    assertEquals(CACHE_ENTRY_LIMIT, remaining.get());
    remaining.set(budget);
    for (int row = 0; row < rows; row++) {
      final int[] features = new int[FEATURES];
      Arrays.fill(features, row);
      final double hidden = FEATURES * (row + 1) / 16.0;
      final double score = hidden * hidden * hidden;
      final double[] expected = {0, -score, score};
      assertArrayEquals(expected, model.score(features));
      assertArrayEquals(expected, model.score(features));
    }
    assertEquals(budget, cachedEntries(cache));
    assertEquals(0, remaining.get());
  }

  /**
   * Counts published contribution arrays.
   *
   * @param cache The model cache.
   * @return The retained entry count.
   * @throws ReflectiveOperationException If cache inspection fails.
   */
  private int cachedEntries(Object cache) throws ReflectiveOperationException {
    int entries = 0;
    for (AtomicReferenceArray<?> position : (AtomicReferenceArray<?>[]) field(cache, "byPosition")) {
      for (int row = 0; row < position.length(); row++) {
        if (position.get(row) != null) {
          entries++;
        }
      }
    }
    return entries;
  }

  /**
   * Reads private cache state without adding inspection methods to the model.
   *
   * @param owner The field owner.
   * @param name The field name.
   * @return The field value.
   * @throws ReflectiveOperationException If field access fails.
   */
  private Object field(Object owner, String name) throws ReflectiveOperationException {
    final Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  /**
   * Serializes the model for comparisons before and after scoring.
   *
   * @param model The model.
   * @return Its binary representation.
   * @throws IOException If writing fails.
   */
  private byte[] serialize(FeedforwardDependencyModel model) throws IOException {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    model.serialize(output);
    return output.toByteArray();
  }
}
