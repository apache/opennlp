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
import opennlp.wordnet.LexicalExpander.Expansion;
import opennlp.wordnet.LexicalExpander.Kind;

import static opennlp.wordnet.ExpansionAssertions.find;

/**
 * Runs the manual's lexical expansion, synset similarity, and hypernym-anchored typing
 * examples (docbkx {@code wordnet.xml}) verbatim: every value the chapter states is asserted
 * here, so a change breaking this test breaks the manual. The taxonomies are the hand-built
 * miniatures the chapter shows in its listing comments.
 */
public class LexicalExpansionUsageExampleTest {

  /**
   * dog sense 1 = {dog, domestic dog} -> canid; sense 2 = {dog, frank, hot dog} -> sausage.
   */
  private static LexicalKnowledgeBase dogTaxonomy() {
    final Map<String, Synset> synsets = new HashMap<>();
    final Map<String, List<Synset>> senses = new HashMap<>();

    final Synset n1 = new Synset("n1", WordNetPOS.NOUN, List.of("dog", "domestic dog"), "canine",
        Map.of(WordNetRelation.HYPERNYM, List.of("n2")));
    final Synset n2 = new Synset("n2", WordNetPOS.NOUN, List.of("canid"), "canid family", Map.of());
    final Synset n5 = new Synset("n5", WordNetPOS.NOUN, List.of("dog", "frank", "hot dog"),
        "sausage in a bun", Map.of(WordNetRelation.HYPERNYM, List.of("n6")));
    final Synset n6 = new Synset("n6", WordNetPOS.NOUN, List.of("sausage"), "ground meat",
        Map.of());

    for (final Synset synset : List.of(n1, n2, n5, n6)) {
      synsets.put(synset.id(), synset);
    }
    senses.put("dog|NOUN", List.of(n1, n5));
    senses.put("domestic dog|NOUN", List.of(n1));

    return new LexicalKnowledgeBase() {
      @Override
      public List<Synset> lookup(String lemma, WordNetPOS pos) {
        if (lemma == null) {
          throw new IllegalArgumentException("lemma must not be null");
        }
        if (pos == null) {
          throw new IllegalArgumentException("pos must not be null");
        }
        return senses.getOrDefault(LemmaFolding.fold(lemma) + "|" + pos, List.of());
      }

      @Override
      public Optional<Synset> synset(String synsetId) {
        return Optional.ofNullable(synsets.get(synsetId));
      }
    };
  }

  /**
   * The taxonomy shown in the chapter's similarity and typing listings:
   * chemist -> scientist -> person -> organism -> physical -> entity; city -> location ->
   * physical; paris is an instance of city.
   */
  private static LexicalKnowledgeBase similarityTaxonomy() {
    final Map<String, Synset> byId = new HashMap<>();
    final Map<String, List<Synset>> byLemma = new HashMap<>();
    add(byId, byLemma, "n1", "entity", WordNetRelation.HYPERNYM);
    add(byId, byLemma, "n2", "physical", WordNetRelation.HYPERNYM, "n1");
    add(byId, byLemma, "n3", "organism", WordNetRelation.HYPERNYM, "n2");
    add(byId, byLemma, "n4", "person", WordNetRelation.HYPERNYM, "n3");
    add(byId, byLemma, "n5", "scientist", WordNetRelation.HYPERNYM, "n4");
    add(byId, byLemma, "n6", "chemist", WordNetRelation.HYPERNYM, "n5");
    add(byId, byLemma, "n7", "location", WordNetRelation.HYPERNYM, "n2");
    add(byId, byLemma, "n8", "city", WordNetRelation.HYPERNYM, "n7");
    add(byId, byLemma, "n9", "paris", WordNetRelation.INSTANCE_HYPERNYM, "n8");
    return new LexicalKnowledgeBase() {
      @Override
      public List<Synset> lookup(String lemma, WordNetPOS pos) {
        return byLemma.getOrDefault(lemma, List.of());
      }

      @Override
      public Optional<Synset> synset(String synsetId) {
        return Optional.ofNullable(byId.get(synsetId));
      }
    };
  }

  private static void add(Map<String, Synset> byId, Map<String, List<Synset>> byLemma,
                          String id, String lemma, WordNetRelation relation, String... parents) {
    final Map<WordNetRelation, List<String>> relations = parents.length == 0
        ? Map.of() : Map.of(relation, List.of(parents));
    final Synset synset = new Synset(id, WordNetPOS.NOUN, List.of(lemma), "fixture", relations);
    byId.put(id, synset);
    byLemma.computeIfAbsent(lemma, key -> new ArrayList<>()).add(synset);
  }

  /**
   * Default expansion of noun {@code dog}: synonym and depth-1 hypernym weights.
   */
  @Test
  void testExpandDogNoun() {
    final List<Expansion> expansions =
        LexicalExpander.builder(dogTaxonomy()).build().expand("dog", WordNetPOS.NOUN);

    final Expansion domesticDog = find(expansions, "domestic dog");
    Assertions.assertNotNull(domesticDog);
    Assertions.assertEquals(Kind.SYNONYM, domesticDog.kind());
    Assertions.assertEquals(1.0, domesticDog.weight());
    Assertions.assertEquals(0, domesticDog.senseRank());

    final Expansion canid = find(expansions, "canid");
    Assertions.assertNotNull(canid);
    Assertions.assertEquals(Kind.HYPERNYM, canid.kind());
    Assertions.assertEquals(1, canid.depth());
    Assertions.assertEquals(0.5, canid.weight());

    final Expansion frank = find(expansions, "frank");
    Assertions.assertNotNull(frank);
    Assertions.assertEquals(Kind.SYNONYM, frank.kind());
    Assertions.assertEquals(1, frank.senseRank());
    Assertions.assertEquals(0.5, frank.weight());
  }

  /**
   * Path and Wu-Palmer scores on the miniature scientist/city taxonomy, exactly as the
   * chapter's similarity listing prints them.
   */
  @Test
  void testSynsetSimilarityScores() {
    final SynsetSimilarity similarity = new SynsetSimilarity(similarityTaxonomy());
    Assertions.assertEquals(0.5, similarity.path("n6", "n5"), 1e-9);
    Assertions.assertEquals(10.0 / 11.0, similarity.wuPalmer("n5", "n6"), 1e-9);
    Assertions.assertEquals(1.0 / 7.0, similarity.path("n6", "n8"), 1e-9);
  }

  /**
   * Hypernym-anchored typing over the same taxonomy, exactly as the chapter's typing
   * listing prints it: a chemist is a person, paris reaches location through instance
   * hypernymy, and an ancestor of an anchor is never typed because the walk is upward only.
   */
  @Test
  void testHypernymAnchoredTyping() {
    final HypernymTyper typer = new HypernymTyper(similarityTaxonomy(),
        Map.of("person", "person", "location", "location"));
    Assertions.assertEquals(Optional.of("person"), typer.type("chemist"));
    Assertions.assertEquals(Optional.of("location"), typer.type("paris"));
    Assertions.assertEquals(Optional.empty(), typer.type("organism"));
  }
}
