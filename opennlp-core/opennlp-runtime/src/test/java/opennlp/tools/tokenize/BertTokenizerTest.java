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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the deprecated {@link BertTokenizer} shim: constructor validation, delegation to
 * {@link WordpieceEncoder#encodeToPieces(CharSequence)}, the default special tokens, and
 * the unsupported {@link BertTokenizer#tokenizePos(String)}.
 */
@SuppressWarnings("removal") // Exercises BertTokenizer deliberately until its removal in 3.1.
class BertTokenizerTest {

  private static final List<String> VOCABULARY = List.of(
      "[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "world", "##s", "ca", "##fe", ",", "!");

  private static Set<String> vocabularySet() {
    return new HashSet<>(VOCABULARY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Hello, world!", "Caf\u00E9 hellos", ""})
  void testTokenizeReturnsTheEncoderPieceSequence(String input) {
    final BertTokenizer tokenizer = new BertTokenizer(vocabularySet());
    final WordpieceEncoder encoder = new WordpieceEncoder(VOCABULARY);
    assertArrayEquals(encoder.encodeToPieces(input), tokenizer.tokenize(input),
        "delegation broke on: " + input);
  }

  @Test
  void testDefaultConstructorsChainToBertSpecialTokensAndLowerCasing() {
    final String[] uncasedDefault = new BertTokenizer(vocabularySet()).tokenize("Hello worldS");
    assertArrayEquals(new String[] {"[CLS]", "hello", "world", "##s", "[SEP]"}, uncasedDefault);
    assertArrayEquals(uncasedDefault,
        new BertTokenizer(vocabularySet(), true).tokenize("Hello worldS"));
    assertArrayEquals(uncasedDefault,
        new BertTokenizer(vocabularySet(), true, WordpieceTokenizer.BERT_CLS_TOKEN,
            WordpieceTokenizer.BERT_SEP_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN)
            .tokenize("Hello worldS"));
  }

  @Test
  void testCasedTokenizerKeepsCase() {
    // Without lower casing, the capitalized word misses the lowercase-only vocabulary.
    assertArrayEquals(new String[] {"[CLS]", "[UNK]", "[SEP]"},
        new BertTokenizer(vocabularySet(), false).tokenize("Hello"));
  }

  @Test
  void testConstructorsRejectNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> new BertTokenizer(null));
    assertThrows(IllegalArgumentException.class, () -> new BertTokenizer(null, true));
    assertThrows(IllegalArgumentException.class,
        () -> new BertTokenizer(null, true, "[CLS]", "[SEP]", "[UNK]"));
    assertThrows(IllegalArgumentException.class,
        () -> new BertTokenizer(vocabularySet(), true, null, "[SEP]", "[UNK]"));
    assertThrows(IllegalArgumentException.class,
        () -> new BertTokenizer(vocabularySet(), true, "[CLS]", null, "[UNK]"));
    assertThrows(IllegalArgumentException.class,
        () -> new BertTokenizer(vocabularySet(), true, "[CLS]", "[SEP]", null));
  }

  @Test
  void testTokenizeRejectsNullText() {
    final BertTokenizer tokenizer = new BertTokenizer(vocabularySet());
    assertThrows(IllegalArgumentException.class, () -> tokenizer.tokenize(null));
  }

  @Test
  void testTokenizePosIsUnsupportedWithTheDocumentedMessage() {
    final BertTokenizer tokenizer = new BertTokenizer(vocabularySet());
    final UnsupportedOperationException e = assertThrows(UnsupportedOperationException.class,
        () -> tokenizer.tokenizePos("hello world"));
    assertEquals("Wordpiece tokens cannot be mapped to character spans of the original text",
        e.getMessage());
  }
}
