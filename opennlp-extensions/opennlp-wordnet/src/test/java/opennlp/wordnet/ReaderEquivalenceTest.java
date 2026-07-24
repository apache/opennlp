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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Asserts that the WN-LMF fixture and the WNDB fixture, which encode the same miniature
 * wordnet, load into equivalent lexicon views. Synset ids are reader-minted and intentionally
 * differ, so the comparison is structural, joining synsets on their glosses (unique within the
 * fixtures) and comparing everything else through that join.
 */
public class ReaderEquivalenceTest {

  @Test
  void testBothReadersProduceEquivalentViews() {
    final InMemoryWordNetLexicon lmf = (InMemoryWordNetLexicon) WnLmfReaderTest.fixture();
    final InMemoryWordNetLexicon wndb = (InMemoryWordNetLexicon) WndbReaderTest.fixture();
    assertEquals(lmf.size(), wndb.size(), "Both fixtures encode the same synsets");

    final Map<String, Synset> wndbByGloss = byGloss(wndb);
    assertEquals(byGloss(lmf).keySet(), wndbByGloss.keySet(), "Same glosses on both sides");

    for (final Synset expected : lmf.synsets()) {
      final Synset actual = wndbByGloss.get(expected.gloss());
      assertNotNull(actual, "WNDB view has a synset for gloss: " + expected.gloss());
      assertEquals(expected.pos(), actual.pos(), "Part of speech for: " + expected.gloss());
      assertEquals(expected.lemmas(), actual.lemmas(), "Lemmas for: " + expected.gloss());
      assertEquals(relationsByGloss(expected, lmf), relationsByGloss(actual, wndb),
          "Relations for: " + expected.gloss());
    }
  }

  @Test
  void testLookupAgreesForEveryLemmaAndPos() {
    final LexicalKnowledgeBase lmf = WnLmfReaderTest.fixture();
    final InMemoryWordNetLexicon wndb = (InMemoryWordNetLexicon) WndbReaderTest.fixture();
    final Set<String> checked = new HashSet<>();
    for (final Synset synset : wndb.synsets()) {
      for (final String lemma : synset.lemmas()) {
        if (!checked.add(lemma + "/" + synset.pos())) {
          continue;
        }
        assertEquals(
            glosses(lmf.lookup(lemma, synset.pos())),
            glosses(wndb.lookup(lemma, synset.pos())),
            "Sense sequence for " + lemma + " as " + synset.pos());
      }
    }
    for (final WordNetPOS pos : WordNetPOS.values()) {
      assertEquals(lmf.contains("dog", pos), wndb.contains("dog", pos));
    }
  }

  @Test
  void testSenseOrderAgreesForMultiSenseLemma() {
    final LexicalKnowledgeBase lmf = WnLmfReaderTest.fixture();
    final LexicalKnowledgeBase wndb = WndbReaderTest.fixture();
    final List<String> lmfOrder = glosses(lmf.lookup("run", WordNetPOS.NOUN));
    final List<String> wndbOrder = glosses(wndb.lookup("run", WordNetPOS.NOUN));
    assertEquals(2, lmfOrder.size());
    assertEquals(lmfOrder, wndbOrder);
  }

  private static Map<String, Synset> byGloss(InMemoryWordNetLexicon lexicon) {
    final Map<String, Synset> byGloss = new HashMap<>();
    for (final Synset synset : lexicon.synsets()) {
      final Synset previous = byGloss.put(synset.gloss(), synset);
      assertEquals(null, previous, "Fixture glosses must be unique, duplicated: "
          + synset.gloss());
    }
    return byGloss;
  }

  // A synset's relations with targets replaced by their glosses, id-scheme independent.
  private static Map<WordNetRelation, Set<String>> relationsByGloss(Synset synset,
                                                                    LexicalKnowledgeBase lexicon) {
    final Map<WordNetRelation, Set<String>> result = new HashMap<>();
    for (final Map.Entry<WordNetRelation, List<String>> relation :
        synset.relations().entrySet()) {
      final Set<String> targetGlosses = new HashSet<>();
      for (final String targetId : relation.getValue()) {
        targetGlosses.add(lexicon.synset(targetId).orElseThrow().gloss());
      }
      result.put(relation.getKey(), targetGlosses);
    }
    return result;
  }

  private static List<String> glosses(List<Synset> synsets) {
    return synsets.stream().map(Synset::gloss).toList();
  }
}
