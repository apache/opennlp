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
 * Tests the taxonomy measures and the hypernym typer against a project-authored
 * miniature taxonomy; no external lexicon data is involved.
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
      byLemma.computeIfAbsent(lemma, key -> new java.util.ArrayList<>()).add(synset);
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

  private static FixtureKnowledgeBase taxonomy() {
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
  void testTyperFindsTheNearestAnchor() {
    final HypernymTyper typer = new HypernymTyper(taxonomy(), Map.of(
        "person", "person", "location", "location", "organization", "organization"));
    Assertions.assertEquals("person", typer.type("chemist").orElseThrow());
    Assertions.assertEquals("location", typer.type("city").orElseThrow());
    Assertions.assertEquals("organization", typer.type("company").orElseThrow());
    Assertions.assertEquals("location", typer.typeSynset("n11").orElseThrow());
    Assertions.assertTrue(typer.type("entity").isEmpty());
    Assertions.assertTrue(typer.type("blorp").isEmpty());
  }

  @Test
  void testMoreSpecificAnchorsWin() {
    final HypernymTyper typer = new HypernymTyper(taxonomy(), Map.of(
        "person", "person", "scientist", "researcher"));
    Assertions.assertEquals("researcher", typer.type("chemist").orElseThrow());
    Assertions.assertEquals("person", typer.type("person").orElseThrow());
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SynsetSimilarity(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SynsetSimilarity(taxonomy()).path(null, "n1"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of()));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of("blorp", "thing")));
    final HypernymTyper typer = new HypernymTyper(taxonomy(), Map.of("person", "person"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.type(" "));
  }
}
