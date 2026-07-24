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
package opennlp.tools.wordnet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SynsetTest {

  private static Synset dog() {
    return new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog", "domestic dog"),
        "a domesticated canid",
        Map.of(WordNetRelation.HYPERNYM, List.of("test-2-n")));
  }

  @Test
  void testComponents() {
    final Synset synset = dog();
    assertEquals("test-1-n", synset.id());
    assertEquals(WordNetPOS.NOUN, synset.pos());
    assertEquals(List.of("dog", "domestic dog"), synset.lemmas());
    assertEquals("a domesticated canid", synset.gloss());
    assertEquals(Map.of(WordNetRelation.HYPERNYM, List.of("test-2-n")), synset.relations());
  }

  @Test
  void testRelatedReturnsTargetsInOrder() {
    final Synset synset = new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), "",
        Map.of(WordNetRelation.HYPONYM, List.of("test-3-n", "test-2-n")));
    assertEquals(List.of("test-3-n", "test-2-n"), synset.related(WordNetRelation.HYPONYM));
  }

  @Test
  void testRelatedIsEmptyForAbsentRelation() {
    assertTrue(dog().related(WordNetRelation.ANTONYM).isEmpty());
  }

  @Test
  void testRelatedRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> dog().related(null));
  }

  @Test
  void testEmptyGlossAndNoRelationsAreValid() {
    final Synset synset = new Synset("test-9-r", WordNetPOS.ADVERB, List.of("well"), "", Map.of());
    assertEquals("", synset.gloss());
    assertTrue(synset.relations().isEmpty());
  }

  @Test
  void testDefensiveCopies() {
    final List<String> lemmas = new ArrayList<>(List.of("dog"));
    final List<String> targets = new ArrayList<>(List.of("test-2-n"));
    final Map<WordNetRelation, List<String>> relations = new HashMap<>();
    relations.put(WordNetRelation.HYPERNYM, targets);
    final Synset synset = new Synset("test-1-n", WordNetPOS.NOUN, lemmas, "gloss", relations);
    lemmas.add("mutated");
    targets.add("mutated");
    relations.put(WordNetRelation.ANTONYM, List.of("test-3-n"));
    assertEquals(List.of("dog"), synset.lemmas());
    assertEquals(List.of("test-2-n"), synset.related(WordNetRelation.HYPERNYM));
    assertEquals(1, synset.relations().size());
  }

  @Test
  void testReturnedCollectionsAreImmutable() {
    final Synset synset = dog();
    assertThrows(UnsupportedOperationException.class, () -> synset.lemmas().add("x"));
    assertThrows(UnsupportedOperationException.class,
        () -> synset.relations().put(WordNetRelation.ANTONYM, List.of("x")));
    assertThrows(UnsupportedOperationException.class,
        () -> synset.related(WordNetRelation.HYPERNYM).add("x"));
  }

  @Test
  void testRejectsNullOrEmptyId() {
    assertThrows(IllegalArgumentException.class,
        () -> new Synset(null, WordNetPOS.NOUN, List.of("dog"), "", Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("", WordNetPOS.NOUN, List.of("dog"), "", Map.of()));
  }

  @Test
  void testRejectsNullPos() {
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", null, List.of("dog"), "", Map.of()));
  }

  @Test
  void testRejectsNullOrEmptyLemmas() {
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, null, "", Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of(), "", Map.of()));
    final List<String> withNull = new ArrayList<>();
    withNull.add(null);
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, withNull, "", Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of(""), "", Map.of()));
  }

  @Test
  void testRejectsNullGloss() {
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), null, Map.of()));
  }

  @Test
  void testRejectsInvalidRelations() {
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), "", null));
    final Map<WordNetRelation, List<String>> nullKey = new HashMap<>();
    nullKey.put(null, List.of("test-2-n"));
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), "", nullKey));
    final Map<WordNetRelation, List<String>> nullTargets = new HashMap<>();
    nullTargets.put(WordNetRelation.HYPERNYM, null);
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), "", nullTargets));
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), "",
            Map.of(WordNetRelation.HYPERNYM, List.of())));
    assertThrows(IllegalArgumentException.class,
        () -> new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"), "",
            Map.of(WordNetRelation.HYPERNYM, List.of(""))));
  }
}
