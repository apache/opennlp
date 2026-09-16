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

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the contract of {@link StringUtil#split(CharSequence, char)} and
 * {@link StringUtil#split(CharSequence, char, int)}: both are byte-identical to
 * {@link String#split(String)} with a literal single-character pattern, so the
 * corpus-format parsers can drop {@code String.split} without changing any
 * field extraction. The cases below are the ones the tab- and space-separated
 * formats rely on, in particular trailing empty fields being dropped at the
 * default limit and kept at every other limit.
 */
public class StringUtilSplitTest {

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
