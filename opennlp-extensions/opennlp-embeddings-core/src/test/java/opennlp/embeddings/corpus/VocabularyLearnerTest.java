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

package opennlp.embeddings.corpus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the vocabulary learner: folding, greedy longest dictionary matching for
 * multi-word terms, the always-keep rule for dictionary terms, and the frequency and
 * size filters.
 */
public class VocabularyLearnerTest {

  private static final VocabularyLearner ANY = new VocabularyLearner(1, 1000);

  private static Map<String, TermCount> byTerm(List<TermCount> terms) {
    return terms.stream().collect(Collectors.toMap(TermCount::term, t -> t));
  }

  @Test
  void testCountsFoldedWords() {
    final List<TermCount> terms =
        ANY.learn(List.of("The Court finds. The court, agreeing, FINDS."), List.of());
    final Map<String, TermCount> index = byTerm(terms);
    assertEquals(2, index.get("the").count());
    assertEquals(2, index.get("court").count());
    assertEquals(2, index.get("finds").count());
    assertEquals(1, index.get("agreeing").count());
  }

  @Test
  void testMultiWordDictionaryTermConsumesItsWords() {
    final List<TermCount> terms = ANY.learn(
        List.of("The writ of habeas corpus issued; habeas corpus is the great writ."),
        List.of("HABEAS CORPUS"));
    final Map<String, TermCount> index = byTerm(terms);
    assertEquals(2, index.get("habeas corpus").count());
    assertTrue(index.get("habeas corpus").fromDictionary());
    // The words consumed by the term are not also counted individually.
    assertEquals(null, index.get("habeas"));
    assertEquals(null, index.get("corpus"));
    assertEquals(2, index.get("writ").count());
  }

  @Test
  void testGreedyMatchPrefersTheLongestDictionaryTerm() {
    final List<TermCount> terms = ANY.learn(
        List.of("a writ of habeas corpus was granted"),
        List.of("HABEAS CORPUS", "WRIT OF HABEAS CORPUS"));
    final Map<String, TermCount> index = byTerm(terms);
    assertEquals(1, index.get("writ of habeas corpus").count());
    assertEquals(0, index.get("habeas corpus").count());
  }

  @Test
  void testDictionaryTermsAreKeptAtZeroCount() {
    final List<TermCount> terms = ANY.learn(List.of("unrelated text"), List.of("REPLEVIN"));
    final TermCount replevin = byTerm(terms).get("replevin");
    assertEquals(0, replevin.count());
    assertTrue(replevin.fromDictionary());
  }

  @Test
  void testDictionaryTermsSortByCountAndComeFirst() {
    final List<TermCount> terms = new VocabularyLearner(1, 1000).learn(
        List.of("estoppel estoppel laches word word word"),
        List.of("LACHES", "ESTOPPEL"));
    assertEquals("estoppel", terms.get(0).term());
    assertEquals("laches", terms.get(1).term());
    assertEquals("word", terms.get(2).term());
  }

  @Test
  void testMinFrequencyFiltersCorpusWordsOnly() {
    final List<TermCount> terms = new VocabularyLearner(2, 1000).learn(
        List.of("rare frequent frequent"), List.of("REPLEVIN"));
    final Map<String, TermCount> index = byTerm(terms);
    assertEquals(null, index.get("rare"));
    assertEquals(2, index.get("frequent").count());
    assertEquals(0, index.get("replevin").count());
  }

  @Test
  void testMaxTermsCapsCorpusWordsButKeepsDictionaryTerms() {
    final List<TermCount> terms = new VocabularyLearner(1, 2).learn(
        List.of("one two three one two one"),
        List.of("ALPHA", "BETA", "GAMMA"));
    // All three dictionary terms remain when the corpus-word limit is two.
    assertEquals(3, terms.size());
    assertTrue(terms.stream().allMatch(TermCount::fromDictionary));
  }

  @Test
  void testDuplicateHeadwordsMergeAfterFolding() {
    final List<TermCount> terms =
        ANY.learn(List.of("laches laches"), List.of("LACHES", "Laches", "laches"));
    assertEquals(1, terms.size());
    assertEquals(2, terms.get(0).count());
  }

  @Test
  void testValidation() {
    assertThrows(IllegalArgumentException.class, () -> new VocabularyLearner(0, 10));
    assertThrows(IllegalArgumentException.class, () -> new VocabularyLearner(1, 0));
    assertThrows(IllegalArgumentException.class, () -> ANY.learn(null, List.of()));
    assertThrows(IllegalArgumentException.class, () -> ANY.learn(List.of(), null));
    final List<String> nullText = new java.util.ArrayList<>();
    nullText.add(null);
    assertThrows(IllegalArgumentException.class, () -> ANY.learn(nullText, List.of()));
    assertThrows(IllegalArgumentException.class, () -> ANY.learn(List.of(), nullText));
  }
}
