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

package opennlp.spellcheck.dictionary;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InputStreamFactory;

/**
 * Tests for {@link FrequencyDictionaryLoader}.
 */
public class FrequencyDictionaryLoaderTest {

  /**
   * Wraps {@code text}, encoded as UTF-8, as a factory the loader reads from.
   *
   * @param text The dictionary text.
   * @return A factory that opens a fresh stream over the text on every call.
   */
  private static InputStreamFactory stringResource(String text) {
    return () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void testLoaderSkipsBlankAndCommentLines() throws IOException {
    final String text = "the\t100\n\n# a comment\n   \nworld\t50\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    final long read = new FrequencyDictionaryLoader().parseUnigrams(stringResource(text), into);
    Assertions.assertEquals(2, read);
    Assertions.assertEquals(100L, into.get("the"));
    Assertions.assertEquals(50L, into.get("world"));
  }

  @Test
  void testLoaderRejectsMalformedLine() {
    final String text = "the\tnotanumber\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseUnigrams(stringResource(text), into));
    Assertions.assertEquals(1, ex.getLineNumber());
  }

  private static Stream<Arguments> unigramColumns() {
    return Stream.of(
        Arguments.of("a 5", "a"),
        Arguments.of("a\t5", "a"),
        Arguments.of("a \t  \t 5", "a"),
        // leading, trailing, and repeated separators make no empty column
        Arguments.of(" a 5", "a"),
        Arguments.of("\t\ta 5", "a"),
        Arguments.of("a 5 \t", "a"),
        Arguments.of(" a  5 ", "a"),
        // other whitespace inside a word is part of it
        Arguments.of("a\u000Bb 5", "a\u000Bb"),
        Arguments.of("a\fb 5", "a\fb"),
        Arguments.of("a\u00A0b 5", "a\u00A0b"),
        Arguments.of("a\u3000b 5", "a\u3000b"),
        Arguments.of("\uD83D\uDE00 5", "\uD83D\uDE00"),
        Arguments.of("中文 5", "中文"),
        Arguments.of("cafe\u0301 5", "cafe\u0301"));
  }

  @ParameterizedTest
  @MethodSource("unigramColumns")
  void testUnigramWordEndsAtATabOrSpaceOnly(String line, String word) throws IOException {
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(1,
        new FrequencyDictionaryLoader().parseUnigrams(stringResource(line + "\n"), into));
    Assertions.assertEquals(Map.of(word, 5L), into);
  }

  @Test
  void testBigramColumnsSplitOnTabAndSpaceRuns() throws IOException {
    final String text = "the  world\t3\nhello \t there   4\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    final long read = new FrequencyDictionaryLoader().parseBigrams(stringResource(text), into);
    Assertions.assertEquals(2, read);
    Assertions.assertEquals(3L, into.get("the world"));
    Assertions.assertEquals(4L, into.get("hello there"));
  }

  @ParameterizedTest
  // tabs and spaces, a vertical tab and a form feed, an ideographic space, and a line
  // separator are whitespace to String.isBlank(); a no-break space is not
  @ValueSource(strings = {"\t\t", " \t \t ", "\u000B\f", "\u3000", "\u2028"})
  void testLineOfWhitespaceOnlyIsSkipped(String blank) throws IOException {
    final String text = "the\t100\n" + blank + "\nworld 5\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(2, new FrequencyDictionaryLoader().parseUnigrams(stringResource(text), into));
    Assertions.assertEquals(Map.of("the", 100L, "world", 5L), into);
  }

  @Test
  void testSkippedLinesCountTowardTheLineNumber() {
    final String text = "the\t100\n\t\t\n# note\n\nworld\n";
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseUnigrams(stringResource(text), new LinkedHashMap<>()));
    Assertions.assertEquals(5, ex.getLineNumber());
  }

  @ParameterizedTest
  @CsvSource({"007, 7", "0, 0", "9223372036854775807, 9223372036854775807"})
  void testUnigramCountAccepts(String count, long expected) throws IOException {
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(1,
        new FrequencyDictionaryLoader().parseUnigrams(stringResource("the\t" + count + "\n"), into));
    Assertions.assertEquals(expected, into.get("the"));
  }

  private static Stream<Arguments> malformedUnigramLines() {
    return Stream.of(
        Arguments.of("the", "expected 'word<sep>count'"),
        Arguments.of("the\u00A0100", "expected 'word<sep>count'"),
        // a line of no-break spaces is not blank and holds no separator
        Arguments.of("\u00A0", "expected 'word<sep>count'"),
        Arguments.of("\u00A0\u00A0", "expected 'word<sep>count'"),
        Arguments.of("the\t-5", "count must not be negative"),
        // the sign is reported before the size: all digits, so negative, not overflow
        Arguments.of("the\t-99999999999999999999", "count must not be negative"),
        // Long.MAX_VALUE + 1
        Arguments.of("the\t9223372036854775808", "count is not an integer"),
        Arguments.of("the\t5\u00A0", "count is not an integer"),
        // control characters next to the count are not separators and not digits
        Arguments.of("the\t5\u0001", "count is not an integer"),
        Arguments.of("the\t\u00015", "count is not an integer"),
        Arguments.of("the\t5.0", "count is not an integer"),
        Arguments.of("the\t1e3", "count is not an integer"),
        Arguments.of("the\t99999999999999999999", "count is not an integer"),
        Arguments.of("the\t-", "count is not an integer"),
        Arguments.of("the\t+", "count is not an integer"),
        Arguments.of("the\t+5", "count is not an integer"),
        Arguments.of("the\t+-5", "count is not an integer"),
        Arguments.of("the\t-0", "count must not be negative"));
  }

