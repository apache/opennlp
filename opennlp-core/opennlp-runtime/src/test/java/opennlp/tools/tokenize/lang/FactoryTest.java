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

package opennlp.tools.tokenize.lang;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.LegacyAlphaNumericPatterns;
import opennlp.tools.tokenize.TokenizerCharacterPolicy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link Factory} class. The regular expressions in
 * {@link LegacyAlphaNumericPatterns} are the oracle for the character policies that replace them.
 */
public class FactoryTest {

  private static final int DIGIT_COUNT = 10;

  private static final List<String> MULTI_CHARACTER_TOKENS = List.of(
      "abc", "ABC", "0123456789", "a1b2", "Café", "CAFÉ", "straße", "naïve", "ĳs", "IJs",
      "żółw", "año", "Niño", "coração", "ÀÉÎÕÜ", "œuvre", "Ÿ", "maçã", "a-b", "a b", "a_b",
      "ab\n", "ab\r\n", "\u00E9\u0301", "e\u0301", "a\u00A0b", "\u3042", "\uD801\uDC00",
      "ab\uD801\uDC00", "a\uD800", "\uDC00a");

  private static final List<String> SUPPLEMENTARY_TOKENS = List.of(
      "\uD801\uDC00", "\uD835\uDFD8", "\uD83D\uDE00", "a\uD801\uDC00", "\uD801\uDC00a");

  private final Factory factory = new Factory();

  /**
   * Supplies one representative language code per policy with its legacy regex.
   *
   * @return Language code and regex pairs.
   */
  static Stream<Arguments> shippedPolicies() {
    return LegacyAlphaNumericPatterns.BY_LANGUAGE.entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

  @ParameterizedTest
  @MethodSource("shippedPolicies")
  void testPolicyMatchesLegacyRegexOnEveryBmpCodePoint(String languageCode, String regex) {
    Pattern oracle = Pattern.compile(regex);
    TokenizerCharacterPolicy policy = factory.getCharacterPolicy(languageCode);

    for (int codePoint = 0; codePoint <= Character.MAX_VALUE; codePoint++) {
      String token = String.valueOf((char) codePoint);
      assertEquals(oracle.matcher(token).matches(), policy.test(token),
          languageCode + " U+" + Integer.toHexString(codePoint));
    }
  }

  @ParameterizedTest
  @MethodSource("shippedPolicies")
  void testPolicyMatchesLegacyRegexOnMultiCharacterTokens(String languageCode, String regex) {
    Pattern oracle = Pattern.compile(regex);
    TokenizerCharacterPolicy policy = factory.getCharacterPolicy(languageCode);

    for (String token : MULTI_CHARACTER_TOKENS) {
      assertEquals(oracle.matcher(token).matches(), policy.test(token),
          languageCode + " '" + token + "'");
    }
  }

  @ParameterizedTest
  @MethodSource("shippedPolicies")
  void testPolicyRejectsSupplementaryCharactersLikeLegacyRegex(String languageCode, String regex) {
    Pattern oracle = Pattern.compile(regex);
    TokenizerCharacterPolicy policy = factory.getCharacterPolicy(languageCode);

    for (String token : SUPPLEMENTARY_TOKENS) {
      assertFalse(oracle.matcher(token).matches(), languageCode + " oracle '" + token + "'");
      assertFalse(policy.test(token), languageCode + " '" + token + "'");
    }
  }

  @ParameterizedTest
  @MethodSource("shippedPolicies")
  void testShippedPoliciesHaveAsciiDigitsAndNoMarks(String languageCode, String regex) {
    TokenizerCharacterPolicy policy = factory.getCharacterPolicy(languageCode);

    assertEquals(DIGIT_COUNT, policy.getDigits().size(), languageCode);
    for (int digit = '0'; digit <= '9'; digit++) {
      assertTrue(policy.getDigits().contains(digit), languageCode);
    }
    assertTrue(policy.getMarks().isEmpty(), languageCode);
  }

  @ParameterizedTest
  @MethodSource("shippedPolicies")
  void testLetterSetIsExactlyTheLegacyClassMinusDigits(String languageCode, String regex) {
    Pattern oracle = Pattern.compile(regex);
    TokenizerCharacterPolicy policy = factory.getCharacterPolicy(languageCode);

    int expectedLetters = 0;
    for (int codePoint = 0; codePoint <= Character.MAX_VALUE; codePoint++) {
      if (oracle.matcher(String.valueOf((char) codePoint)).matches()
          && !policy.getDigits().contains(codePoint)) {
        expectedLetters++;
        assertTrue(policy.getLetters().contains(codePoint),
            languageCode + " U+" + Integer.toHexString(codePoint));
      }
    }
    assertEquals(expectedLetters, policy.getLetters().size(), languageCode);
  }

  @ParameterizedTest
  @CsvSource({
      "es, spa",
      "it, ita",
      "pt, por",
      "ca, cat",
      "pl, pol",
      "de, deu",
      "de, ger",
      "fr, fre",
      "fr, fra",
      "nl, nld",
      "nl, dut"})
  void testTwoAndThreeLetterCodesShareOnePolicy(String twoLetter, String threeLetter) {
    assertSame(factory.getCharacterPolicy(twoLetter), factory.getCharacterPolicy(threeLetter));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"en", "eng", "xx", "", "ES", "x-unspecified"})
  void testUnknownOrAbsentLanguageUsesTheDefaultPolicy(String languageCode) {
    assertSame(Factory.DEFAULT_CHARACTER_POLICY, factory.getCharacterPolicy(languageCode));
  }

  @Test
  void testDefaultPolicyIsTheAsciiPreset() {
    assertSame(TokenizerCharacterPolicy.ascii(), Factory.DEFAULT_CHARACTER_POLICY);
  }
}
