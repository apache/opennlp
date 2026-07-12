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

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateTokenizerTest {

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
  void testCreatesLowerCasingPipelineTokenizer() {
    final Tokenizer tokenizer = AbstractDL.createPipelineTokenizer(bertVocab(), true);

    // Capitalized input must be lower cased before the wordpiece lookup.
    assertArrayEquals(new String[] {
        WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world", WordpieceTokenizer.BERT_SEP_TOKEN},
        tokenizer.tokenize("Hello World"));
  }

  @Test
  void testCreatesCasePreservingPipelineTokenizer() {
    final Tokenizer tokenizer = AbstractDL.createPipelineTokenizer(bertVocab(), false);

    // Without lower casing, capitalized words miss the lowercase-only vocabulary.
    assertArrayEquals(new String[] {
        WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN, "world",
        WordpieceTokenizer.BERT_SEP_TOKEN},
        tokenizer.tokenize("Hello world"));
  }

  @Test
  void testSelectsRobertaSpecialTokens() {
    final Tokenizer tokenizer = AbstractDL.createPipelineTokenizer(robertaVocab(), false);

    assertArrayEquals(new String[] {
        WordpieceTokenizer.ROBERTA_CLS_TOKEN, "hello", WordpieceTokenizer.ROBERTA_UNK_TOKEN,
        WordpieceTokenizer.ROBERTA_SEP_TOKEN},
        tokenizer.tokenize("hello missing"));
  }

  @Test
  void testFallsBackToBertUnknownToken() {
    final Map<String, Integer> vocab = robertaVocab();
    vocab.remove(WordpieceTokenizer.ROBERTA_UNK_TOKEN);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);

    final Tokenizer tokenizer = AbstractDL.createPipelineTokenizer(vocab, false);

    assertArrayEquals(new String[] {
        WordpieceTokenizer.ROBERTA_CLS_TOKEN, "hello", WordpieceTokenizer.BERT_UNK_TOKEN,
        WordpieceTokenizer.ROBERTA_SEP_TOKEN},
        tokenizer.tokenize("hello missing"));
  }

  @Test
  void testRejectsRobertaVocabularyWithoutUnknownToken() {
    final Map<String, Integer> vocab = robertaVocab();
    vocab.remove(WordpieceTokenizer.ROBERTA_UNK_TOKEN);

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.createPipelineTokenizer(vocab, false));
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.createWordpieceTokenizer(vocab));
  }

  @Test
  void testTokenizePosIsUnsupported() {
    final Tokenizer tokenizer = AbstractDL.createPipelineTokenizer(bertVocab(), true);
    assertThrows(UnsupportedOperationException.class, () -> tokenizer.tokenizePos("the fox"));
  }

  @Test
  void testRejectsBertVocabularyMissingSpecialTokensAtCreation() {
    final Map<String, Integer> vocab = bertVocab();
    vocab.remove(WordpieceTokenizer.BERT_UNK_TOKEN);

    assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.createPipelineTokenizer(vocab, true));
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
