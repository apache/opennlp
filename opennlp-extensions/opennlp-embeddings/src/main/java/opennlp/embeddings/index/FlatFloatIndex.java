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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.java.Experimental;

/**
 * The exact index: full-precision float vectors scanned brute force with double-accumulated
 * cosine similarity. Every query scores every vector, so this is the ground truth the
 * quantized index is measured against, and the right choice outright when the collection is
 * small.
 *
 * <p>Follows the {@link VectorIndex} lifecycle: single-threaded build, then
 * {@link #freeze()}, then concurrent queries.</p>
 *
 * <p>A frozen index can be persisted: {@link #write(Path)} stores the full-precision
 * row-major floats ({@value #VECTORS_FILE}) and the ids in row order ({@value #IDS_FILE});
 * {@link #read(Path)} loads them back into a frozen index that scores identically.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public final class FlatFloatIndex implements VectorIndex {

  /** File name of the row-major float vectors inside a written index directory. */
  public static final String VECTORS_FILE = "vectors.f32";

  /** File name of the row ids, one per line in row order, inside a written index directory. */
  public static final String IDS_FILE = "ids.txt";

  /** Leading marker of {@value #VECTORS_FILE}, "ONF1" in ASCII. */
  private static final int MAGIC = 0x4F4E4631;

  private static final double NORM_EPSILON = 1e-12;

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

  /** Fills {@link #norms} with the Euclidean norm of every frozen row. */
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
    try (OutputStream out = Files.newOutputStream(directory.resolve(VECTORS_FILE));
         DataOutputStream data = new DataOutputStream(new BufferedOutputStream(out))) {
      data.writeInt(MAGIC);
      data.writeInt(dimension);
      data.writeInt(ids.size());
      for (final float value : rowMajor) {
        data.writeFloat(value);
      }
    }
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
   * @throws InvalidFormatException Thrown if a file is malformed or truncated, an id repeats
   *     or is blank, or the id count and the vector row count disagree.
   * @throws IOException Thrown if reading fails.
   */
  public static FlatFloatIndex read(Path directory) throws IOException {
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
    try (InputStream in = Files.newInputStream(vectorsFile);
         DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
      final int magic = data.readInt();
      if (magic != MAGIC) {
        throw new InvalidFormatException(vectorsFile + " is not a flat float vector file "
            + "(magic 0x" + Integer.toHexString(magic) + ", expected 0x"
            + Integer.toHexString(MAGIC) + ")");
      }
      final int dimension = data.readInt();
      final int rows = data.readInt();
      if (dimension < 1 || rows < 0 || rows > Integer.MAX_VALUE / dimension) {
        throw new InvalidFormatException(vectorsFile + " declares an invalid shape: " + rows
            + " rows of dimension " + dimension);
      }
      if (rows != ids.size()) {
        throw new InvalidFormatException(idsFile + " holds " + ids.size() + " ids but "
            + vectorsFile + " holds " + rows + " rows; these files do not belong to the same "
            + "index");
      }
      final float[] rowMajor = new float[rows * dimension];
      try {
        for (int i = 0; i < rowMajor.length; i++) {
          rowMajor[i] = data.readFloat();
        }
      } catch (EOFException e) {
        throw new InvalidFormatException(vectorsFile + " is truncated", e);
      }
      if (data.read() != -1) {
        throw new InvalidFormatException(vectorsFile + " holds trailing bytes");
      }
      return new FlatFloatIndex(dimension, List.copyOf(ids), rowMajor);
    }
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
    final TopK best = new TopK(Math.min(k, ids.size()));
    for (int row = 0; row < norms.length; row++) {
      final double norm = norms[row];
      if (norm < NORM_EPSILON) {
        // A zero vector has no direction; scored 0 rather than NaN from a 0/0 division.
        best.offer(row, 0.0);
        continue;
      }
      final int base = row * dimension;
      // Four accumulators so the JIT can vectorize the dot product without reordering FP adds.
      double dot0 = 0;
      double dot1 = 0;
      double dot2 = 0;
      double dot3 = 0;
      int d = 0;
      for (final int limit = dimension - 3; d < limit; d += 4) {
        dot0 += query[d] * rowMajor[base + d];
        dot1 += query[d + 1] * rowMajor[base + d + 1];
        dot2 += query[d + 2] * rowMajor[base + d + 2];
        dot3 += query[d + 3] * rowMajor[base + d + 3];
      }
      double dot = dot0 + dot1 + dot2 + dot3;
      for (; d < dimension; d++) {
        dot += query[d] * rowMajor[base + d];
      }
      best.offer(row, dot / (queryNorm * norm));
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
