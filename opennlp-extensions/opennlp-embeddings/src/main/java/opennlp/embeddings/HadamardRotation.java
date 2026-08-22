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

/**
 * A seeded randomized Hadamard rotation: a deterministic random sign flip per coordinate followed
 * by the normalized fast Walsh-Hadamard transform. Rotating a vector this way spreads its energy
 * evenly across coordinates, so each coordinate of a rotated unit vector is approximately
 * Gaussian with variance {@code 1/paddedDimension} and nearly independent of the others, which is
 * the property {@link GaussianQuantizer}'s per-coordinate grids rely on.
 *
 * <p>The transform is orthonormal, so it preserves norms and inner products exactly (up to float
 * rounding): two vectors rotated with the same instance have the same dot product as the
 * originals, which lets similarity math stay in rotated space and never pay for the inverse.
 * Writing {@code S} for the sign flip and {@code H} for the normalized Walsh-Hadamard matrix
 * (which is its own inverse), the rotation is {@code H·S} and its inverse is {@code S·H}: the
 * same two operations applied in the opposite order, so no second table is needed.</p>
 *
 * <p>The Walsh-Hadamard transform needs a power-of-two length, so vectors are padded with zeros
 * from their original dimension up to {@link #paddedDimension(int)}. The sign flips derive from
 * the seed through an in-file
 * <a href="https://prng.di.unimi.it/splitmix64.c">splitmix64</a> step, not through a JDK
 * generator, so the same seed produces the same rotation on every JVM and release; the seed is
 * stored in the quantized file and the rotation is rebuilt from it on load.</p>
 *
 * <p>Instances are immutable and safe for concurrent use.</p>
 */
final class HadamardRotation {

  private static final long SPLITMIX64_GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

  private final int paddedDimension;
  // True where the coordinate is negated before (rotate) or after (inverse) the transform.
  private final boolean[] flip;
  private final float inverseSquareRoot;

  /**
   * Creates the rotation for vectors of the given original dimension.
   *
   * @param dimension The original vector dimension. Must be at least 1.
   * @param seed      The seed the sign flips derive from.
   * @throws IllegalArgumentException Thrown if {@code dimension} is less than 1.
   */
  HadamardRotation(int dimension, long seed) {
    if (dimension < 1) {
      throw new IllegalArgumentException("Dimension must be at least 1, got " + dimension);
    }
    this.paddedDimension = paddedDimension(dimension);
    this.flip = new boolean[paddedDimension];
    long state = seed;
    long bits = 0;
    for (int i = 0; i < paddedDimension; i++) {
      if ((i & 63) == 0) {
        state += SPLITMIX64_GOLDEN_GAMMA;
        bits = splitmix64(state);
      }
      flip[i] = (bits & 1L) != 0;
      bits >>>= 1;
    }
    this.inverseSquareRoot = (float) (1.0 / Math.sqrt(paddedDimension));
  }

  /**
   * {@return the power-of-two length vectors are padded to before the transform}
   *
   * @param dimension The original vector dimension. Must be at least 1.
   * @throws IllegalArgumentException Thrown if {@code dimension} is less than 1.
   */
  static int paddedDimension(int dimension) {
    if (dimension < 1) {
      throw new IllegalArgumentException("Dimension must be at least 1, got " + dimension);
    }
    if (dimension > 1 << 30) {
      throw new IllegalArgumentException("Dimension must be at most " + (1 << 30)
          + " so the padded length stays an int power of two, got " + dimension);
    }
    final int highestOneBit = Integer.highestOneBit(dimension);
    return highestOneBit == dimension ? dimension : highestOneBit << 1;
  }

  /** {@return the power-of-two length this instance transforms} */
  int paddedDimension() {
    return paddedDimension;
  }

  /**
   * Rotates a vector in place: sign flips, then the normalized Walsh-Hadamard transform.
   *
   * @param vector The vector to rotate. Must not be {@code null} and must have length
   *               {@link #paddedDimension()}.
   * @throws IllegalArgumentException Thrown if {@code vector} is {@code null} or has the wrong
   *     length.
   */
  void rotate(float[] vector) {
    requirePaddedLength(vector);
    for (int i = 0; i < paddedDimension; i++) {
      if (flip[i]) {
        vector[i] = -vector[i];
      }
    }
    walshHadamard(vector);
  }

  /**
   * Applies the inverse rotation in place: the normalized Walsh-Hadamard transform, then the
   * sign flips.
   *
   * @param vector The rotated vector. Must not be {@code null} and must have length
   *               {@link #paddedDimension()}.
   * @throws IllegalArgumentException Thrown if {@code vector} is {@code null} or has the wrong
   *     length.
   */
  void inverse(float[] vector) {
    requirePaddedLength(vector);
    walshHadamard(vector);
    for (int i = 0; i < paddedDimension; i++) {
      if (flip[i]) {
        vector[i] = -vector[i];
      }
    }
  }

  /**
   * Requires the vector to be non-null and of the padded length.
   *
   * @param vector The vector to check.
   * @throws IllegalArgumentException Thrown if {@code vector} is {@code null} or has the wrong
   *     length.
   */
  private void requirePaddedLength(float[] vector) {
    if (vector == null) {
      throw new IllegalArgumentException("Vector must not be null");
    }
    if (vector.length != paddedDimension) {
      throw new IllegalArgumentException("Vector has length " + vector.length
          + " but this rotation transforms length " + paddedDimension);
    }
  }

  /**
   * The in-place normalized fast Walsh-Hadamard transform, {@code O(n log n)} butterflies
   * followed by a {@code 1/sqrt(n)} scale so the transform is orthonormal and self-inverse.
   *
   * @param vector The vector to transform, of the padded length.
   */
  private void walshHadamard(float[] vector) {
    for (int half = 1; half < paddedDimension; half <<= 1) {
      for (int block = 0; block < paddedDimension; block += half << 1) {
        for (int i = block; i < block + half; i++) {
          final float a = vector[i];
          final float b = vector[i + half];
          vector[i] = a + b;
          vector[i + half] = a - b;
        }
      }
    }
    for (int i = 0; i < paddedDimension; i++) {
      vector[i] *= inverseSquareRoot;
    }
  }

  /**
   * {@return the splitmix64 mix of a state word} The finalizer of the splitmix64 generator,
   * reproduced here so the bit stream is fixed by this file rather than by a JDK class.
   *
   * @param state The state word to mix.
   */
  private static long splitmix64(long state) {
    long z = state;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
