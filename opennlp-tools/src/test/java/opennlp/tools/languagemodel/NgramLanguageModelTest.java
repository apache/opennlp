/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package opennlp.tools.languagemodel;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import opennlp.tools.ngram.NGramGenerator;
import opennlp.tools.util.StringList;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link opennlp.tools.languagemodel.NGramLanguageModel}
 */
public class NgramLanguageModelTest {

  @Test
  public void testEmptyVocabularyProbability() throws Exception {
    NGramLanguageModel model = new NGramLanguageModel();
    assertEquals("probability with an empty vocabulary is always 0", 0d, model.calculateProbability(new StringList("")), 0d);
    assertEquals("probability with an empty vocabulary is always 0", 0d, model.calculateProbability(new StringList("1", "2", "3")), 0d);
  }

  @Test
  public void testRandomVocabularyAndSentence() throws Exception {
    NGramLanguageModel model = new NGramLanguageModel();
    for (StringList sentence : LanguageModelTestUtils.generateRandomVocabulary(10)) {
      model.add(sentence, 2, 3);
    }
    double probability = model.calculateProbability(LanguageModelTestUtils.generateRandomSentence());
    assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]", probability >= 0 && probability <= 1);
  }

  @Test
  public void testNgramModel() throws Exception {
    NGramLanguageModel model = new NGramLanguageModel(4);
    model.add(new StringList("I", "saw", "the", "fox"), 1, 4);
    model.add(new StringList("the", "red", "house"), 1, 4);
    model.add(new StringList("I", "saw", "something", "nice"), 1, 2);
    double probability = model.calculateProbability(new StringList("I", "saw", "the", "red", "house"));
    assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]", probability >= 0 && probability <= 1);

    StringList tokens = model.predictNextTokens(new StringList("I", "saw"));
    assertNotNull(tokens);
    assertEquals(new StringList("the", "fox"), tokens);
  }

  @Test
  public void testBigramProbabilityNoSmoothing() throws Exception {
    NGramLanguageModel model = new NGramLanguageModel(2, 0);
    model.add(new StringList("<s>", "I", "am", "Sam", "</s>"), 1, 2);
    model.add(new StringList("<s>", "Sam", "I", "am", "</s>"), 1, 2);
    model.add(new StringList("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>"), 1, 2);
    double probability = model.calculateProbability(new StringList("<s>", "I"));
    assertEquals(0.666d, probability, 0.001);
    probability = model.calculateProbability(new StringList("Sam", "</s>"));
    assertEquals(0.5d, probability, 0.001);
    probability = model.calculateProbability(new StringList("<s>", "Sam"));
    assertEquals(0.333d, probability, 0.001);
    probability = model.calculateProbability(new StringList("am", "Sam"));
    assertEquals(0.5d, probability, 0.001);
    probability = model.calculateProbability(new StringList("I", "am"));
    assertEquals(0.666d, probability, 0.001);
    probability = model.calculateProbability(new StringList("I", "do"));
    assertEquals(0.333d, probability, 0.001);
    probability = model.calculateProbability(new StringList("I", "am", "Sam"));
    assertEquals(0.333d, probability, 0.001);
  }

  @Test
  public void testTrigram() throws Exception {
    NGramLanguageModel model = new NGramLanguageModel(3);
    model.add(new StringList("I", "see", "the", "fox"), 2, 3);
    model.add(new StringList("the", "red", "house"), 2, 3);
    model.add(new StringList("I", "saw", "something", "nice"), 2, 3);
    double probability = model.calculateProbability(new StringList("I", "saw", "the", "red", "house"));
    assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]", probability >= 0 && probability <= 1);

    StringList tokens = model.predictNextTokens(new StringList("I", "saw"));
    assertNotNull(tokens);
    assertEquals(new StringList("something", "nice"), tokens);
  }

  @Test
  public void testBigram() throws Exception {
    NGramLanguageModel model = new NGramLanguageModel(2);
    model.add(new StringList("I", "see", "the", "fox"), 1, 2);
    model.add(new StringList("the", "red", "house"), 1, 2);
    model.add(new StringList("I", "saw", "something", "nice"), 1, 2);
    double probability = model.calculateProbability(new StringList("I", "saw", "the", "red", "house"));
    assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]", probability >= 0 && probability <= 1);

    StringList tokens = model.predictNextTokens(new StringList("I", "saw"));
    assertNotNull(tokens);
    assertEquals(new StringList("something"), tokens);
  }

  @Test
  public void testSerializedNGramLanguageModel() throws Exception {
    NGramLanguageModel languageModel = new NGramLanguageModel(getClass().getResourceAsStream("/opennlp/tools/ngram/ngram-model.xml"), 3);
    double probability = languageModel.calculateProbability(new StringList("The", "brown", "fox", "jumped"));
    assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]", probability >= 0 && probability <= 1);
    StringList tokens = languageModel.predictNextTokens(new StringList("fox"));
    assertNotNull(tokens);
    assertEquals(new StringList("jumped"), tokens);
  }

  @Test
  public void testTrigramLanguageModelCreationFromText() throws Exception {
    int ngramSize = 3;
    NGramLanguageModel languageModel = new NGramLanguageModel(ngramSize);
    InputStream stream = getClass().getResourceAsStream("/opennlp/tools/languagemodel/sentences.txt");
    for (String line : IOUtils.readLines(stream)) {
      String[] array = line.split(" ");
      List<String> split = Arrays.asList(array);
      List<String> generatedStrings = NGramGenerator.generate(split, ngramSize, " ");
      for (String generatedString : generatedStrings) {
        String[] tokens = generatedString.split(" ");
        if (tokens.length > 0) {
          languageModel.add(new StringList(tokens), 1, ngramSize);
        }
      }
    }
    StringList tokens = languageModel.predictNextTokens(new StringList("neural", "network", "language"));
    assertNotNull(tokens);
    assertEquals(new StringList("models"), tokens);
    double p1 = languageModel.calculateProbability(new StringList("neural", "network", "language", "models"));
    double p2 = languageModel.calculateProbability(new StringList("neural", "network", "language", "model"));
    assertTrue(p1 > p2);
  }
}
