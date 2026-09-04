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

import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests referential-integrity validation independently of the file readers. */
public class InMemoryWordNetLexiconTest {

  private static Synset synset(String id, Map<WordNetRelation, List<String>> relations) {
    return new Synset(id, WordNetPOS.NOUN, List.of("lemma"), "a gloss", relations);
  }

  @Test
  void testAcceptsConsistentMaps() {
    final Synset a = synset("a", Map.of(WordNetRelation.HYPERNYM, List.of("b")));
    final Synset b = synset("b", Map.of());
    final InMemoryWordNetLexicon lexicon = new InMemoryWordNetLexicon(
        Map.of("a", a, "b", b),
        Map.of(InMemoryWordNetLexicon.LemmaKey.of("lemma", WordNetPOS.NOUN), List.of("a", "b")));
    assertEquals(2, lexicon.size());
    assertEquals(List.of(a, b), lexicon.lookup("lemma", WordNetPOS.NOUN));
  }

  @Test
  void testRejectsKeyThatDoesNotMatchSynsetId() {
    final Map<String, Synset> table = Map.of("wrong-key", synset("real-id", Map.of()));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(table, Map.of()));
    assertTrue(e.getMessage().contains("wrong-key"));
  }

  @Test
  void testRejectsDanglingRelationTarget() {
    final Map<String, Synset> table =
        Map.of("a", synset("a", Map.of(WordNetRelation.HYPERNYM, List.of("nope"))));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(table, Map.of()));
    assertTrue(e.getMessage().contains("nope"));
    assertTrue(e.getMessage().contains("HYPERNYM"));
  }

  @Test
  void testRejectsSenseOrderEntryWithUnknownSynset() {
    final Map<String, Synset> table = Map.of("a", synset("a", Map.of()));
    final Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senseOrder =
        Map.of(InMemoryWordNetLexicon.LemmaKey.of("lemma", WordNetPOS.NOUN), List.of("missing"));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(table, senseOrder));
    assertTrue(e.getMessage().contains("missing"));
    assertTrue(e.getMessage().contains("lemma"));
  }

  @Test
  void testRejectsDuplicateSenseOrderEntry() {
    final Map<String, Synset> table = Map.of("a", synset("a", Map.of()));
    final Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senseOrder =
        Map.of(InMemoryWordNetLexicon.LemmaKey.of("lemma", WordNetPOS.NOUN), List.of("a", "a"));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(table, senseOrder));

    assertTrue(e.getMessage().contains("duplicate synset a"));
  }

  @Test
  void testRejectsNullSenseOrderKey() {
    final Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senseOrder = new HashMap<>();
    senseOrder.put(null, List.of());

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(Map.of(), senseOrder));

    assertEquals("senseOrder key must not be null", e.getMessage());
  }

  @Test
  void testRejectsNullSenseOrderList() {
    final Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senseOrder = new HashMap<>();
    senseOrder.put(InMemoryWordNetLexicon.LemmaKey.of("lemma", WordNetPOS.NOUN), null);

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(Map.of(), senseOrder));

    assertEquals("senseOrder value must not be null", e.getMessage());
  }

  @Test
  void testRejectsSenseOrderKeyWithoutPartOfSpeech() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> InMemoryWordNetLexicon.LemmaKey.of("lemma", null));

    assertEquals("pos must not be null", e.getMessage());
  }

  @Test
  void testRejectsNullMaps() {
    assertThrows(IllegalArgumentException.class, () -> new InMemoryWordNetLexicon(null, Map.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new InMemoryWordNetLexicon(Map.of(), null));
  }
}
