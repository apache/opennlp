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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks the complete token arrays created by {@link AbstractDL#encodeTokens(CharSequence)}. */
public class AbstractDLEncodeTokensTest {

  private static final int BERT_CLS_ID = 101;
  private static final int BERT_SEP_ID = Integer.MAX_VALUE;
  private static final int BERT_UNK_ID = 900_001;
  private static final int BERT_HELLO_ID = 42;
  private static final int BERT_WORLD_ID = 1_500_000_000;
  private static final int BERT_PLAY_ID = 0;
  private static final int BERT_ING_ID = Integer.MAX_VALUE - 1;
  private static final int BERT_CAFE_ID = 88;
  private static final int BERT_CASED_CAFE_ID = 89;
  private static final int BERT_SIGMA_ID = 90;
  private static final int BERT_CASED_SIGMA_ID = 91;
  private static final int BERT_ZHONG_ID = 301;
  private static final int BERT_WEN_ID = 302;
  private static final int BERT_SMILE_ID = 303;
  private static final int BERT_COMMA_ID = 304;
  private static final int BERT_BANG_ID = 305;
  private static final int BERT_LONGEST_WORD_ID = 306;
  private static final int BERT_OVERLONG_WORD_ID = 307;

  /** The encoder's default word length limit in code points, as in the reference tokenizer. */
  private static final int MAX_WORD_CODE_POINTS = 100;
  /** U+1F600, a supplementary-plane symbol that is not punctuation and not CJK. */
  private static final String SMILE = "\uD83D\uDE00";
  /** Exactly the limit in code points, one char more: the longest word the encoder still looks up. */
  private static final String LONGEST_WORD = "a".repeat(MAX_WORD_CODE_POINTS - 1) + SMILE;
  /** One code point over the limit, so the word is unknown even though the vocabulary has it. */
  private static final String OVERLONG_WORD = "a".repeat(MAX_WORD_CODE_POINTS + 1);

  private static final int ROBERTA_CLS_ID = Integer.MAX_VALUE;
  private static final int ROBERTA_SEP_ID = 2;
  private static final int ROBERTA_UNK_ID = 800_000_000;
  private static final int ROBERTA_HELLO_ID = 0;
  private static final int ROBERTA_WORLD_ID = 500;
  private static final int ROBERTA_PLAY_ID = 10;
  private static final int ROBERTA_ING_ID = 1_500_000_000;

  private static final int FALLBACK_CLS_ID = 71;
  private static final int FALLBACK_SEP_ID = 72;
  private static final int FALLBACK_UNK_ID = Integer.MAX_VALUE;
  private static final int FALLBACK_HELLO_ID = 73;

  private static final Map<String, Integer> BERT_TOKEN_IDS = Map.ofEntries(
      Map.entry(WordpieceTokenizer.BERT_CLS_TOKEN, BERT_CLS_ID),
      Map.entry(WordpieceTokenizer.BERT_SEP_TOKEN, BERT_SEP_ID),
      Map.entry(WordpieceTokenizer.BERT_UNK_TOKEN, BERT_UNK_ID),
      Map.entry("hello", BERT_HELLO_ID),
      Map.entry("world", BERT_WORLD_ID),
      Map.entry("play", BERT_PLAY_ID),
      Map.entry("##ing", BERT_ING_ID),
      Map.entry("cafe", BERT_CAFE_ID),
      Map.entry("Caf\u00E9", BERT_CASED_CAFE_ID),
      Map.entry("\u03C3\u03BF\u03C6\u03BF\u03C2", BERT_SIGMA_ID),
      Map.entry("\u03A3\u039F\u03A6\u039F\u03A3", BERT_CASED_SIGMA_ID),
      Map.entry("\u4E2D", BERT_ZHONG_ID),
      Map.entry("\u6587", BERT_WEN_ID),
      Map.entry(SMILE, BERT_SMILE_ID),
      Map.entry(",", BERT_COMMA_ID),
      Map.entry("!", BERT_BANG_ID),
      Map.entry(LONGEST_WORD, BERT_LONGEST_WORD_ID),
      Map.entry(OVERLONG_WORD, BERT_OVERLONG_WORD_ID));

  private static final Map<String, Integer> ROBERTA_TOKEN_IDS = Map.of(
      WordpieceTokenizer.ROBERTA_CLS_TOKEN, ROBERTA_CLS_ID,
      WordpieceTokenizer.ROBERTA_SEP_TOKEN, ROBERTA_SEP_ID,
      WordpieceTokenizer.ROBERTA_UNK_TOKEN, ROBERTA_UNK_ID,
      "hello", ROBERTA_HELLO_ID,
      "world", ROBERTA_WORLD_ID,
      "play", ROBERTA_PLAY_ID,
      "##ing", ROBERTA_ING_ID);

  private static final Map<String, Integer> ROBERTA_BERT_UNKNOWN_TOKEN_IDS = Map.of(
      WordpieceTokenizer.ROBERTA_CLS_TOKEN, FALLBACK_CLS_ID,
      WordpieceTokenizer.ROBERTA_SEP_TOKEN, FALLBACK_SEP_ID,
      WordpieceTokenizer.BERT_UNK_TOKEN, FALLBACK_UNK_ID,
      "hello", FALLBACK_HELLO_ID);

  private static final EncodingExpectation BERT_HELLO_EXPECTATION = new EncodingExpectation(
      BERT_TOKEN_IDS, true, "Hello",
      new String[] {"[CLS]", "hello", "[SEP]"},
      new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_SEP_ID});

  private static final EncodingExpectation BERT_MIXED_EXPECTATION = new EncodingExpectation(
      BERT_TOKEN_IDS, true, "Hello playing rabbit",
      new String[] {"[CLS]", "hello", "play", "##ing", "[UNK]", "[SEP]"},
      new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_PLAY_ID, BERT_ING_ID, BERT_UNK_ID, BERT_SEP_ID});

  private static final EncodingExpectation BERT_UNKNOWN_EXPECTATION = new EncodingExpectation(
      BERT_TOKEN_IDS, true, "rabbit",
      new String[] {"[CLS]", "[UNK]", "[SEP]"},
      new long[] {BERT_CLS_ID, BERT_UNK_ID, BERT_SEP_ID});

  private static final EncodingExpectation BERT_WORDPIECES_EXPECTATION =
      new EncodingExpectation(
          BERT_TOKEN_IDS, true, "Playing",
          new String[] {"[CLS]", "play", "##ing", "[SEP]"},
          new long[] {BERT_CLS_ID, BERT_PLAY_ID, BERT_ING_ID, BERT_SEP_ID});

  /** Expected output for one model-free encoding case. */
  private record EncodingExpectation(
      Map<String, Integer> tokenIds,
      boolean lowerCase,
      CharSequence input,
      String[] tokens,
      long[] ids) {
  }

  /**
   * Confirms the token strings, ids, attention mask, and token types.
   *
   * @param name The case name.
   * @param expected The fixture and expected arrays.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("encodingCases")
  void testEncodingArrays(String name, EncodingExpectation expected) {
    final Tokens actual = new ModelFreeDL(expected.tokenIds(), expected.lowerCase())
        .encode(expected.input());

    assertEncoding(expected, actual);
  }

  /** Confirms null text is rejected at the instance encoder boundary. */
  @Test
  void testRejectsNullText() {
    final ModelFreeDL encoder = new ModelFreeDL(BERT_TOKEN_IDS, true);

    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> encoder.encode(null));
    assertEquals("text must not be null", exception.getMessage());
  }

  /** Confirms that successive calls return independent arrays. */
  @Test
  void testRepeatedCallsReturnIndependentArrays() {
    final ModelFreeDL encoder = new ModelFreeDL(BERT_TOKEN_IDS, true);
    final Tokens first = encoder.encode("Hello");
    final Tokens second = encoder.encode("Hello");

    assertNotSame(first.tokens(), second.tokens());
    assertNotSame(first.ids(), second.ids());
    assertNotSame(first.mask(), second.mask());
    assertNotSame(first.types(), second.types());

    first.tokens()[1] = "changed";
    first.ids()[1] = -1;
    first.mask()[1] = 0;
    first.types()[1] = 1;

    assertEncoding(BERT_HELLO_EXPECTATION, second);
    assertEncoding(BERT_HELLO_EXPECTATION, encoder.encode("Hello"));
  }

  /**
   * Supplies model-free encoding fixtures.
   *
   * @return The named fixtures.
   */
  private static Stream<Arguments> encodingCases() {
    return Stream.of(
        Arguments.of("bert-empty", new EncodingExpectation(BERT_TOKEN_IDS, true, "",
            new String[] {"[CLS]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_SEP_ID})),
        Arguments.of("bert-ascii-whitespace", new EncodingExpectation(
            BERT_TOKEN_IDS, true, " \t\n",
            new String[] {"[CLS]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_SEP_ID})),
        Arguments.of("bert-unicode-whitespace", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "\u00A0\u2028\u3000",
            new String[] {"[CLS]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_SEP_ID})),
        Arguments.of("bert-known", BERT_HELLO_EXPECTATION),
        Arguments.of("bert-known-sequence", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello WORLD",
            new String[] {"[CLS]", "hello", "world", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_WORLD_ID, BERT_SEP_ID})),
        Arguments.of("bert-unicode-separator", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello\u00A0WORLD",
            new String[] {"[CLS]", "hello", "world", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_WORLD_ID, BERT_SEP_ID})),
        Arguments.of("bert-unknown", BERT_UNKNOWN_EXPECTATION),
        Arguments.of("bert-supplementary-unknown", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello \uD83D\uDE80 world",
            new String[] {"[CLS]", "hello", "[UNK]", "world", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_UNK_ID, BERT_WORLD_ID, BERT_SEP_ID})),
        Arguments.of("bert-cjk-split", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "hello\u4E2D\u6587world",
            new String[] {"[CLS]", "hello", "\u4E2D", "\u6587", "world", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_ZHONG_ID, BERT_WEN_ID,
                BERT_WORLD_ID, BERT_SEP_ID})),
        Arguments.of("bert-cjk-unknown-split", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "hello\u754Cworld",
            new String[] {"[CLS]", "hello", "[UNK]", "world", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_UNK_ID, BERT_WORLD_ID, BERT_SEP_ID})),
        Arguments.of("bert-cjk-cased", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "hello\u4E2D\u6587world",
            new String[] {"[CLS]", "hello", "\u4E2D", "\u6587", "world", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_ZHONG_ID, BERT_WEN_ID,
                BERT_WORLD_ID, BERT_SEP_ID})),
        Arguments.of("bert-supplementary-known", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello " + SMILE,
            new String[] {"[CLS]", "hello", SMILE, "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_SMILE_ID, BERT_SEP_ID})),
        // a symbol is not a word boundary, so the complete word is unknown
        Arguments.of("bert-supplementary-inside-word", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "hello" + SMILE + "world",
            new String[] {"[CLS]", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_UNK_ID, BERT_SEP_ID})),
        Arguments.of("bert-punctuation-known", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello, WORLD!",
            new String[] {"[CLS]", "hello", ",", "world", "!", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_COMMA_ID, BERT_WORLD_ID,
                BERT_BANG_ID, BERT_SEP_ID})),
        // each punctuation character is its own token, so a run yields one unknown per character
        Arguments.of("bert-punctuation-run-unknown", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello??",
            new String[] {"[CLS]", "hello", "[UNK]", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_UNK_ID, BERT_UNK_ID, BERT_SEP_ID})),
        // special token text is split as punctuation and letters, not mapped to the special id
        Arguments.of("bert-special-token-in-text", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "[CLS]",
            new String[] {"[CLS]", "[UNK]", "[UNK]", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_UNK_ID, BERT_UNK_ID, BERT_UNK_ID, BERT_SEP_ID})),
        Arguments.of("bert-control-removed", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hel\u0001lo",
            new String[] {"[CLS]", "hello", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_SEP_ID})),
        // a format character (zero width space) is removed, which joins the words
        Arguments.of("bert-format-char-joins-words", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "hello\u200Bworld",
            new String[] {"[CLS]", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_UNK_ID, BERT_SEP_ID})),
        // the word length limit counts code points, not chars
        Arguments.of("bert-longest-word", new EncodingExpectation(
            BERT_TOKEN_IDS, true, LONGEST_WORD,
            new String[] {"[CLS]", LONGEST_WORD, "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_LONGEST_WORD_ID, BERT_SEP_ID})),
        Arguments.of("bert-overlong-word-unknown", new EncodingExpectation(
            BERT_TOKEN_IDS, true, OVERLONG_WORD,
            new String[] {"[CLS]", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_UNK_ID, BERT_SEP_ID})),
        Arguments.of("bert-known-unknown", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Hello rabbit",
            new String[] {"[CLS]", "hello", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_UNK_ID, BERT_SEP_ID})),
        Arguments.of("bert-wordpieces", BERT_WORDPIECES_EXPECTATION),
        Arguments.of("bert-mixed", BERT_MIXED_EXPECTATION),
        Arguments.of("bert-string-builder", new EncodingExpectation(
            BERT_TOKEN_IDS, true, new StringBuilder("Hello"),
            new String[] {"[CLS]", "hello", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_HELLO_ID, BERT_SEP_ID})),
        Arguments.of("bert-cased-accent", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "Caf\u00E9",
            new String[] {"[CLS]", "Caf\u00E9", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_CASED_CAFE_ID, BERT_SEP_ID})),
        Arguments.of("bert-cased-miss", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "Hello",
            new String[] {"[CLS]", "[UNK]", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_UNK_ID, BERT_SEP_ID})),
        Arguments.of("bert-precomposed-accent", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "CAF\u00C9",
            new String[] {"[CLS]", "cafe", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_CAFE_ID, BERT_SEP_ID})),
        Arguments.of("bert-decomposed-accent", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "Cafe\u0301",
            new String[] {"[CLS]", "cafe", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_CAFE_ID, BERT_SEP_ID})),
        Arguments.of("bert-final-sigma", new EncodingExpectation(
            BERT_TOKEN_IDS, true, "\u03A3\u039F\u03A6\u039F\u03A3",
            new String[] {"[CLS]", "\u03C3\u03BF\u03C6\u03BF\u03C2", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_SIGMA_ID, BERT_SEP_ID})),
        Arguments.of("bert-cased-sigma", new EncodingExpectation(
            BERT_TOKEN_IDS, false, "\u03A3\u039F\u03A6\u039F\u03A3",
            new String[] {"[CLS]", "\u03A3\u039F\u03A6\u039F\u03A3", "[SEP]"},
            new long[] {BERT_CLS_ID, BERT_CASED_SIGMA_ID, BERT_SEP_ID})),
        Arguments.of("roberta-empty", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "",
            new String[] {"<s>", "</s>"},
            new long[] {ROBERTA_CLS_ID, ROBERTA_SEP_ID})),
        Arguments.of("roberta-known", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "Hello",
            new String[] {"<s>", "hello", "</s>"},
            new long[] {ROBERTA_CLS_ID, ROBERTA_HELLO_ID, ROBERTA_SEP_ID})),
        Arguments.of("roberta-unknown", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "rabbit",
            new String[] {"<s>", "<unk>", "</s>"},
            new long[] {ROBERTA_CLS_ID, ROBERTA_UNK_ID, ROBERTA_SEP_ID})),
        Arguments.of("roberta-mixed", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "Hello rabbit WORLD",
            new String[] {"<s>", "hello", "<unk>", "world", "</s>"},
            new long[] {ROBERTA_CLS_ID, ROBERTA_HELLO_ID, ROBERTA_UNK_ID, ROBERTA_WORLD_ID, ROBERTA_SEP_ID})),
        // Tests WordPiece with RoBERTa special tokens, not RoBERTa byte-level BPE semantics.
        Arguments.of("roberta-wordpieces", new EncodingExpectation(
            ROBERTA_TOKEN_IDS, true, "Playing",
            new String[] {"<s>", "play", "##ing", "</s>"},
            new long[] {ROBERTA_CLS_ID, ROBERTA_PLAY_ID, ROBERTA_ING_ID, ROBERTA_SEP_ID})),
        Arguments.of("roberta-bert-unknown", new EncodingExpectation(
            ROBERTA_BERT_UNKNOWN_TOKEN_IDS, true, "Hello rabbit",
            new String[] {"<s>", "hello", "[UNK]", "</s>"},
            new long[] {FALLBACK_CLS_ID, FALLBACK_HELLO_ID, FALLBACK_UNK_ID, FALLBACK_SEP_ID})));
  }

  /**
   * Compares all model input arrays.
   *
   * @param expected The expected arrays.
   * @param actual The encoded arrays.
   */
  private void assertEncoding(EncodingExpectation expected, Tokens actual) {
    assertArrayEquals(expected.tokens(), actual.tokens());
    assertArrayEquals(expected.ids(), actual.ids());
    final long[] mask = new long[expected.tokens().length];
    Arrays.fill(mask, 1);
    assertArrayEquals(mask, actual.mask());
    assertArrayEquals(new long[expected.tokens().length], actual.types());
  }
}
