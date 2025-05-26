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

import opennlp.tools.dictionary.Dictionary;

/**
 * Tests for the {@link SentenceDetectorME} class.
 * <p>
 * Demonstrates OPENNLP-1554.
 * <p>
 * In this context, well-known known Dutch (nl_NL) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1554">OPENNLP-1554</a>
 */
public class SentenceDetectorMEDutchTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  
  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(LOCALE_DUTCH);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "dut", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, LOCALE_DUTCH);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("dut", sentdetectModel.getLanguage());
  }

  // Example taken from 'Sentences_NL.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx1() {
    final String sent1 = "Een droom, tot de vorming waarvan een bijzonder sterke compressie " +
            "heeft bijgedragen, zal het meest gunstige materiaal zijn voor dit onderzoek.";
    // Here we have one abbreviations "p." => pagina (page)
    final String sent2 = "Ik kies voor de droom van de botanische monografie die " +
            "op p. 183 en volgende wordt beschreven.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String sampleSentences = sent1 + " " + sent2;
    String[] sents = sentDetect.sentDetect(sampleSentences);
    Assertions.assertEquals(2, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    Assertions.assertEquals(sent2, sents[1]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
  }

  // Reduced example taken from 'Sentences_NL.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx2() {
    // Here we have one abbreviations: "d.w.z." = dat wil zeggen (eng.: that is to say)
    final String sent1 = "Met het oog op de overvloed aan ideeën die de analyse op elk " +
            "afzonderlijk element van de droominhoud brengt, zullen sommige lezers twijfels " +
            "hebben over het principe of alles wat later tijdens de analyse in je opkomt, " +
            "tot de droomgedachten gerekend mag worden, d.w.z. of aangenomen mag worden " +
            "dat al deze gedachten al tijdens de slaaptoestand actief waren en bijdroegen " +
            "aan de vorming van de droom?";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sent1);
    Assertions.assertEquals(1, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);
  }

  // Modified example deduced from 'Sentences_NL.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx3() {
    // Here we have two abbreviations "pag." pagina and "e.v." = en verder/en volgende (furthermore)
    final String sent1 = "De gekleurde plaat die ik openmaak (zie de analyse pag. 185 e.v.) " +
            "verwijst naar een nieuw thema, de kritiek van collega's op mijn werk, en naar " +
            "een thema dat al in de droom voorkomt, mijn hobby's, en ook naar de jeugdherinnering " +
            "waarin ik een boek met gekleurde platen uit elkaar pluk, het gedroogde exemplaar " +
            "van de plant raakt aan de gymnasiumervaring met het herbarium en benadrukt deze " +
            "herinnering in het bijzonder.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sent1);
    Assertions.assertEquals(1, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);
  }
}
