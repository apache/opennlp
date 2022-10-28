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

package opennlp.tools.tokenize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link TokenizerME} class.
 * <p>
 * This test trains the tokenizer with a few sample tokens
 * and then predicts a token. This test checks if the
 * tokenizer code can be executed.
 *
 * @see TokenizerME
 */
public class TokenizerMETest {

  @Test
  void testTokenizerSimpleModel() throws IOException {

    TokenizerModel model = TokenizerTestUtil.createSimpleMaxentTokenModel();

    TokenizerME tokenizer = new TokenizerME(model);

    String[] tokens = tokenizer.tokenize("test,");

    Assertions.assertEquals(2, tokens.length);
    Assertions.assertEquals("test", tokens[0]);
    Assertions.assertEquals(",", tokens[1]);
  }

  @Test
  void testTokenizer() throws IOException {
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();

    TokenizerME tokenizer = new TokenizerME(model);
    String[] tokens = tokenizer.tokenize("Sounds like it's not properly thought through!");

    Assertions.assertEquals(9, tokens.length);
    Assertions.assertEquals("Sounds", tokens[0]);
    Assertions.assertEquals("like", tokens[1]);
    Assertions.assertEquals("it", tokens[2]);
    Assertions.assertEquals("'s", tokens[3]);
    Assertions.assertEquals("not", tokens[4]);
    Assertions.assertEquals("properly", tokens[5]);
    Assertions.assertEquals("thought", tokens[6]);
    Assertions.assertEquals("through", tokens[7]);
    Assertions.assertEquals("!", tokens[8]);
  }

  @Test
  void testInsufficientData() {

    Assertions.assertThrows(InsufficientTrainingDataException.class, () -> {

      InputStreamFactory trainDataIn = new ResourceAsStreamFactory(
          TokenizerModel.class, "/opennlp/tools/tokenize/token-insufficient.train");

      ObjectStream<TokenSample> samples = new TokenSampleStream(
          new PlainTextByLineStream(trainDataIn, StandardCharsets.UTF_8));

      TrainingParameters mlParams = new TrainingParameters();
      mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
      mlParams.put(TrainingParameters.CUTOFF_PARAM, 5);

      TokenizerME.train(samples, TokenizerFactory.create(null, "eng", null, true, null), mlParams);

    });


  }

  @Test
  void testNewLineAwareTokenization() throws IOException {
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();
    TokenizerME tokenizer = new TokenizerME(model);
    tokenizer.setKeepNewLines(true);

    Assertions.assertEquals(2, tokenizer.tokenize("a\n").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n"}, tokenizer.tokenize("a\n"));

    Assertions.assertEquals(3, tokenizer.tokenize("a\nb").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, tokenizer.tokenize("a\nb"));

    Assertions.assertEquals(4, tokenizer.tokenize("a\n\n b").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "\n", "b"}, tokenizer.tokenize("a\n\n b"));

    Assertions.assertEquals(7, tokenizer.tokenize("a\n\n b\n\n c").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "\n", "b", "\n", "\n", "c"},
        tokenizer.tokenize("a\n\n b\n\n c"));
  }

  @Test
  void testTokenizationOfStringWithWindowsNewLineTokens() throws IOException {
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();
    TokenizerME tokenizer = new TokenizerME(model);
    tokenizer.setKeepNewLines(true);

    Assertions.assertEquals(3, tokenizer.tokenize("a\r\n").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n"}, tokenizer.tokenize("a\r\n"));

    Assertions.assertEquals(4, tokenizer.tokenize("a\r\nb").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "b"}, tokenizer.tokenize("a\r\nb"));

    Assertions.assertEquals(6, tokenizer.tokenize("a\r\n\r\n b").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b"}, tokenizer
        .tokenize("a\r\n\r\n b"));

    Assertions.assertEquals(11, tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b", "\r", "\n", "\r", "\n", "c"},
        tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c"));
  }

}
