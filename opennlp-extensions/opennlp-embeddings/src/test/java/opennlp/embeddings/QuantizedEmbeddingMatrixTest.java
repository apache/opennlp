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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quantized matrix contract: reconstruction quality per bit width, rotated-space math that
 * agrees with original-space math, linear pooling, deterministic bytes, a self-describing file
 * that round-trips exactly, and loud failure on malformed input.
 */
class QuantizedEmbeddingMatrixTest {

  private static final int ROWS = 50;
  private static final int DIMENSION = 300;
  private static final long SEED = 12345L;

  /**
   * {@return a deterministic random test matrix with varied row norms}
   */
  private static float[] testMatrix() {
    final Random random = new Random(42);
    final float[] matrix = new float[ROWS * DIMENSION];
    for (int row = 0; row < ROWS; row++) {
      // Vary the norms so per-row scaling is actually exercised.
      final float rowScale = 0.1f + 3f * random.nextFloat();
      for (int d = 0; d < DIMENSION; d++) {
        matrix[row * DIMENSION + d] = rowScale * (float) random.nextGaussian();
      }
    }
    return matrix;
  }

  @Test
  void testReconstructionQualityPerBitWidth() {
    // The mean squared error of the Gaussian Lloyd-Max grids translates to an expected cosine
    // between a row and its reconstruction; these thresholds sit safely below the analytic
    // expectation (about 0.945 at 2 bits, 0.983 at 3, 0.995 at 4) but far above what a broken
    // rotation, grid, or scale would produce.
    assertMeanCosineAtLeast(2, 0.92);
    assertMeanCosineAtLeast(3, 0.97);
    assertMeanCosineAtLeast(4, 0.99);
  }

