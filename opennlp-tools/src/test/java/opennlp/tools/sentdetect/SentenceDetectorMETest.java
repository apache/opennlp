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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link SentenceDetectorME} class.
 */

public class SentenceDetectorMETest {

  @Test
  void testSentenceDetector() throws IOException {

    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/sentdetect/Sentences.txt");

    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, 0);

    SentenceDetectorFactory factory = new SentenceDetectorFactory("eng", true, null, null);

    SentenceModel sentdetectModel = SentenceDetectorME.train(
        "eng", new SentenceSampleStream(new PlainTextByLineStream(in,
            StandardCharsets.UTF_8)), factory, mlParams);

    Assertions.assertEquals("eng", sentdetectModel.getLanguage());

    SentenceDetectorME sentDetect = new SentenceDetectorME(sentdetectModel);

    // Tests sentence detector with sentDetect method
    String sampleSentences1 = "This is a test. There are many tests, this is the second.";
    String[] sents = sentDetect.sentDetect(sampleSentences1);
    Assertions.assertEquals(sents.length, 2);
    Assertions.assertEquals(sents[0], "This is a test.");
    Assertions.assertEquals(sents[1], "There are many tests, this is the second.");
    double[] probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(probs.length, 2);

    String sampleSentences2 = "This is a test. There are many tests, this is the second";
    sents = sentDetect.sentDetect(sampleSentences2);
    Assertions.assertEquals(sents.length, 2);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(probs.length, 2);
    Assertions.assertEquals(sents[0], "This is a test.");
    Assertions.assertEquals(sents[1], "There are many tests, this is the second");

    String sampleSentences3 = "This is a \"test\". He said \"There are many tests, this is the second.\"";
    sents = sentDetect.sentDetect(sampleSentences3);
    Assertions.assertEquals(sents.length, 2);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(probs.length, 2);
    Assertions.assertEquals(sents[0], "This is a \"test\".");
    Assertions.assertEquals(sents[1], "He said \"There are many tests, this is the second.\"");

    String sampleSentences4 = "This is a \"test\". I said \"This is a test.\"  Any questions?";
    sents = sentDetect.sentDetect(sampleSentences4);
    Assertions.assertEquals(sents.length, 3);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(probs.length, 3);
    Assertions.assertEquals(sents[0], "This is a \"test\".");
    Assertions.assertEquals(sents[1], "I said \"This is a test.\"");
    Assertions.assertEquals(sents[2], "Any questions?");

    String sampleSentences5 = "This is a one sentence test space at the end.    ";
    sents = sentDetect.sentDetect(sampleSentences5);
    Assertions.assertEquals(1, sentDetect.getSentenceProbabilities().length);
    Assertions.assertEquals(sents[0], "This is a one sentence test space at the end.");

    String sampleSentences6 = "This is a one sentences test with tab at the end.            ";
    sents = sentDetect.sentDetect(sampleSentences6);
    Assertions.assertEquals(sents[0], "This is a one sentences test with tab at the end.");

    String sampleSentences7 = "This is a test.    With spaces between the two sentences.";
    sents = sentDetect.sentDetect(sampleSentences7);
    Assertions.assertEquals(sents[0], "This is a test.");
    Assertions.assertEquals(sents[1], "With spaces between the two sentences.");

    String sampleSentences9 = "";
    sents = sentDetect.sentDetect(sampleSentences9);
    Assertions.assertEquals(0, sents.length);

    String sampleSentences10 = "               "; // whitespaces and tabs
    sents = sentDetect.sentDetect(sampleSentences10);
    Assertions.assertEquals(0, sents.length);

    String sampleSentences11 = "This is test sentence without a dot at the end and spaces          ";
    sents = sentDetect.sentDetect(sampleSentences11);
    Assertions.assertEquals(sents[0], "This is test sentence without a dot at the end and spaces");
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(1, probs.length);

    String sampleSentence12 = "    This is a test.";
    sents = sentDetect.sentDetect(sampleSentence12);
    Assertions.assertEquals(sents[0], "This is a test.");

    String sampleSentence13 = " This is a test";
    sents = sentDetect.sentDetect(sampleSentence13);
    Assertions.assertEquals(sents[0], "This is a test");

    // Test that sentPosDetect also works
    Span[] pos = sentDetect.sentPosDetect(sampleSentences2);
    Assertions.assertEquals(pos.length, 2);
    probs = sentDetect.getSentenceProbabilities();
    Assertions.assertEquals(probs.length, 2);
    Assertions.assertEquals(new Span(0, 15), pos[0]);
    Assertions.assertEquals(new Span(16, 56), pos[1]);

  }

  @Test
  void testInsufficientData() {

    Assertions.assertThrows(InsufficientTrainingDataException.class, () -> {

      InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
          "/opennlp/tools/sentdetect/SentencesInsufficient.txt");

      TrainingParameters mlParams = new TrainingParameters();
      mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
      mlParams.put(TrainingParameters.CUTOFF_PARAM, 0);

      SentenceDetectorFactory factory = new SentenceDetectorFactory("eng", true, null, null);

      SentenceDetectorME.train("eng",
          new SentenceSampleStream(
              new PlainTextByLineStream(in, StandardCharsets.UTF_8)), factory, mlParams);

    });


  }

}
