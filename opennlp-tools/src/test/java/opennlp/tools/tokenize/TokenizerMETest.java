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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Tests for the {@link TokenizerME} class.
 *
 * This test trains the tokenizer with a few sample tokens
 * and then predicts a token. This test checks if the
 * tokenizer code can be executed.
 *
 * @see TokenizerME
 */
public class TokenizerMETest {

  @Test
  public void testTokenizerSimpleModel() throws IOException {

    TokenizerModel model = TokenizerTestUtil.createSimpleMaxentTokenModel();

    TokenizerME tokenizer = new TokenizerME(model);

    String[] tokens = tokenizer.tokenize("test,");

    Assert.assertEquals(2, tokens.length);
    Assert.assertEquals("test", tokens[0]);
    Assert.assertEquals(",", tokens[1]);
  }

  @Test
  public void testTokenizer() throws IOException {
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();

    TokenizerME tokenizer = new TokenizerME(model);
    String[] tokens = tokenizer.tokenize("Sounds like it's not properly thought through!");

    Assert.assertEquals(9, tokens.length);
    Assert.assertEquals("Sounds", tokens[0]);
    Assert.assertEquals("like", tokens[1]);
    Assert.assertEquals("it", tokens[2]);
    Assert.assertEquals("'s", tokens[3]);
    Assert.assertEquals("not", tokens[4]);
    Assert.assertEquals("properly", tokens[5]);
    Assert.assertEquals("thought", tokens[6]);
    Assert.assertEquals("through", tokens[7]);
    Assert.assertEquals("!", tokens[8]);
  }
  
  @Test(expected = InsufficientTrainingDataException.class)
  public void testInsufficientData() throws IOException {

    InputStreamFactory trainDataIn = new ResourceAsStreamFactory(
        TokenizerModel.class, "/opennlp/tools/tokenize/token-insufficient.train");

    ObjectStream<TokenSample> samples = new TokenSampleStream(
        new PlainTextByLineStream(trainDataIn, StandardCharsets.UTF_8));

    TrainingParameters mlParams = new TrainingParameters();
    mlParams.put(TrainingParameters.ITERATIONS_PARAM, 100);
    mlParams.put(TrainingParameters.CUTOFF_PARAM, 5);

    TokenizerME.train(samples, TokenizerFactory.create(null, "en", null, true, null), mlParams);

  }
  
}
