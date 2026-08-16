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

/**
 * The query-argument checks shared by the index implementations.
 */
final class IndexQueries {

  /** Not instantiable. */
  private IndexQueries() {
  }

  /**
   * Validates a query and returns its L2 norm.
   *
   * @param query     The query vector.
   * @param k         The requested result count.
   * @param dimension The index's dimension.
   * @return The query's L2 norm.
   * @throws IllegalArgumentException Thrown if {@code query} is {@code null} or has the wrong
   *     length, or {@code k} is less than 1.
   */
  static double checkedQueryNorm(float[] query, int k, int dimension) {
    if (query == null) {
      throw new IllegalArgumentException("Query must not be null");
    }
    if (query.length != dimension) {
      throw new IllegalArgumentException("Query has length " + query.length
          + " but this index has dimension " + dimension);
    }
    if (k < 1) {
      throw new IllegalArgumentException("K must be at least 1, got " + k);
    }
    double sumOfSquares = 0;
    for (final float value : query) {
      sumOfSquares += (double) value * value;
    }
    return Math.sqrt(sumOfSquares);
  }
}
