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

import org.junit.jupiter.api.Test;

import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;
import opennlp.wordnet.LexicalExpander.Expansion;
import opennlp.wordnet.LexicalExpander.Kind;

import static opennlp.wordnet.ExpansionAssertions.find;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral tests over a hand-built lexicon whose graph shape is fully controlled: sense
 * ranking, hypernym depth and decay, hyponym opt-in, deduplication, exclusion of the input,
 * cycle termination, and configuration validation.
 */
class LexicalExpanderTest {

  // dog: sense 1 = {dog, domestic dog} -> canid -> carnivore, with hyponym puppy;
  //      sense 2 = {dog, frank, hot dog} -> sausage. The verb sense = {dog, chase}.
  // hot dog: the standalone multiword sense {hot dog, red hot}.
  // alpha <-> beta form a malformed hypernym cycle.
  // Lookups fold through LemmaFolding, exactly as the readers fold their keys at load time.
  private static LexicalKnowledgeBase lexicon() {
    final Map<String, Synset> synsets = new HashMap<>();
    final Map<String, List<Synset>> senses = new HashMap<>();

    final Synset n1 = new Synset("n1", WordNetPOS.NOUN, List.of("dog", "domestic dog"), "canine",
        Map.of(WordNetRelation.HYPERNYM, List.of("n2"),
            WordNetRelation.HYPONYM, List.of("n4")));
    final Synset n2 = new Synset("n2", WordNetPOS.NOUN, List.of("canid"), "canid family",
        Map.of(WordNetRelation.HYPERNYM, List.of("n3")));
    final Synset n3 = new Synset("n3", WordNetPOS.NOUN, List.of("carnivore"), "meat eater",
        Map.of());
    final Synset n4 = new Synset("n4", WordNetPOS.NOUN, List.of("puppy"), "young dog", Map.of());
    final Synset n5 = new Synset("n5", WordNetPOS.NOUN, List.of("dog", "frank", "hot dog"),
        "sausage in a bun", Map.of(WordNetRelation.HYPERNYM, List.of("n6")));
    final Synset n6 = new Synset("n6", WordNetPOS.NOUN, List.of("sausage"), "ground meat",
        Map.of());
    final Synset v1 = new Synset("v1", WordNetPOS.VERB, List.of("dog", "chase"), "follow",
        Map.of());
    final Synset m1 = new Synset("m1", WordNetPOS.NOUN, List.of("hot dog", "red hot"),
        "grilled sausage", Map.of());
    final Synset c1 = new Synset("c1", WordNetPOS.NOUN, List.of("alpha"), "cycle start",
        Map.of(WordNetRelation.HYPERNYM, List.of("c2")));
    final Synset c2 = new Synset("c2", WordNetPOS.NOUN, List.of("beta"), "cycle end",
        Map.of(WordNetRelation.HYPERNYM, List.of("c1")));

    for (final Synset synset : List.of(n1, n2, n3, n4, n5, n6, v1, m1, c1, c2)) {
      synsets.put(synset.id(), synset);
    }
    senses.put("dog|NOUN", List.of(n1, n5));
    senses.put("dog|VERB", List.of(v1));
    senses.put("domestic dog|NOUN", List.of(n1));
    senses.put("hot dog|NOUN", List.of(m1));
    senses.put("alpha|NOUN", List.of(c1));

    return new LexicalKnowledgeBase() {
      @Override
      public List<Synset> lookup(String lemma, WordNetPOS pos) {
        if (lemma == null || pos == null) {
          throw new IllegalArgumentException("null");
        }
        return senses.getOrDefault(LemmaFolding.fold(lemma) + "|" + pos, List.of());
      }

      @Override
      public Optional<Synset> synset(String synsetId) {
        return Optional.ofNullable(synsets.get(synsetId));
      }
    };
  }

