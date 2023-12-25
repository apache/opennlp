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

package opennlp.tools.formats.masc;

import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import opennlp.tools.sentdetect.SentenceDetectorEvaluator;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

public class MascSentenceSampleStreamTest extends AbstractMascSampleStreamTest {
  private MascSentenceSampleStream stream;

  @BeforeEach
  public void setup() throws IOException {
    super.setup();
    FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
    stream = new MascSentenceSampleStream(
             new MascDocumentStream(directory, true, fileFilter), 2);
    Assertions.assertNotNull(stream);
  }

  @Test
  void reset() {
    try {
      //exhaust the fake file
      SentenceSample testSample = stream.read();

      //now we should get null
      testSample = stream.read();
      Assertions.assertNull(testSample);

      //by resetting, we should get good results again
      stream.reset();
      testSample = stream.read();
      Assertions.assertNotNull(testSample);

      String documentText = "This is a test Sentence. This is 'nother test sentence. ";
      List<Span> sentenceSpans = new ArrayList<>();
      sentenceSpans.add(new Span(0, 24));
      sentenceSpans.add(new Span(25, 55));
      SentenceSample expectedSample = new SentenceSample(documentText,
          sentenceSpans.toArray(new Span[0]));

      Assertions.assertEquals(testSample.toString(), expectedSample.toString());

    } catch (IOException e) {
      Assertions.fail("IO Exception");
    }
  }

  @Test
  void close() {

    try {
      stream.close();
      stream.read();
    } catch (IOException e) {
      Assertions.assertEquals(e.getMessage(),
          "You are reading an empty document stream. " +
              "Did you close it?");
    }
  }

  @Test
  void read() {
    FileFilter fileFilter = pathname -> pathname.getName().contains("");
    try {
      MascSentenceSampleStream stream = new MascSentenceSampleStream(
          new MascDocumentStream(directory, true, fileFilter), 2);

      String documentText = "This is a test Sentence. This is 'nother test sentence. ";
      List<Span> sentenceSpans = new ArrayList<>();
      sentenceSpans.add(new Span(0, 24));
      sentenceSpans.add(new Span(25, 55));

      SentenceSample expectedSample = new SentenceSample(documentText,
          sentenceSpans.toArray(new Span[0]));
      SentenceSample testSample = stream.read();
      Assertions.assertEquals(testSample.toString(), expectedSample.toString());

      //the fake file is exhausted, we should get null now
      testSample = stream.read();
      Assertions.assertNull(testSample);

    } catch (IOException e) {
      Assertions.fail("IO Exception", e);
    }

  }

  @Test
  @Disabled //todo: We can't train on the FakeMasc data, it is too small.
  void train() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("");
      ObjectStream<SentenceSample> trainSentences = new MascSentenceSampleStream(
          new MascDocumentStream(directory,
              true, fileFilter), 1);

      TrainingParameters trainingParameters = new TrainingParameters();
      trainingParameters.put(TrainingParameters.ITERATIONS_PARAM, 20);

      SentenceModel model = SentenceDetectorME.train("en", trainSentences,
          new SentenceDetectorFactory(), trainingParameters);

      ObjectStream<SentenceSample> testPOS = new MascSentenceSampleStream(
          new MascDocumentStream(directory, true, fileFilter), 1);
      SentenceDetectorEvaluator evaluator = new SentenceDetectorEvaluator(
          new SentenceDetectorME(model));
      evaluator.evaluate(testPOS);

    } catch (Exception e) {
      Assertions.fail("Exception raised", e);
    }


  }

}