  /**
   * Asserts the mean cosine between original and decoded rows for a bit width.
   *
   * @param bits      The bit width under test.
   * @param threshold The minimum acceptable mean cosine.
   */
  private static void assertMeanCosineAtLeast(int bits, double threshold) {
    final float[] matrix = testMatrix();
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, bits, SEED);
    double cosineSum = 0;
    for (int row = 0; row < ROWS; row++) {
      final float[] decoded = quantized.decodeRow(row);
      cosineSum += cosine(matrix, row * DIMENSION, decoded);
    }
    final double meanCosine = cosineSum / ROWS;
    assertTrue(meanCosine >= threshold, bits + " bits reconstructed a mean cosine of "
        + meanCosine + ", below the acceptable " + threshold);
  }

  @Test
  void testRotatedDotEqualsOriginalSpaceDot() {
    final float[] matrix = testMatrix();
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 4, SEED);
    final Random random = new Random(7);
    final float[] query = new float[DIMENSION];
    for (int d = 0; d < DIMENSION; d++) {
      query[d] = (float) random.nextGaussian();
    }
    final float[] rotatedQuery = quantized.rotate(query);
    for (int row = 0; row < ROWS; row++) {
      final float[] decoded = quantized.decodeRow(row);
      double originalDot = 0;
      for (int d = 0; d < DIMENSION; d++) {
        originalDot += (double) decoded[d] * query[d];
      }
      assertEquals(originalDot, quantized.dotRotated(row, rotatedQuery),
          1e-3 * (1 + Math.abs(originalDot)),
          "the rotation is orthonormal, so rotated-space and original-space dots must agree");
    }
  }

  @Test
  void testRowNormIsTheDecodedRowsNorm() {
    final float[] matrix = testMatrix();
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 3, SEED);
    for (int row = 0; row < ROWS; row++) {
      final float[] decoded = quantized.decodeRow(row);
      double sumOfSquares = 0;
      for (final float value : decoded) {
        sumOfSquares += (double) value * value;
      }
      final double decodedNorm = Math.sqrt(sumOfSquares);
      assertEquals(decodedNorm, quantized.rowNorm(row), 1e-3 * (1 + decodedNorm));
    }
  }

  @Test
  void testPoolingInRotatedSpaceEqualsPoolingDecodedRows() {
    final float[] matrix = testMatrix();
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 4, SEED);
    final float[] rotatedSum = new float[quantized.paddedDimension()];
    quantized.addRowRotated(0, 1f, rotatedSum);
    quantized.addRowRotated(1, 2.5f, rotatedSum);
    quantized.addRowRotated(2, -0.5f, rotatedSum);
    final float[] pooled = quantized.toOriginal(rotatedSum);
    final float[] row0 = quantized.decodeRow(0);
    final float[] row1 = quantized.decodeRow(1);
    final float[] row2 = quantized.decodeRow(2);
    final float[] expected = new float[DIMENSION];
    for (int d = 0; d < DIMENSION; d++) {
      expected[d] = row0[d] + 2.5f * row1[d] - 0.5f * row2[d];
    }
    assertArrayEquals(expected, pooled, 1e-3f,
        "rotation is linear, so pooling commutes with it");
  }

  @Test
  void testZeroRowDecodesToZero() {
    final float[] matrix = new float[3 * 8];
    matrix[0] = 1f;
    matrix[2 * 8 + 5] = -2f;
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(matrix, 3, 8, 2, SEED);
    assertArrayEquals(new float[8], quantized.decodeRow(1), 0f);
    assertEquals(0.0, quantized.rowNorm(1), 0.0);
  }

  @Test
  void testQuantizingIsDeterministic(@TempDir Path directory) throws IOException {
    final float[] matrix = testMatrix();
    final Path first = directory.resolve("first.bin");
    final Path second = directory.resolve("second.bin");
    QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 3, SEED).write(first);
    QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 3, SEED).write(second);
    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second),
        "the same matrix, bits, and seed must produce the same file bytes");
  }

  @Test
  void testWriteReadRoundTrip(@TempDir Path directory) throws IOException {
    final float[] matrix = testMatrix();
    final QuantizedEmbeddingMatrix written =
        QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 4, SEED);
    final Path file = directory.resolve("matrix.bin");
    written.write(file);
    final QuantizedEmbeddingMatrix read = QuantizedEmbeddingMatrix.read(file);
    assertEquals(written.rowCount(), read.rowCount());
    assertEquals(written.dimension(), read.dimension());
    assertEquals(written.paddedDimension(), read.paddedDimension());
    assertEquals(written.bits(), read.bits());
    assertEquals(written.seed(), read.seed());
    for (int row = 0; row < ROWS; row++) {
      assertArrayEquals(written.decodeRow(row), read.decodeRow(row), 0f,
          "a read matrix must decode exactly like the written one");
    }
    // Writing the read matrix reproduces the file, so the format loses nothing.
    final Path rewritten = directory.resolve("rewritten.bin");
    read.write(rewritten);
    assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(rewritten));
  }

  @Test
  void testQuantizedFileIsSmallerThanTheFloatMatrix(@TempDir Path directory)
      throws IOException {
    final float[] matrix = testMatrix();
    final Path file = directory.resolve("matrix.bin");
    QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 4, SEED).write(file);
    final long floatBytes = (long) ROWS * DIMENSION * Float.BYTES;
    // 4 bits over the padded dimension (512 for 300) plus one scale per row and the header:
    // still far under half the float size; at equal dimensions the ratio approaches 8x.
    assertTrue(Files.size(file) < floatBytes / 2,
        "the 4-bit file (" + Files.size(file) + " bytes) must be well under half the float "
            + "matrix (" + floatBytes + " bytes)");
  }

  @Test
  void testPoolingWeightsRoundTripThroughTheFile(@TempDir Path directory) throws IOException {
    final float[] matrix = testMatrix();
    final float[] weights = new float[ROWS];
    for (int row = 0; row < ROWS; row++) {
      weights[row] = 0.5f + row / 100f;
    }
    final QuantizedEmbeddingMatrix withWeights =
        QuantizedEmbeddingMatrix.quantize(matrix, ROWS, DIMENSION, 4, SEED)
            .withPoolingWeights(weights);
    final Path file = directory.resolve("weighted.bin");
    withWeights.write(file);
    final QuantizedEmbeddingMatrix read = QuantizedEmbeddingMatrix.read(file);
    assertArrayEquals(weights, read.poolingWeights(), 0f);
    // Rewriting reproduces the file, so the weights block loses nothing.
    final Path rewritten = directory.resolve("rewritten.bin");
    read.write(rewritten);
    assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(rewritten));
    // Without weights the accessor answers null and the file omits the block.
    final QuantizedEmbeddingMatrix withoutWeights = withWeights.withPoolingWeights(null);
    assertEquals(null, withoutWeights.poolingWeights());
    assertTrue(Files.size(file) > sizeWithoutWeights(directory, withoutWeights),
        "the weights block must add to the file size");
  }

  /**
   * {@return the file size of a matrix written without weights}
   *
   * @param directory The directory to write into.
   * @param matrix    The matrix to write.
   */
  private static long sizeWithoutWeights(Path directory, QuantizedEmbeddingMatrix matrix)
      throws IOException {
    final Path file = directory.resolve("unweighted.bin");
    matrix.write(file);
    return Files.size(file);
  }

  @Test
  void testWithPoolingWeightsValidates() {
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(new float[4 * 8], 4, 8, 2, SEED);
    assertThrows(IllegalArgumentException.class,
        () -> quantized.withPoolingWeights(new float[3]));
    assertThrows(IllegalArgumentException.class,
        () -> quantized.withPoolingWeights(new float[] {1f, 2f, Float.NaN, 4f}));
  }

  @Test
  void testQuantizeValidatesItsArguments() {
    final float[] matrix = new float[2 * 4];
    assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(null, 2, 4, 2, SEED));
    assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(matrix, 0, 4, 2, SEED));
    assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(matrix, 2, 0, 2, SEED));
    assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(matrix, 2, 5, 2, SEED));
    assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(matrix, 2, 4, 1, SEED));
    assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(matrix, 2, 4, 5, SEED));
  }

  @Test
  void testQuantizeRejectsNonFiniteValuesNamingTheirPosition() {
    final float[] matrix = new float[2 * 4];
    matrix[5] = Float.NaN;
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> QuantizedEmbeddingMatrix.quantize(matrix, 2, 4, 2, SEED));
    assertTrue(e.getMessage().contains("Row 1"), e.getMessage());
    assertTrue(e.getMessage().contains("dimension 1"), e.getMessage());
  }

  @Test
  void testRotatedSpaceAccessorsValidate() {
    final QuantizedEmbeddingMatrix quantized =
        QuantizedEmbeddingMatrix.quantize(new float[4 * 8], 4, 8, 2, SEED);
    assertThrows(IllegalArgumentException.class, () -> quantized.decodeRow(-1));
    assertThrows(IllegalArgumentException.class, () -> quantized.decodeRow(4));
    assertThrows(IllegalArgumentException.class, () -> quantized.rotate(null));
    assertThrows(IllegalArgumentException.class, () -> quantized.rotate(new float[7]));
    assertThrows(IllegalArgumentException.class, () -> quantized.toOriginal(new float[7]));
    assertThrows(IllegalArgumentException.class,
        () -> quantized.addRowRotated(0, 1f, new float[7]));
    assertThrows(IllegalArgumentException.class,
        () -> quantized.dotRotated(0, new float[7]));
  }

  @Test
  void testReadRejectsForeignAndTruncatedFiles(@TempDir Path directory) throws IOException {
    final Path foreign = directory.resolve("foreign.bin");
    Files.write(foreign, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
    assertThrows(IllegalArgumentException.class, () -> QuantizedEmbeddingMatrix.read(foreign));

    final Path file = directory.resolve("matrix.bin");
    QuantizedEmbeddingMatrix.quantize(testMatrix(), ROWS, DIMENSION, 2, SEED).write(file);
    final byte[] full = Files.readAllBytes(file);
    final Path truncated = directory.resolve("truncated.bin");
    Files.write(truncated, Arrays.copyOf(full, full.length - 10));
    assertThrows(IOException.class, () -> QuantizedEmbeddingMatrix.read(truncated));

    final Path trailing = directory.resolve("trailing.bin");
    final byte[] extra = Arrays.copyOf(full, full.length + 1);
    Files.write(trailing, extra);
    assertThrows(IllegalArgumentException.class, () -> QuantizedEmbeddingMatrix.read(trailing));
  }

  /**
   * {@return the cosine between a matrix row and a decoded vector}
   *
   * @param matrix  The flat row-major matrix.
   * @param base    The row's first index.
   * @param decoded The decoded row.
   */
  private static double cosine(float[] matrix, int base, float[] decoded) {
    double dot = 0;
    double normASquared = 0;
    double normBSquared = 0;
    for (int d = 0; d < decoded.length; d++) {
      dot += (double) matrix[base + d] * decoded[d];
      normASquared += (double) matrix[base + d] * matrix[base + d];
      normBSquared += (double) decoded[d] * decoded[d];
    }
    return dot / (Math.sqrt(normASquared) * Math.sqrt(normBSquared));
  }
}
