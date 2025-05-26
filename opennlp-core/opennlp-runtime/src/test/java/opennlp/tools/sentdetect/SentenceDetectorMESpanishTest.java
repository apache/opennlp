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
 * Demonstrates OPENNLP-1526.
 * <p>
 * In this context, well-known known Spanish (es_ES) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1526">OPENNLP-1526</a>
 */
public class SentenceDetectorMESpanishTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  
  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(LOCALE_SPANISH);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "spa", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, LOCALE_SPANISH);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("spa", sentdetectModel.getLanguage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "El panel de color que abro (cf. el análisis de la pág. 185) trata un tema nuevo",
      "El panel de color que abro (p.ej. el análisis de la pág. 185) trata un tema nuevo",
      "El caballo se come la ensalada de pepino con el n.º dos."
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
    // In this rather long sentence, we find two abbreviations: "cf. (see)", "pág. = Página"
    final String sent1 = "Ya Aristóteles creía en la posibilidad de hallar en los sueños la indicación" +
            "del comienzo de una enfermedad de la que en el estado de vigilia no experimentábamos aún" +
            "el menor indicio (merced a la ampliación que el sueño deja experimentar a las" +
            "impresiones), y autores médicos de cuyas opiniones se hallaba muy lejos el conceder a los" +
            "sueños un valor profético, han aceptado esta significación de los mismos como" +
            "anunciadores de la enfermedad (cf. Simón, pág. 31, y otros muchos autores más" +
            "antiguos).";
    final String sent2 = "Tampoco en la época moderna faltan ejemplos comprobados de una tal " +
            "función diagnóstica del sueño.";

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
