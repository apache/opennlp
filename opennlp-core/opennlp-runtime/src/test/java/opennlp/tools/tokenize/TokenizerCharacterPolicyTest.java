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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.normalizer.CodePointSet;
import opennlp.tools.util.normalizer.UnicodeWhitespace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenizerCharacterPolicyTest {

  private static final int DESERET_LETTER = 0x10400;
  private static final int MUSICAL_MARK = 0x1D165;
  private static final int MATHEMATICAL_DIGIT = 0x1D7D8;

  @Test
  void testAsciiPresetHasExactSets() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.ascii();

    assertEquals(52, policy.getLetters().size());
    assertEquals(10, policy.getDigits().size());
    assertTrue(policy.getMarks().isEmpty());
    for (int cp = 0; cp < 128; cp++) {
      boolean letter = cp >= 'A' && cp <= 'Z' || cp >= 'a' && cp <= 'z';
      boolean digit = cp >= '0' && cp <= '9';
      assertEquals(letter, policy.getLetters().contains(cp), "letter U+" + cp);
      assertEquals(digit, policy.getDigits().contains(cp), "digit U+" + cp);
    }
  }

  @Test
  void testExplicitSetsAreExposedForPersistence() {
    CodePointSet letters = CodePointSet.of('a', DESERET_LETTER);
    CodePointSet digits = CodePointSet.of('7', MATHEMATICAL_DIGIT);
    CodePointSet marks = CodePointSet.of(0x0301, MUSICAL_MARK);

    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(letters, digits, marks);

    assertEquals(letters, policy.getLetters());
    assertEquals(digits, policy.getDigits());
    assertEquals(marks, policy.getMarks());
  }

  @Test
  void testLetterOnlyAndDigitOnlyPoliciesAreValid() {
    CodePointSet empty = CodePointSet.of();

    assertTrue(TokenizerCharacterPolicy.of(CodePointSet.of('a'), empty, empty).test("aaa"));
    assertTrue(TokenizerCharacterPolicy.of(empty, CodePointSet.of('7'), empty).test("777"));
  }

  @Test
  void testGrammarAcrossCategoryTransitions() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.of('a', 'b'), CodePointSet.of('7', '8'), CodePointSet.of(0x0301));

    assertTrue(policy.test("a"));
    assertTrue(policy.test("7"));
    assertTrue(policy.test("a\u0301\u0301"));
    assertTrue(policy.test("a\u03017"));
    assertTrue(policy.test("7a\u0301"));
    assertTrue(policy.test("a7b\u03018"));
    assertFalse(policy.test(""));
    assertFalse(policy.test("\u0301"));
    assertFalse(policy.test("7\u0301"));
    assertFalse(policy.test("a7\u0301"));
    assertFalse(policy.test("a-b"));
  }

  @Test
  void testSupplementaryBasesAndMarks() {
    TokenizerCharacterPolicy policy = TokenizerCharacterPolicy.of(
        CodePointSet.of(DESERET_LETTER), CodePointSet.of(MATHEMATICAL_DIGIT),
        CodePointSet.of(MUSICAL_MARK));

    assertTrue(policy.test(codePoints(DESERET_LETTER, MUSICAL_MARK, MATHEMATICAL_DIGIT)));
    assertFalse(policy.test(codePoints(MATHEMATICAL_DIGIT, MUSICAL_MARK)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\uD800", "\uDC00", "a\uD800", "\uDC00a", "\uDC00\uD800"})
  void testRejectsUnpairedOrReversedSurrogates(String input) {
    assertFalse(TokenizerCharacterPolicy.ascii().test(input));
  }

  @Test
  void testRejectsNullInput() {
    assertThrows(IllegalArgumentException.class, () -> TokenizerCharacterPolicy.ascii().test(null));
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

  @Test
  void testFactoryRejectsNullSets() {
    CodePointSet a = CodePointSet.of('a');
    CodePointSet empty = CodePointSet.of();

    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(null, empty, empty));
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(a, null, empty));
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(a, empty, null));
  }

  @Test
  void testFactoryRequiresABaseCharacter() {
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(
            CodePointSet.of(), CodePointSet.of(), CodePointSet.of(0x0301)));
  }

  @Test
  void testFactoryRejectsOverlappingCategories() {
    CodePointSet a = CodePointSet.of('a');
    CodePointSet seven = CodePointSet.of('7');
    CodePointSet empty = CodePointSet.of();

    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(a, a, empty));
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(a, seven, a));
    assertThrows(IllegalArgumentException.class,
        () -> TokenizerCharacterPolicy.of(a, seven, seven));
  }

  @Test
  void testFactoryRejectsSurrogatesInEveryCategory() {
    CodePointSet a = CodePointSet.of('a');
    CodePointSet seven = CodePointSet.of('7');
    CodePointSet empty = CodePointSet.of();

    for (int surrogate = Character.MIN_SURROGATE;
         surrogate <= Character.MAX_SURROGATE; surrogate++) {
      int cp = surrogate;
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(CodePointSet.of(cp), seven, empty));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(a, CodePointSet.of(cp), empty));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(a, seven, CodePointSet.of(cp)));
    }
  }

  @Test
  void testFactoryRejectsEveryUnicodeWhitespaceInEveryCategory() {
    CodePointSet a = CodePointSet.of('a');
    CodePointSet seven = CodePointSet.of('7');
    CodePointSet empty = CodePointSet.of();

    for (int whitespace : UnicodeWhitespace.codePoints()) {
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(CodePointSet.of(whitespace), seven, empty));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(a, CodePointSet.of(whitespace), empty));
      assertThrows(IllegalArgumentException.class,
          () -> TokenizerCharacterPolicy.of(a, seven, CodePointSet.of(whitespace)));
    }
  }

  private static String codePoints(int... codePoints) {
    StringBuilder value = new StringBuilder();
    for (int codePoint : codePoints) {
      value.appendCodePoint(codePoint);
    }
    return value.toString();
  }
}
