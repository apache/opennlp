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
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Compares the bounded heap with a full score sort, including tied scores. */
class TopKTest {

  @Test
  void testSelectionMatchesAFullSort() {
    final Random random = new Random(19);
    final List<String> ids = new ArrayList<>();
    for (int row = 0; row < 64; row++) {
      ids.add("passage-" + row);
    }

    for (int capacity = 1; capacity <= ids.size(); capacity++) {
      final TopK topK = new TopK(capacity);
      final List<Candidate> candidates = new ArrayList<>();
      for (int row = 0; row < ids.size(); row++) {
        final double score = (random.nextInt(9) - 4) / 4.0;
        topK.offer(row, score);
        candidates.add(new Candidate(row, score));
      }
      candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
          .thenComparingInt(Candidate::row));
      final List<VectorIndex.Hit> expected = candidates.subList(0, capacity).stream()
          .map(candidate -> new VectorIndex.Hit(ids.get(candidate.row()), candidate.score()))
          .toList();

      assertEquals(expected, topK.drain(ids), "capacity " + capacity);
    }
  }

  /** A row and its query score. */
  private record Candidate(int row, double score) {
  }
}
