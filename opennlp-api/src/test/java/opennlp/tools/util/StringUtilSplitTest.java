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

package opennlp.tools.util;

import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the contract of {@link StringUtil#split(CharSequence, char)} and
 * {@link StringUtil#split(CharSequence, char, int)}: both give the result of
 * {@link String#split(String)} with a literal single-character pattern, in
 * particular trailing empty fields are dropped at the default limit and kept
 * at every other limit. A seeded differential test checks both overloads
 * against {@code String.split} on random input.
 */
public class StringUtilSplitTest {

  /**
   * Separators the differential test draws from, including the regular
   * expression metacharacters {@code . $ | #}, which {@code String.split}
   * only treats as literals once quoted.
   */
  private static final char[] SEPARATORS = {',', ' ', '\t', '\n', '-', '_', ';', ':', '#', '$', '.', '|'};

  /** Limits the differential test draws from, covering positive, zero and negative. */
  private static final int[] LIMITS = {0, 1, 2, 3, 5, 7, -1};

  /** Pieces random input is assembled from: separators, ASCII and supplementary code points. */
  private static final String[] PIECES = {",", " ", "\t", "\n", "-", "_", ";", ":", "#", "$", ".", "|",
      "a", "b", "xy", "\uD83D\uDE00", "\uD834\uDD1E", "0"};

  /**
   * Pinned (input, separator, expected fields) cases for the two-argument
   * overload, which applies the {@code String.split} default limit of 0:
   * unlimited splits with trailing empty fields removed.
   */
  private static Stream<Arguments> defaultLimitCases() {
    return Stream.of(
        // empty input yields a one-element array containing the empty string
        Arguments.of("", ',', new String[] {""}),
        // no separator present yields the input unchanged
        Arguments.of("abc", ',', new String[] {"abc"}),
        // a single separator alone splits into empty fields, all trailing, all dropped
        Arguments.of(",", ',', new String[] {}),
        Arguments.of(",abc", ',', new String[] {"", "abc"}),
        Arguments.of("abc,", ',', new String[] {"abc"}),
        Arguments.of(",abc,", ',', new String[] {"", "abc"}),
        // repeated separators keep the empty fields between them
        Arguments.of("a,,b", ',', new String[] {"a", "", "b"}),
        Arguments.of(",,,", ',', new String[] {}),
        Arguments.of(",a,,b,", ',', new String[] {"", "a", "", "b"}),
        // supplementary code points are data and must survive untouched
        Arguments.of("\uD83D\uDE00,\uD83D\uDE00", ',',
            new String[] {"\uD83D\uDE00", "\uD83D\uDE00"}),
        // punctuation separators, including regex metacharacters, are literals
        Arguments.of("a-b-c", '-', new String[] {"a", "b", "c"}),
        Arguments.of("a|b|c", '|', new String[] {"a", "b", "c"}),
        Arguments.of("a.b.c", '.', new String[] {"a", "b", "c"}),
        Arguments.of("a$b$c", '$', new String[] {"a", "b", "c"}),
        Arguments.of("a#b#c", '#', new String[] {"a", "b", "c"}),
        Arguments.of("a\tb\tc", '\t', new String[] {"a", "b", "c"}),
        Arguments.of("a\nb\nc", '\n', new String[] {"a", "b", "c"}),
        Arguments.of("a b c", ' ', new String[] {"a", "b", "c"}));
  }

  /**
   * Pinned (input, separator, limit, expected fields) cases for the
   * three-argument overload, covering the full {@code String.split} limit
   * contract: positive limits bound the number of splits and keep trailing
   * empty fields, zero applies the default, negative limits split without
   * bound and keep trailing empty fields.
   */
  private static Stream<Arguments> limitCases() {
    return Stream.of(
        // empty input yields a one-element array containing the empty string at every limit
        Arguments.of("", ',', 0, new String[] {""}),
        Arguments.of("", ',', 1, new String[] {""}),
        Arguments.of("", ',', 5, new String[] {""}),
        Arguments.of("", ',', -1, new String[] {""}),
        // limit 1 splits nothing, the input is returned as the single field
        Arguments.of("a,b,c", ',', 1, new String[] {"a,b,c"}),
        // a positive limit allows at most limit - 1 splits and keeps the remainder
        Arguments.of("a,b,c", ',', 2, new String[] {"a", "b,c"}),
        Arguments.of("a,b,c", ',', 3, new String[] {"a", "b", "c"}),
        // a positive limit larger than the field count changes nothing
        Arguments.of("a,b,c", ',', 5, new String[] {"a", "b", "c"}),
        // limit 0 matches the two-argument overload
        Arguments.of("a,b,c", ',', 0, new String[] {"a", "b", "c"}),
        Arguments.of(",a,", ',', 0, new String[] {"", "a"}),
        // a negative limit keeps trailing empty fields
        Arguments.of("a,", ',', -1, new String[] {"a", ""}),
        Arguments.of(",a,", ',', -1, new String[] {"", "a", ""}),
        Arguments.of(",", ',', -1, new String[] {"", ""}),
        // a positive limit keeps trailing empty fields as well
        Arguments.of("a,", ',', 2, new String[] {"a", ""}),
        Arguments.of(",a,", ',', 2, new String[] {"", "a,"}),
        Arguments.of(",", ',', 2, new String[] {"", ""}),
        // repeated separators interact with the split budget
        Arguments.of("a,,b", ',', 2, new String[] {"a", ",b"}),
        Arguments.of("a,,b", ',', 3, new String[] {"a", "", "b"}),
        // supplementary code points are data and must survive untouched
        Arguments.of("\uD83D\uDE00,\uD83D\uDE00,", ',', -1,
            new String[] {"\uD83D\uDE00", "\uD83D\uDE00", ""}),
        Arguments.of("a-b-c-d", '-', 2, new String[] {"a", "b-c-d"}));
  }

  @ParameterizedTest(name = "split({0}, {1}) = {2}")
  @MethodSource("defaultLimitCases")
  void testSplitDefaultLimit(String input, char separator, String[] expected) {
    Assertions.assertArrayEquals(expected, StringUtil.split(input, separator));
  }

  @ParameterizedTest(name = "split({0}, {1}, {2}) = {3}")
  @MethodSource("limitCases")
  void testSplitWithLimit(String input, char separator, int limit, String[] expected) {
    Assertions.assertArrayEquals(expected, StringUtil.split(input, separator, limit));
  }

  /**
   * The two-argument overload is the three-argument overload at limit 0, so a
   * caller switching between them never sees a boundary change.
   */
  @Test
  void testTwoArgumentOverloadMatchesLimitZero() {
    final String[] inputs = {"", ",", ",a,", "a,,b", "a,b,c", "\uD83D\uDE00,\uD83D\uDE00,"};
    for (String input : inputs) {
      Assertions.assertArrayEquals(StringUtil.split(input, ',', 0),
          StringUtil.split(input, ','), "input: " + input);
    }
  }

  /**
   * The declared {@link CharSequence} receiver accepts implementations other
   * than {@link String}, matching how {@link StringUtil} treats text elsewhere.
   */
  @Test
  void testAcceptsNonStringCharSequence() {
    Assertions.assertArrayEquals(new String[] {"a", "b", "c"},
        StringUtil.split(new StringBuilder("a,b,c"), ','));
    Assertions.assertArrayEquals(new String[] {"a", "b,c"},
        StringUtil.split(new StringBuilder("a,b,c"), ',', 2));
  }

  /**
   * Both overloads produce the {@code String.split} result for the quoted
   * separator on random input over every separator and limit above, so any
   * drift from the JDK contract is caught here rather than at a parser.
   */
  @Test
  void testMatchesStringSplitOnRandomInput() {
    final Random random = new Random(1955);
    for (int n = 0; n < 20_000; n++) {
      final StringBuilder sb = new StringBuilder();
      final int pieces = random.nextInt(9);
      for (int k = 0; k < pieces; k++) {
        sb.append(PIECES[random.nextInt(PIECES.length)]);
      }
      final String input = sb.toString();
      final char separator = SEPARATORS[random.nextInt(SEPARATORS.length)];
      final int limit = LIMITS[random.nextInt(LIMITS.length)];
      final String pattern = Pattern.quote(Character.toString(separator));
      final String label = "input=" + input + " separator=" + separator + " limit=" + limit;
      Assertions.assertArrayEquals(input.split(pattern, limit),
          StringUtil.split(input, separator, limit), label);
      Assertions.assertArrayEquals(input.split(pattern),
          StringUtil.split(input, separator), label);
    }
  }

  /**
   * A surrogate separator would let the character scan cut a supplementary
   * code point in half, where {@code String.split} matches code points, so
   * both overloads reject it.
   */
  @ParameterizedTest
  @MethodSource("surrogateSeparators")
  void testSurrogateSeparatorThrows(char separator) {
    final String input = "x\uD83D\uDE00y";
    IllegalArgumentException twoArg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.split(input, separator));
    Assertions.assertEquals("separator must not be a surrogate", twoArg.getMessage());
    IllegalArgumentException threeArg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.split(input, separator, -1));
    Assertions.assertEquals("separator must not be a surrogate", threeArg.getMessage());
  }

  private static Stream<Arguments> surrogateSeparators() {
    return Stream.of(
        Arguments.of(Character.MIN_HIGH_SURROGATE),
        Arguments.of((char) 0xD83D),
        Arguments.of((char) 0xDE00),
        Arguments.of(Character.MAX_LOW_SURROGATE));
  }

  /**
   * Null input is rejected the same way {@link StringUtil#splitOnUnicodeWhitespace}
   * rejects it.
   */
  @Test
  void testNullInputThrows() {
    IllegalArgumentException twoArg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.split(null, ','));
    Assertions.assertEquals("input must not be null", twoArg.getMessage());
    IllegalArgumentException threeArg = Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.split(null, ',', 2));
    Assertions.assertEquals("input must not be null", threeArg.getMessage());
  }
}