  @ParameterizedTest
  @MethodSource("malformedUnigramLines")
  void testMalformedUnigramLineNamesTheReason(String line, String reason) {
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseUnigrams(stringResource(line + "\n"), new LinkedHashMap<>()));
    Assertions.assertEquals(1, ex.getLineNumber());
    Assertions.assertTrue(ex.getMessage().contains("(" + reason + ")"), ex.getMessage());
  }

  /**
   * Reads a count written in any decimal digits: an Arabic-Indic digit, a fullwidth digit,
   * mixed digits, three Arabic-Indic digits, four fullwidth digits, a mathematical digit,
   * zero, leading zeros, and an Arabic-Indic leading zero.
   *
   * @param count The count column.
   * @param value The number it denotes.
   * @throws IOException Thrown if parsing fails.
   */
  @ParameterizedTest
  @CsvSource({"\u0665, 5", "\uFF15, 5", "5\u0665, 55", "\u0661\u0662\u0663, 123",
      "\uFF19\uFF12\uFF12\uFF13, 9223", "\uD835\uDFCE, 0", "0, 0", "007, 7", "\u0660\u0665, 5"})
  void testCountsInAnyDecimalDigits(String count, long value) throws IOException {
    final Map<String, Long> unigrams = new LinkedHashMap<>();
    final Map<String, Long> bigrams = new LinkedHashMap<>();
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    loader.parseUnigrams(stringResource("caf\u00E9\t" + count + "\n"), unigrams);
    loader.parseBigrams(stringResource("\u4E2D\u6587 caf\u00E9\t" + count + "\n"), bigrams);
    Assertions.assertEquals(Map.of("caf\u00E9", value), unigrams);
    Assertions.assertEquals(Map.of("\u4E2D\u6587 caf\u00E9", value), bigrams);
  }

  /**
   * Rejects a count that is not digits only: a plus before a fullwidth digit, a sign with
   * nothing after it, an emoji, a digit with a combining mark, and a superscript two, which
   * is a number but not a decimal digit. A minus before an Arabic-Indic digit is negative.
   *
   * @param count The count column.
   * @param reason The expected reason in the message.
   */
  @ParameterizedTest
  @CsvSource({"+\uFF15, count is not an integer", "-\u0660, count must not be negative",
      "-\u0665, count must not be negative", "+5, count is not an integer",
      "-, count is not an integer", "\uD83D\uDE00, count is not an integer",
      "\u0665\u0301, count is not an integer", "\u00B2, count is not an integer"})
  void testCountsThatAreNotDigitsAreRejected(String count, String reason) {
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException unigram = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseUnigrams(stringResource("caf\u00E9\t" + count + "\n"), new LinkedHashMap<>()));
    final MalformedDictionaryLineException bigram = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseBigrams(stringResource("\u4E2D\u6587 caf\u00E9\t" + count + "\n"),
            new LinkedHashMap<>()));
    Assertions.assertTrue(unigram.getMessage().contains("(" + reason + ")"), unigram.getMessage());
    Assertions.assertTrue(bigram.getMessage().contains("(" + reason + ")"), bigram.getMessage());
  }

  @Test
  void testBigramLineWithTwoColumnsNamesTheReason() {
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseBigrams(stringResource("the\t5\n"), new LinkedHashMap<>()));
    Assertions.assertEquals(1, ex.getLineNumber());
    Assertions.assertTrue(ex.getMessage().contains("(expected 'w1<sep>w2<sep>count')"), ex.getMessage());
  }

  @Test
  void testColumnsAfterTheCountAreIgnored() throws IOException {
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(1,
        new FrequencyDictionaryLoader().parseUnigrams(stringResource("the 100 extra\n"), into));
    Assertions.assertEquals(Map.of("the", 100L), into);
  }

  @Test
  void testCommentAfterTheByteOrderMarkIsSkipped() throws IOException {
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(1,
        new FrequencyDictionaryLoader().parseUnigrams(stringResource("\uFEFF# note\nthe 5\n"), into));
    Assertions.assertEquals(Map.of("the", 5L), into);
  }

  @Test
  void testBigramWordsKeepOtherWhitespace() throws IOException {
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(1,
        new FrequencyDictionaryLoader().parseBigrams(stringResource("a b\u00A0c 5\n"), into));
    Assertions.assertEquals(Map.of("a b\u00A0c", 5L), into);
  }
}
