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

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the randomized Hadamard rotation and its inverse.
 */
class HadamardRotationTest {

  @Test
  void testPaddedDimensionIsTheNextPowerOfTwo() {
    assertEquals(1, HadamardRotation.paddedDimension(1));
    assertEquals(2, HadamardRotation.paddedDimension(2));
    assertEquals(4, HadamardRotation.paddedDimension(3));
    assertEquals(256, HadamardRotation.paddedDimension(256));
    assertEquals(512, HadamardRotation.paddedDimension(300));
    assertEquals(1024, HadamardRotation.paddedDimension(1024));
    assertThrows(IllegalArgumentException.class, () -> HadamardRotation.paddedDimension(0));
    assertThrows(IllegalArgumentException.class, () -> HadamardRotation.paddedDimension(-5));
  }

  @Test
  void testRotationPreservesNormsAndDotProducts() {
    final Random random = new Random(42);
    final HadamardRotation rotation = new HadamardRotation(300, 7L);
    final double[] a = randomPadded(rotation, random);
    final double[] b = randomPadded(rotation, random);
    final double normBefore = norm(a);
    final double dotBefore = dot(a, b);
    rotation.rotate(a);
    rotation.rotate(b);
    assertEquals(normBefore, norm(a), 1e-3 * normBefore,
        "an orthonormal transform preserves norms");
    assertEquals(dotBefore, dot(a, b), 1e-3 * (1 + Math.abs(dotBefore)),
        "an orthonormal transform preserves dot products");
  }

  @Test
  void testInverseRestoresTheInput() {
    final Random random = new Random(43);
    final HadamardRotation rotation = new HadamardRotation(256, 99L);
    final double[] vector = randomPadded(rotation, random);
    final double[] original = vector.clone();
    rotation.rotate(vector);
    rotation.inverse(vector);
    assertArrayEquals(original, vector, 1e-12);
  }

  @Test
  void testSameSeedSameRotationDifferentSeedDifferentRotation() {
    final Random random = new Random(44);
    final double[] input = randomPadded(new HadamardRotation(64, 5L), random);
    final double[] first = input.clone();
    final double[] second = input.clone();
    final double[] other = input.clone();
    new HadamardRotation(64, 5L).rotate(first);
    new HadamardRotation(64, 5L).rotate(second);
    new HadamardRotation(64, 6L).rotate(other);
    assertArrayEquals(first, second, 0f, "the same seed must give equal rotations");
    assertFalse(Arrays.equals(first, other),
        "different seeds must give different rotations");
  }

  @Test
  void testEnergySpreadsAcrossCoordinates() {
    // A one-hot vector concentrates all its energy in one coordinate; after rotation every
    // coordinate must hold a share, which is the property the per-coordinate quantizer needs.
    final HadamardRotation rotation = new HadamardRotation(128, 11L);
    final double[] oneHot = new double[rotation.paddedDimension()];
    oneHot[3] = 1.0;
    rotation.rotate(oneHot);
    final double expectedMagnitude = 1.0 / Math.sqrt(rotation.paddedDimension());
    for (final double value : oneHot) {
      assertEquals(expectedMagnitude, Math.abs(value), 1e-6,
          "a rotated one-hot vector has equal magnitude everywhere");
    }
  }

  @Test
  void testRejectsWrongLengthAndNull() {
    final HadamardRotation rotation = new HadamardRotation(300, 1L);
    assertEquals(512, rotation.paddedDimension());
    assertThrows(IllegalArgumentException.class, () -> rotation.rotate(null));
    assertThrows(IllegalArgumentException.class, () -> rotation.rotate(new double[300]));
    assertThrows(IllegalArgumentException.class, () -> rotation.inverse(new double[511]));
    assertThrows(IllegalArgumentException.class, () -> new HadamardRotation(0, 1L));
  }

  @Test
  void testDimensionOneIsTheIdentityUpToSign() {
    final HadamardRotation rotation = new HadamardRotation(1, 123L);
    final double[] vector = new double[] {2.5};
    rotation.rotate(vector);
    assertEquals(2.5, Math.abs(vector[0]), 1e-12);
    rotation.inverse(vector);
    assertEquals(2.5, vector[0], 1e-12);
  }

  private double[] randomPadded(HadamardRotation rotation, Random random) {
    final double[] vector = new double[rotation.paddedDimension()];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = random.nextGaussian();
    }
    return vector;
  }

  private double norm(double[] vector) {
    return Math.sqrt(dot(vector, vector));
  }

  private double dot(double[] a, double[] b) {
    double dot = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
    }
    return dot;
  }
}
