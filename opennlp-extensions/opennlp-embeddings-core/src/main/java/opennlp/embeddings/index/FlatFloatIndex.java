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
import java.util.List;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * An exact index that scans full-precision float vectors and accumulates cosine similarity in
 * double precision. It provides the reference result for evaluating a quantized index and is
 * suitable for small collections.
 *
 * <p>Follows the {@link VectorIndex} lifecycle: single-threaded build, then
 * {@link #freeze()}, then concurrent queries.</p>
 *
 * <p>A frozen index can be persisted: {@link #write(Path)} stores the full-precision
 * row-major floats ({@value #VECTORS_FILE}), the ids in row order ({@value #IDS_FILE}), and an
 * checksum manifest ({@value #MANIFEST_FILE}); {@link #read(Path)} loads them into a frozen
 * index with the same scores.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class FlatFloatIndex implements VectorIndex {

  /** File name of the row-major float vectors inside a written index directory. */
  public static final String VECTORS_FILE = "vectors.f32";

  /** File name of the row ids, one per line in row order, inside a written index directory. */
  public static final String IDS_FILE = "ids.txt";

  /** File name of the checksum manifest inside a written index directory. */
  public static final String MANIFEST_FILE = IndexFiles.MANIFEST_FILE;

  /** Leading marker of {@value #VECTORS_FILE}, "ONF1" in ASCII. */
  private static final int MAGIC = 0x4F4E4631;

  private final int dimension;
  private VectorBuffer buffer;
  private List<String> ids;
  private float[] rowMajor;
  private double[] norms;

  /**
   * Creates an empty index.
   *
   * @param dimension The dimension every vector and query must have. Must be at least 1.
   * @throws IllegalArgumentException Thrown if {@code dimension} is below 1.
   */
  public FlatFloatIndex(int dimension) {
    this.buffer = new VectorBuffer(dimension);
    this.dimension = dimension;
  }

  /**
   * Creates a frozen index over rows read back from disk.
   *
   * @param dimension The vector dimension, at least 1.
   * @param ids The row ids in row order.
   * @param rowMajor The vectors, {@code ids.size() * dimension} floats in row-major order.
   */
  private FlatFloatIndex(int dimension, List<String> ids, float[] rowMajor) {
    this.dimension = dimension;
    this.ids = ids;
    this.rowMajor = rowMajor;
    this.norms = new double[ids.size()];
    computeNorms();
  }

  /** {@inheritDoc} */
  @Override
  public void add(String id, float[] vector) {
    if (buffer == null) {
      throw new IllegalStateException("The index is frozen; vectors can no longer be added");
    }
    buffer.add(id, vector);
  }

  /** {@inheritDoc} */
  @Override
  public void freeze() {
    if (buffer == null) {
      return;
    }
    ids = buffer.ids();
    rowMajor = buffer.rowMajor();
    norms = new double[ids.size()];
    computeNorms();
    buffer = null;
  }

  /** Computes the Euclidean norm of each frozen row. */
  private void computeNorms() {
    for (int row = 0; row < norms.length; row++) {
      final int base = row * dimension;
      double sumOfSquares = 0;
      for (int d = 0; d < dimension; d++) {
        final float value = rowMajor[base + d];
        sumOfSquares += (double) value * value;
      }
      norms[row] = Math.sqrt(sumOfSquares);
    }
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
    IndexFiles.write(directory, VECTORS_FILE, IDS_FILE, ids, this::writeVectors);
  }

  /**
   * Writes the vector data file.
   *
   * @param file The destination file.
   * @throws IOException Thrown if writing fails.
   */
  private void writeVectors(Path file) throws IOException {
    try (OutputStream out = Files.newOutputStream(file);
         DataOutputStream data = new DataOutputStream(new BufferedOutputStream(out))) {
      data.writeInt(MAGIC);
      data.writeInt(dimension);
      data.writeInt(ids.size());
      for (final float value : rowMajor) {
        data.writeFloat(value);
      }
    }
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
   * @throws InvalidFormatException Thrown if a file is malformed or truncated, the index is
   *     empty, an id repeats or is blank, or the id count and the vector row count disagree.
   * @throws IOException Thrown if reading fails.
   */
  public static FlatFloatIndex read(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("Directory must not be null");
    }
    final Path vectorsFile = directory.resolve(VECTORS_FILE);
    final Path idsFile = directory.resolve(IDS_FILE);
    final List<String> ids = IndexFiles.readIds(directory, VECTORS_FILE, IDS_FILE);
    try (InputStream in = Files.newInputStream(vectorsFile);
         DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
      try {
        final int magic = data.readInt();
        if (magic != MAGIC) {
          throw new InvalidFormatException(vectorsFile + " is not a flat float vector file "
              + "(magic 0x" + Integer.toHexString(magic) + ", expected 0x"
              + Integer.toHexString(MAGIC) + ")");
        }
        final int dimension = data.readInt();
        final int rows = data.readInt();
        if (dimension < 1 || rows < 1 || rows > Integer.MAX_VALUE / dimension) {
          throw new InvalidFormatException(vectorsFile + " declares an invalid shape: " + rows
              + " rows of dimension " + dimension);
        }
        if (rows != ids.size()) {
          throw new InvalidFormatException(idsFile + " contains " + ids.size() + " ids but "
              + vectorsFile + " contains " + rows
              + " rows; the files do not belong to the same index");
        }
        final long expectedBytes = 3L * Integer.BYTES
            + (long) rows * dimension * Float.BYTES;
        final long actualBytes = Files.size(vectorsFile);
        if (actualBytes < expectedBytes) {
          throw new InvalidFormatException(vectorsFile + " is truncated");
        }
        if (actualBytes > expectedBytes) {
          throw new InvalidFormatException(vectorsFile + " contains trailing bytes");
        }
        final float[] rowMajor = new float[rows * dimension];
        for (int i = 0; i < rowMajor.length; i++) {
          rowMajor[i] = data.readFloat();
          if (!Float.isFinite(rowMajor[i])) {
            throw new InvalidFormatException(vectorsFile + " contains a non-finite value at row "
                + i / dimension + ", dimension " + i % dimension + ": " + rowMajor[i]);
          }
        }
        return new FlatFloatIndex(dimension, List.copyOf(ids), rowMajor);
      } catch (EOFException e) {
        throw new InvalidFormatException(vectorsFile + " is truncated", e);
      }
    }
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
    final TopK best = new TopK(Math.min(k, ids.size()));
    for (int row = 0; row < norms.length; row++) {
      final double norm = norms[row];
      if (norm == 0.0) {
        // A zero vector has no direction, so its similarity is 0.
        best.offer(row, 0.0);
        continue;
      }
      final int base = row * dimension;
      double dot0 = 0;
      double dot1 = 0;
      double dot2 = 0;
      double dot3 = 0;
      int d = 0;
      for (final int limit = dimension - 3; d < limit; d += 4) {
        dot0 += (double) query[d] * rowMajor[base + d];
        dot1 += (double) query[d + 1] * rowMajor[base + d + 1];
        dot2 += (double) query[d + 2] * rowMajor[base + d + 2];
        dot3 += (double) query[d + 3] * rowMajor[base + d + 3];
      }
      double dot = dot0 + dot1 + dot2 + dot3;
      for (; d < dimension; d++) {
        dot += (double) query[d] * rowMajor[base + d];
      }
      best.offer(row, IndexQueries.cosine(dot, queryNorm * norm));
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
}