  @Test
  void testSynonymsAndHypernymsWithDefaultConfiguration() {
    final List<Expansion> expansions =
        LexicalExpander.builder(lexicon()).build().expand("dog", WordNetPOS.NOUN);

    final Expansion domesticDog = find(expansions, "domestic dog");
    assertEquals(Kind.SYNONYM, domesticDog.kind());
    assertEquals(1.0, domesticDog.weight());
    assertEquals(0, domesticDog.senseRank());

    final Expansion canid = find(expansions, "canid");
    assertEquals(Kind.HYPERNYM, canid.kind());
    assertEquals(1, canid.depth());
    assertEquals(0.5, canid.weight());

    final Expansion frank = find(expansions, "frank");
    assertEquals(Kind.SYNONYM, frank.kind());
    assertEquals(1, frank.senseRank());
    assertEquals(0.5, frank.weight());

    // Depth 1 by default: the grandparent stays out, and so do hyponyms.
    assertNull(find(expansions, "carnivore"));
    assertNull(find(expansions, "puppy"));
  }

  @Test
  void testUnderscoreInputReachesTheSpaceFoldedLexiconEntry() {
    // The lexicon keys "hot_dog" under its folded form "hot dog". The expander must fold the
    // input the same way, so "hot dog" is excluded as the input itself and cannot displace the
    // other member "red hot" from the capped result.
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .maxExpansions(1).build().expand("hot_dog", WordNetPOS.NOUN);

    assertEquals(List.of(new Expansion("red hot", Kind.SYNONYM, 0, 0, 1.0)), expansions);
  }

  @Test
  void testTheInputTermIsNeverAnExpansion() {
    for (final Expansion expansion :
        LexicalExpander.builder(lexicon()).build().expand("dog", WordNetPOS.NOUN)) {
      assertTrue(!expansion.term().equalsIgnoreCase("dog"), "got " + expansion);
    }
  }

  @Test
  void testDeeperHypernymWalkDecaysPerStep() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .hypernymDepth(2).build().expand("dog", WordNetPOS.NOUN);

