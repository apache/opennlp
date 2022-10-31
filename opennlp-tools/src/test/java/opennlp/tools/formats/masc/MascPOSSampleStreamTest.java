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
import java.util.Arrays;

import org.junit.Test;

import opennlp.tools.postag.POSEvaluator;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class MascPOSSampleStreamTest {

  @Test
  public void read() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      MascPOSSampleStream stream;
      stream = new MascPOSSampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      POSSample s = stream.read();

      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      assertArrayEquals(expectedTokens, s.getSentence());

      String[] expectedTags = {"DT", "VB", "AT", "NN", "NN", "."};
      assertArrayEquals(expectedTags, s.getTags());

      s = stream.read();
      expectedTokens = new String[] {"This", "is", "'nother", "test", "sentence", "."};
      assertArrayEquals(expectedTokens, s.getSentence());

      expectedTags = new String[] {"DT", "VB", "RB", "NN", "NN", "."};
      assertArrayEquals(expectedTags, s.getTags());
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
      MascPOSSampleStream stream;
      stream = new MascPOSSampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      stream.close();
      POSSample s = stream.read();
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
      MascPOSSampleStream stream;
      stream = new MascPOSSampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      POSSample s = stream.read();
      s = stream.read();
      s = stream.read();
      assertNull(s);  //The stream should be exhausted by now

      stream.reset();

      s = stream.read();

      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      assertArrayEquals(expectedTokens, s.getSentence());

      String[] expectedTags = {"DT", "VB", "AT", "NN", "NN", "."};
      assertArrayEquals(expectedTags, s.getTags());

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
      ObjectStream<POSSample> trainPOS = new MascPOSSampleStream(
          new MascDocumentStream(directory,
              true, fileFilter));

      System.out.println("Training");
      POSModel model = null;
      TrainingParameters trainingParameters = new TrainingParameters();
      trainingParameters.put(TrainingParameters.ITERATIONS_PARAM, 20);

      model = POSTaggerME.train("en", trainPOS,
          trainingParameters, new POSTaggerFactory());

      ObjectStream<POSSample> testPOS = new MascPOSSampleStream(new MascDocumentStream(directory,
          true, fileFilter));
      POSEvaluator evaluator = new POSEvaluator(new POSTaggerME(model));
      evaluator.evaluate(testPOS);
      System.out.println("Accuracy: " + evaluator.getWordAccuracy());
      System.out.println("Words: " + evaluator.getWordCount());

    } catch (Exception e) {
      System.err.println(e.getMessage());
      System.err.println(Arrays.toString(e.getStackTrace()));
      fail("Exception raised");
    }


  }


}
