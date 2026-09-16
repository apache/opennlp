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

/**
 * A bounded min-heap over parallel score and row arrays. The root is the lowest-ranked retained
 * candidate.
 */
final class TopK {

  private final double[] scores;
  private final int[] rows;
  private int size;

  /**
   * Creates an empty selection.
   *
   * @param capacity The maximum number of rows to keep.
   */
  TopK(int capacity) {
    this.scores = new double[capacity];
    this.rows = new int[capacity];
  }

  /**
   * Offers a candidate row, keeping it only if it ranks among the top {@code capacity}.
   *
   * @param row   The candidate row id.
   * @param score The row's score against the query.
   */
  void offer(int row, double score) {
    if (size < scores.length) {
      int i = size++;
      scores[i] = score;
      rows[i] = row;
      while (i > 0) {
        final int parent = (i - 1) >>> 1;
        if (ranksBelow(parent, i)) {
          break;
        }
        swap(parent, i);
        i = parent;
      }
    } else if (score > scores[0] || (score == scores[0] && row < rows[0])) {
      scores[0] = score;
      rows[0] = row;
      siftDown();
    }
  }

  /**
   * Drains the selection into hits, most similar first, mapping each kept row through the ids.
   *
   * @param ids The indexed ids; a kept row's id is {@code ids.get(row)}.
   * @return The hits, most similar first.
   */
  List<VectorIndex.Hit> drain(List<String> ids) {
    final VectorIndex.Hit[] ordered = new VectorIndex.Hit[size];
    for (int i = ordered.length - 1; i >= 0; i--) {
      ordered[i] = new VectorIndex.Hit(ids.get(rows[0]), scores[0]);
      size--;
      scores[0] = scores[size];
      rows[0] = rows[size];
      siftDown();
    }
    return List.of(ordered);
  }

  /** Restores the min-heap invariant from the root downward. */
  private void siftDown() {
    int i = 0;
    while (true) {
      final int left = 2 * i + 1;
      final int right = left + 1;
      int smallest = i;
      if (left < size && ranksBelow(left, smallest)) {
        smallest = left;
      }
      if (right < size && ranksBelow(right, smallest)) {
        smallest = right;
      }
      if (smallest == i) {
        return;
      }
      swap(i, smallest);
      i = smallest;
    }
  }

  /**
   * Tests whether one heap entry ranks below another. Later rows rank lower when scores tie, so
   * results retain index order.
   *
   * @param candidate The candidate heap position.
   * @param reference The reference heap position.
   * @return {@code true} if the candidate ranks below the reference.
   */
  private boolean ranksBelow(int candidate, int reference) {
    return scores[candidate] < scores[reference]
        || (scores[candidate] == scores[reference] && rows[candidate] > rows[reference]);
  }

  /**
   * Swaps two heap entries in both parallel arrays.
   *
   * @param i The first index.
   * @param j The second index.
   */
  private void swap(int i, int j) {
    final double score = scores[i];
    scores[i] = scores[j];
    scores[j] = score;
    final int row = rows[i];
    rows[i] = rows[j];
    rows[j] = row;
  }
}
