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

package opennlp.dl.vectors;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.dl.Tokens;
import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SentenceVectorsDLTest {

  private static Map<String, Integer> vocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

  @Test
  void testTokenizeUsesSingleSegmentBertEncoding() {
    final Map<String, Integer> vocab = vocab();
    final WordpieceTokenizer tokenizer = new WordpieceTokenizer(vocab.keySet());

    final Tokens tokens = SentenceVectorsDL.tokenize("hello world", tokenizer, vocab);

    assertArrayEquals(new String[] {
        WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world", WordpieceTokenizer.BERT_SEP_TOKEN},
        tokens.tokens());
    assertArrayEquals(new long[] {0, 3, 4, 1}, tokens.ids());
    // The attention mask must be 1 for every real token.
    assertArrayEquals(new long[] {1, 1, 1, 1}, tokens.mask());
    // Single-segment input: all token type ids must be 0.
    assertArrayEquals(new long[] {0, 0, 0, 0}, tokens.types());
  }

  @Test
  void testTokenizeMapsOutOfVocabularyWordsToUnknownToken() {
    final Map<String, Integer> vocab = vocab();
    final WordpieceTokenizer tokenizer = new WordpieceTokenizer(vocab.keySet());

    final Tokens tokens = SentenceVectorsDL.tokenize("hello xyz", tokenizer, vocab);

    assertArrayEquals(new long[] {0, 3, 2, 1}, tokens.ids());
    assertEquals(WordpieceTokenizer.BERT_UNK_TOKEN, tokens.tokens()[2]);
  }

  @Test
  void testTokenizeRejectsTokensMissingFromVocabulary() {
    final Map<String, Integer> vocab = vocab();
    vocab.remove(WordpieceTokenizer.BERT_UNK_TOKEN);
    final WordpieceTokenizer tokenizer = new WordpieceTokenizer(vocab.keySet());

    assertThrows(IllegalArgumentException.class, () ->
        SentenceVectorsDL.tokenize("hello xyz", tokenizer, vocab));
  }
}
