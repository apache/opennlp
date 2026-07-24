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
 * Adapts a {@link QuantizedEmbeddingMatrix} to {@link EmbeddingTable}. Pooling and scoring use the
 * matrix's rotated vector space.
 */
final class QuantizedTableAdapter implements EmbeddingTable {

  private final QuantizedEmbeddingMatrix matrix;

  /**
   * Wraps a quantized matrix.
   *
   * @param matrix The matrix to serve rows from.
   */
  QuantizedTableAdapter(QuantizedEmbeddingMatrix matrix) {
    this.matrix = matrix;
  }

  /** {@inheritDoc} */
  @Override
  public int rowCount() {
    return matrix.rowCount();
  }

  /** {@inheritDoc} */
  @Override
  public int dimension() {
    return matrix.dimension();
  }

  /** {@inheritDoc} */
  @Override
  public int pooledLength() {
    return matrix.paddedDimension();
  }

  /** {@inheritDoc} */
  @Override
  public void addRow(int row, float weight, double[] sum) {
    matrix.addRowRotated(row, weight, sum);
  }

  /** {@inheritDoc} */
  @Override
  public double[] finishPooling(double[] sum) {
    return matrix.toOriginal(sum);
  }

  /** {@inheritDoc} */
  @Override
  public double[] prepareQuery(double[] query) {
    return matrix.rotateQuery(query);
  }

  /** {@inheritDoc} */
  @Override
  public double dot(int row, double[] preparedQuery) {
    return matrix.dotRotated(row, preparedQuery);
  }

  /** {@inheritDoc} */
  @Override
  public double rowNorm(int row) {
    return matrix.rowNorm(row);
  }
}
