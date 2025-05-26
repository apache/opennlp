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
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

public class SentenceDetectorMEIT {

  @Test
  void testSentenceDetectorDownloadModel() throws IOException {

    SentenceDetectorME sentDetect = new SentenceDetectorME("en");

    // Tests sentence detector with sentDetect method
    String sampleSentences1 = "This is a test. There are many tests, this is the second.";
    String[] sents = sentDetect.sentDetect(sampleSentences1);
    Assertions.assertEquals(2, sents.length);
    Assertions.assertEquals("This is a test.", sents[0]);
    Assertions.assertEquals("There are many tests, this is the second.", sents[1]);
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);

    String sampleSentences2 = "This is a test. There are many tests, this is the second";
    sents = sentDetect.sentDetect(sampleSentences2);
    Assertions.assertEquals(2, sents.length);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
    Assertions.assertEquals("This is a test.", sents[0]);
    Assertions.assertEquals("There are many tests, this is the second", sents[1]);

    String sampleSentences3 = "This is a \"test\". He said \"There are many tests, this is the second.\"";
    sents = sentDetect.sentDetect(sampleSentences3);
    Assertions.assertEquals(2, sents.length);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
    Assertions.assertEquals("This is a \"test\".", sents[0]);
    Assertions.assertEquals("He said \"There are many tests, this is the second.\"", sents[1]);

    String sampleSentences4 = "This is a \"test\". I said \"This is a test.\"  Any questions?";
    sents = sentDetect.sentDetect(sampleSentences4);
    Assertions.assertEquals(3, sents.length);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(3, probs.length);
    Assertions.assertEquals("This is a \"test\".", sents[0]);
    Assertions.assertEquals("I said \"This is a test.\"", sents[1]);
    Assertions.assertEquals("Any questions?", sents[2]);

    String sampleSentences5 = "This is a one sentence test space at the end.    ";
    sents = sentDetect.sentDetect(sampleSentences5);
    Assertions.assertEquals(1, sentDetect.getSentenceProbabilities().length);
    Assertions.assertEquals("This is a one sentence test space at the end.", sents[0]);

    String sampleSentences6 = "This is a one sentences test with tab at the end.            ";
    sents = sentDetect.sentDetect(sampleSentences6);
    Assertions.assertEquals("This is a one sentences test with tab at the end.", sents[0]);

    String sampleSentences7 = "This is a test.    With spaces between the two sentences.";
    sents = sentDetect.sentDetect(sampleSentences7);
    Assertions.assertEquals("This is a test.", sents[0]);
    Assertions.assertEquals("With spaces between the two sentences.", sents[1]);

    String sampleSentences9 = "";
    sents = sentDetect.sentDetect(sampleSentences9);
    Assertions.assertEquals(0, sents.length);

    String sampleSentences10 = "               "; // whitespaces and tabs
    sents = sentDetect.sentDetect(sampleSentences10);
    Assertions.assertEquals(0, sents.length);

    String sampleSentences11 = "This is test sentence without a dot at the end and spaces          ";
    sents = sentDetect.sentDetect(sampleSentences11);
    Assertions.assertEquals("This is test sentence without a dot at the end and spaces", sents[0]);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);

    String sampleSentence12 = "    This is a test.";
    sents = sentDetect.sentDetect(sampleSentence12);
    Assertions.assertEquals("This is a test.", sents[0]);

    String sampleSentence13 = " This is a test";
    sents = sentDetect.sentDetect(sampleSentence13);
    Assertions.assertEquals("This is a test", sents[0]);

    // Test that sentPosDetect also works
    Span[] pos = sentDetect.sentPosDetect(sampleSentences2);
    Assertions.assertEquals(2, pos.length);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(2, probs.length);
    Assertions.assertEquals(new Span(0, 15), pos[0]);
    Assertions.assertEquals(new Span(16, 56), pos[1]);

  }

}
