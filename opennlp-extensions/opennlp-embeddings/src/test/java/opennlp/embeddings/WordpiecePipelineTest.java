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
package opennlp.embeddings;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the module-internal tokenization pipeline against reference token sequences.
 * <p>
 * All expected sequences were generated with the HuggingFace {@code tokenizers} reference
 * implementation ({@code BertWordPieceTokenizer}) using the same vocabulary, so the lookup
 * path is verified to be identical to the reference BERT tokenization.
 */
class WordpiecePipelineTest {

  private static final Set<String> VOCABULARY = Set.of(
      "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
      "em", "##bed", "##ding", "##s",
      "wurttemberg", "strasse", "grosse",
      "don", "t", "wait", "what", ".", ",", "?", "!", "'",
      "\u6211", "\u7231",  // CJK
      "natural", "language", "processing");

  @Test
  void testLowerCasesCapitalizedWords() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    final String[] tokens =
        pipeline.tokenize("The quick brown fox jumps over the lazy dog.");

    final String[] expected = {"[CLS]", "the", "quick", "brown", "fox", "jumps", "over",
        "the", "lazy", "dog", ".", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testLowerCasesBeforeWordpieceSplitting() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    final String[] tokens = pipeline.tokenize("Embeddings");

    final String[] expected = {"[CLS]", "em", "##bed", "##ding", "##s", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testStripsAccentsButKeepsNonCombiningCharacters() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    // The u-umlaut decomposes to u plus a combining diaeresis and the mark is stripped;
    // the sharp s is not a combining mark and must survive, leaving an OOV token.
    final String[] tokens = pipeline.tokenize("W\u00fcrttemberg Stra\u00dfe");

    final String[] expected = {"[CLS]", "wurttemberg", "[UNK]", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testSplitsPunctuationRunsIntoSingleCharacters() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    final String[] tokens = pipeline.tokenize("Wait... what?!");

    final String[] expected = {"[CLS]", "wait", ".", ".", ".", "what", "?", "!", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testSplitsApostrophesAsPunctuation() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    final String[] tokens = pipeline.tokenize("don't");

    final String[] expected = {"[CLS]", "don", "'", "t", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testIsolatesCjkIdeographs() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    final String[] tokens = pipeline.tokenize("\u6211\u7231natural language processing");

    final String[] expected = {"[CLS]", "\u6211", "\u7231", "natural", "language",
        "processing", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testCleansControlCharactersAndNormalizesWhitespace() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    // Tab and no-break space are whitespace; the NUL character is removed,
    // joining "brown" and "fox" into one out-of-vocabulary token.
    final String[] tokens = pipeline.tokenize("the\tquick\u00a0brown\u0000fox");

    final String[] expected = {"[CLS]", "the", "quick", "[UNK]", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testRemovesPrivateUseAndUnassignedCharacters() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(VOCABULARY, true);
    // The reference implementation treats all C* categories as control
    // characters: private use (U+E000, Co) and noncharacters (U+FDD0, Cn)
    // are removed, joining the surrounding text into one OOV token.
    final String[] tokens = pipeline.tokenize("fox\ue000jumps and fox\ufdd0jumps");

    final String[] expected = {"[CLS]", "[UNK]", "[UNK]", "[UNK]", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testCasedModeKeepsCaseAndAccents() {
    final WordpiecePipeline pipeline = new WordpiecePipeline(
        Set.of("The", "W\u00fcrttemberg", "fox"), false);
    final String[] tokens = pipeline.tokenize("The W\u00fcrttemberg fox");

    final String[] expected =
        {"[CLS]", "The", "W\u00fcrttemberg", "fox", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testRejectsNullVocabulary() {
    Assertions.assertThrows(NullPointerException.class, () -> new WordpiecePipeline(null, true));
  }
}
