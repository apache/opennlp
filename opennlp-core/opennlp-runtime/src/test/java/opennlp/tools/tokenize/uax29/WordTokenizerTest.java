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
package opennlp.tools.tokenize.uax29;

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WordTokenizerTest {

  private static final WordTokenizer TOKENIZER = new WordTokenizer();

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static List<String> words(String text) {
    return List.of(TOKENIZER.tokenize(text));
  }

  @Test
  void testDropsWhitespaceAndPunctuation() {
    assertEquals(List.of("Hello", "world"), words("Hello, world!"));
  }

  @Test
  void testAlphanumericAndNumericTypes() {
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped("abc 123");
    assertEquals(2, tokens.size());
    assertEquals(WordType.ALPHANUMERIC, tokens.get(0).type());
    assertEquals("abc", tokens.get(0).text("abc 123"));
    assertEquals(WordType.NUMERIC, tokens.get(1).type());
    assertEquals("123", tokens.get(1).text("abc 123"));
  }

  @Test
  void testDecimalIsSingleNumericToken() {
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped("3.14");
    assertEquals(1, tokens.size());
    assertEquals(WordType.NUMERIC, tokens.get(0).type());
    assertEquals("3.14", tokens.get(0).text("3.14"));
  }

  @Test
  void testIdeographsOnePerToken() {
    final String text = cp(0x4E2D) + cp(0x6587);
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(2, tokens.size());
    assertEquals(WordType.IDEOGRAPHIC, tokens.get(0).type());
    assertEquals(WordType.IDEOGRAPHIC, tokens.get(1).type());
  }

  @Test
  void testHiraganaSplitsPerCharacter() {
    final String text = cp(0x3042) + cp(0x3044); // a + i
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(2, tokens.size());
    assertEquals(WordType.HIRAGANA, tokens.get(0).type());
    assertEquals(WordType.HIRAGANA, tokens.get(1).type());
  }

  @Test
  void testKatakanaRunStaysTogether() {
    final String text = cp(0x30A2) + cp(0x30A4); // a + i
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(1, tokens.size());
    assertEquals(WordType.KATAKANA, tokens.get(0).type());
    assertEquals(text, tokens.get(0).text(text));
  }

  @Test
  void testExtendedPictographicSymbolsAreKeptAsEmoji() {
    // Extended_Pictographic includes symbol-like characters (copyright U+00A9, trademark U+2122,
    // double exclamation U+203C), which WordType classifies as EMOJI, so the tokenizer keeps them
    // rather than dropping them as punctuation.
    final String text = "a " + cp(0x00A9) + " " + cp(0x2122) + " " + cp(0x203C) + " b";
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(List.of(WordType.ALPHANUMERIC, WordType.EMOJI, WordType.EMOJI,
            WordType.EMOJI, WordType.ALPHANUMERIC),
        tokens.stream().map(WordToken::type).toList());
  }

  @Test
  void testHangulSyllablesStayTogether() {
    final String text = cp(0xAC00) + cp(0xB098); // ga + na
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(1, tokens.size());
    assertEquals(WordType.HANGUL, tokens.get(0).type());
    assertEquals(text, tokens.get(0).text(text));
  }

  @Test
  void testSoutheastAsianType() {
    final String text = cp(0x0E01); // Thai letter ko kai
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(1, tokens.size());
    assertEquals(WordType.SOUTHEAST_ASIAN, tokens.get(0).type());
  }

  @Test
  void testEmojiType() {
    final String text = cp(0x1F600); // grinning face
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(text);
    assertEquals(1, tokens.size());
    assertEquals(WordType.EMOJI, tokens.get(0).type());
  }

  @Test
  void testRegionalIndicatorFlagIsOneEmoji() {
    final String flag = cp(0x1F1FA) + cp(0x1F1F8); // U + S
    final List<WordToken> tokens = TOKENIZER.tokenizeTyped(flag);
    assertEquals(1, tokens.size());
    assertEquals(WordType.EMOJI, tokens.get(0).type());
    assertEquals(flag, tokens.get(0).text(flag));
  }

  @Test
  void testMaxTokenLengthChopsLongWords() {
    final WordTokenizer tokenizer = new WordTokenizer(3);
    assertEquals(List.of("abc", "def", "g"), List.of(tokenizer.tokenize("abcdefg")));
  }

  @Test
  void testMaxTokenLengthNeverSplitsASurrogatePair() {
    // A two-char emoji must be emitted whole even when the limit is one char.
    final WordTokenizer tokenizer = new WordTokenizer(1);
    final String emoji = cp(0x1F600);
    final List<WordToken> tokens = tokenizer.tokenizeTyped(emoji);
    assertEquals(1, tokens.size());
    assertEquals(emoji, tokens.get(0).text(emoji));
  }

  @Test
  void testConstructorRejectsNonPositiveLength() {
    assertThrows(IllegalArgumentException.class, () -> new WordTokenizer(0));
    assertThrows(IllegalArgumentException.class, () -> new WordTokenizer(-5));
  }

  @Test
  void testEmptyText() {
    assertEquals(List.of(), words(""));
    assertEquals(List.of(), TOKENIZER.tokenizeTyped(""));
  }

  @Test
  void testUsableThroughTokenizerInterface() {
    final Tokenizer tokenizer = new WordTokenizer();
    final String text = "Hello, world!";
    assertArrayEquals(new String[] {"Hello", "world"}, tokenizer.tokenize(text));
    final Span[] spans = tokenizer.tokenizePos(text);
    assertEquals(2, spans.length);
    assertEquals("Hello", spans[0].getCoveredText(text).toString());
    assertEquals("world", spans[1].getCoveredText(text).toString());
  }
}
