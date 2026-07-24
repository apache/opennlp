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
 * The float {@link EmbeddingTable}: a flat row-major matrix, exactly the storage
 * {@link StaticEmbeddingModel} always had. Its working space is original space, so query
 * preparation and pooling finish are the identity, and per-row norms are precomputed once for
 * the neighbor scan.
 */
final class FloatEmbeddingTable implements EmbeddingTable {

  private final float[] values;
  private final int dimension;
  private final int rowCount;
  private final double[] rowNorms;

  /**
   * Wraps a flat row-major matrix. The array is used as given; the loaders that construct this
   * table own it exclusively.
   *
   * @param values    The matrix, {@code rowCount * dimension} floats.
   * @param dimension The row width.
   * @param rowCount  The number of rows.
   */
  FloatEmbeddingTable(float[] values, int dimension, int rowCount) {
    this.values = values;
    this.dimension = dimension;
    this.rowCount = rowCount;
    this.rowNorms = new double[rowCount];
    for (int row = 0; row < rowCount; row++) {
      final int base = row * dimension;
      double sumOfSquares = 0;
      for (int d = 0; d < dimension; d++) {
        final float value = values[base + d];
        sumOfSquares += (double) value * value;
      }
      rowNorms[row] = Math.sqrt(sumOfSquares);
    }
  }

  /** {@inheritDoc} */
  @Override
  public int rowCount() {
    return rowCount;
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return dimension;
  }

  /** {@inheritDoc} */
  @Override
  public int pooledLength() {
    return dimension;
  }

  /** {@inheritDoc} */
  @Override
  public void addRow(int row, float weight, float[] sum) {
    final int base = row * dimension;
    if (weight == 1f) {
      for (int d = 0; d < dimension; d++) {
        sum[d] += values[base + d];
      }
    } else {
      for (int d = 0; d < dimension; d++) {
        sum[d] += values[base + d] * weight;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public float[] finishPooling(float[] sum) {
    return sum;
  }

  /** {@inheritDoc} */
  @Override
  public float[] prepareQuery(float[] query) {
    return query;
  }

  /** {@inheritDoc} */
  @Override
  public double dot(int row, float[] preparedQuery) {
    final int base = row * dimension;
    // Four accumulators so the JIT can vectorize the dot product without reordering FP adds.
    double dot0 = 0;
    double dot1 = 0;
    double dot2 = 0;
    double dot3 = 0;
    int d = 0;
    for (final int limit = dimension - 3; d < limit; d += 4) {
      dot0 += preparedQuery[d] * values[base + d];
      dot1 += preparedQuery[d + 1] * values[base + d + 1];
      dot2 += preparedQuery[d + 2] * values[base + d + 2];
      dot3 += preparedQuery[d + 3] * values[base + d + 3];
    }
    double dot = dot0 + dot1 + dot2 + dot3;
    for (; d < dimension; d++) {
      dot += preparedQuery[d] * values[base + d];
    }
    return dot;
  }

  /** {@inheritDoc} */
  @Override
  public double rowNorm(int row) {
    return rowNorms[row];
  }
}
