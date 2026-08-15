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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

  /**
   * Load through the Path entry points exactly as the chapter's loading listing shows:
   * {@code WnLmfReader.read(Path)} on a file named {@code en-wordnet.xml} (a temp-dir copy of
   * the fixture) and {@code WndbReader.read(Path)} on a WNDB {@code dict} directory.
   */
  @Test
  void testLoadFromPath(@TempDir Path tempDir) throws IOException {
    final Path file = tempDir.resolve("en-wordnet.xml");
    try (InputStream in = WnLmfReaderTest.class.getResourceAsStream("mini-wn-lmf.xml")) {
      assertNotNull(in, "Fixture mini-wn-lmf.xml must be on the test classpath");
      Files.copy(in, file);
    }
    final LexicalKnowledgeBase lexicon = WnLmfReader.read(file);
    assertEquals("mini-n1", lexicon.lookup("dog", WordNetPOS.NOUN).get(0).id());

    final LexicalKnowledgeBase wndbLexicon = WndbReader.read(WndbReaderTest.fixtureDirectory());
    assertEquals(List.of("dog", "domestic dog"),
        wndbLexicon.lookup("dog", WordNetPOS.NOUN).get(0).lemmas());
  }

  /**
   * Follow the hypernym relation from the first sense of dog as the chapter's relation
   * navigation listing shows.
   */
  @Test
  void testNavigateRelations() {
    final LexicalKnowledgeBase lexicon = WnLmfReaderTest.fixture();
    final Synset dog = lexicon.lookup("dog", WordNetPOS.NOUN).get(0);
    final List<String> parents = dog.related(WordNetRelation.HYPERNYM);
    assertEquals(List.of("mini-n2"), parents);

    final Synset parent = lexicon.synset(parents.get(0)).orElseThrow();
    assertEquals(List.of("canid"), parent.lemmas());
    assertEquals("a carnivorous mammal with nonretractile claws", parent.gloss());

    assertEquals(List.of("mini-n1"), lexicon.related("mini-n2", WordNetRelation.HYPONYM));
  }
}
