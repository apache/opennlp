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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.lang.Factory;
import opennlp.tools.util.CompatibilityMode;

public class AlphaNumericCheckTest {

  private static final List<String> LANGUAGES =
      List.of("en", "es", "it", "pt", "ca", "pl", "de", "fr", "nl", "xx");

  /** Tokens for the custom-pattern comparison, including multi-character and mixed ones. */
  private static final List<String> TOKENS = List.of(
      "", "a", "Z", "0", "abc123", "Straße", "Café", "señor", "łódź", "ĳs", "Ÿ", "-", "a-b",
      "a b", "a\nb", "\n", "abc\n", "ñ", "Ç", "ß", "é", " ", "١٢٣", "Ａ", "𐐒", "😀a",
      "aé", "AB.", "x_y", "[", "]", "\\", "^", "&", "$", "!", "~", "#", "a.b", "A", "M",
      "abc-", "-abc", "a-b-c", "\u00FF", "\uD7FF", "\uE000", "\uFFFF",
      "\uD800", "\uDFFF", "\uD83D", "\uDE00", "\uD83D\uDE00", "a\uD83D\uDE00b");

  /** Patterns of the supported form: a class of plain characters and ranges. */
  private static final List<String> SET_LOOKUP_PATTERNS = List.of(
      "^[A-Za-z0-9]+$", "^[a-z-]+$", "^[-a-z]+$", "^[a-]+$", "^[a-c1-3]+$", "^[a]+$",
      "^[a-zA-Z0-9_]+$", "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$",
      // a hyphen after a range, a range that starts at a hyphen, ranges over punctuation
      // and space, a one-character range, and class-literal regex operators
      "^[a-b-c]+$", "^[--a]+$", "^[!-~]+$", "^[ -~]+$", "^[a-a]+$", "^[a-z.*+?(){}|$]+$",
      "^[a-z#]+$",
      // ranges that cover, touch, or avoid the surrogate block
      "^[A-\uFFFF]+$", "^[\uD7FF-\uE000]+$", "^[\u0001-\uFFFF]+$", "^[\u0001-\uD7FF]+$",
      "^[\uE000-\uFFFF]+$", "^[\uD7FF-\uD7FF-\uE000]+$");

  /** The set-lookup patterns with a range over the surrogate block. */
  private static final List<String> SURROGATE_SPANNING_PATTERNS = List.of(
      "^[A-\uFFFF]+$", "^[\uD7FF-\uE000]+$", "^[\u0001-\uFFFF]+$");

  /** Every surrogate code unit as {@code U+XXXX}, in order. */
  private static final List<String> SURROGATE_BLOCK = surrogateBlock();

  /** Patterns that are left to the regular expression engine. */
  private static final List<String> ENGINE_PATTERNS = List.of(
      "^[\\p{L}]+$", "^[^a-z]+$", "^[a-z&&[^b]]+$", "^[\\d]+$", "^[a-z]+$|^[0-9]+$",
      "^[a-z]*$", "[a-z]+", "^(?i)[a-z]+$", "^[a-z]+\\d$", "^[ab\\]]+$", "^[😀]+$",
      "^[\\-a]+$", "^[a-z]++$", "^[a-z]+?$", "^[a-z]{1,}$", "^[a^b]+$", "^[a&b]+$");

  @AfterEach
  void resetCompatibilityMode() {
    CompatibilityMode.reset();
  }

  private static Stream<Arguments> languagesAndPatterns() {
    return LANGUAGES.stream().map(language ->
        Arguments.of(language, new Factory().getAlphanumeric(language)));
  }

  /**
   * Each built-in language pattern runs as a set lookup and gives the engine's result on all
   * code points, including unpaired surrogates and the supplementary planes.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("languagesAndPatterns")
  void testBuiltInPatternsAgreeWithRegexOnEveryCodePoint(String language, Pattern pattern) {
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(check.isCharacterSet(), language + " default runs as a set lookup");
    Assertions.assertEquals(List.of(), disagreements(pattern, check), language);
  }

  private static Stream<String> setLookupPatterns() {
    return SET_LOOKUP_PATTERNS.stream();
  }

  private static Stream<Arguments> setLookupPatternsAndExpectedDisagreements() {
    return SET_LOOKUP_PATTERNS.stream().map(regex -> Arguments.of(regex,
        SURROGATE_SPANNING_PATTERNS.contains(regex) ? SURROGATE_BLOCK : List.of()));
  }

  /**
   * A set lookup gives the engine's result on all code points, except that a range over the
   * surrogate block leaves the unpaired surrogates to the engine, which accepts them, while
   * the set rejects them. The difference is that block and no other code point.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("setLookupPatternsAndExpectedDisagreements")
  void testSetLookupDiffersFromRegexOnlyOnUnpairedSurrogates(String regex,
      List<String> expected) {
    Pattern pattern = Pattern.compile(regex);
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(check.isCharacterSet(), regex + " runs as a set lookup");
    Assertions.assertEquals(expected, disagreements(pattern, check), regex);
  }

  /** Under the legacy mode the set gives the engine's result on all code points. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("setLookupPatterns")
  void testSetLookupAgreesWithRegexOnEveryCodePointUnderLegacyMode(String regex) {
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
    Pattern pattern = Pattern.compile(regex);
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(check.isCharacterSet(), regex + " runs as a set lookup");
    Assertions.assertEquals(List.of(), disagreements(pattern, check), regex);
  }

  /** The mode is read when the check is built, so a check keeps its rule afterwards. */
  @Test
  void testModeIsReadAtConstruction() {
    Pattern pattern = Pattern.compile("^[A-\uFFFF]+$");
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
    AlphaNumericCheck legacy = new AlphaNumericCheck(pattern);
    CompatibilityMode.setActive(CompatibilityMode.CURRENT);
    AlphaNumericCheck current = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(legacy.test("\uD800"));
    Assertions.assertFalse(current.test("\uD800"));
    Assertions.assertTrue(legacy.test("A"));
    Assertions.assertTrue(current.test("A"));
  }

