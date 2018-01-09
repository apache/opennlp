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

package opennlp.tools.languagemodel;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.ngram.NGramGenerator;

/**
 * Tests for {@link opennlp.tools.languagemodel.NGramLanguageModel}
 */
public class NgramLanguageModelTest {

  @Test
  public void testEmptyVocabularyProbability() {
    NGramLanguageModel model = new NGramLanguageModel();
    Assert.assertEquals("probability with an empty vocabulary is always 0",
        0d, model.calculateProbability(""), 0d);
    Assert.assertEquals("probability with an empty vocabulary is always 0",
        0d, model.calculateProbability("1", "2", "3"), 0d);
  }

  @Test
  public void testRandomVocabularyAndSentence() {
    NGramLanguageModel model = new NGramLanguageModel();
    for (String[] sentence : LanguageModelTestUtils.generateRandomVocabulary(10)) {
      model.add(sentence);
    }
    double probability = model.calculateProbability(LanguageModelTestUtils.generateRandomSentence());
    Assert.assertTrue("a probability measure should be between 0 and 1 [was "
        + probability + "]", probability >= 0 && probability <= 1);
  }

  @Test
  public void testNgramModel() {
    NGramLanguageModel model = new NGramLanguageModel(4);
    model.add("I", "saw", "the", "fox");
    model.add("the", "red", "house");
    model.add("I", "saw", "something", "nice");
    double probability = model.calculateProbability("I", "saw", "the", "red", "house");
    Assert.assertTrue("a probability measure should be between 0 and 1 [was "
        + probability + "]", probability >= 0 && probability <= 1);

    String[] tokens = model.predictNextTokens("I", "saw");
    Assert.assertNotNull(tokens);
    Assert.assertArrayEquals(new String[] {"the", "fox"}, tokens);
  }

  @Test
  public void testBigramProbability() {
    NGramLanguageModel model = new NGramLanguageModel(2);
    model.add("<s>", "I", "am", "Sam", "</s>");
    model.add("<s>", "Sam", "I", "am", "</s>");
    model.add("<s>", "I", "do", "not", "like", "green", "eggs", "and", "ham", "</s>");
    double probability = model.calculateProbability("<s>", "I");
    Assert.assertEquals(0.666d, probability, 0.001);
    probability = model.calculateProbability("Sam", "</s>");
    Assert.assertEquals(0.5d, probability, 0.001);
    probability = model.calculateProbability("<s>", "Sam");
    Assert.assertEquals(0.333d, probability, 0.001);
    probability = model.calculateProbability("am", "Sam");
    Assert.assertEquals(0.5d, probability, 0.001);
    probability = model.calculateProbability("I", "am");
    Assert.assertEquals(0.666d, probability, 0.001);
    probability = model.calculateProbability("I", "do");
    Assert.assertEquals(0.333d, probability, 0.001);
    probability = model.calculateProbability("I", "am", "Sam");
    Assert.assertEquals(0.333d, probability, 0.001);
  }

  @Test
  public void testTrigram() {
    NGramLanguageModel model = new NGramLanguageModel(3);
    model.add("I", "see", "the", "fox");
    model.add("the", "red", "house");
    model.add("I", "saw", "something", "nice");
    double probability = model.calculateProbability("I", "saw", "the", "red", "house");
    Assert.assertTrue("a probability measure should be between 0 and 1 [was "
        + probability + "]", probability >= 0 && probability <= 1);

    String[] tokens = model.predictNextTokens("I", "saw");
    Assert.assertNotNull(tokens);
    Assert.assertArrayEquals(new String[] {"something"}, tokens);
  }

  @Test
  public void testBigram() {
    NGramLanguageModel model = new NGramLanguageModel(2);
    model.add("I", "see", "the", "fox");
    model.add("the", "red", "house");
    model.add("I", "saw", "something", "nice");
    double probability = model.calculateProbability("I", "saw", "the", "red", "house");
    Assert.assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]",
        probability >= 0 && probability <= 1);

    String[] tokens = model.predictNextTokens("I", "saw");
    Assert.assertNotNull(tokens);
    Assert.assertArrayEquals(new String[] {"something"}, tokens);
  }

  @Test
  public void testSerializedNGramLanguageModel() throws Exception {
    NGramLanguageModel languageModel = new NGramLanguageModel(getClass().getResourceAsStream(
        "/opennlp/tools/ngram/ngram-model.xml"), 3);
    double probability = languageModel.calculateProbability("The", "brown", "fox", "jumped");
    Assert.assertTrue("a probability measure should be between 0 and 1 [was " + probability + "]",
        probability >= 0 && probability <= 1);
    String[] tokens = languageModel.predictNextTokens("the", "brown", "fox");
    Assert.assertNotNull(tokens);
    Assert.assertArrayEquals(new String[] {"jumped"}, tokens);
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
          languageModel.add(tokens);
        }
      }
    }
    String[] tokens = languageModel.predictNextTokens("neural",
        "network", "language");
    Assert.assertNotNull(tokens);
    Assert.assertArrayEquals(new String[] {"models"}, tokens);
    double p1 = languageModel.calculateProbability("neural", "network",
        "language", "models");
    double p2 = languageModel.calculateProbability("neural", "network",
        "language", "model");
    Assert.assertTrue(p1 > p2);
  }
}
