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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Tests for the {@link SentenceDetectorME} class.
 * <p>
 * Demonstrates OPENNLP-1530.
 * <p>
 * In this context, well-known known Italian (it_IT) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-1530">OPENNLP-1530</a>
 */
public class SentenceDetectorMEItalianTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  
  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(Locale.ITALIAN);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "ita", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, Locale.ITALIAN);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("ita", sentdetectModel.getLanguage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "La chiesa fu costruita fra il 1258 ed il 1308 ca. come chiesa del convento degli Agostiniani.",
      "Laureato in Scienza Politiche presso l'Università S. Pio V, di Roma.",
      "La chiesa, che prima dipendeva da S. Giovanni Battista sopra Quarona, " +
              "fu innalzata parrocchia nel 1588, con la posa della croce sulla sommità."
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
    // In this rather long sentence, we find two abbreviations: "S. = San" and "S. = Santa"
    final String sent1 = "L' antico Conservatorio di S. Giuseppe e Teresa di Pucara del 1662 è " +
        "soggetto a speculazione edilizia.";
    final String sent2 = "La consegna ha avuto luogo nell'auditorium S. Chiara di Trento, che per " +
        "l'occasione era pieno.";

    String sampleSentences = sent1 + " " + sent2;
    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sampleSentences);
    Assertions.assertEquals(2, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    Assertions.assertEquals(sent2, sents[1]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
  }

  /*
   * Verifies OPENNLP-1163,
   * see: https://issues.apache.org/jira/browse/OPENNLP-1163
   *
   * Original problem:
   * "Even though the abbreviation "art." was included in the XML file,
   * the sentence detector breaks the sentence on instances of this
   * abbreviation preceded by article and apostrophe
   * (e.g. nell'art., dall'art., dell'art.)"
   *
   * This test demonstrates it is working, with "art." in the abbreviations xml file.
   */
  @Test
  void testSentDetectOpenNLP1163() throws IOException {
    final SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);

    final String testResource = "/opennlp/tools/sentdetect/Test-Sample_OPENNLP-1163.txt";
    InputStreamFactory in = new ResourceAsStreamFactory(
            AbstractSentenceDetectorTest.class, testResource);
    List<String> detectedSentences = new ArrayList<>();
    try (PlainTextByLineStream stream = new PlainTextByLineStream(in, StandardCharsets.UTF_8)) {
      StringBuilder text = new StringBuilder();
      String line;
      do {
        line = stream.read();
        text.append(line);
      } while (line != null);

      String[] sents = sentDetect.sentDetect(text.toString());
      detectedSentences.addAll(Arrays.asList(sents));
    }
    
    // Test
    Assertions.assertEquals(11, detectedSentences.size());
    for (String sent : detectedSentences) {
      Assertions.assertFalse(hasMisplacedAbbreviationAtEnd(sent));
    }
  }

  private boolean hasMisplacedAbbreviationAtEnd(String sent) {
    return sent.endsWith("dell'art.") || sent.endsWith("dall'art.") || sent.endsWith("nell'art.");
  }
}
