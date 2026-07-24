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
 * The row storage behind {@link StaticEmbeddingModel}: gathering rows into a pooled vector and
 * scoring rows against a query, independent of whether the rows are float or quantized.
 *
 * <p>The seam is shaped so a storage form may pool in a working space of its own.
 * {@link #addRow(int, float, float[])} accumulates into a vector of {@link #pooledLength()}, and
 * {@link #finishPooling(float[])} maps the accumulated vector to original space once per pooled
 * result. The float table's working space is original space and its finish is the identity; the
 * quantized table pools in rotated space, where decoding a row is a grid lookup, and pays its
 * single inverse rotation in the finish. Scoring mirrors this: {@link #prepareQuery(float[])}
 * maps a query into the working space once, and {@link #dot(int, float[])} scores every row
 * against the prepared query there.</p>
 *
 * <p>Implementations are immutable and safe for concurrent use; the accumulator and prepared
 * query arrays belong to the caller.</p>
 */
interface EmbeddingTable {

  /** {@return the number of rows} */
  int rowCount();

  /** {@return the original row width, the length of a pooled result} */
  int dimension();

  /** {@return the length of the pooling accumulator and of a prepared query} */
  int pooledLength();

  /**
   * Adds a row, times a weight, onto a pooling accumulator.
   *
   * @param row    The row to add, between 0 and {@code rowCount() - 1}.
   * @param weight The weight to multiply the row by.
   * @param sum    The accumulator, of length {@link #pooledLength()}.
   */
  void addRow(int row, float weight, float[] sum);

  /**
   * Maps an accumulated vector to original space. Called once per pooled result; the returned
   * array may be {@code sum} itself.
   *
   * @param sum The accumulator, of length {@link #pooledLength()}.
   * @return The pooled vector in original space, of length {@link #dimension()}.
   */
  float[] finishPooling(float[] sum);

  /**
   * Maps an original-space query into this table's working space, once per scan. The returned
   * array may be {@code query} itself.
   *
   * @param query The query, of length {@link #dimension()}. Not modified.
   * @return The prepared query, of length {@link #pooledLength()}.
   */
  float[] prepareQuery(float[] query);

  /**
   * The dot product of a row with a prepared query, equal to the original-space dot product up
   * to float rounding.
   *
   * @param row           The row to score, between 0 and {@code rowCount() - 1}.
   * @param preparedQuery The query as returned by {@link #prepareQuery(float[])}.
   * @return The dot product.
   */
  double dot(int row, float[] preparedQuery);

  /**
   * {@return the L2 norm of a row as this table stores it, for cosine scoring}
   *
   * @param row The row, between 0 and {@code rowCount() - 1}.
   */
  double rowNorm(int row);
}
