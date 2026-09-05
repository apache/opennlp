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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * An embedding matrix quantized to {@code 2}-{@code 4} bits per dimension using an MSE-oriented
 * variant of TurboQuant (Zandieh, Daliri, Hadian, Mirrokni,
 * <a href="https://arxiv.org/abs/2504.19874"><i>TurboQuant: Online Vector Quantization with
 * Near-optimal Distortion Rate</i></a>). Each row is transformed by a seeded
 * {@link HadamardRotation}, and each rotated coordinate is encoded with the
 * {@link GaussianQuantizer} grid for the selected bit width. A row decodes to a per-row scale
 * times its grid levels; the scale is fitted by least squares.
 *
 * <p>The cited algorithm uses a dense random rotation and a dimension-specific coordinate
 * distribution in its MSE stage. This implementation substitutes the fast Hadamard transform and
 * a standard-normal grid. It also omits the paper's residual QJL stage, so it does not claim the
 * paper's unbiased inner-product estimator.</p>
 *
 * <p>The storage is {@code bits} per <em>padded</em> dimension plus two doubles per row for the
 * fitted scale and decoded norm. The rotation pads each row to the next power of two.</p>
 *
 * <p>Rows live in <b>rotated space</b>. The rotation is
 * orthonormal, so dot products and norms of rotated vectors equal those of the originals, and
 * pooling commutes with it because rotation is linear. A consumer embeds text by summing rows
 * with {@link #addRowRotated(int, float, double[])} and applying {@link #toOriginal(double[])}
 * once per text, not once per row; a similarity scan rotates the query once with
 * {@link #rotate(float[])} and scores every row with {@link #dotRotated(int, double[])}, without
 * leaving rotated space. {@link #decodeRow(int)} exists for callers that need one original-space
 * row and for measuring reconstruction quality.</p>
 *
 * <p>The file format stores the grid levels and rotation seed. The reader therefore uses the
 * same decoder parameters that wrote the file.</p>
 *
 * <p>Instances are immutable and safe for concurrent use after construction.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
@ThreadSafe
public final class QuantizedEmbeddingMatrix {

  /** The smallest supported bit width. */
  public static final int MIN_BITS = GaussianQuantizer.MIN_BITS;

  /** The largest supported bit width. */
  public static final int MAX_BITS = GaussianQuantizer.MAX_BITS;

  // "ONQ2": OpenNLP quantized matrix, format 2. Version 2 stores scales and norms as doubles.
  private static final int MAGIC = 0x4F4E5132;

  private final int rowCount;
  private final int dimension;
  private final int paddedDimension;
  private final int bits;
  private final long seed;
  private final int rowBytes;
  private final GaussianQuantizer quantizer;
  private final HadamardRotation rotation;
  // One scale per row: decoded rotated coordinate i of a row is scale * level(code_i).
  private final double[] scales;
  // Packed codes, row-major: row r's code i occupies bits [i*bits, (i+1)*bits) of the row's
  // rowBytes region, little-endian within the region.
  private final byte[] codes;
  // The L2 norm of each decoded original-space row. Quantization noise leaves some energy in
  // the padding coordinates, which truncation drops, so this is computed during quantization
  // time (one inverse rotation per row) and stored in the file rather than recomputed from the
  // codes on load.
  private final double[] decodedNorms;
  // Optional per-row pooling weights stored alongside the matrix, so a quantized file can
  // fully replace a safetensors file that bundled a "weights" tensor; null when absent. The
  // weights are stored as they are, not quantized.
  private final float[] poolingWeights;

  /**
   * Constructs state validated by {@link #quantize} or {@link #read}.
   */
  private QuantizedEmbeddingMatrix(int rowCount, int dimension, int bits, long seed,
                                   GaussianQuantizer quantizer, double[] scales, byte[] codes,
                                   double[] decodedNorms, float[] poolingWeights) {
    this.rowCount = rowCount;
    this.dimension = dimension;
    this.paddedDimension = HadamardRotation.paddedDimension(dimension);
    this.bits = bits;
    this.seed = seed;
    this.rowBytes = rowByteCount(paddedDimension, bits);
    this.quantizer = quantizer;
    this.rotation = new HadamardRotation(dimension, seed);
    this.scales = scales;
    this.codes = codes;
    this.decodedNorms = decodedNorms;
    this.poolingWeights = poolingWeights;
  }

  /**
   * Quantizes a float matrix.
   *
   * @param rowMajor  The matrix, row-major, {@code rowCount * dimension} floats. Must not be
   *                  {@code null} and every value must be finite.
   * @param rowCount  The number of rows. Must be at least 1.
   * @param dimension The row width. Must be at least 1.
   * @param bits      The bit width per (padded) dimension. Must be between {@link #MIN_BITS} and
   *                  {@link #MAX_BITS}.
   * @param seed      The rotation seed. Any value; stored in the file so decoding rebuilds the
   *                  same rotation.
   * @return The quantized matrix.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or out of range, the
   *     array length does not match {@code rowCount * dimension}, or a value is not finite.
   */
  public static QuantizedEmbeddingMatrix quantize(float[] rowMajor, int rowCount, int dimension,
                                                  int bits, long seed) {
    if (rowMajor == null) {
      throw new IllegalArgumentException("RowMajor must not be null");
    }
    if (rowCount < 1) {
      throw new IllegalArgumentException("RowCount must be at least 1, got " + rowCount);
    }
    if (dimension < 1) {
      throw new IllegalArgumentException("Dimension must be at least 1, got " + dimension);
    }
    if (rowMajor.length != (long) rowCount * dimension) {
      throw new IllegalArgumentException("RowMajor has " + rowMajor.length + " floats but "
          + rowCount + " rows of dimension " + dimension + " need "
          + ((long) rowCount * dimension));
    }
    GaussianQuantizer.requireSupportedBits(bits);
    final GaussianQuantizer quantizer = GaussianQuantizer.forBits(bits);
    final HadamardRotation rotation = new HadamardRotation(dimension, seed);
    final int paddedDimension = rotation.paddedDimension();
    final int rowBytes = rowByteCount(paddedDimension, bits);
    requireStorableSize(rowCount, rowBytes);
    final double[] scales = new double[rowCount];
    final byte[] codes = new byte[rowCount * rowBytes];
    final double[] decodedNorms = new double[rowCount];
    final double[] rotated = new double[paddedDimension];
    final double[] decoded = new double[paddedDimension];
    final double squareRootOfPadded = Math.sqrt(paddedDimension);
    for (int row = 0; row < rowCount; row++) {
      final int base = row * dimension;
      double sumOfSquares = 0;
      for (int d = 0; d < dimension; d++) {
        final float value = rowMajor[base + d];
        if (!Float.isFinite(value)) {
          throw new IllegalArgumentException("Row " + row + " has a non-finite value at "
              + "dimension " + d + ": " + value + "; a quantized matrix cannot represent it");
        }
        rotated[d] = value;
        sumOfSquares += (double) value * value;
      }
      Arrays.fill(rotated, dimension, paddedDimension, 0f);
      final double norm = Math.sqrt(sumOfSquares);
      if (norm == 0) {
        // A zero row has no direction; a zero scale decodes it to zero whatever the codes say,
        // and encoding zeros keeps the bytes deterministic.
        scales[row] = 0f;
        final int zeroCode = quantizer.encode(0f);
        for (int i = 0; i < paddedDimension; i++) {
          writeCode(codes, row * rowBytes, bits, i, zeroCode);
        }
        continue;
      }
      rotation.rotate(rotated);
      // Standardized coordinates are near N(0,1); encode each against the grid, then fit the
      // one free scale to the row: the alpha minimizing ||z - alpha*g||^2 is (z.g)/(g.g).
      final double standardize = squareRootOfPadded / norm;
      double gridDot = 0;
      double gridSquares = 0;
      for (int i = 0; i < paddedDimension; i++) {
        final double standardized = rotated[i] * standardize;
        final int code = quantizer.encode((float) standardized);
        writeCode(codes, row * rowBytes, bits, i, code);
        final float level = quantizer.level(code);
        gridDot += (double) standardized * level;
        gridSquares += (double) level * level;
      }
      final double fitted = gridDot > 0 ? gridDot / gridSquares : 1.0;
      scales[row] = norm / squareRootOfPadded * fitted;
      // The decoded original-space norm: quantization noise leaves energy in the padding
      // coordinates and truncation drops it, so the norm is measured on the truncated decode,
      // not on the rotated codes.
      for (int i = 0; i < paddedDimension; i++) {
        decoded[i] = scales[row] * quantizer.level(readCode(codes, row * rowBytes, bits, i));
      }
      rotation.inverse(decoded);
      double largestDecodedMagnitude = 0;
      for (int d = 0; d < dimension; d++) {
        largestDecodedMagnitude = Math.max(largestDecodedMagnitude, Math.abs(decoded[d]));
      }
      double decodedAdjustment = 1.0;
      if (largestDecodedMagnitude > Float.MAX_VALUE) {
        // Quantization can overshoot the finite float range even when every source coordinate is
        // finite. Scale all reconstructed coordinates instead of clipping them individually, so
        // decoding, stored norms, and rotated-space dot products continue to describe one row.
        decodedAdjustment = Float.MAX_VALUE / largestDecodedMagnitude;
        scales[row] *= decodedAdjustment;
      }
      double decodedSumOfSquares = 0;
      for (int d = 0; d < dimension; d++) {
        final float value = finiteFloat(decoded[d] * decodedAdjustment);
        decodedSumOfSquares += (double) value * value;
      }
      decodedNorms[row] = Math.sqrt(decodedSumOfSquares);
    }
    return new QuantizedEmbeddingMatrix(rowCount, dimension, bits, seed, quantizer, scales,
        codes, decodedNorms, null);
  }

  /**
   * {@return a copy of this matrix with per-row pooling weights} The file stores the weights
   * unquantized, so a quantized file can fully replace a safetensors file that bundled
   * a {@code weights} tensor.
   *
   * @param weights One weight per row, or {@code null} when absent. Every weight must be
   *                finite. The array is copied.
   * @return A matrix sharing this one's codes and scales, with the given weights.
   * @throws IllegalArgumentException Thrown if {@code weights} has the wrong length or a
   *     non-finite value.
   */
  public QuantizedEmbeddingMatrix withPoolingWeights(float[] weights) {
    if (weights == null) {
      return new QuantizedEmbeddingMatrix(rowCount, dimension, bits, seed, quantizer, scales,
          codes, decodedNorms, null);
    }
    if (weights.length != rowCount) {
      throw new IllegalArgumentException("Weights has " + weights.length + " values but this "
          + "matrix has " + rowCount + " rows");
    }
    for (int row = 0; row < rowCount; row++) {
      if (!Float.isFinite(weights[row])) {
        throw new IllegalArgumentException("Weight for row " + row + " is not finite: "
            + weights[row]);
      }
    }
    return new QuantizedEmbeddingMatrix(rowCount, dimension, bits, seed, quantizer, scales,
        codes, decodedNorms, Arrays.copyOf(weights, weights.length));
  }

  /**
   * {@return a copy of the per-row pooling weights, or {@code null} when this matrix has
   * none}
   */
  public float[] poolingWeights() {
    return poolingWeights == null ? null : Arrays.copyOf(poolingWeights, poolingWeights.length);
  }

  /**
   * Requires the packed code array to fit in one Java array.
   *
   * @param rowCount The number of rows.
   * @param rowBytes The packed bytes per row.
   * @throws IllegalArgumentException Thrown if the total exceeds what an array can hold.
   */
  private static void requireStorableSize(int rowCount, int rowBytes) {
    if ((long) rowCount * rowBytes > Integer.MAX_VALUE - 8) {
      throw new IllegalArgumentException("The packed codes need " + ((long) rowCount * rowBytes)
          + " bytes, more than one array can hold; split the matrix");
    }
  }

  /**
   * {@return the packed byte count of one row} Computed in long arithmetic and range-checked, so
   * a padded dimension large enough to overflow {@code paddedDimension * bits} as a signed int
   * (which would silently produce a negative or wrapped byte count) is rejected instead.
   *
   * @param paddedDimension The power-of-two padded dimension.
   * @param bits            The bit width per padded dimension.
   * @throws IllegalArgumentException Thrown if the padded bit count exceeds what an {@code int}
   *     can address.
   */
  private static int rowByteCount(int paddedDimension, int bits) {
    final long paddedBits = (long) paddedDimension * bits;
    if (paddedBits > Integer.MAX_VALUE - 7) {
      throw new IllegalArgumentException("A padded dimension of " + paddedDimension + " at "
          + bits + " bits needs " + paddedBits + " bits per row, more than a quantized matrix "
          + "can address; use a smaller dimension");
    }
    return (int) ((paddedBits + 7) / 8);
  }

  /** {@return the number of rows} */
  public int rowCount() {
    return rowCount;
  }

  /** {@return the original row width} */
  public int dimension() {
    return dimension;
  }

  /** {@return the power-of-two width rows are padded to in rotated space} */
  public int paddedDimension() {
    return paddedDimension;
  }

  /** {@return the bit width per padded dimension} */
  public int bits() {
    return bits;
  }

  /** {@return the rotation seed} */
  public long seed() {
    return seed;
  }

  /**
   * Rotates an original-space vector into this matrix's rotated space, padding it first. Rotate
   * a query once, then score rows against it with {@link #dotRotated(int, double[])}.
   *
   * @param vector The original-space vector. Must not be {@code null} and must have length
   *               {@link #dimension()}.
   * @return A new double-precision array of length {@link #paddedDimension()} containing the
   *     rotated vector.
   * @throws IllegalArgumentException Thrown if {@code vector} is {@code null} or has the wrong
   *     length.
   */
  public double[] rotate(float[] vector) {
    if (vector == null) {
      throw new IllegalArgumentException("Vector must not be null");
    }
    if (vector.length != dimension) {
      throw new IllegalArgumentException("Vector has length " + vector.length
          + " but this matrix has dimension " + dimension);
    }
    final double[] padded = new double[paddedDimension];
    for (int d = 0; d < dimension; d++) {
      padded[d] = vector[d];
    }
    rotation.rotate(padded);
    return padded;
  }

  /**
   * Rotates an internal double-precision query without narrowing analogy results to floats.
   *
   * @param query The query, of length {@link #dimension()}. Not modified.
   * @return A new rotated query of length {@link #paddedDimension()}.
   */
  double[] rotateQuery(double[] query) {
    final double[] padded = Arrays.copyOf(query, paddedDimension);
    rotation.rotate(padded);
    return padded;
  }

  /**
   * Maps a rotated-space vector back to original space. Apply this once per pooled result, after
   * accumulating rows with {@link #addRowRotated(int, float, double[])}; rotation is linear, so
   * the sum of rotated rows is the rotation of the summed rows.
   *
   * @param rotated The rotated-space vector. Must not be {@code null} and must have length
   *                {@link #paddedDimension()}. Not modified.
   * @return A new double-precision array of length {@link #dimension()} containing the
   *     original-space vector.
   * @throws IllegalArgumentException Thrown if {@code rotated} is {@code null} or has the wrong
   *     length.
   */
  public double[] toOriginal(double[] rotated) {
    if (rotated == null) {
      throw new IllegalArgumentException("Rotated must not be null");
    }
    if (rotated.length != paddedDimension) {
      throw new IllegalArgumentException("Rotated has length " + rotated.length
          + " but this matrix's padded dimension is " + paddedDimension);
    }
    final double[] copy = rotated.clone();
    rotation.inverse(copy);
    return Arrays.copyOf(copy, dimension);
  }

  /**
   * Adds a decoded row, times a weight, onto a rotated-space accumulator. This is the pooling
   * primitive: decode stays in rotated space and costs one grid lookup per coordinate.
   *
   * @param row    The row to add. Must be between 0 and {@code rowCount() - 1}.
   * @param weight The weight to multiply the row by.
   * @param sum    The accumulator. Must not be {@code null} and must have length
   *               {@link #paddedDimension()}.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range or {@code sum} is
   *     {@code null} or has the wrong length.
   */
  public void addRowRotated(int row, float weight, double[] sum) {
    requireRow(row);
    if (sum == null) {
      throw new IllegalArgumentException("Sum must not be null");
    }
    if (sum.length != paddedDimension) {
      throw new IllegalArgumentException("Sum has length " + sum.length
          + " but this matrix's padded dimension is " + paddedDimension);
    }
    final double scaledWeight = scales[row] * weight;
    final int base = row * rowBytes;
    for (int i = 0; i < paddedDimension; i++) {
      sum[i] += scaledWeight * quantizer.level(readCode(codes, base, bits, i));
    }
  }

  /**
   * The dot product of a decoded row with a rotated-space query. Because the rotation is
   * orthonormal, this equals the original-space dot product of the decoded row with the
   * un-rotated query, within floating-point rounding.
   *
   * @param row          The row to score. Must be between 0 and {@code rowCount() - 1}.
   * @param rotatedQuery The query in rotated space, as returned by {@link #rotate(float[])}.
   *                     Must not be {@code null} and must have length
   *                     {@link #paddedDimension()}.
   * @return The dot product.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range or
   *     {@code rotatedQuery} is {@code null} or has the wrong length.
   */
  public double dotRotated(int row, double[] rotatedQuery) {
    requireRow(row);
    if (rotatedQuery == null) {
      throw new IllegalArgumentException("RotatedQuery must not be null");
    }
    if (rotatedQuery.length != paddedDimension) {
      throw new IllegalArgumentException("RotatedQuery has length " + rotatedQuery.length
          + " but this matrix's padded dimension is " + paddedDimension);
    }
    final int base = row * rowBytes;
    double dot = 0;
    for (int i = 0; i < paddedDimension; i++) {
      dot += rotatedQuery[i] * quantizer.level(readCode(codes, base, bits, i));
    }
    return dot * scales[row];
  }

  /**
   * {@return the L2 norm of the decoded original-space row, for cosine scoring} Computed
   * during quantization and stored in the file: quantization noise leaves some energy in
   * the padding coordinates, which decoding truncates away, so this norm matches
   * {@link #decodeRow(int)}'s result rather than the rotated codes.
   *
   * @param row The row. Must be between 0 and {@code rowCount() - 1}.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range.
   */
  public double rowNorm(int row) {
    requireRow(row);
    return decodedNorms[row];
  }

  /**
   * Decodes one row back to original space. This performs the inverse rotation for a single row;
   * pooling and scanning callers should stay in rotated space instead (see the class comment).
   *
   * @param row The row to decode. Must be between 0 and {@code rowCount() - 1}.
   * @return A new array of length {@link #dimension()} holding the decoded row.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range.
   */
  public float[] decodeRow(int row) {
    requireRow(row);
    final double[] rotated = new double[paddedDimension];
    addRowRotated(row, 1f, rotated);
    return toFloatVector(toOriginal(rotated));
  }

  /**
   * Requires a row index in range.
   *
   * @param row The row index to check.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range.
   */
  private void requireRow(int row) {
    if (row < 0 || row >= rowCount) {
      throw new IllegalArgumentException("Row must be between 0 and " + (rowCount - 1)
          + ", got " + row);
    }
  }

  /**
   * Writes this matrix to a file, deterministically: the same matrix, bit width, and seed
   * produce the same bytes.
   *
   * @param file The file to write. Must not be {@code null}; an existing file is replaced.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}.
   * @throws IOException Thrown if writing fails.
   */
  public void write(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    try (OutputStream out = Files.newOutputStream(file);
         DataOutputStream data = new DataOutputStream(new BufferedOutputStream(out))) {
      data.writeInt(MAGIC);
      data.writeInt(rowCount);
      data.writeInt(dimension);
      data.writeInt(bits);
      data.writeLong(seed);
      final float[] levels = quantizer.levels();
      data.writeInt(levels.length);
      for (final float level : levels) {
        data.writeFloat(level);
      }
      for (final double scale : scales) {
        data.writeDouble(scale);
      }
      for (final double decodedNorm : decodedNorms) {
        data.writeDouble(decodedNorm);
      }
      data.writeBoolean(poolingWeights != null);
      if (poolingWeights != null) {
        for (final float weight : poolingWeights) {
          data.writeFloat(weight);
        }
      }
      data.write(codes);
    }
  }

  /**
   * Reads a matrix written by {@link #write(Path)}. The stored grid and seed rebuild the decoder.
   *
   * @param file The file to read. Must not be {@code null}.
   * @return The quantized matrix.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}.
   * @throws InvalidFormatException Thrown if the content has an unsupported version, sizes that
   *     conflict with the file length, an invalid grid, invalid row metadata, or trailing bytes.
   * @throws IOException Thrown if reading fails or the file is truncated.
   */
  public static QuantizedEmbeddingMatrix read(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    try (InputStream in = Files.newInputStream(file);
         DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
      final int magic = data.readInt();
      if (magic != MAGIC) {
        throw new InvalidFormatException(file + " is not a quantized embedding matrix "
            + "(magic 0x" + Integer.toHexString(magic) + ", expected 0x"
            + Integer.toHexString(MAGIC) + ")");
      }
      final int rowCount = data.readInt();
      if (rowCount < 1) {
        throw new InvalidFormatException(file + " declares " + rowCount + " rows; a "
            + "quantized matrix has at least 1");
      }
      final int dimension = data.readInt();
      if (dimension < 1) {
        throw new InvalidFormatException(file + " declares dimension " + dimension + "; a "
            + "quantized matrix's dimension is at least 1");
      }
      final long fileSize = Files.size(file);
      if (rowCount > fileSize || dimension > fileSize) {
        throw new InvalidFormatException(file + " declares " + rowCount + " rows and dimension "
            + dimension + " but has only " + fileSize + " bytes");
      }
      final int bits = data.readInt();
      try {
        GaussianQuantizer.requireSupportedBits(bits);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(file + " declares an unsupported bit width: "
            + e.getMessage(), e);
      }
      final long seed = data.readLong();
      final int levelCount = data.readInt();
      if (levelCount != 1 << bits) {
        throw new InvalidFormatException(file + " declares " + levelCount + " grid levels "
            + "for " + bits + " bits; expected " + (1 << bits));
      }
      final int paddedDimension;
      final int rowBytes;
      try {
        paddedDimension = HadamardRotation.paddedDimension(dimension);
        rowBytes = rowByteCount(paddedDimension, bits);
        requireStorableSize(rowCount, rowBytes);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(file + " declares a matrix this reader cannot "
            + "store: " + e.getMessage(), e);
      }
      // Check the minimum length before allocating row-sized arrays.
      final long declaredBytes = 28L + 4L * levelCount + (16L + rowBytes) * rowCount + 1L;
      if (declaredBytes > fileSize) {
        throw new InvalidFormatException(file + " declares " + rowCount + " rows and "
            + "dimension " + dimension + " at " + bits + " bits, needing at least "
            + declaredBytes + " bytes of scales, norms, and packed codes, but has only "
            + fileSize + " bytes");
      }
      final float[] levels = new float[levelCount];
      for (int i = 0; i < levelCount; i++) {
        levels[i] = data.readFloat();
      }
      final GaussianQuantizer quantizer;
      try {
        quantizer = GaussianQuantizer.fromLevels(levels);
      } catch (IllegalArgumentException e) {
        throw new InvalidFormatException(file + " stores an invalid grid: " + e.getMessage(), e);
      }
      double maximumLevelMagnitude = 0;
      for (final float level : levels) {
        maximumLevelMagnitude = Math.max(maximumLevelMagnitude, Math.abs(level));
      }
      // Each inverse-transform output can sum paddedDimension decoded coordinates before the
      // normalization factor is applied.
      final double maximumDecodableScale =
          Double.MAX_VALUE / paddedDimension / maximumLevelMagnitude;
      final double[] scales = new double[rowCount];
      for (int row = 0; row < rowCount; row++) {
        scales[row] = data.readDouble();
        if (!Double.isFinite(scales[row]) || scales[row] < 0
            || scales[row] >= maximumDecodableScale) {
          throw new InvalidFormatException(file + " has an invalid scale for row " + row
              + ": " + scales[row]);
        }
      }
      // A finite float vector's L2 norm cannot exceed its L1 norm.
      final double maximumDecodedNorm = (double) Float.MAX_VALUE * dimension;
      final double[] decodedNorms = new double[rowCount];
      for (int row = 0; row < rowCount; row++) {
        decodedNorms[row] = data.readDouble();
        if (!Double.isFinite(decodedNorms[row]) || decodedNorms[row] < 0
            || decodedNorms[row] > maximumDecodedNorm) {
          throw new InvalidFormatException(file + " has an invalid decoded norm for row "
              + row + ": " + decodedNorms[row]);
        }
        if (scales[row] == 0.0 && decodedNorms[row] > 0.0) {
          throw new InvalidFormatException(file + " has zero scale but positive decoded norm for "
              + "row " + row + ": " + decodedNorms[row]);
        }
      }
      float[] poolingWeights = null;
      final int poolingWeightsFlag = data.readUnsignedByte();
      if (poolingWeightsFlag > 1) {
        throw new InvalidFormatException(file + " has invalid pooling-weight flag "
            + poolingWeightsFlag + "; expected 0 or 1");
      }
      if (poolingWeightsFlag == 1) {
        if (declaredBytes + 4L * rowCount > fileSize) {
          throw new InvalidFormatException(file + " declares per-row pooling weights, "
              + "needing at least " + (declaredBytes + 4L * rowCount) + " bytes in total, but "
              + "has only " + fileSize + " bytes");
        }
        poolingWeights = new float[rowCount];
        for (int row = 0; row < rowCount; row++) {
          poolingWeights[row] = data.readFloat();
          if (!Float.isFinite(poolingWeights[row])) {
            throw new InvalidFormatException(file + " has a non-finite pooling weight for "
                + "row " + row + ": " + poolingWeights[row]);
          }
        }
      }
      final byte[] codes = new byte[rowCount * rowBytes];
      try {
        data.readFully(codes);
      } catch (EOFException e) {
        throw new IOException(file + " is truncated: the header declares " + rowCount
            + " rows of " + rowBytes + " packed bytes, but the file ends early", e);
      }
      if (data.read() != -1) {
        throw new InvalidFormatException(file + " has trailing bytes after the declared "
            + "content; it is not a quantized matrix of this version");
      }
      return new QuantizedEmbeddingMatrix(rowCount, dimension, bits, seed, quantizer, scales,
          codes, decodedNorms, poolingWeights);
    }
  }

  /**
   * {@return one packed code} A 3-bit code may cross a byte boundary, so this reads a second byte
   * when required.
   *
   * @param codes    The packed code array.
   * @param rowBase  The row's first byte index.
   * @param bits     The code width.
   * @param index    The code's index within the row.
   */
  private static int readCode(byte[] codes, int rowBase, int bits, int index) {
    final int bitPosition = index * bits;
    final int byteIndex = rowBase + (bitPosition >>> 3);
    final int shift = bitPosition & 7;
    int word = codes[byteIndex] & 0xFF;
    if (shift + bits > 8) {
      word |= (codes[byteIndex + 1] & 0xFF) << 8;
    }
    return (word >>> shift) & ((1 << bits) - 1);
  }

  /**
   * Writes one packed code, the mirror of {@link #readCode(byte[], int, int, int)}.
   *
   * @param codes    The packed code array.
   * @param rowBase  The row's first byte index.
   * @param bits     The code width.
   * @param index    The code's index within the row.
   * @param code     The code value, within the bit width.
   */
  private static void writeCode(byte[] codes, int rowBase, int bits, int index, int code) {
    final int bitPosition = index * bits;
    final int byteIndex = rowBase + (bitPosition >>> 3);
    final int shift = bitPosition & 7;
    codes[byteIndex] |= (byte) (code << shift);
    if (shift + bits > 8) {
      codes[byteIndex + 1] |= (byte) (code >>> (8 - shift));
    }
  }

  /**
   * Converts a finite double to float, saturating values outside the finite float range.
   *
   * @param value The value to convert.
   * @return The converted value.
   */
  private static float finiteFloat(double value) {
    if (value > Float.MAX_VALUE) {
      return Float.MAX_VALUE;
    }
    if (value < -Float.MAX_VALUE) {
      return -Float.MAX_VALUE;
    }
    return (float) value;
  }

  /**
   * Converts a double-precision vector to finite floats.
   *
   * @param values The vector to convert.
   * @return The converted vector.
   */
  private float[] toFloatVector(double[] values) {
    final float[] result = new float[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = finiteFloat(values[i]);
    }
    return result;
  }
}
