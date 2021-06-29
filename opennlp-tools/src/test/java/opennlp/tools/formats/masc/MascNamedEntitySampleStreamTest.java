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

import org.junit.Test;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderEvaluator;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MascNamedEntitySampleStreamTest {

  @Test
  public void read() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      MascNamedEntitySampleStream stream;
      stream = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      NameSample s = stream.read();

      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      assertArrayEquals(expectedTokens, s.getSentence());

      Span[] expectedTags = new Span[] {new Span(4, 5, "org")};
      Span[] returnedTags = s.getNames();
      // check the start/end positions
      assertEquals(expectedTags.length, returnedTags.length);
      for (int i = 0; i < returnedTags.length; i++) {
        assertTrue(expectedTags[i].equals(returnedTags[i]));
      }

      s = stream.read();
      expectedTokens = new String[] {"This", "is", "'nother", "test", "sentence", "."};
      assertArrayEquals(expectedTokens, s.getSentence());

      expectedTags = new Span[] {};
      returnedTags = s.getNames();
      assertArrayEquals(expectedTags, returnedTags);

    } catch (IOException e) {
      fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  public void close() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      MascNamedEntitySampleStream stream;
      stream = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      stream.close();
      NameSample s = stream.read();
    } catch (IOException e) {
      assertEquals(e.getMessage(),
          "You are reading an empty document stream. " +
              "Did you close it?");
    }
  }

  @Test
  public void reset() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      MascNamedEntitySampleStream stream;
      stream = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      NameSample s = stream.read();
      s = stream.read();
      s = stream.read();
      assertNull(s);  //The stream should be exhausted by now

      stream.reset();

      s = stream.read();
      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      assertArrayEquals(expectedTokens, s.getSentence());

      Span[] expectedTags = new Span[] {new Span(4, 5, "org")};
      Span[] returnedTags = s.getNames();
      // check the start/end positions
      assertEquals(expectedTags.length, returnedTags.length);
      for (int i = 0; i < returnedTags.length; i++) {
        assertTrue(expectedTags[i].equals(returnedTags[i]));
      }

    } catch (IOException e) {
      fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  public void train() {
    try {
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      FileFilter fileFilter = pathname -> pathname.getName().contains("");
      ObjectStream<NameSample> trainSample = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory,
              true, fileFilter));

      System.out.println("Training");
      TokenNameFinderModel model = null;
      TrainingParameters trainingParameters = new TrainingParameters();
      trainingParameters.put(TrainingParameters.ITERATIONS_PARAM, 100);

      model = NameFinderME.train("en", null, trainSample,
          trainingParameters, new TokenNameFinderFactory());

      ObjectStream<NameSample> testNames = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory, true, fileFilter));
      TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(new NameFinderME(model));
      evaluator.evaluate(testNames);

      System.out.println(evaluator.getFMeasure());

    } catch (Exception e) {
      System.err.println(e.getMessage());
      StackTraceElement[] traces = e.getStackTrace();
      for (StackTraceElement trace : traces) {
        System.err.println(trace.toString());
      }
      fail("Exception raised");
    }
  }

}
