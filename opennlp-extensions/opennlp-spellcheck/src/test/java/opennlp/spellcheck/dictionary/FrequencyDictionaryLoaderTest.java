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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.InputStreamFactory;

public class FrequencyDictionaryLoaderTest {

  private static final Pattern FORMER_COLUMN_SEPARATOR = Pattern.compile("[\\t ]+");

  private static Stream<Arguments> columnSplits() {
    return Stream.of(
        Arguments.of("", new String[] {""}),
        Arguments.of(" ", new String[0]),
        Arguments.of("\t", new String[0]),
        Arguments.of(" \t \t", new String[0]),
        Arguments.of("a", new String[] {"a"}),
        Arguments.of("a b", new String[] {"a", "b"}),
        Arguments.of("a\tb", new String[] {"a", "b"}),
        Arguments.of("a \t  \t b", new String[] {"a", "b"}),
        Arguments.of(" a", new String[] {"", "a"}),
        Arguments.of("\t\ta b", new String[] {"", "a", "b"}),
        Arguments.of("a b \t", new String[] {"a", "b"}),
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
    Assertions.assertArrayEquals(FORMER_COLUMN_SEPARATOR.split(line),
        FrequencyDictionaryLoader.splitColumns(line));
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
  void testUnigramLineWithoutTabOrSpaceIsMalformed() {
    final String text = "the\u00A0100\n";
    final Map<String, Long> into = new LinkedHashMap<>();
    final FrequencyDictionaryLoader loader = new FrequencyDictionaryLoader();
    final MalformedDictionaryLineException ex = Assertions.assertThrows(
        MalformedDictionaryLineException.class, () -> loader.parseUnigrams(stringResource(text), into));
    Assertions.assertEquals(1, ex.getLineNumber());
  }

  private static InputStreamFactory stringResource(String text) {
    return () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }
}
