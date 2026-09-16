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

import java.util.List;

import opennlp.tools.util.java.Experimental;

/**
 * A build-once, read-many index over embedding vectors: vectors are added under caller-chosen
 * ids, the index is frozen, and queries return the nearest ids by cosine similarity.
 *
 * <p>The lifecycle is two-phase. During the build phase, {@link #add(String, float[])} collects
 * vectors; neither adding nor freezing is safe from more than one thread. {@link #freeze()} ends
 * the build phase, after which {@link #add(String, float[])} is rejected and
 * {@link #topK(float[], int)} is available; a frozen index that is safely published is safe for
 * concurrent queries from any number of threads. Implementations must support concurrent
 * {@code topK} calls after safe publication. No other concurrent calls are permitted.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public interface VectorIndex {

  /**
   * A query result: an indexed id and its cosine similarity to the query.
   *
   * @param id    The indexed id. Must not be {@code null} or blank and must not contain a carriage
   *              return or line feed.
   * @param score The cosine similarity, in {@code [-1, 1]}.
   */
  record Hit(String id, double score) {

    /**
     * Creates a query result.
     *
     * @throws IllegalArgumentException Thrown if {@code id} or {@code score} is invalid.
     */
    public Hit {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("Id must not be null or blank");
      }
      if (id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0) {
        throw new IllegalArgumentException("Id must not contain a carriage return or line feed");
      }
      if (!Double.isFinite(score) || score < -1.0 || score > 1.0) {
        throw new IllegalArgumentException("Score must be finite and between -1 and 1, got "
            + score);
      }
    }
  }

  /**
   * Adds a vector under an id during the build phase.
   *
   * @param id     The vector's id. Must not be {@code null} or blank, must not contain a carriage
   *               return or line feed, and must not already be indexed.
   * @param vector The vector. Must not be {@code null}, must have the index's dimension, and
   *               every value must be finite. The array is copied.
   * @throws IllegalArgumentException Thrown if {@code id} or {@code vector} is invalid.
   * @throws IllegalStateException Thrown if the index is frozen.
   */
  void add(String id, float[] vector);

  /**
   * Ends the build phase. Calling this more than once has no further effect.
   */
  void freeze();

  /**
   * Finds the indexed vectors nearest a query, most similar first.
   *
   * @param query The query vector. Must not be {@code null}, must have the index's dimension,
   *              and every value must be finite. Not modified.
   * @param k     The maximum number of results. Must be at least 1.
   * @return Up to {@code k} hits, most similar first; ties retain insertion order. Returns an
   *     empty list when the index is empty or the query has no direction. An indexed zero vector
   *     has score zero.
   * @throws IllegalArgumentException Thrown if {@code query} is {@code null}, has the wrong
   *     length, or contains a non-finite value, or {@code k} is less than 1.
   * @throws IllegalStateException Thrown if the index is not frozen.
   */
  List<Hit> topK(float[] query, int k);

  /** {@return the number of indexed vectors} */
  int size();

  /** {@return the dimension every indexed vector and query must have} */
  int dimension();
}
