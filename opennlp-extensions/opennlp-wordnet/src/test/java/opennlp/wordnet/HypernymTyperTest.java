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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.WordNetRelation;

/**
 * Tests that {@link HypernymTyper} labels a word by its nearest anchored hypernym over
 * the fixture taxonomy of {@link SynsetSimilarityTest}, follows instance hypernymy,
 * prefers the closer of two anchors, and validates its arguments.
 */
public class HypernymTyperTest {

  /**
   * @return A typer over the shared fixture taxonomy with person and location anchors.
   *         Never {@code null}.
   */
  private static HypernymTyper typer() {
    return new HypernymTyper(taxonomy(),
        Map.of("person", "person", "location", "location"));
  }

  /**
   * @return The shared fixture taxonomy. Never {@code null}.
   */
  private static SynsetSimilarityTest.FixtureKnowledgeBase taxonomy() {
    final SynsetSimilarityTest.FixtureKnowledgeBase kb =
        new SynsetSimilarityTest.FixtureKnowledgeBase();
    kb.add("n1", "entity", WordNetRelation.HYPERNYM);
    kb.add("n2", "physical", WordNetRelation.HYPERNYM, "n1");
    kb.add("n3", "organism", WordNetRelation.HYPERNYM, "n2");
    kb.add("n4", "person", WordNetRelation.HYPERNYM, "n3");
    kb.add("n5", "scientist", WordNetRelation.HYPERNYM, "n4");
    kb.add("n6", "chemist", WordNetRelation.HYPERNYM, "n5");
    kb.add("n7", "location", WordNetRelation.HYPERNYM, "n2");
    kb.add("n8", "city", WordNetRelation.HYPERNYM, "n7");
    kb.add("n11", "paris", WordNetRelation.INSTANCE_HYPERNYM, "n8");
    kb.add("n12", "abstract", WordNetRelation.HYPERNYM);
    return kb;
  }

  /**
   * Verifies that a noun whose hypernym chain reaches an anchor receives that anchor's
   * label, both through plain and instance hypernymy, and that the anchor lemma itself
   * is typed with its own label at distance zero.
   */
  @Test
  void testTypesThroughHypernymAndInstanceChains() {
    final HypernymTyper typer = typer();
    Assertions.assertEquals(Optional.of("person"), typer.type("chemist"));
    Assertions.assertEquals(Optional.of("location"), typer.type("paris"));
    Assertions.assertEquals(Optional.of("person"), typer.type("person"));
    Assertions.assertEquals(Optional.of("location"), typer.typeSynset("n8"));
  }

  /**
   * Verifies that no label is produced when no sense of the word, or no ancestor of
   * the synset, reaches an anchor.
   */
  @Test
  void testUnreachableAnchorsYieldEmpty() {
    final HypernymTyper typer = typer();
    Assertions.assertEquals(Optional.empty(), typer.type("abstract"));
    Assertions.assertEquals(Optional.empty(), typer.type("unknownword"));
    Assertions.assertEquals(Optional.empty(), typer.typeSynset("n12"));
    Assertions.assertEquals(Optional.empty(), typer.typeSynset("missing"));
  }

  /**
   * Verifies that the nearest anchor wins: with scientist registered as its own
   * anchor, a chemist is a scientist rather than the more distant person.
   */
  @Test
  void testNearestAnchorWins() {
    final Map<String, String> anchors = new LinkedHashMap<>();
    anchors.put("person", "person");
    anchors.put("scientist", "scientist");
    final HypernymTyper typer = new HypernymTyper(taxonomy(), anchors);
    Assertions.assertEquals(Optional.of("scientist"), typer.type("chemist"));
    Assertions.assertEquals(Optional.of("scientist"), typer.type("scientist"));
    // the walk is upward only, so an ancestor of an anchor is never typed by it
    Assertions.assertEquals(Optional.empty(), typer.type("organism"));
  }

  /**
   * Verifies that invalid construction and query arguments are rejected: null or
   * empty inputs, blank anchor entries, and an anchor lemma the knowledge base does
   * not know.
   */
  @Test
  void testInvalidArguments() {
    final Map<String, String> anchors = Map.of("person", "person");
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(null, anchors));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of()));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of(" ", "person")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of("person", "\u00A0")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of("notaword", "label")));
    final HypernymTyper typer = typer();
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.type(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.type(" "));
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.typeSynset(null));
  }
}
