/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.tools.util.Span;

/**
 * Tests for the {@link SentenceDetectorME} class.
 */
public class SentenceDetectorMETest extends TestCase {
  
  public void testSentenceDetector() {

    InputStream in = getClass().getResourceAsStream(
        "/opennlp/tools/sentdetect/Sentences.txt");

    SentenceModel sentdetectModel = SentenceDetectorME.train(
        "en", new SentenceSampleStream(new PlainTextByLineDataStream(
        new InputStreamReader(in))), true, null, 100, 0);
    
    assertEquals("en", sentdetectModel.getLanguage());
    
    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);

    // Tests sentence detector with sentDetect method
    String sampleSentences1 = "This is a test. There are many tests, this is the second.";
    String[] sents = sentDetect.sentDetect(sampleSentences1);
    assertTrue(sents.length == 2);
    assertTrue(sents[0].equals("This is a test."));
    assertTrue(sents[1].equals("There are many tests, this is the second."));
    double[] probs = sentDetect.getSentenceProbabilities();
    assertTrue(probs.length == 2);
    String sampleSentences2 = "This is a test. There are many tests, this is the second";
    sents = sentDetect.sentDetect(sampleSentences2);
    assertTrue(sents.length == 2);
    probs = sentDetect.getSentenceProbabilities();
    assertTrue(probs.length == 2);
    assertTrue(sents[0].equals("This is a test."));
    assertTrue(sents[1].equals("There are many tests, this is the second"));
    assertTrue(sents.length == 2);
    probs = sentDetect.getSentenceProbabilities();
    assertTrue(probs.length == 2);
    String sampleSentences3 = "This is a \"test\". He said \"There are many tests, this is the second.\"";
    sents = sentDetect.sentDetect(sampleSentences3);
    assertTrue(sents.length == 2);
    probs = sentDetect.getSentenceProbabilities();
    assertTrue(probs.length == 2);
    assertTrue(sents[0].equals("This is a \"test\"."));
    assertTrue(sents[1].equals("He said \"There are many tests, this is the second.\""));
    String sampleSentences4 = "This is a \"test\". I said \"This is a test.\"  Any questions?";
    sents = sentDetect.sentDetect(sampleSentences4);
    assertTrue(sents.length == 3);
    probs = sentDetect.getSentenceProbabilities();
    assertTrue(probs.length == 3);
    assertTrue(sents[0].equals("This is a \"test\"."));
    assertTrue(sents[1].equals("I said \"This is a test.\""));
    assertTrue(sents[2].equals("Any questions?"));
    String sampleSentences5 = "This is a one sentence test space at the end.    ";
    sents = sentDetect.sentDetect(sampleSentences5);
    assertEquals(1, sentDetect.getSentenceProbabilities().length);
    assertTrue(sents[0].equals("This is a one sentence test space at the end."));
    String sampleSentences6 = "This is a one sentences test with tab at the end.            ";
    sents = sentDetect.sentDetect(sampleSentences6);
    assertTrue(sents[0].equals("This is a one sentences test with tab at the end."));
    String sampleSentences7 = "This is a test.    With spaces between the two sentences.";
    sents = sentDetect.sentDetect(sampleSentences7);
    assertTrue(sents[0].equals("This is a test."));
    assertTrue(sents[1].equals("With spaces between the two sentences."));
    String sampleSentences9 = "";
    sents = sentDetect.sentDetect(sampleSentences9);
    assertEquals(0, sents.length);
    String sampleSentences10 = "               "; // whitespaces and tabs
    sents = sentDetect.sentDetect(sampleSentences10);
    assertEquals(0, sents.length);
    String sampleSentences11 = "This is test sentence without a dot at the end and spaces          ";
    sents = sentDetect.sentDetect(sampleSentences11);
    assertTrue(sents[0].equals("This is test sentence without a dot at the end and spaces"));
    String sampleSentence12 = "    This is a test.";
    sents = sentDetect.sentDetect(sampleSentence12);
    assertTrue(sents[0].equals("This is a test."));
    
    // Test that sentPosDetect also works
    Span pos[] = sentDetect.sentPosDetect(sampleSentences2);
    assertTrue(pos.length == 2);
    probs = sentDetect.getSentenceProbabilities();
    assertTrue(probs.length == 2);
    assertEquals(new Span(0, 15), pos[0]);
    assertEquals(new Span(16, 56), pos[1]);
  }
}
