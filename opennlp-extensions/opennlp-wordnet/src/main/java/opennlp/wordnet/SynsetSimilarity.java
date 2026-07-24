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

package opennlp.wordnet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * Taxonomy-based similarity between synsets: measures over the hypernym graph of a
 * {@link LexicalKnowledgeBase}, computed on demand with no precomputed tables.
 *
 * <p>Path similarity is {@code 1 / (1 + d)} for the shortest hypernym-graph distance
 * {@code d} through a common ancestor. Wu-Palmer similarity relates the depth of the
 * deepest common ancestor to the depths of both synsets. Leacock-Chodorow scales the
 * shortest path against a caller-supplied taxonomy depth, since the knowledge base
 * interface does not enumerate the taxonomy. Unrelated synsets, those sharing no
 * ancestor, score zero everywhere. Information-content measures need corpus counts and
 * are not provided here.</p>
 *
 * <p>Both plain and instance hypernyms count as taxonomy edges. The measures read only
 * the knowledge base and hold no mutable state, so instances are as thread-safe as
 * their knowledge base.</p>
 */
@ThreadSafe
public class SynsetSimilarity {

  private final LexicalKnowledgeBase knowledgeBase;

  /**
   * Initializes the measures.
   *
   * @param knowledgeBase The knowledge base to walk. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code knowledgeBase} is {@code null}.
   */
  public SynsetSimilarity(LexicalKnowledgeBase knowledgeBase) {
    if (knowledgeBase == null) {
      throw new IllegalArgumentException("knowledgeBase must not be null");
    }
    this.knowledgeBase = knowledgeBase;
  }

  /**
   * Computes path similarity: {@code 1 / (1 + d)} over the shortest hypernym-graph
   * distance.
   *
   * @param synsetId The first synset identifier. Must not be {@code null}.
   * @param otherSynsetId The second synset identifier. Must not be {@code null}.
   * @return The similarity in {@code (0, 1]}, or {@code 0} when the synsets share no
   *         ancestor.
   * @throws IllegalArgumentException Thrown if {@code synsetId} or
   *         {@code otherSynsetId} is {@code null}.
   */
  public double path(String synsetId, String otherSynsetId) {
    final int distance = shortestDistance(synsetId, otherSynsetId);
    return distance < 0 ? 0.0 : 1.0 / (1.0 + distance);
  }

  /**
   * Computes Wu-Palmer similarity: {@code 2 * depth(lcs) / (depth(a) + depth(b))},
   * with depths counted from the taxonomy root and the deepest common ancestor as the
   * lcs.
   *
   * @param synsetId The first synset identifier. Must not be {@code null}.
   * @param otherSynsetId The second synset identifier. Must not be {@code null}.
   * @return The similarity in {@code (0, 1]}, or {@code 0} when the synsets share no
   *         ancestor.
   * @throws IllegalArgumentException Thrown if {@code synsetId} or
   *         {@code otherSynsetId} is {@code null}.
   */
  public double wuPalmer(String synsetId, String otherSynsetId) {
    validateIds(synsetId, otherSynsetId);
    final Map<String, Integer> up = depthsAbove(synsetId);
    final Map<String, Integer> otherUp = depthsAbove(otherSynsetId);
    double best = 0.0;
    for (final Map.Entry<String, Integer> common : up.entrySet()) {
      final Integer otherDistance = otherUp.get(common.getKey());
      if (otherDistance == null) {
        continue;
      }
      final int rootDepth = depthFromRoot(common.getKey());
      final int depthA = rootDepth + common.getValue();
      final int depthB = rootDepth + otherDistance;
      if (depthA + depthB == 0) {
        continue;
      }
      final double score = 2.0 * rootDepth / (depthA + depthB);
      best = Math.max(best, score);
    }
    return best;
  }

