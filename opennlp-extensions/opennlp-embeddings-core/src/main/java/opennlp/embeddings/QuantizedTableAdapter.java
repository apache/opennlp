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
 * The quantized {@link EmbeddingTable}: a {@link QuantizedEmbeddingMatrix} whose working space
 * is rotated space, so pooling accumulates decoded rows there and pays one inverse rotation per
 * pooled result, and a scan rotates the query once and scores every row without leaving rotated
 * space (see the matrix's class comment for why both are safe).
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

  @Override
  public int rowCount() {
    return matrix.rowCount();
  }

  @Override
  public int dimension() {
    return matrix.dimension();
  }

  @Override
  public int pooledLength() {
    return matrix.paddedDimension();
  }

  @Override
  public void addRow(int row, float weight, float[] sum) {
    matrix.addRowRotated(row, weight, sum);
  }

  @Override
  public float[] finishPooling(float[] sum) {
    return matrix.toOriginal(sum);
  }

  @Override
  public float[] prepareQuery(float[] query) {
    return matrix.rotate(query);
  }

  @Override
  public double dot(int row, float[] preparedQuery) {
    return matrix.dotRotated(row, preparedQuery);
  }

  @Override
  public double rowNorm(int row) {
    return matrix.rowNorm(row);
  }
}
