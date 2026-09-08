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
 * Checks token sequences defined by the BERT tokenizer for lower casing, accent stripping,
 * punctuation, CJK isolation, and text cleaning.
 *
 * @see <a href="https://github.com/google-research/bert/blob/master/tokenization.py">
 *     BERT reference tokenizer</a>
 */
class WordpieceEncoderReferenceSequencesTest {

  private static final List<String> VOCABULARY = List.of(
      "[CLS]", "[SEP]", "[UNK]",
      "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog",
      "em", "##bed", "##ding", "##s",
      "wurttemberg", "strasse", "grosse",
      "\u03C3\u03BF\u03C6\u03BF\u03C2", "\u03C3", "\u03BF\u03C3\u03B1",
      "\u03BF\u03C3", "\u03BF\u03C2", "\u03B1", "\u03BF\u03C2\u302E\u03C3",
      "don", "t", "wait", "what", ".", ",", "?", "!", "'", "\"",
      "\u6211", "\u7231",  // CJK
      "natural", "language", "processing", "foxjumps");

  /**
   * The reference input and expected-sequence pairs, one argument set per pipeline behavior.
   *
   * @return The (input, expected pieces) pairs.
   */
  private static Stream<Arguments> referenceSequences() {
    return Stream.of(
        // Lower cases capitalized words.
        Arguments.of("The quick brown fox jumps over the lazy dog.",
            new String[] {"[CLS]", "the", "quick", "brown", "fox", "jumps", "over",
                "the", "lazy", "dog", ".", "[SEP]"}),
        // Lower cases before wordpiece splitting.
        Arguments.of("Embeddings",
            new String[] {"[CLS]", "em", "##bed", "##ding", "##s", "[SEP]"}),
        // Lowercases a word-final Greek sigma to U+03C2.
        Arguments.of("\u03A3\u039F\u03A6\u039F\u03A3",
            new String[] {"[CLS]", "\u03C3\u03BF\u03C6\u03BF\u03C2", "[SEP]"}),
        // A sigma without a preceding cased letter is not a final sigma.
        Arguments.of("\u03A3", new String[] {"[CLS]", "\u03C3", "[SEP]"}),
        // A sigma followed by a cased letter is not a final sigma.
        Arguments.of("\u039F\u03A3\u0391",
            new String[] {"[CLS]", "\u03BF\u03C3\u03B1", "[SEP]"}),
        // Case context skips the apostrophe before punctuation isolation.
        Arguments.of("\u039F\u03A3'\u0391",
            new String[] {"[CLS]", "\u03BF\u03C3", "'", "\u03B1", "[SEP]"}),
        // A full stop is also case-ignorable before punctuation isolation.
        Arguments.of("\u039F\u03A3.\u0391",
            new String[] {"[CLS]", "\u03BF\u03C3", ".", "\u03B1", "[SEP]"}),
        // A quotation mark terminates the case context, so the sigma before it is word-final.
        Arguments.of("\u039F\u03A3\"\u0391",
            new String[] {"[CLS]", "\u03BF\u03C2", "\"", "\u03B1", "[SEP]"}),
        // Case context skips a combining mark, which accent stripping later removes.
        Arguments.of("\u039F\u03A3\u0301",
            new String[] {"[CLS]", "\u03BF\u03C2", "[SEP]"}),
        // A combining spacing mark terminates the case context.
        Arguments.of("\u039F\u03A3\u302E\u03A3",
            new String[] {"[CLS]", "\u03BF\u03C2\u302E\u03C3", "[SEP]"}),
        // The u-umlaut decomposes to u plus a combining diaeresis and the mark is stripped;
        // the sharp s is not a combining mark and is retained, leaving an OOV token.
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
        // BERT removes all Unicode control categories, including private-use,
        // unassigned, and surrogate code units.
        Arguments.of("fox\ue000jumps and fox\ufdd0jumps fox\ud800jumps",
            new String[] {"[CLS]", "foxjumps", "[UNK]", "foxjumps", "foxjumps",
                "[SEP]"}));
  }

  @ParameterizedTest
  @MethodSource("referenceSequences")
  void testEncodesTheReferenceSequence(String input, String[] expected) {
    final WordpieceEncoder encoder = new WordpieceEncoder(VOCABULARY);
    Assertions.assertArrayEquals(expected, encoder.encodeToPieces(input),
        "unexpected sequence for: " + input);
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
