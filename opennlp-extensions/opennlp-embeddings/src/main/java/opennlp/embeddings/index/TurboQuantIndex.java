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
import java.nio.file.Path;
import java.util.List;

import opennlp.embeddings.QuantizedEmbeddingMatrix;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * A quantized index backed by {@link QuantizedEmbeddingMatrix}. Freezing normalizes and encodes
 * the indexed vectors with a seeded rotation, per-coordinate grid codes, and a fitted scale per
 * row. A query is normalized and rotated once, then compared with each packed row without
 * decoding the matrix to original space. Compared with {@link FlatFloatIndex}, it uses fewer
 * bytes per row at the cost of some recall.
 *
 * <p>Follows the {@link VectorIndex} lifecycle: single-threaded build, then
 * {@link #freeze()}, then concurrent queries. A frozen, non-empty index persists as a
 * directory containing the quantized matrix ({@value #VECTORS_FILE}), the ids in row order
 * ({@value #IDS_FILE}), and a checksum manifest ({@value #MANIFEST_FILE});
 * {@link #read(Path)} loads it back frozen.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class TurboQuantIndex implements VectorIndex {

  /** The quantized vectors of a persisted index, in the TurboQuant file format. */
  public static final String VECTORS_FILE = "vectors.onq";

  /** The ids of a persisted index, one per line in matrix row order. */
  public static final String IDS_FILE = "ids.txt";

  /** File name of the checksum manifest inside a written index directory. */
  public static final String MANIFEST_FILE = IndexFiles.MANIFEST_FILE;

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
   * @param seed      The deterministic rotation seed.
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

  /** Creates a frozen index from a loaded matrix and its row ids. */
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
   * <p>Freezing normalizes and quantizes every added vector; this is the index's one expensive
   * step.</p>
   */
  @Override
  public void freeze() {
    if (buffer == null) {
      return;
    }
    ids = buffer.ids();
    if (!ids.isEmpty()) {
      matrix = QuantizedEmbeddingMatrix.quantize(normalizedRows(), ids.size(), dimension,
          bits, seed);
    }
    buffer = null;
  }

  /**
   * Returns the buffered rows normalized for cosine scoring. Zero rows remain zero.
   *
   * @return The normalized rows in row-major order.
   */
  private float[] normalizedRows() {
    final float[] rows = buffer.rowMajor();
    for (int row = 0; row < ids.size(); row++) {
      final int base = row * dimension;
      double sumOfSquares = 0.0;
      for (int d = 0; d < dimension; d++) {
        sumOfSquares += (double) rows[base + d] * rows[base + d];
      }
      final double norm = Math.sqrt(sumOfSquares);
      if (norm != 0.0) {
        for (int d = 0; d < dimension; d++) {
          rows[base + d] = (float) (rows[base + d] / norm);
        }
      }
    }
    return rows;
  }

  /** {@inheritDoc} */
  @Override
  public List<Hit> topK(float[] query, int k) {
    if (buffer != null) {
      throw new IllegalStateException("The index is not frozen; freeze() ends the build phase");
    }
    final double queryNorm = IndexQueries.checkedQueryNorm(query, k, dimension);
    if (queryNorm == 0.0 || ids.isEmpty()) {
      return List.of();
    }
    final float[] unitQuery = new float[dimension];
    for (int d = 0; d < dimension; d++) {
      unitQuery[d] = (float) (query[d] / queryNorm);
    }
    final double[] rotated = matrix.rotate(unitQuery);
    final TopK best = new TopK(Math.min(k, ids.size()));
    for (int row = 0; row < ids.size(); row++) {
      final double rowNorm = matrix.rowNorm(row);
      if (rowNorm == 0.0) {
        // A zero row has no direction, so its similarity is 0.
        best.offer(row, 0.0);
        continue;
      }
      best.offer(row, IndexQueries.cosine(matrix.dotRotated(row, rotated), rowNorm));
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
   * Writes this frozen, non-empty index as a directory of {@value #VECTORS_FILE},
   * {@value #IDS_FILE}, and {@value #MANIFEST_FILE}. The directory is created when missing.
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
      throw new IllegalStateException("An empty index cannot be persisted");
    }
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, ids, matrix::write);
  }

  /**
   * Reads an index written by {@link #write(Path)}. The loaded index is frozen.
   *
   * @param directory The index directory. Must not be {@code null} and must be a directory
   *                  containing {@value #VECTORS_FILE}, {@value #IDS_FILE}, and
   *                  {@value #MANIFEST_FILE}.
   * @return The loaded index.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null}, is not a
   *     directory, or lacks one of the three files.
   * @throws InvalidFormatException Thrown if a file is malformed, an id repeats or is blank,
   *     or the id count and the matrix's row count disagree.
   * @throws IOException Thrown if reading fails.
   */
  public static TurboQuantIndex read(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("Directory must not be null");
    }
    final Path vectorsFile = directory.resolve(VECTORS_FILE);
    final Path idsFile = directory.resolve(IDS_FILE);
    final List<String> ids = IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE);
    final QuantizedEmbeddingMatrix matrix = QuantizedEmbeddingMatrix.read(vectorsFile);
    if (matrix.rowCount() != ids.size()) {
      throw new InvalidFormatException(idsFile + " contains " + ids.size() + " ids but "
          + vectorsFile + " contains " + matrix.rowCount()
          + " rows; the files do not belong to the same index");
    }
    return new TurboQuantIndex(List.copyOf(ids), matrix);
  }
}
