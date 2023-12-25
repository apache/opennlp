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

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerEvaluator;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

public class MascTokenSampleStreamTest extends AbstractMascSampleStreamTest {

  private MascTokenSampleStream stream;

  @BeforeEach
  public void setup() throws IOException {
    super.setup();
    FileFilter fileFilter = pathname -> pathname.getName().contains("MASC");
    stream = new MascTokenSampleStream(
             new MascDocumentStream(directory, true, fileFilter));
    Assertions.assertNotNull(stream);
  }

  @Test
  void read() {
    try {
      TokenSample s = stream.read();

      String expectedString = "This is a test Sentence.";
      Assertions.assertEquals(expectedString, s.getText());

      Span[] expectedTags = {
          new Span(0, 4),
          new Span(5, 7),
          new Span(8, 9),
          new Span(10, 14),
          new Span(15, 23),
          new Span(23, 24)};
      Assertions.assertArrayEquals(expectedTags, s.getTokenSpans());

      s = stream.read();
      String expectedTokens = "This is 'nother test sentence.";
      Assertions.assertEquals(expectedTokens, s.getText());

      expectedTags = new Span[] {
          new Span(0, 4),
          new Span(5, 7),
          new Span(8, 15),
          new Span(16, 20),
          new Span(21, 29),
          new Span(29, 30)};
      Assertions.assertArrayEquals(expectedTags, s.getTokenSpans());
    } catch (IOException e) {
      Assertions.fail("IO Exception: " + e.getMessage());
    }
  }

  @Test
  void close() {
    try {
      stream.close();
      TokenSample s = stream.read();
    } catch (IOException e) {
      Assertions.assertEquals(e.getMessage(),
          "You are reading an empty document stream. " +
              "Did you close it?");
    }
  }

  @Test
  void reset() {
    try {
      TokenSample s = stream.read();
      s = stream.read();
      s = stream.read();
      Assertions.assertNull(s);  //The stream should be exhausted by now

      stream.reset();

      s = stream.read();

      String expectedString = "This is a test Sentence.";
      Assertions.assertEquals(expectedString, s.getText());

      Span[] expectedTags = {
          new Span(0, 4),
          new Span(5, 7),
          new Span(8, 9),
          new Span(10, 14),
          new Span(15, 23),
          new Span(23, 24)};
      Assertions.assertArrayEquals(expectedTags, s.getTokenSpans());

    } catch (IOException e) {
      Assertions.fail("IO Exception: " + e.getMessage());
    }
  }


  @Test
  void train() {
    try {
      FileFilter fileFilter = pathname -> pathname.getName().contains("");
      ObjectStream<TokenSample> trainTokens = new MascTokenSampleStream(
          new MascDocumentStream(directory, true, fileFilter));

      TrainingParameters trainingParameters = new TrainingParameters();
      trainingParameters.put(TrainingParameters.ITERATIONS_PARAM, 20);

      TokenizerModel model = TokenizerME.train(trainTokens, new TokenizerFactory(
              "en", null, false, null), trainingParameters);

      ObjectStream<TokenSample> testTokens = new MascTokenSampleStream(
          new MascDocumentStream(directory, true, fileFilter));
      TokenizerEvaluator evaluator = new TokenizerEvaluator(new TokenizerME(model));
      evaluator.evaluate(testTokens);

    } catch (Exception e) {
      Assertions.fail("Exception raised", e);
    }

  }

}