  private static Stream<Arguments> customPatternsAndTokens() {
    return Stream.concat(SET_LOOKUP_PATTERNS.stream(), ENGINE_PATTERNS.stream())
        .flatMap(regex -> {
          Pattern pattern = Pattern.compile(regex);
          return TOKENS.stream().map(token -> Arguments.of(regex, pattern, token));
        });
  }

  /** A set lookup rejects a token with an unpaired surrogate; otherwise both agree. */
  @ParameterizedTest(name = "{0}: \"{2}\"")
  @MethodSource("customPatternsAndTokens")
  void testCustomPatternsAgreeWithRegex(String regex, Pattern pattern, String token) {
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    boolean expected = pattern.matcher(token).matches()
        && !(check.isCharacterSet() && hasUnpairedSurrogate(token));
    Assertions.assertEquals(expected, check.test(token));
  }

  @ParameterizedTest
  @MethodSource("setLookupPatterns")
  void testEligiblePatternsRunAsSetLookup(String regex) {
    Assertions.assertTrue(new AlphaNumericCheck(Pattern.compile(regex)).isCharacterSet());
  }

  private static Stream<String> enginePatterns() {
    return ENGINE_PATTERNS.stream();
  }

  @ParameterizedTest
  @MethodSource("enginePatterns")
  void testOtherPatternsFallBackToRegex(String regex) {
    Assertions.assertFalse(new AlphaNumericCheck(Pattern.compile(regex)).isCharacterSet());
  }

  private static Stream<Arguments> flagsAndTokens() {
    return Stream.of(
        Arguments.of(Pattern.CASE_INSENSITIVE, "ABC", true),
        Arguments.of(Pattern.CASE_INSENSITIVE, "abc", true),
        Arguments.of(Pattern.UNICODE_CHARACTER_CLASS, "abc", true),
        Arguments.of(Pattern.UNICODE_CHARACTER_CLASS, "ABC", false));
  }

  /** A pattern compiled with a flag is evaluated by the engine, whatever its form. */
  @ParameterizedTest
  @MethodSource("flagsAndTokens")
  void testFlagsForceRegex(int flags, String token, boolean expected) {
    Pattern pattern = Pattern.compile("^[a-z]+$", flags);
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertFalse(check.isCharacterSet());
    Assertions.assertEquals(expected, check.test(token));
    Assertions.assertEquals(pattern.matcher(token).matches(), check.test(token));
  }

  @Test
  void testNullPatternIsRejected() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new AlphaNumericCheck(null));
    Assertions.assertEquals("pattern must not be null", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\uD800", "\uDFFF", "\uD83D", "\uDE00", "a\uD800b"})
  void testSurrogateSpanningRangeRejectsUnpairedSurrogates(String token) {
    Pattern pattern = Pattern.compile("^[A-\uFFFF]+$");
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertTrue(pattern.matcher(token).matches());
    Assertions.assertFalse(check.test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "ABC", "aBZ", "caf\u00E9", "\u00FF", "\uD7FF", "\uE000",
      "\uFFFF"})
  void testSurrogateSpanningRangeAcceptsPlaneZeroCharacters(String token) {
    Pattern pattern = Pattern.compile("^[A-\uFFFF]+$");
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertTrue(pattern.matcher(token).matches());
    Assertions.assertTrue(check.test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\uD83D\uDE00", "\uD801\uDC12", "a\uD83D\uDE00b", "a b", "\t"})
  void testSurrogateSpanningRangeRejectsSupplementaryAndOthers(String token) {
    Pattern pattern = Pattern.compile("^[A-\uFFFF]+$");
    AlphaNumericCheck check = new AlphaNumericCheck(pattern);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertFalse(pattern.matcher(token).matches());
    Assertions.assertFalse(check.test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "Z", "0", "abc123"})
  void testDefaultPatternAcceptsAsciiAlphanumerics(String token) {
    AlphaNumericCheck check = new AlphaNumericCheck(Factory.DEFAULT_ALPHANUMERIC);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertTrue(check.test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a b", "AB.", "a-b", "caf\u00E9", "\uD83D\uDE00",
      "\uD801\uDC12", "\uD800"})
  void testDefaultPatternRejectsOthers(String token) {
    AlphaNumericCheck check = new AlphaNumericCheck(Factory.DEFAULT_ALPHANUMERIC);
    Assertions.assertTrue(check.isCharacterSet());
    Assertions.assertFalse(check.test(token));
  }

  /**
   * Compares the set and the engine on every code point as a one-character token, unpaired
   * surrogates included.
   *
   * @return The code points on which the results differ, as {@code U+XXXX}, in code point
   *         order.
   */
  private static List<String> disagreements(Pattern pattern, AlphaNumericCheck check) {
    List<String> differing = new ArrayList<>();
    for (int codePoint = Character.MIN_CODE_POINT; codePoint <= Character.MAX_CODE_POINT;
         codePoint++) {
      String token = new String(Character.toChars(codePoint));
      if (pattern.matcher(token).matches() != check.test(token)) {
        differing.add(String.format("U+%04X", codePoint));
      }
    }
    return differing;
  }

  private static boolean hasUnpairedSurrogate(String token) {
    return token.codePoints().anyMatch(
        codePoint -> codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE);
  }

  private static List<String> surrogateBlock() {
    List<String> names = new ArrayList<>();
    for (int c = Character.MIN_SURROGATE; c <= Character.MAX_SURROGATE; c++) {
      names.add(String.format("U+%04X", c));
    }
    return names;
  }
}
