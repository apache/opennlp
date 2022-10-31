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

import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.StringList;

/**
 * Tests for evaluating accuracy of language models
 */
public class LanguageModelEvaluationTest {

  @Test
  public void testPerplexityComparison() throws Exception {

    Collection<String[]> trainingVocabulary =
        LanguageModelTestUtils.generateRandomVocabulary(1100000);
    Collection<String[]> testVocabulary =
        LanguageModelTestUtils.generateRandomVocabulary(100);

    NGramLanguageModel unigramLM = new NGramLanguageModel(1);
    for (String[] sentence : trainingVocabulary) {
      unigramLM.add(new StringList(sentence), 1, 1);
    }
    double unigramPerplexity =
        LanguageModelTestUtils.getPerplexity(unigramLM, testVocabulary, 1);

    NGramLanguageModel bigramLM = new NGramLanguageModel(2);
    for (String[] sentence : trainingVocabulary) {
      bigramLM.add(new StringList(sentence), 1, 2);
    }
    double bigramPerplexity =
        LanguageModelTestUtils.getPerplexity(bigramLM, testVocabulary, 2);
    Assert.assertTrue(unigramPerplexity >= bigramPerplexity);

    NGramLanguageModel trigramLM = new NGramLanguageModel(3);
    for (String[] sentence : trainingVocabulary) {
      trigramLM.add(new StringList(sentence), 1, 3);
    }
    double trigramPerplexity =
        LanguageModelTestUtils.getPerplexity(trigramLM, testVocabulary, 3);
    Assert.assertTrue(bigramPerplexity >= trigramPerplexity);

  }
}
