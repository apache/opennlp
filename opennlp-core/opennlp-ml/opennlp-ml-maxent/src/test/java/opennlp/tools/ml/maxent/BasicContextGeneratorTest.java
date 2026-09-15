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

package opennlp.tools.ml.maxent;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class BasicContextGeneratorTest {

  private static final String[] NONE = new String[0];

  private static Stream<Arguments> literalSeparators() {
    return Stream.of(
        Arguments.of(",", "a,b,c", new String[] {"a", "b", "c"}),
        Arguments.of(",", "single", new String[] {"single"}),
        // the separator is taken as written, not as a regular expression
        Arguments.of("|", "a|b|c", new String[] {"a", "b", "c"}),
        Arguments.of(".", "a.b", new String[] {"a", "b"}),
        Arguments.of("+", "a+b", new String[] {"a", "b"}),
        Arguments.of("(", "a(b", new String[] {"a", "b"}),
        Arguments.of("\\s", "a\\sb", new String[] {"a", "b"}),
        Arguments.of("\\s", "a b", new String[] {"a b"}),
        // a multi-character separator, and a prefix of it in the input
        Arguments.of("::", "a::b::c", new String[] {"a", "b", "c"}),
        Arguments.of("::", "a:b", new String[] {"a:b"}),
        Arguments.of("::", "a:::b", new String[] {"a", ":b"}),
        // occurrences never overlap
        Arguments.of("aa", "aaa", new String[] {"a"}),
        Arguments.of("aa", "baaab", new String[] {"b", "ab"}),
        // an empty predicate is never produced: leading, repeated, and trailing separators
        Arguments.of(",", ",a", new String[] {"a"}),
        Arguments.of(",", "a,", new String[] {"a"}),
        Arguments.of(",", "a,,b", new String[] {"a", "b"}),
        Arguments.of(",", ",a,,b,,", new String[] {"a", "b"}),
        Arguments.of(",", ",,", NONE),
        Arguments.of(",", ",", NONE),
        Arguments.of(",", "", NONE),
        // a separator that is whitespace splits only on itself, not on other whitespace
        Arguments.of(" ", "a b\tc", new String[] {"a", "b\tc"}),
        Arguments.of("\t", "a\tb c", new String[] {"a", "b c"}),
        // a supplementary-plane separator, and one inside the predicates
        Arguments.of("😀", "a😀b", new String[] {"a", "b"}),
        Arguments.of(",", "😀,𐐒", new String[] {"😀", "𐐒"}),
        // an unpaired surrogate is ordinary content
        Arguments.of(",", "\uD83D,b", new String[] {"\uD83D", "b"}));
  }

  @ParameterizedTest
  @MethodSource("literalSeparators")
  void testSplitsOnTheLiteralSeparator(String separator, String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator(separator).getContext(input));
  }

  private static Stream<Arguments> whitespaceContexts() {
    return Stream.of(
        Arguments.of("cp_1 cp_2 cp_3", new String[] {"cp_1", "cp_2", "cp_3"}),
        Arguments.of("single", new String[] {"single"}),
        // runs, tabs, and Unicode whitespace all separate; no empty predicate is produced
        Arguments.of("a  b", new String[] {"a", "b"}),
        Arguments.of("a\tb", new String[] {"a", "b"}),
        Arguments.of("a b", new String[] {"a", "b"}),
        Arguments.of("a　b", new String[] {"a", "b"}),
        Arguments.of("a \t  b", new String[] {"a", "b"}),
        Arguments.of(" a b ", new String[] {"a", "b"}),
        Arguments.of(" a　", new String[] {"a"}),
        Arguments.of("", NONE),
        Arguments.of(" ", NONE),
        Arguments.of(" \t 　", NONE),
        // format characters and supplementary-plane content are not whitespace
        Arguments.of("a​b", new String[] {"a​b"}),
        Arguments.of("😀 𐐒", new String[] {"😀", "𐐒"}),
        Arguments.of("\uD83D b", new String[] {"\uD83D", "b"}));
  }

  @ParameterizedTest
  @MethodSource("whitespaceContexts")
  void testDefaultSplitsOnWhitespace(String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator().getContext(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {""})
  void testEmptySeparatorIsRejected(String separator) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BasicContextGenerator(separator));
  }

  @Test
  void testNullSeparatorIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new BasicContextGenerator(null));
  }

  @Test
  void testNullInputIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new BasicContextGenerator(",").getContext(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new BasicContextGenerator().getContext(null));
  }
}
