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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.postag.POSEvaluator;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerFactory;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public class MascPOSSampleStreamTest {

  @Test
  void read() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
      File directory = new File(this.getClass().getResource(
          "/opennlp/tools/formats/masc/").getFile());
      MascPOSSampleStream stream;
      stream = new MascPOSSampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      POSSample s = stream.read();

      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      Assertions.assertArrayEquals(expectedTokens, s.getSentence());

      String[] expectedTags = {"DT", "VB", "AT", "NN", "NN", "."};
      Assertions.assertArrayEquals(expectedTags, s.getTags());

      s = stream.read();
      expectedTokens = new String[] {"This", "is", "'nother", "test", "sentence", "."};
      Assertions.assertArrayEquals(expectedTokens, s.getSentence());

      expectedTags = new String[] {"DT", "VB", "RB", "NN", "NN", "."};
      Assertions.assertArrayEquals(expectedTags, s.getTags());
    } catch (IOException e) {
      Assertions.fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  void close() {
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
      Assertions.assertEquals(e.getMessage(),
          "You are reading an empty document stream. " +
              "Did you close it?");
    }
  }

  @Test
  void reset() {
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
      Assertions.assertNull(s);  //The stream should be exhausted by now

      stream.reset();

      s = stream.read();

      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      Assertions.assertArrayEquals(expectedTokens, s.getSentence());

      String[] expectedTags = {"DT", "VB", "AT", "NN", "NN", "."};
      Assertions.assertArrayEquals(expectedTags, s.getTags());

    } catch (IOException e) {
      Assertions.fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  void train() {
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
      Assertions.fail("Exception raised");
    }


  }


}