    final Expansion carnivore = find(expansions, "carnivore");
    assertEquals(2, carnivore.depth());
    assertEquals(0.25, carnivore.weight());
  }

  @Test
  void testHyponymsAreOptIn() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .includeHyponyms(true).build().expand("dog", WordNetPOS.NOUN);

    final Expansion puppy = find(expansions, "puppy");
    assertEquals(Kind.HYPONYM, puppy.kind());
    assertEquals(0.5, puppy.weight());
  }

  @Test
  void testMaxSensesLimitsToTheMostSalient() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .maxSenses(1).build().expand("dog", WordNetPOS.NOUN);

    assertNull(find(expansions, "frank"));
    assertNotNull(find(expansions, "domestic dog"));
  }

  @Test
  void testAllPosExpansionIncludesVerbSynonyms() {
    final List<Expansion> expansions =
        LexicalExpander.builder(lexicon()).build().expand("dog");

    assertNotNull(find(expansions, "chase"));
    assertNotNull(find(expansions, "domestic dog"));
  }

  @Test
  void testCyclicHypernymDataTerminates() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .hypernymDepth(10).build().expand("alpha", WordNetPOS.NOUN);

    final Expansion beta = find(expansions, "beta");
    assertEquals(1, beta.depth());
    // The cycle leads back to alpha's own synset, which is visited and the input besides.
    assertEquals(1, expansions.size());
  }

  @Test
  void testDeduplicationKeepsTheHighestWeight() {
    // "domestic dog" reaches n1 at rank 0; its synonym "dog" is the only other member and
    // must appear once with the rank-0 weight even though deeper paths could yield it again.
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .hypernymDepth(2).build().expand("domestic dog", WordNetPOS.NOUN);

    final Expansion dog = find(expansions, "dog");
    assertEquals(1.0, dog.weight());
    assertEquals(1, expansions.stream().filter(e -> e.term().equals("dog")).count());
  }

  @Test
  void testOrderingIsWeightDescendingAndStable() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .hypernymDepth(2).includeHyponyms(true).build().expand("dog", WordNetPOS.NOUN);

    for (int i = 1; i < expansions.size(); i++) {
      assertTrue(expansions.get(i - 1).weight() >= expansions.get(i).weight(),
          "weights must not increase: " + expansions);
    }
    assertEquals(expansions,
        LexicalExpander.builder(lexicon()).hypernymDepth(2).includeHyponyms(true).build()
            .expand("dog", WordNetPOS.NOUN));
  }

  @Test
  void testMaxExpansionsCapsAfterRanking() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .hypernymDepth(2).includeHyponyms(true).maxExpansions(2).build()
        .expand("dog", WordNetPOS.NOUN);

    assertEquals(2, expansions.size());
    assertEquals(1.0, expansions.get(0).weight());
  }

  @Test
  void testUnknownTermExpandsToNothing() {
    assertEquals(List.of(),
        LexicalExpander.builder(lexicon()).build().expand("xyzzy", WordNetPOS.NOUN));
  }

  @Test
  void testLemmatizerFallbackExpandsInflectedInput() {
    final LexicalExpander expander = LexicalExpander.builder(lexicon())
        .lemmatizer(new Lemmatizer() {
          @Override
          public String[] lemmatize(String[] tokens, String[] tags) {
            final String[] lemmas = new String[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
              lemmas[i] = "dogs".equals(tokens[i]) ? "dog" : "O";
            }
            return lemmas;
          }

          @Override
          public List<List<String>> lemmatize(List<String> tokens, List<String> tags) {
            throw new UnsupportedOperationException();
          }
        })
        .build();

    final List<Expansion> expansions = expander.expand("dogs", WordNetPOS.NOUN);
    // The lemma itself surfaces as a synonym, along with the rest of its synsets.
    final Expansion dog = find(expansions, "dog");
    assertEquals(Kind.SYNONYM, dog.kind());
    assertEquals(1.0, dog.weight());
    assertNotNull(find(expansions, "domestic dog"));

    assertEquals(List.of(), expander.expand("cats", WordNetPOS.NOUN));
  }

  @Test
  void testValidationFailsLoudly() {
    assertThrows(IllegalArgumentException.class, () -> LexicalExpander.builder(null));
    final LexicalExpander.Builder builder = LexicalExpander.builder(lexicon());
    assertThrows(IllegalArgumentException.class, () -> builder.lemmatizer(null));
    assertThrows(IllegalArgumentException.class, () -> builder.maxSenses(0));
    assertThrows(IllegalArgumentException.class, () -> builder.hypernymDepth(-1));
    assertThrows(IllegalArgumentException.class, () -> builder.maxExpansions(0));
    assertThrows(IllegalArgumentException.class, () -> builder.senseDecay(0));
    assertThrows(IllegalArgumentException.class, () -> builder.senseDecay(1.5));
    assertThrows(IllegalArgumentException.class, () -> builder.depthDecay(0));
    assertThrows(IllegalArgumentException.class, () -> builder.depthDecay(1.5));

    final LexicalExpander expander = builder.build();
    assertThrows(IllegalArgumentException.class, () -> expander.expand(null));
    assertThrows(IllegalArgumentException.class, () -> expander.expand("  "));
    assertThrows(IllegalArgumentException.class, () -> expander.expand("dog", null));
  }

  /**
   * Verifies that decay products which underflow to zero are dropped instead of emitted:
   * with the smallest positive depth decay, the first hypernym level keeps the smallest
   * positive weight while the second level underflows to zero and never appears, and no
   * reported expansion carries a weight outside the documented range.
   */
  @Test
  void testUnderflowedWeightsAreDropped() {
    final List<Expansion> expansions = LexicalExpander.builder(lexicon())
        .depthDecay(Double.MIN_VALUE).hypernymDepth(2).maxSenses(1).build()
        .expand("domestic dog", WordNetPOS.NOUN);

    final Expansion canid = find(expansions, "canid");
    assertNotNull(canid);
    assertEquals(Double.MIN_VALUE, canid.weight(), 0.0);
    assertNull(find(expansions, "carnivore"));
    for (final Expansion expansion : expansions) {
      assertTrue(expansion.weight() > 0.0 && expansion.weight() <= 1.0,
          "weight out of (0, 1]: " + expansion);
    }
  }

  /**
   * Verifies that the {@link Expansion} record rejects every component
   * outside its documented range with a loud exception.
   */
  @Test
  void testExpansionValidatesItsComponents() {
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        null, Kind.SYNONYM, 0, 0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        " ", Kind.SYNONYM, 0, 0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        "dog", null, 0, 0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        "dog", Kind.SYNONYM, -1, 0, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        "dog", Kind.SYNONYM, 0, -1, 1.0));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        "dog", Kind.SYNONYM, 0, 0, 0.0));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        "dog", Kind.SYNONYM, 0, 0, 1.5));
    assertThrows(IllegalArgumentException.class, () -> new Expansion(
        "dog", Kind.SYNONYM, 0, 0, Double.NaN));
  }
}
