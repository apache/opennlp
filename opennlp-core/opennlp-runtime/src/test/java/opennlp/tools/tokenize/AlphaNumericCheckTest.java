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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.lang.Factory;

public class AlphaNumericCheckTest {

  private static final List<String> LANGUAGES =
      List.of("en", "es", "it", "pt", "ca", "pl", "de", "fr", "nl", "xx");

  private static final List<String> TOKENS = List.of(
      "", "a", "Z", "0", "abc123", "Straße", "Café", "señor", "łódź", "ĳs", "Ÿ", "-", "a-b",
      "a b", "a\nb", "\n", "abc\n", "ñ", "Ç", "ß", "é", " ", "١٢٣", "Ａ", "𐐒", "😀a",
      "aé", "AB.", "x_y", "[", "]", "\\", "^", "&", "$", "!", "~", "#", "a.b", "A", "M",
      "abc-", "-abc", "a-b-c", "\u00FF", "\uD7FF", "\uE000", "\uFFFF",
      "\uD800", "\uDFFF", "\uD83D", "\uDE00", "\uD83D\uDE00", "a\uD83D\uDE00b");

  /** Unpaired surrogates: not characters, so a set lookup never accepts them. */
  private static final List<String> UNPAIRED_SURROGATES =
      List.of("\uD800", "\uDFFF", "\uD83D", "\uDE00");

  /** Every surrogate code unit as {@code U+XXXX}, in order. */
  private static final List<String> SURROGATE_BLOCK = surrogateBlock();

  private static Stream<Arguments> builtInPatternsAndTokens() {
    return LANGUAGES.stream().flatMap(language -> {
      Pattern pattern = new Factory().getAlphanumeric(language);
      return TOKENS.stream().map(token -> Arguments.of(language, pattern, token));
    });
  }

  @ParameterizedTest(name = "{0}: \"{2}\"")
  @MethodSource("builtInPatternsAndTokens")
  void testBuiltInPatternsAgreeWithRegex(String language, Pattern pattern, String token) {
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertTrue(check.isCharacterSet(), language + " default runs as a set lookup");
    Assertions.assertEquals(pattern.matcher(token).matches(), check.test(token));
  }

  private static Stream<Arguments> customPatternsAndTokens() {
    List<String> patterns = List.of(
        "^[a-z-]+$", "^[-a-z]+$", "^[a-c1-3]+$", "^[a]+$", "^[a-zA-Z0-9_]+$",
        "^[\\p{L}]+$", "^[^a-z]+$", "^[a-z&&[^b]]+$", "^[\\d]+$", "^[a-z]+$|^[0-9]+$",
        "^[a-z]*$", "[a-z]+", "^(?i)[a-z]+$", "^[a-z]+\\d$", "^[ab\\]]+$", "^[a-]+$",
        "^[😀]+$",
        // a hyphen after a range, a range that starts at a hyphen, ranges over punctuation
        // and space, a one-character range, and class-literal regex operators
        "^[a-b-c]+$", "^[--a]+$", "^[!-~]+$", "^[ -~]+$", "^[a-a]+$", "^[a-z.*+?(){}|$]+$",
        "^[a-z#]+$");
    return patterns.stream().flatMap(regex -> {
      Pattern pattern = Pattern.compile(regex);
      return TOKENS.stream().map(token -> Arguments.of(regex, pattern, token));
    });
  }

  @ParameterizedTest(name = "{0}: \"{2}\"")
  @MethodSource("customPatternsAndTokens")
  void testCustomPatternsAgreeWithRegex(String regex, Pattern pattern, String token) {
    Assertions.assertEquals(pattern.matcher(token).matches(), AlphaNumericCheck.of(pattern).test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"^[A-Za-z0-9]+$", "^[a-z-]+$", "^[-a-z]+$", "^[a-]+$", "^[a-c1-3]+$",
      "^[a]+$", "^[a-zA-Z0-9_]+$", "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$",
      "^[a-b-c]+$", "^[--a]+$", "^[!-~]+$", "^[ -~]+$", "^[a-a]+$", "^[a-z.*+?(){}|$]+$",
      "^[a-z#]+$", "^[A-\uFFFF]+$", "^[\uD7FF-\uE000]+$"})
  void testEligiblePatternsRunAsSetLookup(String regex) {
    Assertions.assertTrue(AlphaNumericCheck.of(Pattern.compile(regex)).isCharacterSet());
  }

  @ParameterizedTest
  @ValueSource(strings = {"^[\\p{L}]+$", "^[^a-z]+$", "^[a-z&&[^b]]+$", "^[\\d]+$",
      "^[a-z]+$|^[0-9]+$", "^[a-z]*$", "[a-z]+", "^(?i)[a-z]+$", "^[a-z]+\\d$", "^[ab\\]]+$",
      "^[😀]+$"})
  void testOtherPatternsFallBackToRegex(String regex) {
    Assertions.assertFalse(AlphaNumericCheck.of(Pattern.compile(regex)).isCharacterSet());
  }

  @Test
  void testFlagsForceRegex() {
    Pattern pattern = Pattern.compile("^[a-z]+$", Pattern.CASE_INSENSITIVE);
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertFalse(check.isCharacterSet());
    Assertions.assertTrue(check.test("ABC"));
  }

