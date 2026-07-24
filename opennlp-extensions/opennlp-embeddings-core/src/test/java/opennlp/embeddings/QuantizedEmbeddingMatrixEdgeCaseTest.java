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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge cases for the quantized matrix: dimensions that are not powers of two (so the padding
 * and truncation path runs), a one-dimensional matrix, adversarial rows the random rotation
 * must still reconstruct (constant, one-hot, sign-alternating), and the dot/norm consistency
 * over padded dimensions.
 */
class QuantizedEmbeddingMatrixEdgeCaseTest {

  private static final long SEED = 3L;

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 5, 17, 100, 300, 513})
  void testDotRotatedMatchesOriginalDotAtEveryDimension(int dimension) {
    final Random random = new Random(dimension);
    final int rows = 8;
    final float[] matrix = new float[rows * dimension];
    for (int i = 0; i < matrix.length; i++) {
      matrix[i] = (float) random.nextGaussian();
    }
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, rows, dimension, 4, SEED);
    final float[] query = new float[dimension];
    for (int d = 0; d < dimension; d++) {
      query[d] = (float) random.nextGaussian();
    }
    final float[] rotatedQuery = quantized.rotate(query);
    for (int row = 0; row < rows; row++) {
      final float[] decoded = quantized.decodeRow(row);
      double originalDot = 0;
      for (int d = 0; d < dimension; d++) {
        originalDot += (double) decoded[d] * query[d];
      }
      // The rotation is orthonormal, so scoring in rotated space over the padded coordinates
      // must equal the original-space dot with the truncated decoded row.
      assertEquals(originalDot, quantized.dotRotated(row, rotatedQuery),
          1e-3 * (1 + Math.abs(originalDot)),
          "dot mismatch at dimension " + dimension + ", row " + row);
    }
  }

  @Test
  void testConstantRowReconstructsDespiteBeingSpikyAfterRotation() {
    // A constant vector is the worst case for the transform: its rotation concentrates all
    // energy in one coordinate, which the grid clamps. The per-row least-squares scale must
    // absorb that clamp, so the reconstruction still points the same way.
    final int dimension = 300;
    final float[] matrix = new float[dimension];
    java.util.Arrays.fill(matrix, 0.7f);
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, 1, dimension, 4, SEED);
    assertTrue(cosine(matrix, 0, dimension, quantized.decodeRow(0)) > 0.98,
        "a constant row must still reconstruct in direction");
  }

  @Test
  void testOneHotAndAlternatingRowsReconstruct() {
    final int dimension = 128;
    final float[] oneHot = new float[dimension];
    oneHot[7] = 3.5f;
    final float[] alternating = new float[dimension];
    for (int d = 0; d < dimension; d++) {
      alternating[d] = (d % 2 == 0 ? 1f : -1f);
    }
    for (final float[] row : new float[][] {oneHot, alternating}) {
      final QuantizedEmbeddingMatrix quantized =
          QuantizedEmbeddingMatrix.quantize(row, 1, dimension, 4, SEED);
      assertTrue(cosine(row, 0, dimension, quantized.decodeRow(0)) > 0.95,
          "an adversarial row must reconstruct in direction");
    }
  }

  @Test
  void testRowNormMatchesDecodedRowAtNonPowerOfTwoDimension() {
    final int dimension = 17;
    final Random random = new Random(17);
    final int rows = 5;
    final float[] matrix = new float[rows * dimension];
    for (int i = 0; i < matrix.length; i++) {
      matrix[i] = 2f * (float) random.nextGaussian();
    }
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, rows, dimension, 3, SEED);
    for (int row = 0; row < rows; row++) {
      final float[] decoded = quantized.decodeRow(row);
      double sumOfSquares = 0;
      for (final float value : decoded) {
        sumOfSquares += (double) value * value;
      }
      assertEquals(Math.sqrt(sumOfSquares), quantized.rowNorm(row),
          1e-4 * (1 + Math.sqrt(sumOfSquares)),
          "rowNorm must equal the decoded row's norm at a padded dimension");
    }
  }

  /**
   * {@return the cosine between a matrix row and a decoded vector}
   *
   * @param matrix    The flat row-major matrix.
   * @param base      The row's first index.
   * @param dimension The row width.
   * @param decoded   The decoded row.
   */
  private static double cosine(float[] matrix, int base, int dimension, float[] decoded) {
    return ModelQuantizer.cosine(matrix, base, dimension, decoded);
  }
}
