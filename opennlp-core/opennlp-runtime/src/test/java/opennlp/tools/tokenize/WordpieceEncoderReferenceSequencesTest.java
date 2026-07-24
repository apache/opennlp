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

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Reference token-sequence expectations for {@link WordpieceEncoder}, covering lower casing,
 * accent stripping, punctuation and CJK isolation, and text cleaning.
 * <p>
 * All expected token sequences in this test were generated with the HuggingFace
 * {@code tokenizers} reference implementation ({@code BertWordPieceTokenizer})
 * using the same vocabulary, so they are verified to be identical to the
 * reference BERT tokenization. The encoder requires its special tokens to be
 * present in the vocabulary (every piece must have an id), so the vocabularies
 * here include them; the token sequences are unchanged.
 */
public class WordpieceEncoderReferenceSequencesTest {

  private static final List<String> VOCABULARY = List.of(
      "[CLS]", "[SEP]", "[UNK]",
      "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
      "em", "##bed", "##ding", "##s",
      "wurttemberg", "strasse", "grosse",
      "don", "t", "wait", "what", ".", ",", "?", "!", "'",
      "\u6211", "\u7231",  // CJK
      "natural", "language", "processing");

  /**
   * The reference input and expected-sequence pairs, one argument set per pipeline behavior.
   *
   * @return The (input, expected pieces) pairs.
   */
  static Stream<Arguments> referenceSequences() {
    return Stream.of(
        // Lower cases capitalized words.
        Arguments.of("The quick brown fox jumps over the lazy dog.",
            new String[] {"[CLS]", "the", "quick", "brown", "fox", "jumps", "over",
                "the", "lazy", "dog", ".", "[SEP]"}),
        // Lower cases before wordpiece splitting.
        Arguments.of("Embeddings",
            new String[] {"[CLS]", "em", "##bed", "##ding", "##s", "[SEP]"}),
        // The u-umlaut decomposes to u plus a combining diaeresis and the mark is stripped;
        // the sharp s is not a combining mark and must survive, leaving an OOV token.
        Arguments.of("W\u00fcrttemberg Stra\u00dfe",
            new String[] {"[CLS]", "wurttemberg", "[UNK]", "[SEP]"}),
        // Splits punctuation runs into single characters.
        Arguments.of("Wait... what?!",
            new String[] {"[CLS]", "wait", ".", ".", ".", "what", "?", "!", "[SEP]"}),
        // Splits apostrophes as punctuation.
        Arguments.of("don't",
            new String[] {"[CLS]", "don", "'", "t", "[SEP]"}),
        // Isolates CJK ideographs into single-character pieces.
        Arguments.of("\u6211\u7231natural language processing",
            new String[] {"[CLS]", "\u6211", "\u7231", "natural", "language",
                "processing", "[SEP]"}),
        // Tab and no-break space are whitespace; the NUL character is removed,
        // joining "brown" and "fox" into one out-of-vocabulary token.
        Arguments.of("the\tquick\u00a0brown\u0000fox",
            new String[] {"[CLS]", "the", "quick", "[UNK]", "[SEP]"}),
        // The reference implementation treats all C* categories as control
        // characters: private use (U+E000, Co) and noncharacters (U+FDD0, Cn)
        // are removed, joining the surrounding text into one OOV token.
        Arguments.of("fox\ue000jumps and fox\ufdd0jumps",
            new String[] {"[CLS]", "[UNK]", "[UNK]", "[UNK]", "[SEP]"}));
  }

  @ParameterizedTest
  @MethodSource("referenceSequences")
  void testEncodesTheReferenceSequence(String input, String[] expected) {
    final WordpieceEncoder encoder = new WordpieceEncoder(VOCABULARY);
    Assertions.assertArrayEquals(expected, encoder.encodeToPieces(input),
        "sequence broke on: " + input);
  }

  @Test
  void testRejectsNullSpecialTokens() {
    // The encoder's contract throws IllegalArgumentException for null special tokens.
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WordpieceEncoder(VOCABULARY, true, null, "[SEP]", "[UNK]"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WordpieceEncoder(VOCABULARY, true, "[CLS]", null, "[UNK]"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new WordpieceEncoder(VOCABULARY, true, "[CLS]", "[SEP]", null));
  }

  @Test
  void testCasedModeKeepsCaseAndAccents() {
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("[CLS]", "[SEP]", "[UNK]", "The", "W\u00fcrttemberg", "fox"), false);
    final String[] tokens = encoder.encodeToPieces("The W\u00fcrttemberg fox");

    final String[] expected = {"[CLS]", "The", "W\u00fcrttemberg", "fox", "[SEP]"};
    Assertions.assertArrayEquals(expected, tokens);
  }

  @Test
  void testCustomSpecialTokens() {
    final WordpieceEncoder encoder = new WordpieceEncoder(
        List.of("<s>", "</s>", "<unk>", "the", "fox"), true,
        WordpieceTokenizer.ROBERTA_CLS_TOKEN, WordpieceTokenizer.ROBERTA_SEP_TOKEN,
        WordpieceTokenizer.ROBERTA_UNK_TOKEN);
    final String[] tokens = encoder.encodeToPieces("The unknown fox");

    final String[] expected = {"<s>", "the", "<unk>", "fox", "</s>"};
    Assertions.assertArrayEquals(expected, tokens);
  }
}
