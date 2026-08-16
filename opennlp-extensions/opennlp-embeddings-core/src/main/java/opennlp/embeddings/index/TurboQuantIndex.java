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
package opennlp.embeddings.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.embeddings.QuantizedEmbeddingMatrix;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * The quantized index: freezing quantizes the indexed vectors with the
 * {@link QuantizedEmbeddingMatrix TurboQuant construction} (seeded rotation, per-coordinate
 * grid codes, per-row fitted scale), and a query rotates once and scans every row's packed
 * codes without ever decoding to original space. Against {@link FlatFloatIndex} this trades a
 * little recall for {@code bits}-per-dimension storage instead of 32, and the scan reads
 * proportionally fewer bytes.
 *
 * <p>Follows the {@link VectorIndex} lifecycle: single-threaded build, then
 * {@link #freeze()}, then concurrent queries. A frozen, non-empty index persists as a
 * directory of two files, the quantized matrix ({@value #VECTORS_FILE}, the self-describing
 * TurboQuant format) and the ids in row order ({@value #IDS_FILE}); {@link #read(Path)} loads
 * it back frozen.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class TurboQuantIndex implements VectorIndex {

  /** The quantized vectors of a persisted index, in the TurboQuant file format. */
  public static final String VECTORS_FILE = "vectors.onq";

  /** The ids of a persisted index, one per line in matrix row order. */
  public static final String IDS_FILE = "ids.txt";

  private static final double NORM_EPSILON = 1e-12;

  private final int dimension;
  private final int bits;
  private final long seed;
  private VectorBuffer buffer;
  private List<String> ids;
  // Null while building, and in the frozen empty index, which has no rows to quantize.
  private QuantizedEmbeddingMatrix matrix;

  /**
   * Creates an empty index.
   *
   * @param dimension The dimension every vector and query must have. Must be at least 1.
   * @param bits      The bit width per stored dimension. Must be between
   *                  {@link QuantizedEmbeddingMatrix#MIN_BITS} and
   *                  {@link QuantizedEmbeddingMatrix#MAX_BITS}.
   * @param seed      The rotation seed; the same vectors, bit width, and seed quantize
   *                  identically on every JVM.
   * @throws IllegalArgumentException Thrown if {@code dimension} or {@code bits} is out of
   *     range.
   */
  public TurboQuantIndex(int dimension, int bits, long seed) {
    this.buffer = new VectorBuffer(dimension);
    if (bits < QuantizedEmbeddingMatrix.MIN_BITS || bits > QuantizedEmbeddingMatrix.MAX_BITS) {
      throw new IllegalArgumentException("Bits must be between "
          + QuantizedEmbeddingMatrix.MIN_BITS + " and " + QuantizedEmbeddingMatrix.MAX_BITS
          + ", got " + bits);
    }
    this.dimension = dimension;
    this.bits = bits;
    this.seed = seed;
  }

  /** Holds a loaded index; callers reach this through {@link #read(Path)}. */
  private TurboQuantIndex(List<String> ids, QuantizedEmbeddingMatrix matrix) {
    this.dimension = matrix.dimension();
    this.bits = matrix.bits();
    this.seed = matrix.seed();
    this.ids = ids;
    this.matrix = matrix;
  }

  /** {@inheritDoc} */
  @Override
  public void add(String id, float[] vector) {
    if (buffer == null) {
      throw new IllegalStateException("The index is frozen; vectors can no longer be added");
    }
    buffer.add(id, vector);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Freezing quantizes every added vector; this is the index's one expensive step.</p>
   */
  @Override
  public void freeze() {
    if (buffer == null) {
      return;
    }
    ids = buffer.ids();
    if (!ids.isEmpty()) {
      matrix = QuantizedEmbeddingMatrix.quantize(buffer.rowMajor(), ids.size(), dimension,
          bits, seed);
    }
    buffer = null;
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> topK(float[] query, int k) {
    if (buffer != null) {
      throw new IllegalStateException("The index is not frozen; freeze() ends the build phase");
    }
    final double queryNorm = IndexQueries.checkedQueryNorm(query, k, dimension);
    if (queryNorm < NORM_EPSILON || ids.isEmpty()) {
      return List.of();
    }
    final float[] rotated = matrix.rotate(query);
    final TopK best = new TopK(Math.min(k, ids.size()));
    for (int row = 0; row < ids.size(); row++) {
      final double rowNorm = matrix.rowNorm(row);
      if (rowNorm < NORM_EPSILON) {
        // A zero row has no direction; scored 0 rather than NaN from a 0/0 division.
        best.offer(row, 0.0);
        continue;
      }
      best.offer(row, matrix.dotRotated(row, rotated) / (queryNorm * rowNorm));
    }
    return best.drain(ids);
  }

  /** {@inheritDoc} */
  @Override
  public int size() {
    return buffer != null ? buffer.size() : ids.size();
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return dimension;
  }

  /** {@return the bit width per stored dimension} */
  public int bits() {
    return bits;
  }

  /**
   * {@return the storage cost of one indexed vector: the packed codes over the padded
   * dimension plus the per-row scale and norm floats}
   *
   * @throws IllegalStateException Thrown if the index is not frozen or is empty.
   */
  public double bytesPerVector() {
    if (buffer != null) {
      throw new IllegalStateException("The index is not frozen; freeze() ends the build phase");
    }
    if (matrix == null) {
      throw new IllegalStateException("An empty index stores no vectors");
    }
    return (matrix.paddedDimension() * bits + Byte.SIZE - 1) / Byte.SIZE + 2 * Float.BYTES;
  }

  /**
   * Writes this frozen, non-empty index as a directory of {@value #VECTORS_FILE} and
   * {@value #IDS_FILE}. The directory is created when missing; the two files are replaced.
   *
   * @param directory The directory to write. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null}.
   * @throws IllegalStateException Thrown if the index is not frozen or is empty.
   * @throws IOException Thrown if writing fails.
   */
  public void write(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("Directory must not be null");
    }
    if (buffer != null) {
      throw new IllegalStateException("The index is not frozen; freeze() ends the build phase");
    }
    if (ids.isEmpty()) {
      throw new IllegalStateException("An empty index has nothing to persist");
    }
    Files.createDirectories(directory);
    matrix.write(directory.resolve(VECTORS_FILE));
    Files.write(directory.resolve(IDS_FILE), ids);
  }

  /**
   * Reads an index a previous {@link #write(Path)} persisted. The loaded index is frozen.
   *
   * @param directory The index directory. Must not be {@code null} and must be a directory
   *                  holding {@value #VECTORS_FILE} and {@value #IDS_FILE}.
   * @return The loaded index.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null}, is not a
   *     directory, or lacks one of the two files.
   * @throws InvalidFormatException Thrown if a file is malformed, an id repeats or is blank,
   *     or the id count and the matrix's row count disagree.
   * @throws IOException Thrown if reading fails.
   */
  public static TurboQuantIndex read(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("Directory must not be null");
    }
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          "Index directory does not exist or is not a directory: " + directory);
    }
    final Path vectorsFile = directory.resolve(VECTORS_FILE);
    final Path idsFile = directory.resolve(IDS_FILE);
    if (!Files.isRegularFile(vectorsFile) || !Files.isRegularFile(idsFile)) {
      throw new IllegalArgumentException("Index directory " + directory + " does not hold "
          + VECTORS_FILE + " and " + IDS_FILE);
    }
    final List<String> ids = Files.readAllLines(idsFile);
    final Set<String> seen = new HashSet<>(ids.size() * 2);
    for (final String id : ids) {
      if (id.isBlank()) {
        throw new InvalidFormatException(idsFile + " holds a blank id");
      }
      if (!seen.add(id)) {
        throw new InvalidFormatException(idsFile + " holds id '" + id + "' more than once");
      }
    }
    final QuantizedEmbeddingMatrix matrix = QuantizedEmbeddingMatrix.read(vectorsFile);
    if (matrix.rowCount() != ids.size()) {
      throw new InvalidFormatException(idsFile + " holds " + ids.size() + " ids but "
          + vectorsFile + " holds " + matrix.rowCount() + " rows; these files do not belong "
          + "to the same index");
    }
    return new TurboQuantIndex(List.copyOf(ids), matrix);
  }
}
