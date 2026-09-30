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

import java.util.Arrays;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the contract of {@link StringUtil#splitNonEmpty(CharSequence, char...)}: fields are
 * separated by any of the literal separators and empty fields are never returned. A seeded
 * differential test checks it against a quoted character-class regular expression.
 */
public class StringUtilSplitNonEmptyTest {

  private static final String SMILEY = "\uD83D\uDE00";

  private static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of("", new char[] {':'}, new String[0]),
        Arguments.of(":", new char[] {':'}, new String[0]),
        Arguments.of(":::", new char[] {':'}, new String[0]),
        Arguments.of("a", new char[] {':'}, new String[] {"a"}),
        Arguments.of("a:b", new char[] {':'}, new String[] {"a", "b"}),
        Arguments.of(":a::b:", new char[] {':'}, new String[] {"a", "b"}),
        Arguments.of("C:\\lib\\a.jar;;D:\\b.jar;", new char[] {';'},
            new String[] {"C:\\lib\\a.jar", "D:\\b.jar"}),
        Arguments.of("a b\tc", new char[] {' ', '\t'}, new String[] {"a", "b", "c"}),
        Arguments.of(" \t a \r\n\f b ", new char[] {' ', '\t', '\r', '\n', '\f'},
            new String[] {"a", "b"}),
        // a separator is a literal, even when it is a regex metacharacter
        Arguments.of("a.b|c", new char[] {'.', '|'}, new String[] {"a", "b", "c"}),
        Arguments.of("a\\sb", new char[] {' '}, new String[] {"a\\sb"}),
        // only the listed separators split, other whitespace stays in the field
        Arguments.of("a\u00A0b\u2003c", new char[] {' '}, new String[] {"a\u00A0b\u2003c"}),
        Arguments.of(SMILEY + ":" + SMILEY, new char[] {':'}, new String[] {SMILEY, SMILEY}));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void testSplitNonEmpty(String input, char[] separators, String[] expected) {
    Assertions.assertArrayEquals(expected, StringUtil.splitNonEmpty(input, separators));
  }

  @Test
  void testRejectsInvalidArguments() {
    Assertions.assertEquals("input must not be null",
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> StringUtil.splitNonEmpty(null, ':')).getMessage());
    Assertions.assertEquals("separators must not be null",
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> StringUtil.splitNonEmpty("a", (char[]) null)).getMessage());
    Assertions.assertThrows(IllegalArgumentException.class, () -> StringUtil.splitNonEmpty("a"));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.splitNonEmpty("a", ':', (char) 0xD83D));
  }

  /**
   * Compares against a {@code [...]+} character-class split with
   * empty fields removed, on seeded random input.
   */
  @Test
  void testMatchesRegexSplitWithoutEmptyFields() {
    final String[] pieces = {":", ";", " ", "\t", ".", "|", "a", "b", "xy", SMILEY, "0"};
    final char[][] separatorSets = {{':'}, {';'}, {' ', '\t'}, {'.', '|'}, {':', ';', ' '}};
    final Pattern[] patterns = new Pattern[separatorSets.length];
    for (int s = 0; s < separatorSets.length; s++) {
      final StringBuilder charClass = new StringBuilder("[");
      for (char separator : separatorSets[s]) {
        // a backslash before a non-letter makes it a literal inside the class
        charClass.append('\\').append(separator);
      }
      patterns[s] = Pattern.compile(charClass.append("]+").toString());
    }
    final Random random = new Random(1932);
    for (int n = 0; n < 5_000; n++) {
      final StringBuilder input = new StringBuilder();
      final int parts = random.nextInt(12);
      for (int p = 0; p < parts; p++) {
        input.append(pieces[random.nextInt(pieces.length)]);
      }
      final int set = random.nextInt(separatorSets.length);
      final char[] separators = separatorSets[set];
      final String[] expected = Arrays.stream(patterns[set].split(input))
          .filter(field -> !field.isEmpty()).toArray(String[]::new);
      Assertions.assertArrayEquals(expected, StringUtil.splitNonEmpty(input, separators),
          () -> "input '" + input + "' separators " + Arrays.toString(separators));
    }
  }
}
