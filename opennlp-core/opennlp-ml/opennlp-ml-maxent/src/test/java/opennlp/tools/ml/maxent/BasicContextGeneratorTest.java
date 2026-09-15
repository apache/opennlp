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

import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.WhitespaceMode;

public class BasicContextGeneratorTest {

  private static final String[] NONE = new String[0];

  private static final String NO_BREAK_SPACE = "\u00A0";
  private static final String NEXT_LINE = "\u0085";
  private static final String FILE_SEPARATOR = "\u001C";
  private static final String EM_SPACE = "\u2003";
  private static final String LINE_SEPARATOR = "\u2028";
  private static final String PARAGRAPH_SEPARATOR = "\u2029";
  private static final String IDEOGRAPHIC_SPACE = "\u3000";
  private static final String ZERO_WIDTH_SPACE = "\u200B";
  private static final String BYTE_ORDER_MARK = "\uFEFF";
  private static final String GRINNING_FACE = "\uD83D\uDE00";
  private static final String DESERET_BEE = "\uD801\uDC12";
  private static final String HIGH_SURROGATE = "\uD83D";
  private static final String LOW_SURROGATE = "\uDE00";

  @AfterEach
  void resetSharedState() {
    WhitespaceTokenizer.INSTANCE.setKeepNewLines(false);
    WhitespaceMode.reset();
  }

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
        Arguments.of("<=>", "a<=>b<=", new String[] {"a", "b<="}),
        // occurrences never overlap
        Arguments.of("aa", "aaa", new String[] {"a"}),
        Arguments.of("aa", "baaab", new String[] {"b", "ab"}),
        // the separator is the whole input, or longer than the input
        Arguments.of("abc", "abc", NONE),
        Arguments.of("abc", "ab", new String[] {"ab"}),
        Arguments.of(",,", ",", new String[] {","}),
        // an empty predicate is never produced: leading, repeated, and trailing separators
        Arguments.of(",", ",a", new String[] {"a"}),
        Arguments.of(",", "a,", new String[] {"a"}),
        Arguments.of(",", "a,,b", new String[] {"a", "b"}),
        Arguments.of(",", ",a,,b,,", new String[] {"a", "b"}),
        Arguments.of(",", ",,", NONE),
        Arguments.of(",", ",", NONE),
        Arguments.of(",", "", NONE),
        Arguments.of("::", "::::a::::", new String[] {"a"}),
        // a supplementary-plane separator, and one inside the predicates
        Arguments.of(GRINNING_FACE, "a" + GRINNING_FACE + "b", new String[] {"a", "b"}),
        Arguments.of(GRINNING_FACE, GRINNING_FACE + GRINNING_FACE, NONE),
        Arguments.of(GRINNING_FACE, HIGH_SURROGATE + "a" + GRINNING_FACE + LOW_SURROGATE,
            new String[] {HIGH_SURROGATE + "a", LOW_SURROGATE}),
        Arguments.of(",", GRINNING_FACE + "," + DESERET_BEE,
            new String[] {GRINNING_FACE, DESERET_BEE}),
        // an unpaired surrogate is ordinary content
        Arguments.of(",", HIGH_SURROGATE + ",b", new String[] {HIGH_SURROGATE, "b"}),
        Arguments.of(",", LOW_SURROGATE + HIGH_SURROGATE, new String[] {LOW_SURROGATE + HIGH_SURROGATE}));
  }

  @ParameterizedTest
  @MethodSource("literalSeparators")
  void testSplitsOnTheLiteralSeparator(String separator, String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator(separator).getContext(input));
  }

  private static Stream<Arguments> patternsTakenAsText() {
    return Stream.of(
        // a regular expression passed as the separator matches its own text only, so input
        // that a pattern split before 3.0.0 is one predicate now
        Arguments.of("\\|", "a|b", new String[] {"a|b"}),
        Arguments.of("\\|", "a\\|b", new String[] {"a", "b"}),
        Arguments.of("\\.", "a.b", new String[] {"a.b"}),
        Arguments.of(Pattern.quote("|"), "a|b", new String[] {"a|b"}),
        Arguments.of("\\s+", "a  b\tc", new String[] {"a  b\tc"}),
        Arguments.of("[ \t]", "a b\tc", new String[] {"a b\tc"}),
        Arguments.of("[,;]", "a,b;c", new String[] {"a,b;c"}));
  }

  @ParameterizedTest
  @MethodSource("patternsTakenAsText")
  void testPatternSeparatorIsTakenAsText(String separator, String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator(separator).getContext(input));
  }

  private static Stream<Arguments> whitespaceSeparators() {
    return Stream.of(
        // a whitespace separator splits on itself only; other whitespace stays in the predicates
        Arguments.of(" ", "a b\tc", new String[] {"a", "b\tc"}),
        Arguments.of(" ", "a b" + NO_BREAK_SPACE + "c", new String[] {"a", "b" + NO_BREAK_SPACE + "c"}),
        Arguments.of(" ", "a" + EM_SPACE + "b", new String[] {"a" + EM_SPACE + "b"}),
        Arguments.of(" ", NO_BREAK_SPACE, new String[] {NO_BREAK_SPACE}),
        Arguments.of("\t", "a\tb c", new String[] {"a", "b c"}),
        Arguments.of("\n", "a\nb c\r\nd", new String[] {"a", "b c\r", "d"}),
        Arguments.of(NO_BREAK_SPACE, "a" + NO_BREAK_SPACE + "b c", new String[] {"a", "b c"}),
        Arguments.of(IDEOGRAPHIC_SPACE, "a" + IDEOGRAPHIC_SPACE + "b\tc", new String[] {"a", "b\tc"}),
        Arguments.of(EM_SPACE, EM_SPACE + "a" + EM_SPACE + EM_SPACE + "b" + EM_SPACE,
            new String[] {"a", "b"}),
        // a run of the separator is one boundary, other whitespace around it is kept
        Arguments.of(" ", " a  b ", new String[] {"a", "b"}),
        Arguments.of(" ", "\ta \t b", new String[] {"\ta", "\t", "b"}),
        Arguments.of(" ", "   ", NONE),
        Arguments.of(" ", "\t", new String[] {"\t"}));
  }

  @ParameterizedTest
  @MethodSource("whitespaceSeparators")
  void testWhitespaceSeparatorSplitsOnItselfOnly(String separator, String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator(separator).getContext(input));
  }

  private static Stream<Arguments> predicatesWithValues() {
    return Stream.of(
        // the separator inside a value still separates
        Arguments.of(" ", "w=a b=c", new String[] {"w=a", "b=c"}),
        Arguments.of(" ", "w=a b", new String[] {"w=a", "b"}),
        Arguments.of(",", "w=a,b x=c", new String[] {"w=a", "b x=c"}),
        Arguments.of(",", "w=a,,x=b,", new String[] {"w=a", "x=b"}),
        // an equals sign as separator splits names from values
        Arguments.of("=", "w=a", new String[] {"w", "a"}),
        Arguments.of("=", "w==a", new String[] {"w", "a"}),
        Arguments.of("=", "w=", new String[] {"w"}),
        // an empty value keeps the name and the sign
        Arguments.of(" ", "w= x", new String[] {"w=", "x"}),
        Arguments.of(",", "w=,x=1", new String[] {"w=", "x=1"}),
        // a value that is the separator text itself
        Arguments.of("::", "w=::x=1", new String[] {"w=", "x=1"}),
        Arguments.of(",", "w=,", new String[] {"w="}));
  }

  @ParameterizedTest
  @MethodSource("predicatesWithValues")
  void testSeparatorInsidePredicateValueSeparates(String separator, String input,
      String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator(separator).getContext(input));
  }

  private static Stream<Arguments> whitespaceContexts() {
    return Stream.of(
        Arguments.of("cp_1 cp_2 cp_3", new String[] {"cp_1", "cp_2", "cp_3"}),
        Arguments.of("single", new String[] {"single"}),
        // runs, tabs, and Unicode whitespace all separate; no empty predicate is produced
        Arguments.of("a  b", new String[] {"a", "b"}),
        Arguments.of("a\tb", new String[] {"a", "b"}),
        Arguments.of("a\nb", new String[] {"a", "b"}),
        Arguments.of("a" + NO_BREAK_SPACE + "b", new String[] {"a", "b"}),
        Arguments.of("a" + EM_SPACE + "b", new String[] {"a", "b"}),
        Arguments.of("a" + IDEOGRAPHIC_SPACE + "b", new String[] {"a", "b"}),
        Arguments.of("a \t" + NO_BREAK_SPACE + " b", new String[] {"a", "b"}),
        Arguments.of("a" + NO_BREAK_SPACE + "b" + EM_SPACE + "c" + IDEOGRAPHIC_SPACE + "d",
            new String[] {"a", "b", "c", "d"}),
        Arguments.of("a\r\nb\fc", new String[] {"a", "b", "c"}),
        Arguments.of(" a b ", new String[] {"a", "b"}),
        Arguments.of(NO_BREAK_SPACE + "a" + IDEOGRAPHIC_SPACE, new String[] {"a"}),
        Arguments.of("", NONE),
        Arguments.of(" ", NONE),
        Arguments.of(" \t" + NO_BREAK_SPACE + IDEOGRAPHIC_SPACE, NONE),
        Arguments.of(NO_BREAK_SPACE + EM_SPACE + IDEOGRAPHIC_SPACE, NONE),
        // the next line, line separator, and paragraph separator characters have the
        // White_Space property; the ASCII information separators and format characters do not
        Arguments.of("a" + NEXT_LINE + "b", new String[] {"a", "b"}),
        Arguments.of("a" + LINE_SEPARATOR + "b", new String[] {"a", "b"}),
        Arguments.of("a" + PARAGRAPH_SEPARATOR + "b", new String[] {"a", "b"}),
        Arguments.of("a" + FILE_SEPARATOR + "b", new String[] {"a" + FILE_SEPARATOR + "b"}),
        Arguments.of("a" + ZERO_WIDTH_SPACE + "b", new String[] {"a" + ZERO_WIDTH_SPACE + "b"}),
        Arguments.of(BYTE_ORDER_MARK + "a b", new String[] {BYTE_ORDER_MARK + "a", "b"}),
        // name=value predicates are kept whole, whitespace in a value separates
        Arguments.of("w=a b x=c", new String[] {"w=a", "b", "x=c"}),
        Arguments.of("w=a" + NO_BREAK_SPACE + "b", new String[] {"w=a", "b"}),
        Arguments.of("w= x", new String[] {"w=", "x"}),
        // supplementary-plane content is not whitespace
        Arguments.of(GRINNING_FACE + " " + DESERET_BEE, new String[] {GRINNING_FACE, DESERET_BEE}),
        Arguments.of(HIGH_SURROGATE + " b", new String[] {HIGH_SURROGATE, "b"}));
  }

  @ParameterizedTest
  @MethodSource("whitespaceContexts")
  void testDefaultSplitsOnWhitespace(String input, String[] expected) {
    Assertions.assertArrayEquals(expected, new BasicContextGenerator().getContext(input));
  }

  /**
   * The default split does not follow the whitespace mode: the next line character
   * separates and the file separator does not, in both modes.
   */
  @ParameterizedTest
  @MethodSource("whitespaceContexts")
  void testDefaultSplitIsTheSameInLegacyMode(String input, String[] expected) {
    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    Assertions.assertArrayEquals(expected, new BasicContextGenerator().getContext(input));
  }

  /**
   * The default split does not go through the shared {@link WhitespaceTokenizer#INSTANCE},
   * whose keep-new-lines flag any {@code TokenizerME} may switch on.
   */
  @Test
  void testDefaultSplitIsUnaffectedByTheSharedTokenizer() {
    WhitespaceTokenizer.INSTANCE.setKeepNewLines(true);
    Assertions.assertArrayEquals(new String[] {"a", "b", "c"},
        new BasicContextGenerator().getContext("a\nb\r\nc"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void testNullOrEmptySeparatorIsRejected(String separator) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new BasicContextGenerator(separator));
    Assertions.assertEquals("sep must not be null or empty", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {HIGH_SURROGATE, LOW_SURROGATE, "a" + HIGH_SURROGATE,
      LOW_SURROGATE + HIGH_SURROGATE})
  void testUnpairedSurrogateSeparatorIsRejected(String separator) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new BasicContextGenerator(separator));
    Assertions.assertEquals("sep must not contain an unpaired surrogate", e.getMessage());
  }

  private static Stream<Arguments> generators() {
    return Stream.of(
        Arguments.of("whitespace", new BasicContextGenerator()),
        Arguments.of("literal", new BasicContextGenerator(",")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generators")
  void testNullInputIsRejected(String name, BasicContextGenerator generator) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> generator.getContext(null));
    Assertions.assertEquals("o must not be null", e.getMessage());
  }
}
