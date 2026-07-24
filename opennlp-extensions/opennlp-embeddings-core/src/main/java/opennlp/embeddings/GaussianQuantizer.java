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

/**
 * An optimal scalar quantizer for standard-normal values: {@code 2^bits} representation levels
 * minimizing the mean squared error over {@code N(0,1)}, with encoding by nearest level. The
 * coordinates of a {@link HadamardRotation rotated} unit vector, scaled by the square root of the
 * padded dimension, follow this distribution closely, which is what makes one fixed grid
 * near-optimal for every coordinate of every vector (Zandieh et al., <i>TurboQuant: Online Vector
 * Quantization with Near-optimal Distortion Rate</i>, arXiv:2504.19874).
 *
 * <p>The levels are the classic Lloyd-Max quantizer of the Gaussian (Max, <i>Quantizing for
 * minimum distortion</i>, IRE Transactions on Information Theory, 1960), computed here by Lloyd
 * iteration over a fine discretization of the density rather than copied from published tables,
 * so the derivation is in this file and reproducible. Computed grids are cached per bit width.
 * Encoding compares against the midpoints between adjacent levels, which is exactly the
 * nearest-level rule for a sorted grid.</p>
 *
 * <p>A quantized file stores its grid, and reading rebuilds the quantizer from the stored levels
 * through {@link #fromLevels(float[])}, so decoding never depends on this derivation matching the
 * one that encoded the file.</p>
 *
 * <p>Instances are immutable and safe for concurrent use.</p>
 */
final class GaussianQuantizer {

  /** The smallest supported bit width. */
  static final int MIN_BITS = 2;

  /** The largest supported bit width. */
  static final int MAX_BITS = 4;

  // The density is discretized on [-RANGE, RANGE]; beyond eight standard deviations the
  // remaining mass (~1e-15) is far below the iteration tolerance.
  private static final double LLOYD_RANGE = 8.0;
  private static final int LLOYD_SAMPLES = 200_001;
  private static final double LLOYD_TOLERANCE = 1e-10;
  private static final int LLOYD_MAX_ITERATIONS = 1_000;

  private static final GaussianQuantizer[] CACHE = new GaussianQuantizer[MAX_BITS + 1];

  private final float[] levels;
  // Midpoints between adjacent levels: level i is nearest exactly when the value lies in
  // (thresholds[i-1], thresholds[i]], with the outermost intervals unbounded.
  private final float[] thresholds;

  /**
   * Holds a validated grid; callers reach this through {@link #forBits(int)} or
   * {@link #fromLevels(float[])}.
   *
   * @param levels The representation levels, ascending.
   */
  private GaussianQuantizer(float[] levels) {
    this.levels = levels;
    this.thresholds = new float[levels.length - 1];
    for (int i = 0; i < thresholds.length; i++) {
      thresholds[i] = (levels[i] + levels[i + 1]) / 2f;
    }
  }

  /**
   * {@return the quantizer for a bit width, computed once and cached}
   *
   * @param bits The bit width. Must be between {@link #MIN_BITS} and {@link #MAX_BITS}.
   * @throws IllegalArgumentException Thrown if {@code bits} is outside the supported range.
   */
  static GaussianQuantizer forBits(int bits) {
    requireSupportedBits(bits);
    synchronized (CACHE) {
      if (CACHE[bits] == null) {
        CACHE[bits] = new GaussianQuantizer(lloydMaxLevels(1 << bits));
      }
      return CACHE[bits];
    }
  }

  /**
   * {@return a quantizer over a stored grid, as read back from a quantized file}
   *
   * @param levels The representation levels, strictly ascending and finite, of a power-of-two
   *               length between {@code 2^MIN_BITS} and {@code 2^MAX_BITS}. The array is copied.
   * @throws IllegalArgumentException Thrown if {@code levels} is {@code null}, of an unsupported
   *     length, not strictly ascending, or not finite.
   */
  static GaussianQuantizer fromLevels(float[] levels) {
    if (levels == null) {
      throw new IllegalArgumentException("Levels must not be null");
    }
    if (levels.length != Integer.highestOneBit(levels.length)
        || levels.length < 1 << MIN_BITS || levels.length > 1 << MAX_BITS) {
      throw new IllegalArgumentException("Levels must have a power-of-two length between "
          + (1 << MIN_BITS) + " and " + (1 << MAX_BITS) + ", got " + levels.length);
    }
    for (int i = 0; i < levels.length; i++) {
      if (!Float.isFinite(levels[i])) {
        throw new IllegalArgumentException("Level " + i + " is not finite: " + levels[i]);
      }
      if (i > 0 && levels[i] <= levels[i - 1]) {
        throw new IllegalArgumentException("Levels must be strictly ascending, but level "
            + i + " (" + levels[i] + ") is not above level " + (i - 1)
            + " (" + levels[i - 1] + ")");
      }
    }
    return new GaussianQuantizer(Arrays.copyOf(levels, levels.length));
  }

