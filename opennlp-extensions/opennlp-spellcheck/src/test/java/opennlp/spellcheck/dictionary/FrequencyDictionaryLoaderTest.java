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

public class FrequencyDictionaryLoaderTest {

  private static Stream<Arguments> columnSplits() {
    return Stream.of(
        Arguments.of("", new String[0]),
        Arguments.of(" ", new String[0]),
        Arguments.of("\t", new String[0]),
        Arguments.of(" \t \t", new String[0]),
        Arguments.of("a", new String[] {"a"}),
        Arguments.of("a b", new String[] {"a", "b"}),
        Arguments.of("a\tb", new String[] {"a", "b"}),
        Arguments.of("a \t  \t b", new String[] {"a", "b"}),
        // leading, trailing, and repeated separators make no empty column
        Arguments.of(" a", new String[] {"a"}),
        Arguments.of("\t\ta b", new String[] {"a", "b"}),
        Arguments.of("a b \t", new String[] {"a", "b"}),
        Arguments.of(" a  b ", new String[] {"a", "b"}),
        // other whitespace is part of a column
        Arguments.of("ab", new String[] {"ab"}),
        Arguments.of("a\fb\rc\nd", new String[] {"a\fb\rc\nd"}),
        Arguments.of("a\u00A0b 5", new String[] {"a\u00A0b", "5"}),
        Arguments.of("a b\u3000c", new String[] {"a", "b\u3000c"}),
        Arguments.of("\uD83D\uDE00 5\t\uD83D\uDE00",
            new String[] {"\uD83D\uDE00", "5", "\uD83D\uDE00"}));
  }

  @ParameterizedTest
  @MethodSource("columnSplits")
  void testSplitColumnsOnTabAndSpaceRuns(String line, String[] expected) {
    Assertions.assertArrayEquals(expected, FrequencyDictionaryLoader.splitColumns(line));
  }

  @Test
  void testUnigramColumnsSplitOnTabAndSpaceRunsOnly() throws IOException {
    final String text = "the \t 100\nworld\t\t50\n  hello  7  \nab 5\nc\u00A0d\t9\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    final long read = new FrequencyDictionaryLoader().parseUnigrams(stringResource(text), into);
    Assertions.assertEquals(5, read);
    Assertions.assertEquals(100L, into.get("the"));
    Assertions.assertEquals(50L, into.get("world"));
    Assertions.assertEquals(7L, into.get("hello"));
    Assertions.assertEquals(5L, into.get("ab"));
    Assertions.assertEquals(9L, into.get("c\u00A0d"));
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

  @Test
  void testUnigramLineWithLeadingSeparatorsIsRead() throws IOException {
    final String text = "\t the\t100\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    Assertions.assertEquals(1, new FrequencyDictionaryLoader().parseUnigrams(stringResource(text), into));
    Assertions.assertEquals(100L, into.get("the"));
  }

  @Test
  void testUnigramLineWithoutTabOrSpaceIsMalformed() {
    final String text = "the\u00A0100\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class, () -> loader.parseUnigrams(stringResource(text), into));
    Assertions.assertEquals(1, ex.getLineNumber());
  }

  @ParameterizedTest
  // tabs and spaces, no-break spaces, a figure space, a narrow no-break space, an ideographic space
  @ValueSource(strings = {"\t\t", " \t \t ", "\u00A0", "\u00A0\u00A0", " \u00A0\t", "\u2007",
      "\u202F", "\u3000"})
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
  @CsvSource({"+5, 5", "007, 7", "0, 0", "9223372036854775807, 9223372036854775807"})
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
        Arguments.of("the\t-5", "count must not be negative"),
        Arguments.of("the\t5\u00A0", "count must be ASCII digits with an optional sign"),
        Arguments.of("the\t5.0", "count must be ASCII digits with an optional sign"),
        Arguments.of("the\t1e3", "count must be ASCII digits with an optional sign"),
        Arguments.of("the\t99999999999999999999", "count is out of range"),
        Arguments.of("the\t-", "count must be ASCII digits with an optional sign"),
        Arguments.of("the\t+", "count must be ASCII digits with an optional sign"),
        Arguments.of("the\t+-5", "count must be ASCII digits with an optional sign"),
        Arguments.of(" # note", "count must be ASCII digits with an optional sign"));
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

  @ParameterizedTest
  // Arabic-Indic, fullwidth, mixed with ASCII, and a supplementary digit
  @ValueSource(strings = {"\u0665", "\uFF15", "5\u0665", "\u0661\u0662\u0663", "+\uFF15",
      "\uD835\uDFCE"})
  void testCountInDigitsOfAnotherScriptIsMalformed(String count) {
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class,
        () -> loader.parseUnigrams(stringResource("the\t" + count + "\n"), new LinkedHashMap<>()));
    Assertions.assertEquals(1, ex.getLineNumber());
    Assertions.assertTrue(ex.getMessage().contains("(count must be ASCII digits"), ex.getMessage());
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

  private static InputStreamFactory stringResource(String text) {
    return () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }
}
