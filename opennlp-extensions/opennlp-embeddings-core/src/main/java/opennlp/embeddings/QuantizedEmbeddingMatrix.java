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

/**
 * An embedding matrix quantized to {@code 2}-{@code 4} bits per dimension, following the
 * TurboQuant construction (Zandieh, Daliri, Hadian, Mirrokni,
 * <a href="https://arxiv.org/abs/2504.19874"><i>TurboQuant: Online Vector Quantization with
 * Near-optimal Distortion Rate</i></a>): each row is rotated by
 * a seeded {@link HadamardRotation}, so its coordinates become near-independent and
 * near-Gaussian, and each rotated coordinate is encoded independently with the
 * {@link GaussianQuantizer} grid of the chosen bit width. A row decodes to a per-row scale times
 * its grid levels; the scale is least-squares fitted per row, which strictly reduces the squared
 * error of the fixed grid.
 *
 * <p>The storage is {@code bits} per dimension plus one float per row, against 32 bits per
 * dimension for the float matrix: a 500,000-row, 300-dimension table shrinks from roughly 600 MB
 * to 77 MB at 4 bits (the padded dimension, 512 here, is what is stored). The workload this
 * serves is memory-bound row gathering, so reading fewer bytes is also the throughput lever.</p>
 *
 * <p>Rows live in <b>rotated space</b>, and the cheap operations stay there: the rotation is
 * orthonormal, so dot products and norms of rotated vectors equal those of the originals, and
 * pooling commutes with it because rotation is linear. A consumer embeds text by summing rows
 * with {@link #addRowRotated(int, float, float[])} and applying {@link #toOriginal(float[])}
 * once per text, not once per row; a similarity scan rotates the query once with
 * {@link #rotate(float[])} and scores every row with {@link #dotRotated(int, float[])}, never
 * leaving rotated space. {@link #decodeRow(int)} exists for callers that need one original-space
 * row and for measuring reconstruction quality.</p>
 *
 * <p>The file format is self-describing: it stores the grid levels and the rotation seed, so a
 * reader reconstructs exactly the decoder the writer used and never depends on this class's grid
 * derivation staying fixed. Quantizing is deterministic: the same matrix, bit width, and seed
 * produce the same file bytes on every JVM.</p>
 *
 * <p>Instances are immutable and safe for concurrent use after construction.</p>
 */
@ThreadSafe
public final class QuantizedEmbeddingMatrix {

  /** The smallest supported bit width. */
  public static final int MIN_BITS = GaussianQuantizer.MIN_BITS;

  /** The largest supported bit width. */
  public static final int MAX_BITS = GaussianQuantizer.MAX_BITS;

  // "ONQ1": OpenNLP quantized matrix, format 1.
  private static final int MAGIC = 0x4F4E5131;

  private final int rowCount;
  private final int dimension;
  private final int paddedDimension;
  private final int bits;
  private final long seed;
  private final int rowBytes;
  private final GaussianQuantizer quantizer;
  private final HadamardRotation rotation;
  // One scale per row: decoded rotated coordinate i of a row is scale * level(code_i).
  private final float[] scales;
  // Packed codes, row-major: row r's code i occupies bits [i*bits, (i+1)*bits) of the row's
  // rowBytes region, little-endian within the region.
  private final byte[] codes;
  // The L2 norm of each decoded original-space row. Quantization noise leaves some energy in
  // the padding coordinates, which truncation drops, so this is computed exactly at quantize
  // time (one inverse rotation per row) and stored in the file rather than recomputed from the
  // codes on load.
  private final float[] decodedNorms;
  // Optional per-row pooling weights carried alongside the matrix, so a quantized file can
  // fully replace a safetensors file that bundled a "weights" tensor; null when absent. The
  // weights are stored as they are, not quantized.
  private final float[] poolingWeights;