  /**
   * Requires a bit width within the supported range.
   *
   * @param bits The bit width to check.
   * @throws IllegalArgumentException Thrown if {@code bits} is outside the supported range.
   */
  static void requireSupportedBits(int bits) {
    if (bits < MIN_BITS || bits > MAX_BITS) {
      throw new IllegalArgumentException("Bits must be between " + MIN_BITS + " and "
          + MAX_BITS + ", got " + bits);
    }
  }

  /** {@return the number of representation levels} */
  int levelCount() {
    return levels.length;
  }

  /**
   * {@return the representation level of a code}
   *
   * @param code The code, between 0 and {@code levelCount() - 1}.
   */
  float level(int code) {
    return levels[code];
  }

  /** {@return a copy of the representation levels, ascending} */
  float[] levels() {
    return Arrays.copyOf(levels, levels.length);
  }

  /**
   * {@return the code of the representation level nearest a value} Ties at a midpoint take the
   * lower level, a fixed convention so encoding is deterministic.
   *
   * @param value The value to encode.
   */
  int encode(float value) {
    int low = 0;
    int high = thresholds.length;
    while (low < high) {
      final int middle = (low + high) >>> 1;
      if (value <= thresholds[middle]) {
        high = middle;
      } else {
        low = middle + 1;
      }
    }
    return low;
  }

  /**
   * {@return the Lloyd-Max representation levels for the standard normal} Lloyd iteration over a
   * fine discretization of the density: assign each sample to its nearest level, move each level
   * to the probability-weighted mean of its samples, repeat to a fixed point. The discretization,
   * tolerance, and iteration cap are constants of this file, so the result is deterministic.
   *
   * @param levelCount The number of levels, a power of two.
   */
  private static float[] lloydMaxLevels(int levelCount) {
    final double step = 2 * LLOYD_RANGE / (LLOYD_SAMPLES - 1);
    final double[] samples = new double[LLOYD_SAMPLES];
    final double[] weights = new double[LLOYD_SAMPLES];
    for (int i = 0; i < LLOYD_SAMPLES; i++) {
      samples[i] = -LLOYD_RANGE + i * step;
      weights[i] = Math.exp(-samples[i] * samples[i] / 2);
    }
    // Initial levels: evenly spaced over the central mass; Lloyd converges to the optimum for
    // the log-concave Gaussian regardless of the starting spread.
    final double[] levels = new double[levelCount];
    for (int i = 0; i < levelCount; i++) {
      levels[i] = -3.0 + 6.0 * (i + 0.5) / levelCount;
    }
    final double[] weightSums = new double[levelCount];
    final double[] weightedValueSums = new double[levelCount];
    for (int iteration = 0; iteration < LLOYD_MAX_ITERATIONS; iteration++) {
      Arrays.fill(weightSums, 0);
      Arrays.fill(weightedValueSums, 0);
      int level = 0;
      for (int i = 0; i < LLOYD_SAMPLES; i++) {
        while (level < levelCount - 1
            && samples[i] > (levels[level] + levels[level + 1]) / 2) {
          level++;
        }
        weightSums[level] += weights[i];
        weightedValueSums[level] += weights[i] * samples[i];
      }
      double largestMove = 0;
      for (int i = 0; i < levelCount; i++) {
        if (weightSums[i] > 0) {
          final double moved = weightedValueSums[i] / weightSums[i];
          largestMove = Math.max(largestMove, Math.abs(moved - levels[i]));
          levels[i] = moved;
        }
      }
      if (largestMove < LLOYD_TOLERANCE) {
        break;
      }
    }
    final float[] result = new float[levelCount];
    for (int i = 0; i < levelCount; i++) {
      result[i] = (float) levels[i];
    }
    return result;
  }
}
