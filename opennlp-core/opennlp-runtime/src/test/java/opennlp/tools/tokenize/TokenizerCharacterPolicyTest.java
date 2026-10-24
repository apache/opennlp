/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.normalizer.CodePointSet;
import opennlp.tools.util.normalizer.UnicodeWhitespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenizerCharacterPolicyTest {

  private static final int DESERET_LETTER = 0x10400;
  private static final int MUSICAL_MARK = 0x1D165;
  private static final int MATHEMATICAL_DIGIT = 0x1D7D8;
  private static final int ACUTE_ACCENT = 0x0301;
  private static final String ASCII_TEXT = "letters=0041-005A,0061-007A;digits=0030-0039;marks=";

  private static final CodePointSet A = CodePointSet.of('a');
  private static final CodePointSet SEVEN = CodePointSet.of('7');
  private static final CodePointSet EMPTY = CodePointSet.of();

  private static final TokenizerCharacterPolicy GRAMMAR = TokenizerCharacterPolicy.of(
      CodePointSet.of('a', 'b'), CodePointSet.of('7', '8'), CodePointSet.of(ACUTE_ACCENT));

  @Test
  void testPolicyIsACharSequencePredicate() {
    assertInstanceOf(Predicate.class, TokenizerCharacterPolicy.ascii());
  }

  @Test
  void testAsciiPresetHasExactSets() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.ascii();

    assertEquals(52, policy.getLetters().size());
    assertEquals(10, policy.getDigits().size());
    assertTrue(policy.getMarks().isEmpty());
    for (int cp = 0; cp < 128; cp++) {
      boolean letter = cp >= 'A' && cp <= 'Z' || cp >= 'a' && cp <= 'z';
      boolean digit = cp >= '0' && cp <= '9';
      assertEquals(letter, policy.getLetters().contains(cp), String.format("letter U+%04X", cp));
      assertEquals(digit, policy.getDigits().contains(cp), String.format("digit U+%04X", cp));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"\u00E9", "e\u0301", "\uD801\uDC00"})
  void testAsciiPresetRejectsNonAsciiInput(String input) {
    assertFalse(TokenizerCharacterPolicy.ascii().test(input));
  }

  @Test
  void testManualExampleAcceptsDecomposedCafe() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.ofRange('A', 'Z').union(CodePointSet.ofRange('a', 'z')),
        CodePointSet.ofRange('0', '9'),
        CodePointSet.of(0x0300, ACUTE_ACCENT));

    assertTrue(policy.test("Cafe\u0301"));
  }

  @Test
  void testGettersReturnSuppliedSets() {
    CodePointSet letters = CodePointSet.of('a', DESERET_LETTER);
    CodePointSet digits = CodePointSet.of('7', MATHEMATICAL_DIGIT);
    CodePointSet marks = CodePointSet.of(ACUTE_ACCENT, MUSICAL_MARK);

    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(letters, digits, marks);

    assertEquals(letters, policy.getLetters());
    assertEquals(digits, policy.getDigits());
    assertEquals(marks, policy.getMarks());
  }

  @Test
  void testLetterOnlyAndDigitOnlyPoliciesAreValid() {
    assertTrue(TokenizerCharacterPolicy.of(A, EMPTY, EMPTY).test("aaa"));
    assertTrue(TokenizerCharacterPolicy.of(EMPTY, SEVEN, EMPTY).test("777"));
  }

  @ParameterizedTest
  @CsvSource({
      "a, true",
      "7, true",
      "a\u0301\u0301, true",
      "a\u03017, true",
      "7a\u0301, true",
      "a7b\u03018, true",
      "'', false",
      "\u0301, false",
      "7\u0301, true",
      "a7\u0301, true",
      "7\u0301\u0301b, true",
      "a\u0301-, false",
      "a-b, false"})
  void testGrammarAcrossCategoryTransitions(String input, boolean expected) {
    assertEquals(expected, GRAMMAR.test(input), input);
  }

  @Test
  void testSupplementaryBasesAndMarks() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.of(DESERET_LETTER), CodePointSet.of(MATHEMATICAL_DIGIT),
        CodePointSet.of(MUSICAL_MARK));

    assertTrue(policy.test(codePoints(DESERET_LETTER, MUSICAL_MARK, MATHEMATICAL_DIGIT)));
    assertTrue(policy.test(codePoints(MATHEMATICAL_DIGIT, MUSICAL_MARK)));
    assertFalse(policy.test(codePoints(MUSICAL_MARK, MATHEMATICAL_DIGIT)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\uD800", "\uDC00", "a\uD800", "\uDC00a", "\uDC00\uD800", "\uD800\uD800",
      "a\uD800b"})
  void testRejectsUnpairedOrReversedSurrogates(String input) {
    assertFalse(TokenizerCharacterPolicy.ascii().test(input));
  }

  @Test
  void testRejectsNullInput() {
    assertEquals("input must not be null", assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.ascii().test(null)).getMessage());
  }

  @Test
  void testAcceptsGeneralCharSequenceAndLongInput() {
    StringBuilder input = new StringBuilder(100_000);
    for (int i = 0; i < 100_000; i++) {
      input.append(i % 2 == 0 ? 'a' : '7');
    }

    assertTrue(TokenizerCharacterPolicy.ascii().test(input));
    input.setCharAt(input.length() - 1, '-');
    assertFalse(TokenizerCharacterPolicy.ascii().test(input));
  }

  /**
   * Supplies a null value for each category in turn.
   *
   * @return Category sets with one null argument, and the expected message.
   */
  static Stream<Arguments> nullSets() {
    return Stream.of(
        Arguments.of(null, EMPTY, EMPTY, "letters must not be null"),
        Arguments.of(A, null, EMPTY, "digits must not be null"),
        Arguments.of(A, EMPTY, null, "marks must not be null"));
  }

  @ParameterizedTest
  @MethodSource("nullSets")
  void testFactoryRejectsNullSets(CodePointSet letters, CodePointSet digits, CodePointSet marks,
                                  String message) {
    assertEquals(message, assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(letters, digits, marks)).getMessage());
  }

  @Test
  void testFactoryRequiresABaseCharacter() {
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(
            EMPTY, EMPTY, CodePointSet.of(ACUTE_ACCENT)));
  }

  /**
   * Supplies an overlap between each pair of categories.
   *
   * @return Category sets with a shared code point.
   */
  static Stream<Arguments> overlappingSets() {
    return Stream.of(
        Arguments.of(A, A, EMPTY),
        Arguments.of(A, SEVEN, A),
        Arguments.of(A, SEVEN, SEVEN));
  }

  @ParameterizedTest
  @MethodSource("overlappingSets")
  void testFactoryRejectsOverlappingCategories(
      CodePointSet letters, CodePointSet digits, CodePointSet marks) {
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(letters, digits, marks));
  }

  @Test
  void testFactoryMessagesNameTheOffendingCodePoint() {
    IllegalArgumentException overlap = assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(A, A, EMPTY));
    IllegalArgumentException surrogate = assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(A, SEVEN, CodePointSet.of(Character.MIN_SURROGATE)));
    IllegalArgumentException whitespace = assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(A, SEVEN, CodePointSet.of(' ')));

    assertTrue(overlap.getMessage().contains("U+0061"), overlap.getMessage());
    assertTrue(surrogate.getMessage().contains("U+D800"), surrogate.getMessage());
    assertTrue(whitespace.getMessage().contains("U+0020"), whitespace.getMessage());
  }

  @Test
  void testFactoryRejectsSurrogatesInEveryCategory() {
    for (int surrogate = Character.MIN_SURROGATE;
         surrogate <= Character.MAX_SURROGATE; surrogate++) {
      int cp = surrogate;
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(CodePointSet.of(cp), SEVEN, EMPTY));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(A, CodePointSet.of(cp), EMPTY));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(A, SEVEN, CodePointSet.of(cp)));
    }
  }

  @Test
  void testFactoryRejectsEveryUnicodeWhitespaceInEveryCategory() {
    for (int whitespace : UnicodeWhitespace.codePoints()) {
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(CodePointSet.of(whitespace), SEVEN, EMPTY));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(A, CodePointSet.of(whitespace), EMPTY));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(A, SEVEN, CodePointSet.of(whitespace)));
    }
  }

  @Test
  void testAsciiPresetHasAFixedTextForm() {
    assertEquals(ASCII_TEXT, TokenizerCharacterPolicy.ascii().toString());
  }

  @Test
  void testTextFormWritesSupplementaryCodePointsWithFiveDigits() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.of(DESERET_LETTER), EMPTY, CodePointSet.of(MUSICAL_MARK));

    assertEquals("letters=10400;digits=;marks=1D165", policy.toString());
  }

  @Test
  void testTextFormJoinsAdjacentCodePointsIntoRanges() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.of('a', 'b', 'c', 'e', 'g', 'h'), CodePointSet.of('7'), EMPTY);

    assertEquals("letters=0061-0063,0065,0067-0068;digits=0037;marks=", policy.toString());
  }

  /**
   * Supplies policies with different set shapes.
   *
   * @return Policies to write and read back.
   */
  static Stream<TokenizerCharacterPolicy> roundTripPolicies() {
    return Stream.of(
        TokenizerCharacterPolicy.ascii(),
        GRAMMAR,
        TokenizerCharacterPolicy.of(A, EMPTY, EMPTY),
        TokenizerCharacterPolicy.of(EMPTY, SEVEN, EMPTY),
        TokenizerCharacterPolicy.of(CodePointSet.of(DESERET_LETTER),
            CodePointSet.of(MATHEMATICAL_DIGIT), CodePointSet.of(MUSICAL_MARK)),
        TokenizerCharacterPolicy.of(CodePointSet.ofRange(0x21, 0x2F).union(CodePointSet.of('a')),
            CodePointSet.ofRange(Character.MAX_SURROGATE + 1, Character.MAX_SURROGATE + 3),
            CodePointSet.of(Character.MAX_CODE_POINT)));
  }

  @ParameterizedTest
  @MethodSource("roundTripPolicies")
  void testParseReadsTheTextFormBack(TokenizerCharacterPolicy policy) {
    TokenizerCharacterPolicy parsed = TokenizerCharacterPolicy.parse(policy.toString());

    assertEquals(policy, parsed);
    assertEquals(policy.hashCode(), parsed.hashCode());
    assertEquals(policy.toString(), parsed.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "letters=0061;digits=;marks=0301",
      "letters=61;digits=;marks=301",
      "letters=0061-0061;digits=;marks=0301-0301",
      "letters=0000061;digits=;marks=0301"})
  void testParseAcceptsShortLongAndSingletonRangeSpellings(String text) {
    TokenizerCharacterPolicy expected = TokenizerCharacterPolicy.of(
        A, EMPTY, CodePointSet.of(ACUTE_ACCENT));

    assertEquals(expected, TokenizerCharacterPolicy.parse(text));
  }

  @Test
  void testParseAcceptsLowerCaseHexDigits() {
    TokenizerCharacterPolicy expected = TokenizerCharacterPolicy.of(
        CodePointSet.of(DESERET_LETTER), EMPTY, CodePointSet.of(MUSICAL_MARK));

    assertEquals(expected, TokenizerCharacterPolicy.parse("letters=10400;digits=;marks=1d165"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
      "",
      "letters=;digits=;marks=",
      "letters=0041-005A",
      "letters=0041;digits=0030",
      "digits=0030;letters=0041;marks=",
      "letters=zz;digits=;marks=",
      "letters=005A-0041;digits=;marks=",
      "letters=0041;digits=0041;marks=",
      "letters=D800;digits=;marks=",
      "letters=0020;digits=;marks=",
      "letters=0041,;digits=;marks=",
      "letters=,0041;digits=;marks=",
      "letters=0041-;digits=;marks=",
      "letters=-0041;digits=;marks=",
      "letters=0041-005A-0061;digits=;marks=",
      "letters=110000;digits=;marks=",
      "letters=0041;digits=;marks=;extra=",
      "letters=0041;digits=;marks=0301;",
      "letters 0041;digits=;marks=",
      "LETTERS=0041;DIGITS=;MARKS=",
      "letters=0x41;digits=;marks=",
      "letters=U+0041;digits=;marks=",
      "letters= 0041;digits=;marks=",
      "letters=0041;digits=;marks=\n",
      "^[A-Za-z0-9]+$"})
  void testParseRejectsMalformedText(String text) {
    assertThrows(IllegalArgumentException.class, () -> TokenizerCharacterPolicy.parse(text));
  }

  @Test
  void testParseErrorNamesTheOffendingText() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.parse("letters=zz;digits=;marks="));

    assertTrue(e.getMessage().contains("zz"), e.getMessage());
  }

  @Test
  void testEqualsComparesTheThreeSets() {
    TokenizerCharacterPolicy ascii = TokenizerCharacterPolicy.of(
        CodePointSet.ofRange('A', 'Z').union(CodePointSet.ofRange('a', 'z')),
        CodePointSet.ofRange('0', '9'), EMPTY);
    TokenizerCharacterPolicy swapped = TokenizerCharacterPolicy.of(
        CodePointSet.ofRange('0', '9'),
        CodePointSet.ofRange('A', 'Z').union(CodePointSet.ofRange('a', 'z')), EMPTY);
    TokenizerCharacterPolicy marked = TokenizerCharacterPolicy.of(
        CodePointSet.ofRange('A', 'Z').union(CodePointSet.ofRange('a', 'z')),
        CodePointSet.ofRange('0', '9'), CodePointSet.of(ACUTE_ACCENT));

    assertEquals(TokenizerCharacterPolicy.ascii(), ascii);
    assertEquals(TokenizerCharacterPolicy.ascii().hashCode(), ascii.hashCode());
    assertNotEquals(TokenizerCharacterPolicy.ascii(), swapped);
    assertNotEquals(TokenizerCharacterPolicy.ascii(), marked);
    assertNotEquals(TokenizerCharacterPolicy.ascii(), null);
    assertNotEquals(TokenizerCharacterPolicy.ascii(), ASCII_TEXT);
  }

  private static String codePoints(int... codePoints) {
    StringBuilder value = new StringBuilder();
    for (int codePoint : codePoints) {
      value.appendCodePoint(codePoint);
    }
    return value.toString();
  }
}
