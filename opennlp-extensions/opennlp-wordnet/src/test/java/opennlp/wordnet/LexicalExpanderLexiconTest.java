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

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.wordnet.LexicalExpander.Expansion;
import opennlp.wordnet.LexicalExpander.Kind;

import static opennlp.wordnet.ExpansionAssertions.find;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end expansion over the miniature lexicon fixtures: the WN-LMF and WNDB readers each
 * feed the expander, and the Morphy lemmatizer bridges inflected input, exercising the whole
 * stack the way a consumer wires it.
 */
class LexicalExpanderLexiconTest {

  @Test
  void testExpansionOverTheWnLmfLexicon() {
    final LexicalExpander expander =
        LexicalExpander.builder(WnLmfReaderTest.fixture()).build();

    final List<Expansion> expansions = expander.expand("dog", WordNetPOS.NOUN);
    final Expansion domesticDog = find(expansions, "domestic dog");
    assertEquals(Kind.SYNONYM, domesticDog.kind());
    assertEquals(1.0, domesticDog.weight());
    final Expansion canid = find(expansions, "canid");
    assertEquals(Kind.HYPERNYM, canid.kind());
    assertEquals(0.5, canid.weight());
  }

  @Test
  void testExpansionOverTheWndbLexicon() {
    final LexicalExpander expander =
        LexicalExpander.builder(WndbReaderTest.fixture()).build();

    final List<Expansion> expansions = expander.expand("dog", WordNetPOS.NOUN);
    assertNotNull(find(expansions, "domestic dog"), "got " + expansions);
    final Expansion canid = find(expansions, "canid");
    assertEquals(Kind.HYPERNYM, canid.kind());
  }

  @Test
  void testUnderscoreQueryFoldsToTheMultiwordLexiconEntry() {
    // The WNDB index stores the entry as "domestic_dog"; the reader folds it to "domestic dog"
    // at load time. The expander must fold the underscore query the same way, so the entry's
    // own synset expands and the query never surfaces as its own synonym.
    final List<Expansion> expansions = LexicalExpander.builder(WndbReaderTest.fixture())
        .build().expand("domestic_dog", WordNetPOS.NOUN);

    assertEquals(List.of(
        new Expansion("dog", Kind.SYNONYM, 0, 0, 1.0),
        new Expansion("canid", Kind.HYPERNYM, 1, 0, 0.5)), expansions);
  }

  @Test
  void testReadersAgreeOnExpansions() {
    final List<Expansion> lmf = LexicalExpander.builder(WnLmfReaderTest.fixture())
        .hypernymDepth(2).build().expand("mouse", WordNetPOS.NOUN);
    final List<Expansion> wndb = LexicalExpander.builder(WndbReaderTest.fixture())
        .hypernymDepth(2).build().expand("mouse", WordNetPOS.NOUN);

    assertEquals(
        lmf.stream().map(e -> e.term() + "|" + e.kind() + "|" + e.weight()).toList(),
        wndb.stream().map(e -> e.term() + "|" + e.kind() + "|" + e.weight()).toList());
    assertNotNull(find(lmf, "rodent"), "got " + lmf);
  }

  @Test
  void testMorphyBridgesInflectedInput() {
    final LexicalKnowledgeBase lexicon = WnLmfReaderTest.fixture();
    final LexicalExpander expander = LexicalExpander.builder(lexicon)
        .lemmatizer(new MorphyLemmatizer(lexicon, MorphyExceptionsTest.fixture()))
        .build();

    // A regular inflection resolves by rule, an irregular one by the exception list.
    final List<Expansion> dogs = expander.expand("dogs", WordNetPOS.NOUN);
    assertEquals(Kind.SYNONYM, find(dogs, "dog").kind());
    assertNotNull(find(dogs, "canid"), "got " + dogs);

    final List<Expansion> mice = expander.expand("mice", WordNetPOS.NOUN);
    assertEquals(Kind.SYNONYM, find(mice, "mouse").kind());
    assertNotNull(find(mice, "rodent"), "got " + mice);
  }
}
