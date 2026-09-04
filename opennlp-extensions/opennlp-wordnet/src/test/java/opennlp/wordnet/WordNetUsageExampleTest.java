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

/** Runs the examples from the WordNet manual chapter against the test fixtures. */
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
   * Loads through the Path entry points shown in the manual:
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

  /** Load and select one language from a multi-lexicon resource as the chapter shows. */
  @Test
  void testLoadMultilingualResource() throws IOException {
    try (InputStream in = WordNetUsageExampleTest.class
        .getResourceAsStream("omw-multilingual.xml")) {
      assertNotNull(in, "Fixture omw-multilingual.xml must be on the test classpath");
      final WnLmfResource resource = WnLmfReader.readResource(in, "omw-multilingual.xml");
      final WnLmfLexicon spanish = resource.lexicon("omw-es").orElseThrow();

      assertEquals("es", spanish.language());
      final WnLmfDependency englishBase = spanish.dependencies().get(0);
      assertEquals("omw-en", englishBase.ref());
      assertEquals("2.0", englishBase.version());
      assertEquals("omw-es-02084071-n",
          spanish.knowledgeBase().lookup("perro", WordNetPOS.NOUN).get(0).id());
    }
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

  /** Navigate WN-LMF semantic-role relations as the extended relation example shows. */
  @Test
  void testNavigateSemanticRoleRelations() throws IOException {
    try (InputStream in = WordNetUsageExampleTest.class
        .getResourceAsStream("relation-usage-wn-lmf.xml")) {
      assertNotNull(in, "Fixture relation-usage-wn-lmf.xml must be on the test classpath");
      final LexicalKnowledgeBase lexicon =
          WnLmfReader.read(in, "relation-usage-wn-lmf.xml");
      final Synset purchase = lexicon.lookup("purchase", WordNetPOS.VERB).get(0);

      final String agentId = purchase.related(WordNetRelation.INVOLVED_AGENT).get(0);
      final String instrumentId = purchase.related(WordNetRelation.INVOLVED_INSTRUMENT).get(0);
      assertEquals(List.of("buyer"), lexicon.synset(agentId).orElseThrow().lemmas());
      assertEquals(List.of("payment card"),
          lexicon.synset(instrumentId).orElseThrow().lemmas());
    }
  }
}
