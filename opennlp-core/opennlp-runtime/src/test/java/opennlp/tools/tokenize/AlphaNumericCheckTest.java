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

  /** Whole-token cases that single-code-point coverage cannot establish. */
  private static Stream<Arguments> customPatternsAndTokens() {
    return Stream.of(
        Arguments.of("^[a-c1-3]+$", "abc123", true),
        Arguments.of("^[a-c1-3]+$", "abc4", false),
        Arguments.of("^[a-z-]+$", "x-cafe", true),
        Arguments.of("^[a-z-]+$", "x_cafe", false),
        Arguments.of("^[a-z]+$", "abc\n", false),
        Arguments.of("^[a-z]+$", "abc\u0085", false),
        Arguments.of("^[a-z]+$", "abc\u2028", false),
        Arguments.of("^[a-z]+$", "abc\u00A0", false),
        Arguments.of("^[a-z]+$|^[0-9]+$", "123", true),
        Arguments.of("^[a-z]+$|^[0-9]+$", "abc123", false),
        Arguments.of("^[a-z&&[^b]]+$", "ace", true),
        Arguments.of("^[a-z&&[^b]]+$", "abc", false),
        Arguments.of("^[ab\\]]+$", "ab]", true),
        Arguments.of("^[ab\\]]+$", "ab[", false),
        Arguments.of("^[\\p{L}\\p{Nd}]+$", "\uD801\uDC12\uD835\uDFCE", true),
        Arguments.of("^[\\p{L}\\p{Nd}]+$", "a😀", false),
        Arguments.of("^[\\p{L}][\\p{L}\\p{M}\\p{Nd}]*$", "cafe\u0301", true),
        Arguments.of("^[\\p{L}][\\p{L}\\p{M}\\p{Nd}]*$", "\u0301cafe", false));
  }

  @ParameterizedTest(name = "{0}: {1}")
  @MethodSource("customPatternsAndTokens")
  void testCustomPatternWholeTokens(String regex, String token, boolean expected) {
    AlphaNumericCheck check = new AlphaNumericCheck(Pattern.compile(regex));
    Assertions.assertEquals(expected, check.test(token));
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

  private static Stream<Arguments> malformedTokens() {
    return Stream.of(
        Arguments.of("literal range", Pattern.compile("^[A-\uFFFF]+$"), "a\uD800b"),
        Arguments.of("escaped range", Pattern.compile("^[A-\\uFFFF]+$"), "a\uD800b"),
        Arguments.of("flagged range", Pattern.compile("^[A-\uFFFF]+$",
            Pattern.UNICODE_CHARACTER_CLASS), "a\uD800b"),
        Arguments.of("negated class", Pattern.compile("^[^0-9]+$"), "a\uDFFFb"),
        Arguments.of("leading high surrogate", Pattern.compile("(?s)^.+$"), "\uD800ab"),
        Arguments.of("trailing high surrogate", Pattern.compile("(?s)^.+$"), "ab\uDBFF"),
        Arguments.of("leading low surrogate", Pattern.compile("(?s)^.+$"), "\uDC00ab"),
        Arguments.of("trailing low surrogate", Pattern.compile("(?s)^.+$"), "ab\uDFFF"),
        Arguments.of("reversed pair", Pattern.compile("(?s)^.+$"), "\uDC00\uD800"),
        Arguments.of("two high surrogates", Pattern.compile("(?s)^.+$"), "\uD800\uDBFF"),
        Arguments.of("pair then lone surrogate", Pattern.compile("(?s)^.+$"), "😀\uD800"));
  }

  /** Invalid UTF-16 must not bypass model evaluation, whatever pattern syntax is used. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("malformedTokens")
  void testMalformedTokensAreNeverAlphanumeric(String label, Pattern pattern, String token) {
    Assertions.assertFalse(new AlphaNumericCheck(pattern).test(token));
  }

  /** Valid pairs at both supplementary boundaries must survive UTF-16 validation. */
  @ParameterizedTest
  @ValueSource(strings = {"\uD800\uDC00", "\uDBFF\uDFFF", "a😀b", "😀😀"})
  void testFallbackAcceptsValidSurrogatePairs(String token) {
    Assertions.assertTrue(new AlphaNumericCheck(Pattern.compile("(?s)^.+$")).test(token));
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

  private static List<String> surrogateBlock() {
    List<String> names = new ArrayList<>();
    for (int c = Character.MIN_SURROGATE; c <= Character.MAX_SURROGATE; c++) {
      names.add(String.format("U+%04X", c));
    }
    return names;
  }
}
