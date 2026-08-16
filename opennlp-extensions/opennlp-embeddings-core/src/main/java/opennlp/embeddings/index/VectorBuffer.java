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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The build-phase collector shared by the index implementations: validates and copies each
 * added id and vector, and flattens the rows when the index freezes. Not safe for concurrent
 * use; the build phase is single-threaded by contract.
 */
final class VectorBuffer {

  private final int dimension;
  private final List<String> ids = new ArrayList<>();
  private final Set<String> seen = new HashSet<>();
  private final List<float[]> rows = new ArrayList<>();

  /**
   * Creates an empty buffer.
   *
   * @param dimension The dimension every vector must have. Must be at least 1.
   * @throws IllegalArgumentException Thrown if {@code dimension} is below 1.
   */
  VectorBuffer(int dimension) {
    if (dimension < 1) {
      throw new IllegalArgumentException("Dimension must be at least 1, got " + dimension);
    }
    this.dimension = dimension;
  }

  /**
   * Validates and stores one id and vector.
   *
   * @param id     The vector's id. Must not be {@code null} or blank, must not contain a line
   *               break, and must not already be present.
   * @param vector The vector. Must not be {@code null}, must have the buffer's dimension, and
   *               every value must be finite. The array is copied.
   * @throws IllegalArgumentException Thrown if {@code id} or {@code vector} is invalid.
   */
  void add(String id, float[] vector) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Id must not be null or blank");
    }
    if (id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("Id must not contain a line break: '" + id + "'");
    }
    if (vector == null) {
      throw new IllegalArgumentException("Vector must not be null");
    }
    if (vector.length != dimension) {
      throw new IllegalArgumentException("Vector has length " + vector.length
          + " but this index has dimension " + dimension);
    }
    for (int d = 0; d < dimension; d++) {
      if (!Float.isFinite(vector[d])) {
        throw new IllegalArgumentException("Vector '" + id + "' has a non-finite value at "
            + "dimension " + d + ": " + vector[d]);
      }
    }
    if (!seen.add(id)) {
      throw new IllegalArgumentException("Id '" + id + "' is already indexed");
    }
    ids.add(id);
    rows.add(vector.clone());
  }

  /** {@return the number of stored vectors} */
  int size() {
    return ids.size();
  }

  /** {@return the dimension every vector has} */
  int dimension() {
    return dimension;
  }

  /** {@return the ids in add order, as an immutable list} */
  List<String> ids() {
    return List.copyOf(ids);
  }

  /** {@return the vectors flattened row-major, in add order} */
  float[] rowMajor() {
    final float[] rowMajor = new float[rows.size() * dimension];
    for (int row = 0; row < rows.size(); row++) {
      System.arraycopy(rows.get(row), 0, rowMajor, row * dimension, dimension);
    }
    return rowMajor;
  }
}
