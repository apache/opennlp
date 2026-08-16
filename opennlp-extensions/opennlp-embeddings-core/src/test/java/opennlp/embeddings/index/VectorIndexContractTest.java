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
package opennlp.embeddings.index;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link VectorIndex} lifecycle and validation contract, run against both implementations:
 * build-phase validation, the freeze boundary, and the query result guarantees.
 */
class VectorIndexContractTest {

  private static final int DIMENSION = 8;

  static Stream<Arguments> indexes() {
    return Stream.of(
        Arguments.of(Named.<IntFunction<VectorIndex>>of("flat", FlatFloatIndex::new)),
        Arguments.of(Named.<IntFunction<VectorIndex>>of("turboquant",
            dimension -> new TurboQuantIndex(dimension, 4, 42))),
        Arguments.of(Named.<IntFunction<VectorIndex>>of("hnsw", HnswFloatIndex::new)));
  }

  /** {@return a unit vector along the given axis} */
  private float[] axis(int d) {
    final float[] vector = new float[DIMENSION];
    vector[d] = 1f;
    return vector;
  }

  /** Returns a copy of a vector multiplied by a positive factor. */
  private float[] scaled(float[] vector, float factor) {
    final float[] scaled = vector.clone();
    for (int i = 0; i < scaled.length; i++) {
      scaled[i] *= factor;
    }
    return scaled;
  }

