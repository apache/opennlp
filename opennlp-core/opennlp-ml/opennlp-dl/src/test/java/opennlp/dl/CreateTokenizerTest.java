/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.SubwordTokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateTokenizerTest {

  private static final class TestDL extends AbstractDL {

    private TestDL(Map<String, Integer> vocab) {
      super(null, null, vocab, true);
    }

    private Tokens encode(String text) {
      return encodeTokens(text);
    }
  }

  private static Map<String, Integer> bertVocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

  private static Map<String, Integer> robertaVocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.ROBERTA_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.ROBERTA_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.ROBERTA_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    return vocab;
  }

  @Test
  void testCreatesLowerCasingWordpieceEncoder() {
    final SubwordTokenizer tokenizer = AbstractDL.createWordpieceEncoder(bertVocab(), true);

    // Capitalized input must be lower cased before the wordpiece lookup.
    assertArrayEquals(new String[] {
        WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world", WordpieceTokenizer.BERT_SEP_TOKEN},
        tokenizer.encodeToPieces("Hello World"));
  }

  @Test
  void testCreatesCasePreservingWordpieceEncoder() {
    final SubwordTokenizer tokenizer = AbstractDL.createWordpieceEncoder(bertVocab(), false);

    // Without lower casing, capitalized words miss the lowercase-only vocabulary.
    assertArrayEquals(new String[] {
        WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN, "world",
        WordpieceTokenizer.BERT_SEP_TOKEN},
        tokenizer.encodeToPieces("Hello world"));
  }

  @Test
  void testSelectsRobertaSpecialTokens() {
    final SubwordTokenizer tokenizer = AbstractDL.createWordpieceEncoder(robertaVocab(), false);

    assertArrayEquals(new String[] {
        WordpieceTokenizer.ROBERTA_CLS_TOKEN, "hello", WordpieceTokenizer.ROBERTA_UNK_TOKEN,
        WordpieceTokenizer.ROBERTA_SEP_TOKEN},
        tokenizer.encodeToPieces("hello missing"));
  }

  @Test
  void testFallsBackToBertUnknownToken() {
    final Map<String, Integer> vocab = robertaVocab();
    vocab.remove(WordpieceTokenizer.ROBERTA_UNK_TOKEN);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);

    final SubwordTokenizer tokenizer = AbstractDL.createWordpieceEncoder(vocab, false);

    assertArrayEquals(new String[] {
        WordpieceTokenizer.ROBERTA_CLS_TOKEN, "hello", WordpieceTokenizer.BERT_UNK_TOKEN,
        WordpieceTokenizer.ROBERTA_SEP_TOKEN},
        tokenizer.encodeToPieces("hello missing"));
  }

  @Test
  void testRejectsRobertaVocabularyWithoutUnknownToken() {
    final Map<String, Integer> vocab = robertaVocab();
    vocab.remove(WordpieceTokenizer.ROBERTA_UNK_TOKEN);

    assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.createWordpieceEncoder(vocab, false));
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.createWordpieceTokenizer(vocab));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      WordpieceTokenizer.BERT_CLS_TOKEN,
      WordpieceTokenizer.BERT_SEP_TOKEN,
      WordpieceTokenizer.BERT_UNK_TOKEN
  })
  void testRejectsBertVocabularyMissingSpecialTokensAtCreation(String missingToken) {
    final Map<String, Integer> vocab = bertVocab();
    vocab.remove(missingToken);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.createWordpieceEncoder(vocab, true));
    assertEquals("vocabulary must contain special token '" + missingToken + "'",
        exception.getMessage());
  }

  @Test
  void testRejectsNullVocabularyAtCreation() {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.createWordpieceEncoder(null, true));
    assertEquals("vocab must not be null", exception.getMessage());
  }

  @Test
  void testDlEncodingPreservesVocabularyIds() {
    final Map<String, Integer> vocab = Map.of(
        WordpieceTokenizer.BERT_CLS_TOKEN, 101,
        WordpieceTokenizer.BERT_SEP_TOKEN, 205,
        WordpieceTokenizer.BERT_UNK_TOKEN, 999,
        "hello", 42);

    final Tokens tokens = new TestDL(vocab).encode("Hello");

    assertArrayEquals(new String[] {"[CLS]", "hello", "[SEP]"}, tokens.tokens());
    assertArrayEquals(new long[] {101, 42, 205}, tokens.ids());
    assertArrayEquals(new long[] {1, 1, 1}, tokens.mask());
    assertArrayEquals(new long[] {0, 0, 0}, tokens.types());
  }

  @Test
  void testResolveLowerCaseUsesComponentDefaultWhenUnset() {
    final InferenceOptions options = new InferenceOptions();

    assertTrue(AbstractDL.resolveLowerCase(options, true));
    assertFalse(AbstractDL.resolveLowerCase(options, false));
  }

  @Test
  void testResolveLowerCaseOverridesComponentDefault() {
    final InferenceOptions options = new InferenceOptions();
    options.setLowerCase(false);
    assertFalse(AbstractDL.resolveLowerCase(options, true));

    options.setLowerCase(true);
    assertTrue(AbstractDL.resolveLowerCase(options, false));
  }
}
