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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderEvaluator;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

public class MascNamedEntitySampleStreamTest extends AbstractMascSampleStreamTest {

  private MascNamedEntitySampleStream stream;

  @BeforeEach
  public void setup() throws IOException {
    super.setup();
    FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
    stream = new MascNamedEntitySampleStream(
            new MascDocumentStream(directory, true, fileFilter));
    Assertions.assertNotNull(stream);
  }

  @Test
  void read() {
    try {
      NameSample s = stream.read();

      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      Assertions.assertArrayEquals(expectedTokens, s.getSentence());

      Span[] expectedTags = new Span[] {new Span(4, 5, "org")};
      Span[] returnedTags = s.getNames();
      // check the start/end positions
      Assertions.assertEquals(expectedTags.length, returnedTags.length);
      Assertions.assertArrayEquals(expectedTags, returnedTags);

      s = stream.read();
      expectedTokens = new String[] {"This", "is", "'nother", "test", "sentence", "."};
      Assertions.assertArrayEquals(expectedTokens, s.getSentence());

      expectedTags = new Span[] {};
      returnedTags = s.getNames();
      Assertions.assertArrayEquals(expectedTags, returnedTags);

    } catch (IOException e) {
      Assertions.fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  void close() {
    try {
      stream.close();
      NameSample s = stream.read();
    } catch (IOException e) {
      Assertions.assertEquals(e.getMessage(),
          "You are reading an empty document stream. " +
              "Did you close it?");
    }
  }

  @Test
  void reset() {
    try {
      NameSample s = stream.read();
      s = stream.read();
      s = stream.read();
      Assertions.assertNull(s);  //The stream should be exhausted by now

      stream.reset();

      s = stream.read();
      String[] expectedTokens = {"This", "is", "a", "test", "Sentence", "."};
      Assertions.assertArrayEquals(expectedTokens, s.getSentence());

      Span[] expectedTags = new Span[] {new Span(4, 5, "org")};
      Span[] returnedTags = s.getNames();
      // check the start/end positions
      Assertions.assertEquals(expectedTags.length, returnedTags.length);
      Assertions.assertArrayEquals(expectedTags, returnedTags);

    } catch (IOException e) {
      Assertions.fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  void train() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("");
      ObjectStream<NameSample> trainSample = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      TrainingParameters trainingParameters = new TrainingParameters();
      trainingParameters.put(TrainingParameters.ITERATIONS_PARAM, 100);

      TokenNameFinderModel model = NameFinderME.train("en", null, trainSample,
          trainingParameters, new TokenNameFinderFactory());

      ObjectStream<NameSample> testNames = new MascNamedEntitySampleStream(
          new MascDocumentStream(directory, true, fileFilter));
      TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(new NameFinderME(model));
      evaluator.evaluate(testNames);

    } catch (Exception e) {
      Assertions.fail("Exception raised", e);
    }
  }

}