  @Test
  void testNullPatternIsRejected() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> AlphaNumericCheck.of(null));
    Assertions.assertEquals("pattern must not be null", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "ABC", "aBZ", "caf\u00E9", "\u00FF"})
  void testSurrogateSpanningRangeAcceptsPlaneZero(String token) {
    AlphaNumericCheck check = AlphaNumericCheck.of(Pattern.compile("^[A-\uFFFF]+$"));
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertTrue(check.test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\uD800", "\uDFFF", "\uD83D", "\uDE00", "\uD83D\uDE00",
      "\uD801\uDC12", "a\uD83D\uDE00b"})
  void testSurrogateSpanningRangeRejectsSurrogatesAndSupplementary(String token) {
    AlphaNumericCheck check = AlphaNumericCheck.of(Pattern.compile("^[A-\uFFFF]+$"));
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertFalse(check.test(token));
  }

  private static Stream<Arguments> surrogateSpanningPatternsAndTokens() {
    return Stream.of("^[A-\uFFFF]+$", "^[\uD7FF-\uE000]+$", "^[\u0001-\uFFFF]+$")
        .flatMap(regex -> TOKENS.stream().map(token -> Arguments.of(regex, token)));
  }

  /**
   * A range over the surrogate block agrees with the engine on every token except an unpaired
   * surrogate, which the engine accepts as a code point and a set lookup rejects.
   */
  @ParameterizedTest(name = "{0}: \"{1}\"")
  @MethodSource("surrogateSpanningPatternsAndTokens")
  void testSurrogateSpanningRangeDiffersFromEngineOnlyOnUnpairedSurrogates(String regex,
      String token) {
    Pattern pattern = Pattern.compile(regex);
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertTrue(check.isCharacterSet());
    if (UNPAIRED_SURROGATES.contains(token)) {
      Assertions.assertTrue(pattern.matcher(token).matches());
      Assertions.assertFalse(check.test(token));
    }
    else {
      Assertions.assertEquals(pattern.matcher(token).matches(), check.test(token));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "Z", "0", "abc123"})
  void testDefaultPatternAcceptsAsciiAlphanumerics(String token) {
    AlphaNumericCheck check = AlphaNumericCheck.of(Factory.DEFAULT_ALPHANUMERIC);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertTrue(check.test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a b", "AB.", "a-b", "caf\u00E9", "\uD83D\uDE00",
      "\uD801\uDC12", "\uD800"})
  void testDefaultPatternRejectsOthers(String token) {
    AlphaNumericCheck check = AlphaNumericCheck.of(Factory.DEFAULT_ALPHANUMERIC);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertFalse(check.test(token));
  }

  private static Stream<Arguments> languagesAndPatterns() {
    return LANGUAGES.stream().map(language ->
        Arguments.of(language, new Factory().getAlphanumeric(language)));
  }

  /**
   * Every built-in language pattern agrees with the engine on every single code point,
   * including unpaired surrogates and the supplementary planes.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("languagesAndPatterns")
  void testBuiltInPatternsAgreeWithRegexOnEveryCodePoint(String language, Pattern pattern) {
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertTrue(check.isCharacterSet(), language + " default runs as a set lookup");
    Assertions.assertEquals(List.of(), disagreements(pattern, check), language);
  }

  private static Stream<Arguments> setLookupPatternsAndExpectedDisagreements() {
    List<String> agreeEverywhere = List.of("^[A-Za-z0-9]+$", "^[a-z-]+$", "^[-a-z]+$", "^[a-]+$",
        "^[a-c1-3]+$", "^[a]+$", "^[a-zA-Z0-9_]+$", "^[a-b-c]+$", "^[--a]+$", "^[!-~]+$",
        "^[ -~]+$", "^[a-a]+$", "^[a-z.*+?(){}|$]+$", "^[a-z#]+$", "^[\u0001-\uD7FF]+$",
        "^[\uE000-\uFFFF]+$",
        // the hyphen after a one-character range is a plain hyphen, no range spans the block
        "^[\uD7FF-\uD7FF-\uE000]+$");
    List<String> spanSurrogates = List.of("^[A-\uFFFF]+$", "^[\uD7FF-\uE000]+$",
        "^[\u0001-\uFFFF]+$");
    return Stream.concat(
        agreeEverywhere.stream().map(regex -> Arguments.of(regex, List.of())),
        spanSurrogates.stream().map(regex -> Arguments.of(regex, SURROGATE_BLOCK)));
  }

  /**
   * A set lookup agrees with the engine on every code point, except that a range over the
   * surrogate block leaves every unpaired surrogate to the engine, which accepts it, while
   * the set rejects it. The difference is that block and no other code point.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("setLookupPatternsAndExpectedDisagreements")
  void testSetLookupDisagreesWithRegexOnlyOnUnpairedSurrogates(String regex,
      List<String> expected) {
    Pattern pattern = Pattern.compile(regex);
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertTrue(check.isCharacterSet(), regex + " runs as a set lookup");
    Assertions.assertEquals(expected, disagreements(pattern, check), regex);
  }

  /**
   * Compares the set and the engine on every code point as a one-character token, unpaired
   * surrogates included.
   *
   * @return The code points on which the results differ, as {@code U+XXXX}, in code point order.
   */
  private static List<String> disagreements(Pattern pattern, AlphaNumericCheck check) {
    List<String> differing = new ArrayList<>();
    for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT;
         codePoint++) {
      String token = new String(Character.toChars(codePoint));
      if (pattern.matcher(token).matches() != check.test(token)) {
        differing.add(codePointName(codePoint));
      }
    }
    return differing;
  }

  private static String codePointName(int codePoint) {
    return String.format("U+%04X", codePoint);
  }

  private static List<String> surrogateBlock() {
    List<String> names = new ArrayList<>();
    for (int c = Character.MIN_SURROGATE; c <= Character.MAX_SURROGATE; c++) {
      names.add(codePointName(c));
    }
    return names;
  }
}
