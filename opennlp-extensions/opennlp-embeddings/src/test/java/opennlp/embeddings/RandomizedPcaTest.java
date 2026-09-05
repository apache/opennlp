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
package opennlp.embeddings;

import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The randomized PCA recovers the dominant subspace of a low-rank matrix: for data that is
 * exactly rank-k, projecting to k components preserves the pairwise geometry (dot products) of
 * the centered rows almost exactly, and it reports nearly all variance kept. A fixed seed makes
 * the projection deterministic.
 */
class RandomizedPcaTest {

  private static final int ROWS = 400;
  private static final int COLS = 48;
  private static final int RANK = 6;

  /** Small enough that a fixed absolute regularization would swamp the rescaled matrix. */
  private static final float SMALL_SCALE = 1e-6f;

  /**
   * {@return an exactly rank-{@link #RANK} matrix: a random factor times a random loading
   * matrix, plus a non-zero column mean so centering is exercised}
   */
  private static float[] lowRankData() {
    final Random random = new Random(7);
    final float[][] factors = new float[ROWS][RANK];
    final float[][] loadings = new float[RANK][COLS];
    for (final float[] row : factors) {
      for (int j = 0; j < RANK; j++) {
        row[j] = (float) random.nextGaussian() * (RANK - j);
      }
    }
    for (final float[] row : loadings) {
      for (int c = 0; c < COLS; c++) {
        row[c] = (float) random.nextGaussian();
      }
    }
    final float[] data = new float[ROWS * COLS];
    for (int i = 0; i < ROWS; i++) {
      for (int c = 0; c < COLS; c++) {
        float value = c; // a column mean the PCA must subtract
        for (int j = 0; j < RANK; j++) {
          value += factors[i][j] * loadings[j][c];
        }
        data[i * COLS + c] = value;
      }
    }
    return data;
  }

  private static double dot(float[] data, int rowA, int rowB, int cols) {
    double dot = 0;
    for (int c = 0; c < cols; c++) {
      dot += (double) data[rowA * cols + c] * data[rowB * cols + c];
    }
    return dot;
  }

  @Test
  void testRecoversTheExactSubspaceOfLowRankData() {
    final float[] original = lowRankData();
    // The centered reference, for the geometry comparison.
    final float[] centered = original.clone();
    for (int c = 0; c < COLS; c++) {
      float mean = 0;
      for (int i = 0; i < ROWS; i++) {
        mean += centered[i * COLS + c];
      }
      mean /= ROWS;
      for (int i = 0; i < ROWS; i++) {
        centered[i * COLS + c] -= mean;
      }
    }

    final RandomizedPca.Result result =
        RandomizedPca.fitTransform(original.clone(), ROWS, COLS, RANK, 42);

    assertEquals(ROWS * RANK, result.transformed().length);
    assertTrue(result.explainedVarianceRatio() > 0.999,
        "rank-6 data projected to 6 components keeps (almost) all variance, got "
            + result.explainedVarianceRatio());
    // Projecting exactly rank-k data onto its k principal components preserves pairwise dot
    // products up to numerical noise; check the diagonal and a few off-diagonal pairs.
    for (final int[] pair : new int[][] {{0, 0}, {1, 2}, {17, 399}, {5, 5}, {123, 321}}) {
      final double expected = dot(centered, pair[0], pair[1], COLS);
      final double actual = dot(result.transformed(), pair[0], pair[1], RANK);
      final double scale = Math.max(Math.abs(expected), 1);
      assertEquals(expected, actual, 1e-3 * scale,
          "pairwise dot product of rows " + pair[0] + " and " + pair[1]);
    }
  }

  @Test
  void testDeterministicForAFixedSeed() {
    final float[] first =
        RandomizedPca.fitTransform(lowRankData(), ROWS, COLS, RANK, 42).transformed();
    final float[] second =
        RandomizedPca.fitTransform(lowRankData(), ROWS, COLS, RANK, 42).transformed();
    assertArrayEquals(first, second);
  }

  /**
   * The decomposition is equivariant under a rescaling of the whole matrix: the projected
   * coordinates scale with the input and the explained variance ratio, being a ratio, does not
   * move. Any absolute (rather than relative) tolerance inside the pipeline breaks this.
   */
  @Test
  void testIsUnchangedByRescalingTheWholeMatrix() {
    final RandomizedPca.Result unscaled =
        RandomizedPca.fitTransform(lowRankData(), ROWS, COLS, RANK, 42);
    final float[] scaledData = lowRankData();
    for (int i = 0; i < scaledData.length; i++) {
      scaledData[i] *= SMALL_SCALE;
    }

    final RandomizedPca.Result scaled =
        RandomizedPca.fitTransform(scaledData, ROWS, COLS, RANK, 42);

    assertEquals(unscaled.explainedVarianceRatio(), scaled.explainedVarianceRatio(), 1e-6,
        "the explained variance ratio must not depend on the magnitude of the input");
    double largest = 0;
    for (final float value : unscaled.transformed()) {
      largest = Math.max(largest, Math.abs(value));
    }
    final double tolerance = 1e-4 * largest * SMALL_SCALE;
    for (int i = 0; i < unscaled.transformed().length; i++) {
      assertEquals(unscaled.transformed()[i] * (double) SMALL_SCALE, scaled.transformed()[i],
          tolerance, "projected coordinate " + i);
    }
  }

