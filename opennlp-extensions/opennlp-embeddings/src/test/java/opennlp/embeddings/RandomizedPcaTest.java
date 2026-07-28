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

import org.junit.jupiter.api.Test;

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
  void testRejectsInconsistentArguments() {
    final float[] data = new float[12];
    assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(null, 3, 4, 2, 42));
    assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, 3, 5, 2, 42));
    assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, 3, 4, 0, 42));
    assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, 3, 4, 5, 42));
    assertThrows(IllegalArgumentException.class,
        () -> RandomizedPca.fitTransform(data, 3, 4, 3, 42));
  }
}
