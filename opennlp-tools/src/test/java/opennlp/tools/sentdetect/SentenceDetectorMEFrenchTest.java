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
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.dictionary.Dictionary;

/**
 * Tests for the {@link SentenceDetectorME} class.
 * <p>
 * Demonstrates OPENNLP-1540.
 * <p>
 * In this context, well-known known French (fr_FR) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1540">OPENNLP-1540</a>
 */
public class SentenceDetectorMEFrenchTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  
  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(Locale.FRENCH);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "fra", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, Locale.FRENCH);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("fra", sentdetectModel.getLanguage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "Je choisis le rêve de la monographie botanique communiqué à la p. 205.",
      "Devant la surabondance des idées incidentes que l’analyse apporte à propos de chacun des " +
      "éléments du contenu de rêve, un doute principiel s’éveillera chez plus d’un lecteur: peut-on " +
      "donc compter au nombre des pensées de rêve tout ce qui, après coup, vous vient à l’idée dans " +
      "l’analyse, c.-à-d. peut-on supposer que toutes ces pensées ont déjà été actives pendant l’état " +
      "de sommeil et ont coopéré à la formation du rêve?"
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
    // In this rather long sentence, we find two abbreviations: "cf. = see", "p. = page"
    final String sent1 = "La planche en couleurs que je déplie conduit (cf. l’analyse, p. 208) à un " +
            "nouveau thème – les critiques que les confrères font de mes travaux – et à un thème déjà " +
            "représenté dans le rêve, celui de mes occupations favorites, puis encore au souvenir " +
            "d’enfance où j’arrache les pages d’un livre aux planches en couleurs; l’exemplaire " +
            "séché de la plante touche à l’expérience de l’herbier vécue au lycée et fait " +
            "particulièrement ressortir ce souvenir.";
    final String sent2 = "Je vois donc de quelle sorte est la relation entre le contenu du rêve et les " +
            "pensées du rêve: non seulement les éléments du rêve sont déterminés de multiples façons, " +
            "mais les pensées du rêve prises une à une sont aussi représentées dans le rêve " +
            "par plusieurs éléments.";

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