  /**
   * Holds validated state; callers reach this through {@link #quantize} or {@link #read}.
   */
  private QuantizedEmbeddingMatrix(int rowCount, int dimension, int bits, long seed,
                                   GaussianQuantizer quantizer, float[] scales, byte[] codes,
                                   float[] decodedNorms, float[] poolingWeights) {
    this.rowCount = rowCount;
    this.dimension = dimension;
    this.paddedDimension = HadamardRotation.paddedDimension(dimension);
    this.bits = bits;
    this.seed = seed;
    this.rowBytes = (paddedDimension * bits + 7) / 8;
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
    final int rowBytes = (paddedDimension * bits + 7) / 8;
    requireStorableSize(rowCount, rowBytes);
    final float[] scales = new float[rowCount];
    final byte[] codes = new byte[rowCount * rowBytes];
    final float[] decodedNorms = new float[rowCount];
    final float[] rotated = new float[paddedDimension];
    final float[] decoded = new float[paddedDimension];
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
        final float standardized = (float) (rotated[i] * standardize);
        final int code = quantizer.encode(standardized);
        writeCode(codes, row * rowBytes, bits, i, code);
        final float level = quantizer.level(code);
        gridDot += (double) standardized * level;
        gridSquares += (double) level * level;
      }
      final double fitted = gridDot > 0 ? gridDot / gridSquares : 1.0;
      scales[row] = (float) (norm / squareRootOfPadded * fitted);
      // The decoded original-space norm: quantization noise leaves energy in the padding
      // coordinates and truncation drops it, so the norm is measured on the truncated decode,
      // not on the rotated codes.
      for (int i = 0; i < paddedDimension; i++) {
        decoded[i] = scales[row] * quantizer.level(readCode(codes, row * rowBytes, bits, i));
      }
      rotation.inverse(decoded);
      double decodedSumOfSquares = 0;
      for (int d = 0; d < dimension; d++) {
        decodedSumOfSquares += (double) decoded[d] * decoded[d];
      }
      decodedNorms[row] = (float) Math.sqrt(decodedSumOfSquares);
    }
    return new QuantizedEmbeddingMatrix(rowCount, dimension, bits, seed, quantizer, scales,
        codes, decodedNorms, null);
  }

  /**
   * {@return a copy of this matrix carrying per-row pooling weights} The weights ride along in
   * the file unquantized, so a quantized file can fully replace a safetensors file that bundled
   * a {@code weights} tensor.
   *
   * @param weights One weight per row, or {@code null} to carry none. Every weight must be
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
   * {@return a copy of the per-row pooling weights, or {@code null} when this matrix carries
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
   * a query once, then score rows against it with {@link #dotRotated(int, float[])}.
   *
   * @param vector The original-space vector. Must not be {@code null} and must have length
   *               {@link #dimension()}.
   * @return A new array of length {@link #paddedDimension()} holding the rotated vector.
   * @throws IllegalArgumentException Thrown if {@code vector} is {@code null} or has the wrong
   *     length.
   */
  public float[] rotate(float[] vector) {
    if (vector == null) {
      throw new IllegalArgumentException("Vector must not be null");
    }
    if (vector.length != dimension) {
      throw new IllegalArgumentException("Vector has length " + vector.length
          + " but this matrix has dimension " + dimension);
    }
    final float[] padded = new float[paddedDimension];
    System.arraycopy(vector, 0, padded, 0, dimension);
    rotation.rotate(padded);
    return padded;
  }

  /**
   * Maps a rotated-space vector back to original space. Apply this once per pooled result, after
   * accumulating rows with {@link #addRowRotated(int, float, float[])}; rotation is linear, so
   * the sum of rotated rows is the rotation of the summed rows.
   *
   * @param rotated The rotated-space vector. Must not be {@code null} and must have length
   *                {@link #paddedDimension()}. Not modified.
   * @return A new array of length {@link #dimension()} holding the original-space vector.
   * @throws IllegalArgumentException Thrown if {@code rotated} is {@code null} or has the wrong
   *     length.
   */
  public float[] toOriginal(float[] rotated) {
    if (rotated == null) {
      throw new IllegalArgumentException("Rotated must not be null");
    }
    if (rotated.length != paddedDimension) {
      throw new IllegalArgumentException("Rotated has length " + rotated.length
          + " but this matrix's padded dimension is " + paddedDimension);
    }
    final float[] copy = rotated.clone();
    rotation.inverse(copy);
    final float[] original = new float[dimension];
    System.arraycopy(copy, 0, original, 0, dimension);
    return original;
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
  public void addRowRotated(int row, float weight, float[] sum) {
    requireRow(row);
    if (sum == null) {
      throw new IllegalArgumentException("Sum must not be null");
    }
    if (sum.length != paddedDimension) {
      throw new IllegalArgumentException("Sum has length " + sum.length
          + " but this matrix's padded dimension is " + paddedDimension);
    }
    final float scaledWeight = scales[row] * weight;
    final int base = row * rowBytes;
    for (int i = 0; i < paddedDimension; i++) {
      sum[i] += scaledWeight * quantizer.level(readCode(codes, base, bits, i));
    }
  }

  /**
   * The dot product of a decoded row with a rotated-space query. Because the rotation is
   * orthonormal, this equals the original-space dot product of the decoded row with the
   * un-rotated query, up to float rounding.
   *
   * @param row          The row to score. Must be between 0 and {@code rowCount() - 1}.
   * @param rotatedQuery The query in rotated space, as returned by {@link #rotate(float[])}.
   *                     Must not be {@code null} and must have length
   *                     {@link #paddedDimension()}.
   * @return The dot product.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range or
   *     {@code rotatedQuery} is {@code null} or has the wrong length.
   */
  public double dotRotated(int row, float[] rotatedQuery) {
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
      dot += (double) rotatedQuery[i] * quantizer.level(readCode(codes, base, bits, i));
    }
    return dot * scales[row];
  }

  /**
   * {@return the L2 norm of the decoded original-space row, for cosine scoring} Computed
   * exactly at quantize time and stored in the file: quantization noise leaves some energy in
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
   * Decodes one row back to original space. This pays the inverse rotation for a single row;
   * pooling and scanning callers should stay in rotated space instead (see the class comment).
   *
   * @param row The row to decode. Must be between 0 and {@code rowCount() - 1}.
   * @return A new array of length {@link #dimension()} holding the decoded row.
   * @throws IllegalArgumentException Thrown if {@code row} is out of range.
   */
  public float[] decodeRow(int row) {
    requireRow(row);
    final float[] rotated = new float[paddedDimension];
    addRowRotated(row, 1f, rotated);
    return toOriginal(rotated);
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
      for (final float scale : scales) {
        data.writeFloat(scale);
      }
      for (final float decodedNorm : decodedNorms) {
        data.writeFloat(decodedNorm);
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
   * Reads a matrix written by {@link #write(Path)}. The stored grid and seed rebuild exactly the
   * decoder the writer used.
   *
   * @param file The file to read. Must not be {@code null}.
   * @return The quantized matrix.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or its content is
   *     not a quantized matrix of a supported version.
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
        throw new IllegalArgumentException(file + " is not a quantized embedding matrix "
            + "(magic 0x" + Integer.toHexString(magic) + ", expected 0x"
            + Integer.toHexString(MAGIC) + ")");
      }
      final int rowCount = data.readInt();
      if (rowCount < 1) {
        throw new IllegalArgumentException(file + " declares " + rowCount + " rows; a "
            + "quantized matrix has at least 1");
      }
      final int dimension = data.readInt();
      if (dimension < 1) {
        throw new IllegalArgumentException(file + " declares dimension " + dimension + "; a "
            + "quantized matrix's dimension is at least 1");
      }
      final int bits = data.readInt();
      GaussianQuantizer.requireSupportedBits(bits);
      final long seed = data.readLong();
      final int levelCount = data.readInt();
      if (levelCount != 1 << bits) {
        throw new IllegalArgumentException(file + " declares " + levelCount + " grid levels "
            + "for " + bits + " bits; expected " + (1 << bits));
      }
      final float[] levels = new float[levelCount];
      for (int i = 0; i < levelCount; i++) {
        levels[i] = data.readFloat();
      }
      final GaussianQuantizer quantizer = GaussianQuantizer.fromLevels(levels);
      final float[] scales = new float[rowCount];
      for (int row = 0; row < rowCount; row++) {
        scales[row] = data.readFloat();
        if (!Float.isFinite(scales[row])) {
          throw new IllegalArgumentException(file + " has a non-finite scale for row " + row
              + ": " + scales[row]);
        }
      }
      final float[] decodedNorms = new float[rowCount];
      for (int row = 0; row < rowCount; row++) {
        decodedNorms[row] = data.readFloat();
        if (!Float.isFinite(decodedNorms[row]) || decodedNorms[row] < 0) {
          throw new IllegalArgumentException(file + " has an invalid decoded norm for row "
              + row + ": " + decodedNorms[row]);
        }
      }
      float[] poolingWeights = null;
      if (data.readBoolean()) {
        poolingWeights = new float[rowCount];
        for (int row = 0; row < rowCount; row++) {
          poolingWeights[row] = data.readFloat();
          if (!Float.isFinite(poolingWeights[row])) {
            throw new IllegalArgumentException(file + " has a non-finite pooling weight for "
                + "row " + row + ": " + poolingWeights[row]);
          }
        }
      }
      final int paddedDimension = HadamardRotation.paddedDimension(dimension);
      final int rowBytes = (paddedDimension * bits + 7) / 8;
      requireStorableSize(rowCount, rowBytes);
      final byte[] codes = new byte[rowCount * rowBytes];
      try {
        data.readFully(codes);
      } catch (EOFException e) {
        throw new IOException(file + " is truncated: the header declares " + rowCount
            + " rows of " + rowBytes + " packed bytes, but the file ends early", e);
      }
      if (data.read() != -1) {
        throw new IllegalArgumentException(file + " has trailing bytes after the declared "
            + "content; it is not a quantized matrix of this version");
      }
      return new QuantizedEmbeddingMatrix(rowCount, dimension, bits, seed, quantizer, scales,
          codes, decodedNorms, poolingWeights);
    }
  }

  /**
   * {@return one packed code} Codes may straddle a byte boundary (3-bit widths do), so two
   * adjacent bytes are read and the code's bits selected; the second byte is only touched when
   * the code actually crosses into it, so the last code of a row never reads past its region.
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
}
