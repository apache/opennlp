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
import java.util.HexFormat;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests fixed ONQ2 files defined independently of the production writer.
 *
 * <p>Each file contains a 3-by-3 matrix padded to dimension 4. Grid levels are
 * ascending odd integers, with scales [0.5, 1, 0]. Initial codes are [0,1,2,3],
 * [0,2,5,7], and [0,5,10,15] for widths 2, 3, and 4. Reversing those codes at
 * scale 1 gives vector 1; scale 0 gives vector 2. Expected vectors use direct
 * multiplication by the normalized 4-by-4 Hadamard matrix and seed signs.</p>
 *
 * <p>Header fields, grid levels, scales, norms, and optional pooling weights
 * use big-endian encoding. Packed codes start at the low bits of each byte.
 * The 3-bit fixture tests codes spanning byte boundaries. The stored norms
 * are sqrt(5), sqrt(29), and sqrt(125), respectively, multiplied by [1,2,0].</p>
 */
class QuantizedMatrixCompatibilityTest {

  private static final String ONQ2_BITS_2 =
      "4f4e5132000000030000000300000002000000000000001100000004"
          + "c0400000bf8000003f80000040400000"
          + "3fe00000000000003ff00000000000000000000000000000"
          + "4001e3779b97f4a84011e3779b97f4a80000000000000000"
          + "00e41b00";

  private static final String ONQ2_BITS_3 =
      "4f4e5132000000030000000300000003ffffffffffffffff00000008"
          + "c0e00000c0a00000c0400000bf8000003f8000004040000040a0000040e00000"
          + "3fe00000000000003ff00000000000000000000000000000"
          + "40158a68a4a8d9f340258a68a4a8d9f30000000000000000"
          + "013fc00000bf00000000000000500faf000000";

  private static final String ONQ2_BITS_4 =
      "4f4e5132000000030000000300000004800000000000000000000010"
          + "c1700000c1500000c1300000c1100000c0e00000c0a00000c0400000bf800000"
          + "3f8000004040000040a0000040e0000041100000413000004150000041700000"
          + "3fe00000000000003ff00000000000000000000000000000"
          + "40265c55827df1d240365c55827df1d20000000000000000"
          + "013fc00000bf0000000000000050faaf050000";

  /**
   * Loads known coordinates and checks scoring, pooling, and file output.
   *
   * @param bits The quantization width.
   * @param seed The stored rotation seed.
   * @param y The expected coordinate at index 1.
   * @param z The expected coordinate at index 2.
   * @param squaredNorm The expected squared norm of vector 0.
   * @param dir The temporary directory.
   * @throws IOException Thrown if file access fails.
   */
  @ParameterizedTest
  @CsvSource({"2,17,1,-2,5", "3,-1,-2,-5,29", "4,-9223372036854775808,5,-10,125"})
  void testKnownVersion2Vectors(int bits, long seed, float y, float z,
                              int squaredNorm, @TempDir Path dir) throws IOException {
    final byte[] bytes = fixture(bits);
    final Path file = dir.resolve("model.quantized");
    Files.write(file, bytes);
    final QuantizedEmbeddingMatrix matrix = QuantizedEmbeddingMatrix.read(file);

    assertEquals(3, matrix.rowCount());
    assertEquals(3, matrix.dimension());
    assertEquals(4, matrix.paddedDimension());
    assertEquals(bits, matrix.bits());
    assertEquals(seed, matrix.seed());
    assertArrayEquals(new float[] {0f, y, z}, matrix.decodeRow(0), 0f);
    assertArrayEquals(new float[] {0f, -2 * y, -2 * z}, matrix.decodeRow(1), 0f);
    assertArrayEquals(new float[3], matrix.decodeRow(2), 0f);
    assertEquals(Math.sqrt(squaredNorm), matrix.rowNorm(0), 1e-14);
    assertEquals(2 * Math.sqrt(squaredNorm), matrix.rowNorm(1), 1e-14);
    assertEquals(0.0, matrix.rowNorm(2));

    final double[] query = matrix.rotate(new float[] {1f, 2f, 3f});
    final double dot = 2 * y + 3 * z;
    assertEquals(dot, matrix.dotRotated(0, query), 0.0);
    assertEquals(-2 * dot, matrix.dotRotated(1, query), 0.0);
    assertEquals(0.0, matrix.dotRotated(2, query), 0.0);

    final double[] sum = new double[matrix.paddedDimension()];
    matrix.addRowRotated(0, 1.5f, sum);
    matrix.addRowRotated(1, -0.5f, sum);
    assertArrayEquals(new double[] {0, 2.5 * y, 2.5 * z}, matrix.toOriginal(sum), 0.0);
    if (bits == 2) {
      assertNull(matrix.poolingWeights());
    } else {
      assertArrayEquals(new float[] {1.5f, -0.5f, 0f}, matrix.poolingWeights(), 0f);
    }

    final Path written = dir.resolve("written.quantized");
    matrix.write(written);
    assertArrayEquals(bytes, Files.readAllBytes(written));
  }

  /**
   * Rejects truncated fixtures at all byte offsets with a checked exception.
   *
   * @param bits The quantization width.
   * @param dir The temporary directory.
   * @throws IOException Thrown if writing fails.
   */
  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4})
  void testTruncatedVersion2Fixture(int bits, @TempDir Path dir) throws IOException {
    final byte[] bytes = fixture(bits);
    final Path file = dir.resolve("truncated.quantized");
    for (int length = 0; length < bytes.length; length++) {
      Files.write(file, Arrays.copyOf(bytes, length));
      assertThrows(IOException.class, () -> QuantizedEmbeddingMatrix.read(file),
          "file length " + length);
    }
  }

  /**
   * Rejects content appended to a complete fixture.
   *
   * @param bits The quantization width.
   * @param dir The temporary directory.
   * @throws IOException Thrown if writing fails.
   */
  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4})
  void testVersion2FixtureWithTrailingByte(int bits, @TempDir Path dir) throws IOException {
    final byte[] bytes = fixture(bits);
    final Path file = dir.resolve("trailing.quantized");
    Files.write(file, Arrays.copyOf(bytes, bytes.length + 1));

    assertThrows(InvalidFormatException.class, () -> QuantizedEmbeddingMatrix.read(file));
  }

  /**
   * {@return the fixed file bytes for a quantization width}
   *
   * @param bits The width: 2, 3, or 4.
   * @throws IllegalArgumentException Thrown for an unsupported width.
   */
  private byte[] fixture(int bits) {
    return HexFormat.of().parseHex(switch (bits) {
      case 2 -> ONQ2_BITS_2;
      case 3 -> ONQ2_BITS_3;
      case 4 -> ONQ2_BITS_4;
      default -> throw new IllegalArgumentException("Unsupported fixture width: " + bits);
    });
  }
}
