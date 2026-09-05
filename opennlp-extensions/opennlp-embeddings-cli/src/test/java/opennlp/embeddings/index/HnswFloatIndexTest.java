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

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests HNSW retrieval, recall, cosine scores, and serialized size.
 */
class HnswFloatIndexTest {

  private static final int DIMENSION = 64;
  private static final int COUNT = 100;
  private static final int BEAM_COLLECTION_SIZE = 1_000;
  private static final int BEAM_QUERY_COUNT = 100;
  private static final long SEED = 7;
  private static final int TOP_K = 10;

  /** {@return the deterministic Gaussian test collection, one row per id} */
  private float[][] collection() {
    return randomVectors(COUNT, SEED);
  }

  /**
   * {@return deterministic Gaussian vectors}
   *
   * @param count The number of vectors.
   * @param seed  The random seed.
   */
  private float[][] randomVectors(int count, long seed) {
    final Random random = new Random(seed);
    final float[][] vectors = new float[count][DIMENSION];
    for (final float[] vector : vectors) {
      for (int d = 0; d < DIMENSION; d++) {
        vector[d] = (float) random.nextGaussian();
      }
    }
    return vectors;
  }

  /** {@return a frozen HNSW index over the supplied vectors} */
  private HnswFloatIndex hnsw(float[][] vectors) {
    final HnswFloatIndex index = new HnswFloatIndex(DIMENSION);
    for (int i = 0; i < vectors.length; i++) {
      index.add("v" + i, vectors[i]);
    }
    index.freeze();
    return index;
  }

  @Test
  void testSelfRetrievalRanksEveryVectorFirst() {
    final float[][] vectors = collection();
    try (HnswFloatIndex index = hnsw(vectors)) {
      for (int i = 0; i < vectors.length; i++) {
        assertEquals("v" + i, index.topK(vectors[i], 1).get(0).id(),
            "vector " + i + " must be its own nearest neighbor");
      }
    }
  }

  @Test
  void testASearchWidthBelowOneIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new HnswFloatIndex(DIMENSION, 0));
  }

  @Test
  void testTheDefaultBeamImprovesRecallOverANarrowOne() {
    final float[][] vectors = randomVectors(BEAM_COLLECTION_SIZE, SEED);
    final float[][] queries = randomVectors(BEAM_QUERY_COUNT, SEED + 1);
    final FlatFloatIndex exact = new FlatFloatIndex(DIMENSION);
    for (int i = 0; i < vectors.length; i++) {
      exact.add("v" + i, vectors[i]);
    }
    exact.freeze();
    final HnswFloatIndex narrow = new HnswFloatIndex(DIMENSION, 1);
    for (int i = 0; i < vectors.length; i++) {
      narrow.add("v" + i, vectors[i]);
    }
    narrow.freeze();
    try (narrow; HnswFloatIndex wide = hnsw(vectors)) {
      final double narrowRecall = recallAgainst(exact, narrow, queries);
      final double wideRecall = recallAgainst(exact, wide, queries);
      assertTrue(wideRecall > narrowRecall,
          "default beam recall " + wideRecall + " must exceed narrow recall " + narrowRecall);
      assertTrue(wideRecall >= 0.9, "default beam recall@" + TOP_K + ": " + wideRecall);
    }
  }

  @Test
  void testGraphRecallAgainstTheExactIndex() {
    final float[][] vectors = collection();
    final FlatFloatIndex exact = new FlatFloatIndex(DIMENSION);
    for (int i = 0; i < vectors.length; i++) {
      exact.add("v" + i, vectors[i]);
    }
    exact.freeze();
    try (HnswFloatIndex index = hnsw(vectors)) {
      final double recall = recallAgainst(exact, index, vectors);
      assertTrue(recall >= 0.9, "graph recall@" + TOP_K + ": " + recall);
    }
  }

  /** {@return the graph index's mean top-k overlap with the exact index over all queries} */
  private double recallAgainst(FlatFloatIndex exact, HnswFloatIndex index,
                               float[][] queries) {
    long overlap = 0;
    for (final float[] query : queries) {
      final Set<String> truth = new HashSet<>();
      for (final VectorIndex.Hit hit : exact.topK(query, TOP_K)) {
        truth.add(hit.id());
      }
      for (final VectorIndex.Hit hit : index.topK(query, TOP_K)) {
        if (truth.contains(hit.id())) {
          overlap++;
        }
      }
    }
    return overlap / (double) (queries.length * TOP_K);
  }

  @Test
  void testScoresAreCosineSimilarities() {
    final float[] axis = new float[DIMENSION];
    axis[0] = 1f;
    final float[] diagonal = new float[DIMENSION];
    diagonal[0] = 3f;
    diagonal[1] = 4f;
    final HnswFloatIndex index = new HnswFloatIndex(DIMENSION);
    index.add("axis", axis);
    index.add("diagonal", diagonal);
    index.freeze();
    try (index) {
      final List<VectorIndex.Hit> hits = index.topK(axis, 2);
      assertEquals("axis", hits.get(0).id());
      assertEquals(1.0, hits.get(0).score(), 1e-6);
      // cos(axis, diagonal) = 3 / 5, independent of the stored vector's length.
      assertEquals("diagonal", hits.get(1).id());
      assertEquals(0.6, hits.get(1).score(), 1e-6);
    }
  }

  @Test
  void testTinyNonzeroQueryRetainsItsDirection() {
    final float[] axis = new float[DIMENSION];
    axis[0] = 1f;
    final float[] tinyQuery = new float[DIMENSION];
    tinyQuery[0] = 1e-13f;
    final HnswFloatIndex index = new HnswFloatIndex(DIMENSION);
    index.add("axis", axis);
    index.freeze();

    try (index) {
      assertEquals("axis", index.topK(tinyQuery, 1).get(0).id());
    }
  }

  @Test
  void testSerializedBytesPerVectorCoversTheFloatDataAndTheGraph() {
    try (HnswFloatIndex index = hnsw(collection())) {
      // At least the raw float32 vector; the rest is graph links and metadata.
      assertTrue(index.serializedBytesPerVector() >= DIMENSION * (double) Float.BYTES,
          "serialized bytes/vector: " + index.serializedBytesPerVector());
    }
  }

  @Test
  void testSerializedBytesPerVectorRequiresAFrozenIndex() {
    final HnswFloatIndex index = new HnswFloatIndex(DIMENSION);
    assertThrows(IllegalStateException.class, index::serializedBytesPerVector);
    index.freeze();
    try (index) {
      assertEquals(0, index.serializedBytesPerVector(), "an empty index stores zero bytes");
    }
  }

  @Test
  void testCloseIsIdempotent() {
    final HnswFloatIndex index = hnsw(collection());
    index.close();
    index.close();
  }
}
