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
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WordpieceTokenizerTest {

  @Test
  void testSentence() {

    final Tokenizer tokenizer = new WordpieceTokenizer(getVocabulary());
    final String[] tokens = tokenizer.tokenize("the quick brown fox jumps over the very lazy dog");

    final String[] expected = {"[CLS]", "the", "quick", "brown", "fox", "jumps", "over", "the",
        "[UNK]", "lazy", "dog", "[SEP]"};

    Assertions.assertArrayEquals(expected, tokens);

  }

  @Test
  void testSentenceWithPunctuation() {

    final Tokenizer tokenizer = new WordpieceTokenizer(getVocabulary());
    final String[] tokens = tokenizer.tokenize("The quick brown fox jumps over the very lazy dog.");

    final String[] expected = {"[CLS]", "[UNK]", "quick", "brown", "fox", "jumps", "over", "the",
        "[UNK]", "lazy", "dog", "[UNK]", "[SEP]"};

    Assertions.assertArrayEquals(expected, tokens);

  }

  @Test
  void testPunctuationRunsAreSplitIntoSingleCharacters() {

    final Set<String> vocabulary = getVocabulary();
    vocabulary.add(".");

    final Tokenizer tokenizer = new WordpieceTokenizer(vocabulary);
    final String[] tokens = tokenizer.tokenize("the lazy dog...");

    final String[] expected = {"[CLS]", "the", "lazy", "dog", ".", ".", ".", "[SEP]"};

    Assertions.assertArrayEquals(expected, tokens);

  }

  @Test
  void testPartiallyMatchedWordBecomesSingleUnknownToken() {

    // "brownfox" starts with the vocabulary piece "brown", but the remainder has no
    // matching piece. The reference BERT implementation replaces the whole word with
    // the unknown token instead of emitting the matched prefix pieces.
    final Tokenizer tokenizer = new WordpieceTokenizer(getVocabulary());
    final String[] tokens = tokenizer.tokenize("the brownfox jumps");

    final String[] expected = {"[CLS]", "the", "[UNK]", "jumps", "[SEP]"};

    Assertions.assertArrayEquals(expected, tokens);

  }

  @Test
  void testTokenizePosIsUnsupported() {

    final Tokenizer tokenizer = new WordpieceTokenizer(getVocabulary());

    Assertions.assertThrows(UnsupportedOperationException.class,
        () -> tokenizer.tokenizePos("the lazy dog"));

  }

  private Set<String> getVocabulary() {

    final Set<String> vocabulary = new HashSet<>();

    vocabulary.add("the");
    vocabulary.add("quick");
    vocabulary.add("brown");
    vocabulary.add("fox");
    vocabulary.add("jumps");
    vocabulary.add("over");
    vocabulary.add("the");
    vocabulary.add("lazy");
    vocabulary.add("dog");

    return vocabulary;

  }

}
