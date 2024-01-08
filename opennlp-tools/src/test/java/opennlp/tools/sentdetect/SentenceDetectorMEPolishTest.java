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
 * Demonstrates OPENNLP-1543.
 * <p>
 * In this context, well-known known Polish (pl_PL) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1543">OPENNLP-1543</a>
 * <p>
 * Examples taken from:
 * <a href="https://pl.wikipedia.org/wiki/Sigmund_Freud">
 *   https://pl.wikipedia.org/wiki/Sigmund_Freud</a>
 */
public class SentenceDetectorMEPolishTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  
  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(LOCALE_POLISH);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "pol", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, LOCALE_POLISH);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("pol", sentdetectModel.getLanguage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "Freud zauważył, że w normalnych warunkach silne pobudzenie emocjonalne wymaga " +
      "odpowiedniego rozładowania w formie działania (np. uraza ze strony jakiejś osoby wymaga " +
      "odwetu) lub opracowania intelektualnego.",
      "Z tego pierwszego badania można odnieść wrażenie, że elementy \"botaniczny\" i \"monografia\" " +
      "znalazły się w treści snu, ponieważ mogą mieć najszerszy kontakt z większością myśli sennych, " +
      "tj. reprezentują punkty węzłowe, w których spotyka się wiele myśli sennych, ponieważ są one " +
      "niejednoznaczne w odniesieniu do interpretacji snów."
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
    // One abbreviated term: "pt." ->  
    final String sent1 = "W szkicu autobiograficznym pt. moje życie i psychoanaliza Freud pisze, że " +
        "jego przodkowie żyli przez wiele lat w Kolonii.";
    final String sent2 = "W wyniku prześladowań Żydów w XIV i XV wieku uciekli na wschód do " +
        "Polski i na Litwę.";

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
