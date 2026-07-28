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

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the manual's WordNet examples (docbkx {@code wordnet.xml}) verbatim: every value
 * the chapter states is asserted here, so a change breaking this test breaks the manual.
 * The lexicon is the classpath fixture {@code mini-wn-lmf.xml}; exception lists come from
 * the sibling {@code mini-wndb} directory.
 */
public class WordNetUsageExampleTest {

  /**
   * Load, lookup, and Morphy lemmatize as the chapter shows.
   */
  @Test
  void testLoadLookupAndLemmatize() throws IOException {
    final LexicalKnowledgeBase lexicon = WnLmfReaderTest.fixture();
    final List<Synset> senses = lexicon.lookup("dog", WordNetPOS.NOUN);
    assertEquals(1, senses.size());
    assertEquals("mini-n1", senses.get(0).id());
    assertEquals(List.of("dog", "domestic dog"), senses.get(0).lemmas());
    assertEquals("a domesticated canid", senses.get(0).gloss());

    final MorphyLemmatizer lemmatizer = new MorphyLemmatizer(lexicon,
        MorphyExceptions.load(WndbReaderTest.fixtureDirectory()));
    assertEquals("mouse",
        lemmatizer.lemmatize(new String[] {"mice"}, new String[] {"NNS"})[0]);
    assertEquals("dog",
        lemmatizer.lemmatize(new String[] {"dogs"}, new String[] {"NNS"})[0]);
  }
}