  /**
   * The parallel loops reduce per-block partial sums in a fixed block order, so the result does not
   * depend on how many threads the fork/join pool runs the blocks on.
   */
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 7})
  void testDeterministicAcrossThreadCounts(int parallelism) throws Exception {
    final float[] expected =
        RandomizedPca.fitTransform(lowRankData(), ROWS, COLS, RANK, 42).transformed();
    final ForkJoinPool pool = new ForkJoinPool(parallelism);

    try {
      final float[] actual = pool.submit(
          () -> RandomizedPca.fitTransform(lowRankData(), ROWS, COLS, RANK, 42).transformed())
          .get();
      assertArrayEquals(expected, actual);
    } finally {
      pool.shutdown();
    }
  }

  @Test
  void testCentersTheDataInPlace() {
    final float[] data = lowRankData();
    RandomizedPca.fitTransform(data, ROWS, COLS, RANK, 42);
    for (int c = 0; c < COLS; c++) {
      double mean = 0;
      for (int i = 0; i < ROWS; i++) {
        mean += data[i * COLS + c];
      }
      assertEquals(0, mean / ROWS, 1e-5, "column " + c + " is centered");
    }
  }

  @Test
  void testWideMatrixUsesNoMoreSampleDimensionsThanCenteredRank() {
    final int rows = 3;
    final int cols = 20;
    final int components = 2;
    final float[] data = new float[rows * cols];
    for (int c = 0; c < cols; c++) {
      data[c] = c + 1;
      data[cols + c] = (c + 1) * (c + 1);
      data[2 * cols + c] = c % 3 - 1;
    }

    final RandomizedPca.Result result =
        RandomizedPca.fitTransform(data, rows, cols, components, 42);

    assertEquals(rows * components, result.transformed().length);
    assertTrue(result.explainedVarianceRatio() > 0.999999,
        "two components must retain all variance of three centered rows");
    for (final float value : result.transformed()) {
      assertTrue(Float.isFinite(value));
    }
  }

  @Test
  void testRejectsNullData() {
    assertEquals("data must not be null", assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(null, 3, 4, 2, 42)).getMessage());
  }

  /**
   * The shape must describe the array exactly: a wrong column count, a non-positive dimension, or
   * a length that is not {@code rows * cols} is rejected.
   */
  @ParameterizedTest
  @CsvSource({"3, 5, 2", "3, 3, 2", "0, 4, 2", "3, 0, 2", "-1, 4, 2", "4, 4, 2", "2, 4, 1"})
  void testRejectsAShapeThatDoesNotDescribeTheData(int rows, int cols, int components) {
    final float[] data = new float[12];

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, rows, cols, components, 42));
    assertTrue(e.getMessage().startsWith("Data has 12 elements, not " + rows + " x " + cols),
        e.getMessage());
  }

  /**
   * The component count must be a genuine reduction: at least one, no more than the column count,
   * and strictly fewer than the row count (the randomized range finder has no subspace to find
   * otherwise).
   */
  @ParameterizedTest
  @CsvSource({"0", "-1", "5", "3", "4"})
  void testRejectsAComponentCountThatIsNotAReduction(int components) {
    final float[] data = new float[12];

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, 3, 4, components, 42));
    assertTrue(e.getMessage().startsWith("Components must be in [1, 2], got " + components),
        e.getMessage());
  }

  /**
   * Data whose rows are all identical centers to exactly zero, so there is no subspace and the
   * explained-variance ratio would be 0/0. The calculation must reject this case.
   */
  @Test
  void testRejectsDataWithoutVariance() {
    final float[] data = new float[ROWS * COLS];
    for (int i = 0; i < ROWS; i++) {
      for (int c = 0; c < COLS; c++) {
        data[i * COLS + c] = c;
      }
    }

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, ROWS, COLS, RANK, 42));
    assertTrue(e.getMessage().contains("total variance"), e.getMessage());
  }

  /**
   * A non-finite value poisons the column mean, so every centered value becomes NaN and the total
   * variance is NaN. The check must reject that too, not let NaN through into the table.
   */
  @ParameterizedTest
  @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
  void testRejectsNonFiniteData(float value) {
    final float[] data = lowRankData();
    data[0] = value;

    assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, ROWS, COLS, RANK, 42));
  }
}
