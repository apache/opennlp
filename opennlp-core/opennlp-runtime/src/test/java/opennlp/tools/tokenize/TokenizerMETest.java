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
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.InsufficientTrainingDataException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.normalizer.CodePointSet;

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

  private static final String CONTRACTION = "it's";

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

  /**
   * Supplies sentences with the tokens the model trained from token.train produces for them.
   *
   * @return Sentence and token array pairs.
   */
  static Stream<Arguments> pinnedSentences() {
    return Stream.of(
        Arguments.of("Sounds like it's not properly thought through!",
            new String[] {"Sounds", "like", "it", "'s", "not", "properly", "thought", "through", "!"}),
        Arguments.of("Mr. Vinken is chairman of Elsevier N.V., the Dutch publishing group.",
            new String[] {"Mr", ".", "Vinken", "is", "chairman", "of", "Elsevier", "N", ".V", ".", ",",
                "the", "Dutch", "publishing", "group", "."}),
        Arguments.of("The cat wasn't in the house, and the dog wasn't either.",
            new String[] {"The", "cat", "wasn", "'t", "in", "the", "house", ",", "and", "the", "dog",
                "wasn", "'t", "either", "."}),
        Arguments.of("Pierre Vinken, 61 years old, will join the board as a nonexecutive director Nov. 29.",
            new String[] {"Pierre", "Vinken", ",", "61", "years", "old", ",", "will", "join", "the",
                "board", "as", "a", "nonexecutive", "director", "Nov", ".", "29", "."}),
        Arguments.of("Call me at 555-1234 (anytime).",
            new String[] {"Call", "me", "at", "555-1234", "(anytime)", "."}),
        Arguments.of("It's a 3.5\" disk; don't lose it!",
            new String[] {"It", "'s", "a", "3.5", "\"", "disk;", "don", "'t", "lose", "it!"}));
  }

  /**
   * Pins the output of a model trained with and one trained without the alphanumeric
   * optimization: on these sentences the policy only skips tokens the model would not split.
   */
  @ParameterizedTest
  @MethodSource("pinnedSentences")
  void testTokenizationWithAndWithoutAlphaNumericOptimization(String sentence, String[] expected)
      throws IOException {
    TokenizerME optimized = new TokenizerME(TokenizerTestUtil.createMaxentTokenModel(true));
    TokenizerME plain = new TokenizerME(TokenizerTestUtil.createMaxentTokenModel(false));

    Assertions.assertTrue(optimized.useAlphaNumericOptimization());
    Assertions.assertFalse(plain.useAlphaNumericOptimization());
    Assertions.assertArrayEquals(expected, optimized.tokenize(sentence));
    Assertions.assertArrayEquals(expected, plain.tokenize(sentence));
  }

  @Test
  void testCharacterPolicyDecidesWhichTokensStayWhole() throws IOException {
    TokenizerCharacterPolicy ascii = TokenizerCharacterPolicy.ascii();
    TokenizerCharacterPolicy withApostrophe = TokenizerCharacterPolicy.of(
        ascii.getLetters().union(CodePointSet.of('\'')), ascii.getDigits(), ascii.getMarks());
    TokenizerME tokenizer = new TokenizerME(TokenizerTestUtil.createMaxentTokenModel(
        new TokenizerFactory("eng", null, true, withApostrophe)));

    Assertions.assertArrayEquals(new String[] {CONTRACTION}, tokenizer.tokenize(CONTRACTION));
    Assertions.assertArrayEquals(new String[] {"it", "'s"},
        new TokenizerME(TokenizerTestUtil.createMaxentTokenModel(true)).tokenize(CONTRACTION));
  }

  @Test
  void testInsufficientData() {

    Assertions.assertThrows(InsufficientTrainingDataException.class, () -> {

      InputStreamFactory trainDataIn = new ResourceAsStreamFactory(
          TokenizerModel.class, "/opennlp/tools/tokenize/token-insufficient.train");

      ObjectStream<TokenSample> samples = new TokenSampleStream(
          new PlainTextByLineStream(trainDataIn, StandardCharsets.UTF_8));

      TrainingParameters mlParams = new TrainingParameters();
      mlParams.put(Parameters.ITERATIONS_PARAM, 100);
      mlParams.put(Parameters.CUTOFF_PARAM, 5);

      TokenizerME.train(samples, TokenizerFactory.create(null, "eng", null, true), mlParams);

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

  @Test
  void testKeepNewLinesLeavesTheSharedWhitespaceTokenizerAlone() throws IOException {
    TokenizerME tokenizer = new TokenizerME(TokenizerTestUtil.createMaxentTokenModel());
    tokenizer.setKeepNewLines(true);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, tokenizer.tokenize("a\nb"));

    Assertions.assertArrayEquals(new String[] {"a", "b"},
        WhitespaceTokenizer.INSTANCE.tokenize("a\nb"));
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        WhitespaceTokenizer.INSTANCE.tokenize("a\r\nb"));
  }

  @Test
  void testTokenizersWithDifferentNewLineSettingsKeepTheirOwn() throws IOException {
    TokenizerModel model = TokenizerTestUtil.createMaxentTokenModel();
    TokenizerME keeping = new TokenizerME(model);
    keeping.setKeepNewLines(true);
    TokenizerME dropping = new TokenizerME(model);

    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, keeping.tokenize("a\nb"));
    Assertions.assertArrayEquals(new String[] {"a", "b"}, dropping.tokenize("a\nb"));
    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, keeping.tokenize("a\nb"));
    Assertions.assertArrayEquals(new String[] {"a", "b"}, dropping.tokenize("a\nb"));
  }

  @Test
  void testKeepNewLinesCanBeSwitchedOffAgain() throws IOException {
    TokenizerME tokenizer = new TokenizerME(TokenizerTestUtil.createMaxentTokenModel());
    tokenizer.setKeepNewLines(true);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, tokenizer.tokenize("a\nb"));
    tokenizer.setKeepNewLines(false);
    Assertions.assertArrayEquals(new String[] {"a", "b"}, tokenizer.tokenize("a\nb"));
  }

}
