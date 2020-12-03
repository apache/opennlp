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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.Span;

public class SentenceDetectorMEIT {

  @Test
  public void testSentenceDetectorDownloadModel() throws IOException {

    SentenceDetectorME sentDetect = new SentenceDetectorME("en");

    // Tests sentence detector with sentDetect method
    String sampleSentences1 = "This is a test. There are many tests, this is the second.";
    String[] sents = sentDetect.sentDetect(sampleSentences1);
    Assert.assertEquals(sents.length,2);
    Assert.assertEquals(sents[0],"This is a test.");
    Assert.assertEquals(sents[1],"There are many tests, this is the second.");
    double[] probs = sentDetect.getSentenceProbabilities();
    Assert.assertEquals(probs.length,2);

    String sampleSentences2 = "This is a test. There are many tests, this is the second";
    sents = sentDetect.sentDetect(sampleSentences2);
    Assert.assertEquals(sents.length,2);
    probs = sentDetect.getSentenceProbabilities();
    Assert.assertEquals(probs.length,2);
    Assert.assertEquals(sents[0],"This is a test.");
    Assert.assertEquals(sents[1],"There are many tests, this is the second");

    String sampleSentences3 = "This is a \"test\". He said \"There are many tests, this is the second.\"";
    sents = sentDetect.sentDetect(sampleSentences3);
    Assert.assertEquals(sents.length,2);
    probs = sentDetect.getSentenceProbabilities();
    Assert.assertEquals(probs.length,2);
    Assert.assertEquals(sents[0],"This is a \"test\".");
    Assert.assertEquals(sents[1],"He said \"There are many tests, this is the second.\"");

    String sampleSentences4 = "This is a \"test\". I said \"This is a test.\"  Any questions?";
    sents = sentDetect.sentDetect(sampleSentences4);
    Assert.assertEquals(sents.length,3);
    probs = sentDetect.getSentenceProbabilities();
    Assert.assertEquals(probs.length,3);
    Assert.assertEquals(sents[0],"This is a \"test\".");
    Assert.assertEquals(sents[1],"I said \"This is a test.\"");
    Assert.assertEquals(sents[2],"Any questions?");

    String sampleSentences5 = "This is a one sentence test space at the end.    ";
    sents = sentDetect.sentDetect(sampleSentences5);
    Assert.assertEquals(1, sentDetect.getSentenceProbabilities().length);
    Assert.assertEquals(sents[0],"This is a one sentence test space at the end.");

    String sampleSentences6 = "This is a one sentences test with tab at the end.            ";
    sents = sentDetect.sentDetect(sampleSentences6);
    Assert.assertEquals(sents[0],"This is a one sentences test with tab at the end.");

    String sampleSentences7 = "This is a test.    With spaces between the two sentences.";
    sents = sentDetect.sentDetect(sampleSentences7);
    Assert.assertEquals(sents[0],"This is a test.");
    Assert.assertEquals(sents[1],"With spaces between the two sentences.");

    String sampleSentences9 = "";
    sents = sentDetect.sentDetect(sampleSentences9);
    Assert.assertEquals(0, sents.length);

    String sampleSentences10 = "               "; // whitespaces and tabs
    sents = sentDetect.sentDetect(sampleSentences10);
    Assert.assertEquals(0, sents.length);

    String sampleSentences11 = "This is test sentence without a dot at the end and spaces          ";
    sents = sentDetect.sentDetect(sampleSentences11);
    Assert.assertEquals(sents[0],"This is test sentence without a dot at the end and spaces");
    probs = sentDetect.getSentenceProbabilities();
    Assert.assertEquals(1, probs.length);

    String sampleSentence12 = "    This is a test.";
    sents = sentDetect.sentDetect(sampleSentence12);
    Assert.assertEquals(sents[0],"This is a test.");

    String sampleSentence13 = " This is a test";
    sents = sentDetect.sentDetect(sampleSentence13);
    Assert.assertEquals(sents[0],"This is a test");

    // Test that sentPosDetect also works
    Span[] pos = sentDetect.sentPosDetect(sampleSentences2);
    Assert.assertEquals(pos.length,2);
    probs = sentDetect.getSentenceProbabilities();
    Assert.assertEquals(probs.length,2);
    Assert.assertEquals(new Span(0, 15), pos[0]);
    Assert.assertEquals(new Span(16, 56), pos[1]);

  }
  
}