  @Test
  void testHitValidation() {
    assertEquals(-1.0, new VectorIndex.Hit("inverse", -1.0).score());
    assertEquals(1.0, new VectorIndex.Hit("same", 1.0).score());
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit(null, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit(" ", 0.0));
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit("two\nlines", 0.0));
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit("two\rlines", 0.0));
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit("row", Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> new VectorIndex.Hit("row", Double.NEGATIVE_INFINITY));
    assertThrows(IllegalArgumentException.class,
        () -> new VectorIndex.Hit("row", Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit("row", -1.01));
    assertThrows(IllegalArgumentException.class, () -> new VectorIndex.Hit("row", 1.01));
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testSelfRetrievalRanksTheIndexedVectorFirst(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    for (int d = 0; d < 4; d++) {
      index.add("axis-" + d, axis(d));
    }
    index.freeze();

    for (int d = 0; d < 4; d++) {
      final List<VectorIndex.Hit> hits = index.topK(axis(d), 2);
      assertEquals(2, hits.size());
      assertEquals("axis-" + d, hits.get(0).id());
      assertTrue(hits.get(0).score() > 0.9, "self similarity: " + hits.get(0).score());
      assertTrue(hits.get(0).score() >= hits.get(1).score(), "ordering");
    }
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testKBeyondTheSizeReturnsEverything(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("a", axis(0));
    index.add("b", axis(1));
    index.freeze();

    assertEquals(2, index.topK(axis(0), 100).size());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testAnEmptyIndexAnswersNoHits(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.freeze();
    assertEquals(0, index.size());
    assertTrue(index.topK(axis(0), 3).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testAZeroQueryHasNoDirectionAndAnswersNoHits(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("a", axis(0));
    index.freeze();
    assertTrue(index.topK(new float[DIMENSION], 3).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testANonzeroSmallQueryRetainsItsDirection(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    final float[] small = axis(0);
    small[0] = 1e-15f;
    index.add("small", small);
    index.freeze();

    final List<VectorIndex.Hit> hits = index.topK(small, 1);
    assertEquals(1, hits.size());
    assertEquals("small", hits.get(0).id());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testAZeroVectorScoresZeroInsteadOfNaN(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("zero", new float[DIMENSION]);
    index.freeze();

    final List<VectorIndex.Hit> hits = index.topK(axis(0), 1);
    assertEquals(1, hits.size());
    assertEquals(0.0, hits.get(0).score());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testTheFreezeBoundaryIsEnforcedBothWays(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("a", axis(0));
    assertThrows(IllegalStateException.class, () -> index.topK(axis(0), 1));
    index.freeze();
    index.freeze();
    assertThrows(IllegalStateException.class, () -> index.add("b", axis(1)));
    assertEquals(1, index.size());
    assertEquals(DIMENSION, index.dimension());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testBuildPhaseValidation(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("a", axis(0));

    assertThrows(IllegalArgumentException.class, () -> index.add(null, axis(1)));
    assertThrows(IllegalArgumentException.class, () -> index.add("  ", axis(1)));
    assertThrows(IllegalArgumentException.class, () -> index.add("b\nc", axis(1)));
    assertThrows(IllegalArgumentException.class, () -> index.add("b\rc", axis(1)));
    assertThrows(IllegalArgumentException.class, () -> index.add("a", axis(1)));
    assertThrows(IllegalArgumentException.class, () -> index.add("b", null));
    assertThrows(IllegalArgumentException.class, () -> index.add("b", new float[3]));
    final float[] infinite = axis(1);
    infinite[2] = Float.POSITIVE_INFINITY;
    assertThrows(IllegalArgumentException.class, () -> index.add("b", infinite));
    final float[] notANumber = axis(1);
    notANumber[2] = Float.NaN;
    assertThrows(IllegalArgumentException.class, () -> index.add("b", notANumber));
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testQueryValidation(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("a", axis(0));
    index.freeze();

    assertThrows(IllegalArgumentException.class, () -> index.topK(null, 1));
    assertThrows(IllegalArgumentException.class, () -> index.topK(new float[3], 1));
    assertThrows(IllegalArgumentException.class, () -> index.topK(axis(0), 0));
    assertThrows(IllegalArgumentException.class, () -> index.topK(axis(0), -1));

    final float[] notANumber = axis(0);
    notANumber[1] = Float.NaN;
    assertThrows(IllegalArgumentException.class, () -> index.topK(notANumber, 1));

    final float[] infinite = axis(0);
    infinite[1] = Float.POSITIVE_INFINITY;
    assertThrows(IllegalArgumentException.class, () -> index.topK(infinite, 1));
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testEqualScoresRetainInsertionOrder(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("first", axis(0));
    index.add("second", axis(0));
    index.add("third", axis(0));
    index.freeze();

    assertEquals(List.of("first", "second"), index.topK(axis(0), 2).stream()
        .map(VectorIndex.Hit::id)
        .toList());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testAddedVectorsAreCopied(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    final float[] first = axis(0);
    index.add("first", first);
    index.add("second", axis(1));
    first[0] = 0f;
    first[1] = 1f;
    index.freeze();

    assertEquals("first", index.topK(axis(0), 1).get(0).id());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testQueriesAreNotModified(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    index.add("Alice", axis(0));
    index.freeze();
    final float[] query = scaled(axis(0), 7f);
    final float[] original = query.clone();

    index.topK(query, 1);

    assertArrayEquals(original, query);
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testPositiveScalingDoesNotChangeCosineResults(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    final float[] direction = axis(0);
    direction[1] = 2f;
    index.add("Alice", direction);
    index.add("Queen", axis(1));
    index.freeze();

    final List<VectorIndex.Hit> small = index.topK(scaled(direction, 1e-30f), 2);
    final List<VectorIndex.Hit> large = index.topK(scaled(direction, 1e30f), 2);

    assertEquals(small.stream().map(VectorIndex.Hit::id).toList(),
        large.stream().map(VectorIndex.Hit::id).toList());
    for (int i = 0; i < small.size(); i++) {
      assertEquals(small.get(i).score(), large.get(i).score(), 1e-6);
    }
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testConcurrentQueriesReturnTheSameResults(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    for (int d = 0; d < DIMENSION; d++) {
      index.add("direction-" + d, axis(d));
    }
    index.freeze();
    final List<VectorIndex.Hit> expected = index.topK(axis(3), 4);
    final AtomicReference<List<VectorIndex.Hit>> mismatch = new AtomicReference<>();

    IntStream.range(0, 1_000).parallel().forEach(ignored -> {
      final List<VectorIndex.Hit> actual = index.topK(axis(3), 4);
      if (!expected.equals(actual)) {
        mismatch.compareAndSet(null, actual);
      }
    });

    assertNull(mismatch.get());
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testLargestFiniteQueryValuesProduceABoundedScore(IntFunction<VectorIndex> factory) {
    final VectorIndex index = factory.apply(DIMENSION);
    final float[] vector = new float[DIMENSION];
    java.util.Arrays.fill(vector, Float.MAX_VALUE);
    index.add("maximum", vector);
    index.freeze();

    final double score = index.topK(vector, 1).get(0).score();
    assertTrue(Double.isFinite(score), "score: " + score);
    assertTrue(score >= -1.0 && score <= 1.0, "score: " + score);
    assertTrue(score > 0.9, "self similarity: " + score);
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testADimensionBelowOneIsRejected(IntFunction<VectorIndex> factory) {
    assertThrows(IllegalArgumentException.class, () -> factory.apply(0));
  }
}
