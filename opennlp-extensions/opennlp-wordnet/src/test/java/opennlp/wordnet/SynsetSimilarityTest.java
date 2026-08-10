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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * Tests the taxonomy measures against a project-authored miniature taxonomy; no external
 * lexicon data is involved. {@link HypernymTyperTest} shares the same taxonomy.
 */
public class SynsetSimilarityTest {

  /** A tiny in-memory knowledge base over a hand-built noun taxonomy. */
  static final class FixtureKnowledgeBase implements LexicalKnowledgeBase {
    private final Map<String, Synset> byId = new HashMap<>();
    private final Map<String, List<Synset>> byLemma = new HashMap<>();

    void add(String id, String lemma, WordNetRelation relation, String... parents) {
      final Map<WordNetRelation, List<String>> relations = parents.length == 0
          ? Map.of() : Map.of(relation, List.of(parents));
      final Synset synset =
          new Synset(id, WordNetPOS.NOUN, List.of(lemma), "fixture", relations);
      byId.put(id, synset);
      byLemma.computeIfAbsent(lemma, key -> new ArrayList<>()).add(synset);
    }

    @Override
    public List<Synset> lookup(String lemma, WordNetPOS pos) {
      return byLemma.getOrDefault(lemma, List.of());
    }

    @Override
    public Optional<Synset> synset(String synsetId) {
      return Optional.ofNullable(byId.get(synsetId));
    }
  }

  /** {@return the taxonomy both this test and {@link HypernymTyperTest} assert against} */
  static FixtureKnowledgeBase taxonomy() {
    final FixtureKnowledgeBase kb = new FixtureKnowledgeBase();
    kb.add("n1", "entity", WordNetRelation.HYPERNYM);
    kb.add("n2", "physical", WordNetRelation.HYPERNYM, "n1");
    kb.add("n3", "organism", WordNetRelation.HYPERNYM, "n2");
    kb.add("n4", "person", WordNetRelation.HYPERNYM, "n3");
    kb.add("n5", "scientist", WordNetRelation.HYPERNYM, "n4");
    kb.add("n6", "chemist", WordNetRelation.HYPERNYM, "n5");
    kb.add("n7", "location", WordNetRelation.HYPERNYM, "n2");
    kb.add("n8", "city", WordNetRelation.HYPERNYM, "n7");
    kb.add("n9", "organization", WordNetRelation.HYPERNYM, "n1");
    kb.add("n10", "company", WordNetRelation.HYPERNYM, "n9");
    kb.add("n11", "paris", WordNetRelation.INSTANCE_HYPERNYM, "n8");
    kb.add("n12", "abstract", WordNetRelation.HYPERNYM);
    return kb;
  }

  @Test
  void testPathSimilarity() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    Assertions.assertEquals(1.0, similarity.path("n5", "n5"), 1e-9);
    Assertions.assertEquals(0.5, similarity.path("n6", "n5"), 1e-9);
    // chemist up four to physical, city up two: six edges apart
    Assertions.assertEquals(1.0 / 7.0, similarity.path("n6", "n8"), 1e-9);
    Assertions.assertEquals(0.0, similarity.path("n6", "n12"), 1e-9);
  }

  @Test
  void testWuPalmerRewardsDeepSharedAncestry() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    // scientist and chemist share scientist itself at depth four
    Assertions.assertEquals(8.0 / 9.0, similarity.wuPalmer("n5", "n6"), 1e-9);
    final double siblingBranches = similarity.wuPalmer("n6", "n8");
    Assertions.assertTrue(siblingBranches < similarity.wuPalmer("n5", "n6"));
    Assertions.assertTrue(siblingBranches > 0.0);
    Assertions.assertEquals(0.0, similarity.wuPalmer("n6", "n12"), 1e-9);
  }

  /**
   * Pins the self-similarity regime at the taxonomy root. Wu-Palmer counts depth in
   * edges from the root, so the root itself has depth zero and the formula
   * {@code 2 * depth(lcs) / (depth(a) + depth(b))} has a zero denominator for the pair
   * (root, root); that case is skipped and the score is {@code 0.0}, while path
   * similarity of any synset with itself, root included, is {@code 1.0} by
   * {@code 1 / (1 + 0)}. Both values follow the documented formulas; this test keeps
   * the asymmetry a deliberate pin rather than a surprise.
   */
  @Test
  void testRootSelfSimilarityFollowsTheFormulas() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    Assertions.assertEquals(1.0, similarity.path("n1", "n1"), 1e-9);
    Assertions.assertEquals(0.0, similarity.wuPalmer("n1", "n1"), 1e-9);
    // Away from the root the same formula does yield full self-similarity.
    Assertions.assertEquals(1.0, similarity.wuPalmer("n5", "n5"), 1e-9);
  }

  /**
   * Pins the sign regime of Leacock-Chodorow. The measure is
   * {@code -log((d + 1) / (2 * taxonomyDepth))} with a caller-supplied depth; when the
   * shortest path exceeds the stated depth budget, {@code d + 1 > 2 * taxonomyDepth},
   * the ratio exceeds one and the score goes negative rather than clamping at zero.
   * That is the documented formula behavior for an understated taxonomy depth, pinned
   * here so the negative range is a deliberate contract.
   */
  @Test
  void testLeacockChodorowGoesNegativeWhenDistanceExceedsTheDepthBudget() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    // chemist to city is six edges, so (6 + 1) / (2 * 3) is greater than one
    final double score = similarity.leacockChodorow("n6", "n8", 3);
    Assertions.assertEquals(-Math.log(7.0 / 6.0), score, 1e-9);
    Assertions.assertTrue(score < 0.0);
  }

  @Test
  void testLeacockChodorow() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    Assertions.assertEquals(Math.log(10.0),
        similarity.leacockChodorow("n5", "n6", 10), 1e-9);
    Assertions.assertEquals(0.0, similarity.leacockChodorow("n6", "n12", 10), 1e-9);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> similarity.leacockChodorow("n5", "n6", 0));
  }

  @Test
  void testInstanceHypernymsCountAsEdges() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    Assertions.assertEquals(0.5, similarity.path("n11", "n8"), 1e-9);
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SynsetSimilarity(null));
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> similarity.path(null, "n1"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> similarity.path("n1", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> similarity.wuPalmer(null, "n1"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> similarity.shortestDistance("n1", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> similarity.leacockChodorow(null, "n1", 10));
  }

  @Test
  void testUnknownSynsetsAreUnrelatedRatherThanFatal() {
    final SynsetSimilarity similarity = new SynsetSimilarity(taxonomy());
    Assertions.assertEquals(-1, similarity.shortestDistance("n5", "missing"));
    Assertions.assertEquals(0.0, similarity.path("n5", "missing"), 1e-9);
    Assertions.assertEquals(0.0, similarity.wuPalmer("n5", "missing"), 1e-9);
    Assertions.assertEquals(0.0, similarity.leacockChodorow("n5", "missing", 10), 1e-9);
  }
}
