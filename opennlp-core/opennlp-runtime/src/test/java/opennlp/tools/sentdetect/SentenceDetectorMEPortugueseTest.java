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

package opennlp.tools.sentdetect;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.dictionary.Dictionary;

/**
 * Tests for the {@link SentenceDetectorME} class.
 * <p>
 * Demonstrates OPENNLP-1531.
 * <p>
 * In this context, well-known known Portuguese (pt) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1531">OPENNLP-1531</a>
 */
public class SentenceDetectorMEPortugueseTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};

  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(LOCALE_PORTUGUESE);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "por", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, LOCALE_PORTUGUESE);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("por", sentdetectModel.getLanguage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "Medico é o Dr. Avelino, d'aqui a legoa e meia, nas Bolsas.",
      "Mas já V. Ex.a vê, esta gentinha é pobre!",
      "S. Alteza o Gran-Duque Casimiro!"
  })
  void testSentDetectWithInlineAbbreviationsResultsInOneSentence(String input) {
    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(input);
    Assertions.assertEquals(1, sents.length);
    Assertions.assertEquals(input, sents[0]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);
  }

  @Test
  void testSentDetectWithInlineAbbreviationsResultsInTwoSentences() {
    // In this rather long sentence, we find two abbreviations: "Sr. (Senhor)", "D. (Dom)"
    final String sent1 = "O povo pernambucano, tradicionalmente inimigo dos imperadores, " +
            "lembrava-se do tempo em que o Sr. D. Pedro de Alcantara dava-se ao luxo de " +
            "visitar o norte.";
    final String sent2 = "S. Exc.a soffre de fartura.";

    String sampleSentences = sent1 + " " + sent2;
    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sampleSentences);
    Assertions.assertEquals(2, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    Assertions.assertEquals(sent2, sents[1]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
  }

}