  /**
   * Computes Leacock-Chodorow similarity:
   * {@code -log((d + 1) / (2 * taxonomyDepth))} over the shortest hypernym-graph
   * distance {@code d}.
   *
   * @param synsetId The first synset identifier. Must not be {@code null}.
   * @param otherSynsetId The second synset identifier. Must not be {@code null}.
   * @param taxonomyDepth The maximum depth of the taxonomy the synsets live in. Must
   *                      be positive.
   * @return The similarity, higher for closer synsets, or {@code 0} when the synsets
   *         share no ancestor.
   * @throws IllegalArgumentException Thrown if {@code synsetId} or
   *         {@code otherSynsetId} is {@code null}, or {@code taxonomyDepth} is not
   *         positive.
   */
  public double leacockChodorow(String synsetId, String otherSynsetId,
      int taxonomyDepth) {
    if (taxonomyDepth <= 0) {
      throw new IllegalArgumentException(
          "taxonomyDepth must be positive: " + taxonomyDepth);
    }
    final int distance = shortestDistance(synsetId, otherSynsetId);
    if (distance < 0) {
      return 0.0;
    }
    return -Math.log((distance + 1.0) / (2.0 * taxonomyDepth));
  }

  /**
   * Finds the shortest distance between two synsets through a common ancestor.
   *
   * @param synsetId The first synset identifier. Must not be {@code null}.
   * @param otherSynsetId The second synset identifier. Must not be {@code null}.
   * @return The edge count of the shortest connecting path, or {@code -1} when no
   *         common ancestor exists.
   * @throws IllegalArgumentException Thrown if {@code synsetId} or
   *         {@code otherSynsetId} is {@code null}.
   */
  public int shortestDistance(String synsetId, String otherSynsetId) {
    validateIds(synsetId, otherSynsetId);
    final Map<String, Integer> up = depthsAbove(synsetId);
    final Map<String, Integer> otherUp = depthsAbove(otherSynsetId);
    int best = -1;
    for (final Map.Entry<String, Integer> common : up.entrySet()) {
      final Integer otherDistance = otherUp.get(common.getKey());
      if (otherDistance != null) {
        final int total = common.getValue() + otherDistance;
        if (best < 0 || total < best) {
          best = total;
        }
      }
    }
    return best;
  }

  /**
   * Validates the identifier arguments of the public measures.
   *
   * @param synsetId The first synset identifier.
   * @param otherSynsetId The second synset identifier.
   * @throws IllegalArgumentException Thrown if {@code synsetId} or
   *         {@code otherSynsetId} is {@code null}.
   */
  private void validateIds(String synsetId, String otherSynsetId) {
    if (synsetId == null) {
      throw new IllegalArgumentException("synsetId must not be null");
    }
    if (otherSynsetId == null) {
      throw new IllegalArgumentException("otherSynsetId must not be null");
    }
  }

  /** Collects every ancestor with its minimal upward distance, the synset included. */
  private Map<String, Integer> depthsAbove(String synsetId) {
    final Map<String, Integer> depths = new HashMap<>();
    final Deque<String> queue = new ArrayDeque<>();
    depths.put(synsetId, 0);
    queue.add(synsetId);
    while (!queue.isEmpty()) {
      final String current = queue.remove();
      final int depth = depths.get(current);
      for (final String parent : hypernyms(current)) {
        if (!depths.containsKey(parent) || depths.get(parent) > depth + 1) {
          depths.put(parent, depth + 1);
          queue.add(parent);
        }
      }
    }
    return depths;
  }

  /** Measures a synset's depth from its taxonomy root, the shortest way up. */
  private int depthFromRoot(String synsetId) {
    final Map<String, Integer> above = depthsAbove(synsetId);
    int deepest = 0;
    for (final int distance : above.values()) {
      deepest = Math.max(deepest, distance);
    }
    return deepest;
  }

  private Iterable<String> hypernyms(String synsetId) {
    final List<String> parents = new ArrayList<>(
        knowledgeBase.related(synsetId, WordNetRelation.HYPERNYM));
    parents.addAll(knowledgeBase.related(synsetId, WordNetRelation.INSTANCE_HYPERNYM));
    return parents;
  }
}
