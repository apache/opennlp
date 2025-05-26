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

import opennlp.tools.dictionary.Dictionary;

/**
 * Tests for the {@link SentenceDetectorME} class.
 * <p>
 * Verifies OPENNLP-793 in combination with OPENNLP-570.
 * <p>
 * In this context, well-known known German (de_DE) abbreviations must be respected,
 * so that words abbreviated with one or more '.' characters do not
 * result in incorrect sentence boundaries.
 * <p>
 * See:
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-793">OPENNLP-793</a>
 * <a href="https://issues.apache.org/jira/projects/OPENNLP/issues/OPENNLP-570">OPENNLP-570</a>
 */
public class SentenceDetectorMEGermanTest extends AbstractSentenceDetectorTest {

  private static final char[] EOS_CHARS = {'.', '?', '!'};
  
  private static SentenceModel sentdetectModel;

  @BeforeAll
  public static void prepareResources() throws IOException {
    Dictionary abbreviationDict = loadAbbDictionary(Locale.GERMAN);
    SentenceDetectorFactory factory = new SentenceDetectorFactory(
            "deu", true, abbreviationDict, EOS_CHARS);
    sentdetectModel = train(factory, Locale.GERMAN);
    Assertions.assertNotNull(sentdetectModel);
    Assertions.assertEquals("deu", sentdetectModel.getLanguage());
  }

  // Example taken from 'Sentences_DE.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx1() {
    final String sent1 = "Ein Traum, zu dessen Bildung eine besonders starke Verdichtung beigetragen, " +
            "wird für diese Untersuchung das günstigste Material sein.";
    // Here we have two abbreviations "S. = Seite" and "ff. = folgende (Plural)"
    final String sent2 = "Ich wähle den auf S. 183 ff. mitgeteilten Traum von der botanischen Monographie.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String sampleSentences = sent1 + " " + sent2;
    String[] sents = sentDetect.sentDetect(sampleSentences);
    Assertions.assertEquals(2, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    Assertions.assertEquals(sent2, sents[1]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
  }

  // Reduced example taken from 'Sentences_DE.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx2() {
    // Here we have three abbreviations: "S. = Seite", "vgl. = vergleiche", and "f. = folgende (Singular)"
    final String sent1 = "Die farbige Tafel, die ich aufschlage, " +
            "geht (vgl. die Analyse S. 185 f.) auf ein neues Thema ein.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sent1);
    Assertions.assertEquals(1, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);
  }

  // Modified example deduced from 'Sentences_DE.txt'
  @Test
  void testSentDetectWithInlineAbbreviationsEx3() {
    // Here we have two abbreviations "z. B. = zum Beispiel" and "S. = Seite"
    final String sent1 = "Die farbige Tafel, die ich aufschlage, " +
            "geht (z. B. die Analyse S. 185) auf ein neues Thema ein.";

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);
    String[] sents = sentDetect.sentDetect(sent1);
    Assertions.assertEquals(1, sents.length);
    Assertions.assertEquals(sent1, sents[0]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);
  }
}
