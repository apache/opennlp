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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quantizer contract: the derived grids match the published Lloyd-Max tables for the
 * Gaussian, encoding picks the nearest level, grids are symmetric, and a grid read back from a
 * file round-trips through {@code fromLevels}.
 */
class GaussianQuantizerTest {

  @Test
  void testGridsMatchThePublishedLloydMaxTables() {
    // Reference values from Max, "Quantizing for minimum distortion", IRE Transactions on
    // Information Theory 6(1), 1960, table for the standard normal: the positive levels of the
    // symmetric optimal quantizer. The derivation here discretizes the density, so agreement is
    // to the published tables' precision, not bit-exact.
    assertPositiveLevels(GaussianQuantizer.forBits(2), 0.4528, 1.5104);
    assertPositiveLevels(GaussianQuantizer.forBits(3), 0.2451, 0.7560, 1.3439, 2.1520);
    assertPositiveLevels(GaussianQuantizer.forBits(4),
        0.1284, 0.3881, 0.6568, 0.9424, 1.2562, 1.6181, 2.0690, 2.7326);
  }

  /**
   * Asserts the upper half of a symmetric grid, and by symmetry the lower half.
   *
   * @param quantizer The quantizer under test.
   * @param expected  The published positive levels, ascending.
   */
  private static void assertPositiveLevels(GaussianQuantizer quantizer, double... expected) {
    final int half = quantizer.levelCount() / 2;
    assertEquals(expected.length, half);
    for (int i = 0; i < half; i++) {
      assertEquals(expected[i], quantizer.level(half + i), 2e-3,
          "positive level " + i + " must match the published Lloyd-Max table");
      assertEquals(-expected[i], quantizer.level(half - 1 - i), 2e-3,
          "the grid must be symmetric");
    }
  }

  @Test
  void testEncodePicksTheNearestLevel() {
    final Random random = new Random(42);
    for (int bits = GaussianQuantizer.MIN_BITS; bits <= GaussianQuantizer.MAX_BITS; bits++) {
      final GaussianQuantizer quantizer = GaussianQuantizer.forBits(bits);
      for (int trial = 0; trial < 10_000; trial++) {
        final float value = (float) (random.nextGaussian() * 2);
        final int code = quantizer.encode(value);
        final double encodedDistance = Math.abs(value - quantizer.level(code));
        for (int other = 0; other < quantizer.levelCount(); other++) {
          assertTrue(encodedDistance <= Math.abs(value - quantizer.level(other)) + 1e-6,
              "encode(" + value + ") chose level " + code + " but level " + other
                  + " is nearer");
        }
      }
    }
  }

  @Test
  void testEncodeCoversTheFullCodeRange() {
    final GaussianQuantizer quantizer = GaussianQuantizer.forBits(2);
    assertEquals(0, quantizer.encode(-10f));
    assertEquals(quantizer.levelCount() - 1, quantizer.encode(10f));
  }

  @Test
  void testFromLevelsRoundTripsAGrid() {
    final GaussianQuantizer original = GaussianQuantizer.forBits(3);
    final GaussianQuantizer restored = GaussianQuantizer.fromLevels(original.levels());
    assertEquals(original.levelCount(), restored.levelCount());
    for (int code = 0; code < original.levelCount(); code++) {
      assertEquals(original.level(code), restored.level(code), 0f);
    }
    final Random random = new Random(7);
    for (int trial = 0; trial < 1_000; trial++) {
      final float value = (float) (random.nextGaussian() * 2);
      assertEquals(original.encode(value), restored.encode(value),
          "a restored grid must encode exactly like its source");
    }
  }

  @Test
  void testFromLevelsRejectsMalformedGrids() {
    assertThrows(IllegalArgumentException.class, () -> GaussianQuantizer.fromLevels(null));
    assertThrows(IllegalArgumentException.class,
        () -> GaussianQuantizer.fromLevels(new float[] {1f, 2f, 3f}));
    assertThrows(IllegalArgumentException.class,
        () -> GaussianQuantizer.fromLevels(new float[] {-1f, -1f, 1f, 2f}));
    assertThrows(IllegalArgumentException.class,
        () -> GaussianQuantizer.fromLevels(new float[] {-1f, Float.NaN, 1f, 2f}));
    assertThrows(IllegalArgumentException.class,
        () -> GaussianQuantizer.fromLevels(new float[] {1f, 2f}));
  }

  @Test
  void testUnsupportedBitWidthsFailLoud() {
    assertThrows(IllegalArgumentException.class, () -> GaussianQuantizer.forBits(1));
    assertThrows(IllegalArgumentException.class, () -> GaussianQuantizer.forBits(5));
    assertThrows(IllegalArgumentException.class, () -> GaussianQuantizer.forBits(0));
  }
}
