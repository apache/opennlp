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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;

/**
 * Runs the manual's WordNet load and lookup examples (docbkx {@code wordnet.xml})
 * verbatim: every value the chapter states is asserted here, so a change breaking this
 * test breaks the manual. The lexicon is the classpath fixture {@code mini-wn-lmf.xml};
 * exception lists come from the sibling {@code mini-wndb} directory.
 */
public class WordNetUsageExampleTest {

  private static LexicalKnowledgeBase loadMiniWnLmf() throws IOException {
    try (InputStream in = WordNetUsageExampleTest.class.getResourceAsStream("mini-wn-lmf.xml")) {
      Assertions.assertNotNull(in, "Fixture mini-wn-lmf.xml must be on the test classpath");
      return WnLmfReader.read(in, "mini-wn-lmf.xml");
    }
  }

  private static Path miniWndbDirectory() {
    final URL url = WordNetUsageExampleTest.class.getResource("mini-wndb");
    Assertions.assertNotNull(url, "Fixture directory mini-wndb must be on the test classpath");
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Unexpected fixture URI: " + url, e);
    }
  }

  /**
   * Load, lookup, and Morphy lemmatize as the chapter shows.
   */
  @Test
  void testLoadLookupAndLemmatize() throws IOException {
    final LexicalKnowledgeBase lexicon = loadMiniWnLmf();
    final List<Synset> senses = lexicon.lookup("dog", WordNetPOS.NOUN);
    Assertions.assertEquals(1, senses.size());
    Assertions.assertEquals("mini-n1", senses.get(0).id());
    Assertions.assertEquals(List.of("dog", "domestic dog"), senses.get(0).lemmas());
    Assertions.assertEquals("a domesticated canid", senses.get(0).gloss());

    final MorphyLemmatizer lemmatizer =
        new MorphyLemmatizer(lexicon, MorphyExceptions.load(miniWndbDirectory()));
    Assertions.assertEquals("mouse",
        lemmatizer.lemmatize(new String[] {"mice"}, new String[] {"NNS"})[0]);
    Assertions.assertEquals("dog",
        lemmatizer.lemmatize(new String[] {"dogs"}, new String[] {"NNS"})[0]);
  }
}
