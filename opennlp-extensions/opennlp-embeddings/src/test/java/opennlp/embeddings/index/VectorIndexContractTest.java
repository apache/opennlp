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
import java.util.function.IntFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  private static float[] axis(int d) {
    final float[] vector = new float[DIMENSION];
    vector[d] = 1f;
    return vector;
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
    assertThrows(IllegalArgumentException.class, () -> index.add("a", axis(1)));
    assertThrows(IllegalArgumentException.class, () -> index.add("b", null));
    assertThrows(IllegalArgumentException.class, () -> index.add("b", new float[3]));
    final float[] infinite = axis(1);
    infinite[2] = Float.POSITIVE_INFINITY;
    assertThrows(IllegalArgumentException.class, () -> index.add("b", infinite));
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
  }

  @ParameterizedTest
  @MethodSource("indexes")
  void testADimensionBelowOneIsRejected(IntFunction<VectorIndex> factory) {
    assertThrows(IllegalArgumentException.class, () -> factory.apply(0));
  }
}
