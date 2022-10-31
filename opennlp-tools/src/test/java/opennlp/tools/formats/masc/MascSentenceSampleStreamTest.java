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

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import opennlp.tools.sentdetect.SentenceDetectorEvaluator;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class MascSentenceSampleStreamTest {

  @Test
  public void reset() {
    FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
    File directory = new File(this.getClass().getResource(
        "/opennlp/tools/formats/masc/").getFile());
    try {
      MascSentenceSampleStream stream = new MascSentenceSampleStream(
          new MascDocumentStream(directory, true, fileFilter), 2);

      //exhaust the fake file
      SentenceSample testSample = stream.read();

      //now we should get null
      testSample = stream.read();
      assertNull(testSample);

      //by resetting, we should get good results again
      stream.reset();
      testSample = stream.read();
      assertNotNull(testSample);

      String documentText = "This is a test Sentence. This is 'nother test sentence. ";
      List<Span> sentenceSpans = new ArrayList<>();
      sentenceSpans.add(new Span(0, 24));
      sentenceSpans.add(new Span(25, 55));
      SentenceSample expectedSample = new SentenceSample(documentText,
          sentenceSpans.toArray(new Span[sentenceSpans.size()]));

      assertEquals(testSample.toString(), expectedSample.toString());

    } catch (IOException e) {
      fail("IO Exception");
    }
  }

  @Test
  public void close() {

    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      MascSentenceSampleStream stream;
      stream = new MascSentenceSampleStream(
          new MascDocumentStream(directory, true, fileFilter), 2);
      stream.close();
      stream.read();
    } catch (IOException e) {
      assertEquals(e.getMessage(),
          "You are reading an empty document stream. " +
              "Did you close it?");
    }
  }

  @Test
  public void read() {
    FileFilter fileFilter = pathname -> pathname.getName().contains("");
    File directory = new File(this.getClass().getResource("/opennlp/tools/formats/masc").getFile());
    try {
      MascSentenceSampleStream stream = new MascSentenceSampleStream(
          new MascDocumentStream(directory, true, fileFilter), 2);

      String documentText = "This is a test Sentence. This is 'nother test sentence. ";
      List<Span> sentenceSpans = new ArrayList<>();
      sentenceSpans.add(new Span(0, 24));
      sentenceSpans.add(new Span(25, 55));

      SentenceSample expectedSample = new SentenceSample(documentText,
          sentenceSpans.toArray(new Span[sentenceSpans.size()]));
      SentenceSample testSample = stream.read();
      assertEquals(testSample.toString(), expectedSample.toString());

      //the fake file is exhausted, we should get null now
      testSample = stream.read();
      assertNull(testSample);

    } catch (IOException e) {
      System.out.println(e.getMessage());
      System.out.println(Arrays.toString(e.getStackTrace()));
      fail("IO Exception");
    }

  }

  @Ignore //todo: We can't train on the FakeMasc data, it is too small.
  @Test
  public void train() {
    try {
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      FileFilter fileFilter = pathname -> pathname.getName().contains("");
      ObjectStream<SentenceSample> trainSentences = new MascSentenceSampleStream(
          new MascDocumentStream(directory,
              true, fileFilter), 1);

      System.out.println("Training");
      SentenceModel model = null;
      TrainingParameters trainingParameters = new TrainingParameters();
      trainingParameters.put(TrainingParameters.ITERATIONS_PARAM, 20);

      model = SentenceDetectorME.train("en", trainSentences,
          new SentenceDetectorFactory(), trainingParameters);

      ObjectStream<SentenceSample> testPOS = new MascSentenceSampleStream(
          new MascDocumentStream(directory, true, fileFilter), 1);
      SentenceDetectorEvaluator evaluator = new SentenceDetectorEvaluator(
          new SentenceDetectorME(model));
      evaluator.evaluate(testPOS);
      System.out.println(evaluator.getFMeasure());

    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.err.println(Arrays.toString(e.getStackTrace()));
      fail("Exception raised");
    }


  }

}
