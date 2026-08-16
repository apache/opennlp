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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The exact index's scores: hand-computed cosine similarities, exact ordering, and the
 * odd-dimension tail of the unrolled dot product.
 */
class FlatFloatIndexTest {

  @Test
  void testScoresAreExactCosineSimilarities() {
    final FlatFloatIndex index = new FlatFloatIndex(2);
    index.add("x", new float[] {1f, 0f});
    index.add("y", new float[] {0f, 1f});
    index.add("diagonal", new float[] {1f, 1f});
    index.freeze();

    final List<VectorIndex.Hit> hits = index.topK(new float[] {1f, 0f}, 3);
    assertEquals(3, hits.size());
    assertEquals("x", hits.get(0).id());
    assertEquals(1.0, hits.get(0).score(), 1e-12);
    assertEquals("diagonal", hits.get(1).id());
    assertEquals(Math.sqrt(0.5), hits.get(1).score(), 1e-12);
    assertEquals("y", hits.get(2).id());
    assertEquals(0.0, hits.get(2).score(), 1e-12);
  }

  @Test
  void testAnOppositeVectorScoresMinusOne() {
    final FlatFloatIndex index = new FlatFloatIndex(3);
    index.add("opposite", new float[] {-2f, 0f, 0f});
    index.freeze();

    assertEquals(-1.0, index.topK(new float[] {5f, 0f, 0f}, 1).get(0).score(), 1e-12);
  }

  @Test
  void testAnOddDimensionExercisesTheUnrolledTail() {
    // Dimension 5 leaves one coordinate for the scalar tail after the 4-wide unroll.
    final FlatFloatIndex index = new FlatFloatIndex(5);
    index.add("v", new float[] {1f, 2f, 3f, 4f, 5f});
    index.freeze();

    final double norm = Math.sqrt(1 + 4 + 9 + 16 + 25);
    // Query along the last coordinate only: the dot is exactly the tail's contribution.
    final float[] query = new float[] {0f, 0f, 0f, 0f, 2f};
    assertEquals(5.0 * 2 / (2 * norm), index.topK(query, 1).get(0).score(), 1e-12);
  }
}
