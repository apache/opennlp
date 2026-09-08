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

package opennlp.tools.formats.ad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class ADNameSampleStreamTest extends AbstractADSampleStreamTest<NameSample> {

  @BeforeEach
  void setup() throws IOException {
    super.setup();

    try (ADNameSampleStream stream = new ADNameSampleStream(
            new PlainTextByLineStream(in, StandardCharsets.UTF_8), true)) {
      NameSample sample;
      while ((sample = stream.read()) != null) {
        samples.add(sample);
      }
    }
  }

  @Test
  void testSimpleCount() {
    Assertions.assertEquals(NUM_SENTENCES, samples.size());
  }

  @Test
  void testCheckMergedContractions() {

    Assertions.assertEquals("no", samples.get(0).getSentence()[1]);
    Assertions.assertEquals("no", samples.get(0).getSentence()[11]);
    Assertions.assertEquals("Com", samples.get(1).getSentence()[0]);
    Assertions.assertEquals("relação", samples.get(1).getSentence()[1]);
    Assertions.assertEquals("à", samples.get(1).getSentence()[2]);
    Assertions.assertEquals("mais", samples.get(2).getSentence()[4]);
    Assertions.assertEquals("de", samples.get(2).getSentence()[5]);
    Assertions.assertEquals("da", samples.get(2).getSentence()[8]);
    Assertions.assertEquals("num", samples.get(3).getSentence()[26]);

  }

  @Test
  void testSize() {
    Assertions.assertEquals(25, samples.get(0).getSentence().length);
    Assertions.assertEquals(12, samples.get(1).getSentence().length);
    Assertions.assertEquals(59, samples.get(2).getSentence().length);
    Assertions.assertEquals(33, samples.get(3).getSentence().length);
  }

  @Test
  void testNames() {

    Assertions.assertEquals(new Span(4, 7, "time"), samples.get(0).getNames()[0]);
    Assertions.assertEquals(new Span(8, 10, "place"), samples.get(0).getNames()[1]);
    Assertions.assertEquals(new Span(12, 14, "place"), samples.get(0).getNames()[2]);
    Assertions.assertEquals(new Span(15, 17, "person"), samples.get(0).getNames()[3]);
    Assertions.assertEquals(new Span(18, 19, "numeric"), samples.get(0).getNames()[4]);
    Assertions.assertEquals(new Span(20, 22, "place"), samples.get(0).getNames()[5]);
    Assertions.assertEquals(new Span(23, 24, "place"), samples.get(0).getNames()[6]);

    Assertions.assertEquals(new Span(22, 24, "person"), samples.get(2).getNames()[0]);//    22..24
    Assertions.assertEquals(new Span(25, 27, "person"), samples.get(2).getNames()[1]);//    25..27
    Assertions.assertEquals(new Span(28, 30, "person"), samples.get(2).getNames()[2]);//    28..30
    Assertions.assertEquals(new Span(31, 34, "person"), samples.get(2).getNames()[3]);//    31..34
    Assertions.assertEquals(new Span(35, 37, "person"), samples.get(2).getNames()[4]);//    35..37
    Assertions.assertEquals(new Span(38, 40, "person"), samples.get(2).getNames()[5]);//    38..40
    Assertions.assertEquals(new Span(41, 43, "person"), samples.get(2).getNames()[6]);//    41..43
    Assertions.assertEquals(new Span(44, 46, "person"), samples.get(2).getNames()[7]);//    44..46
    Assertions.assertEquals(new Span(47, 49, "person"), samples.get(2).getNames()[8]);//    47..49
    Assertions.assertEquals(new Span(50, 52, "person"), samples.get(2).getNames()[9]);//    50..52
    Assertions.assertEquals(new Span(53, 55, "person"), samples.get(2).getNames()[10]);//    53..55

    Assertions.assertEquals(new Span(0, 1, "place"), samples.get(3).getNames()[0]);//    0..1
    Assertions.assertEquals(new Span(6, 7, "event"), samples.get(3).getNames()[1]);//    6..7
    Assertions.assertEquals(new Span(15, 16, "organization"), samples.get(3).getNames()[2]);//    15..16
    Assertions.assertEquals(new Span(18, 19, "event"), samples.get(3).getNames()[3]);//    18..19
    Assertions.assertEquals(new Span(27, 28, "event"), samples.get(3).getNames()[4]);//    27..28
    Assertions.assertEquals(new Span(29, 30, "event"), samples.get(3).getNames()[5]);//    29..30

    Assertions.assertEquals(new Span(1, 6, "time"), samples.get(4).getNames()[0]);//    0..1
    Assertions.assertEquals(new Span(0, 3, "person"), samples.get(5).getNames()[0]);//    0..1
  }

  @Test
  void testSmallSentence() {
    Assertions.assertEquals(2, samples.get(6).getSentence().length);
  }

  @Test
  void testMissingRightContraction() {
    Assertions.assertEquals(new Span(0, 1, "person"), samples.get(7).getNames()[0]);
    Assertions.assertEquals(new Span(3, 4, "person"), samples.get(7).getNames()[1]);
    Assertions.assertEquals(new Span(5, 6, "person"), samples.get(7).getNames()[2]);
  }

  private static Stream<Arguments> underscoreLexemes() {
    return Stream.of(
        Arguments.of("Rio_de_Janeiro", new String[] {"Rio", "de", "Janeiro"}),
        Arguments.of("a__b", new String[] {"a", "b"}),
        Arguments.of("_a", new String[] {"", "a"}),
        Arguments.of("a_", new String[] {"a"}),
        Arguments.of("__", new String[0]),
        Arguments.of("_", new String[0]),
        Arguments.of("\uD801\uDC12_\uD83D\uDE00", new String[] {"\uD801\uDC12", "\uD83D\uDE00"}),
        Arguments.of("", new String[] {""}),
        Arguments.of("casa", new String[] {"casa"}));
  }

  @ParameterizedTest
  @MethodSource("underscoreLexemes")
  void testSplitOnUnderscores(String lexeme, String[] expected) {
    Assertions.assertArrayEquals(expected, ADNameSampleStream.splitOnUnderscores(lexeme));
    Assertions.assertArrayEquals(lexeme.split("[_]+"), ADNameSampleStream.splitOnUnderscores(lexeme));
  }

  @ParameterizedTest
  @ValueSource(strings = {"casa", "São", "1990", "R2D2", "\uD801\uDC12\u0661"})
  void testIsAlphaNumericAccepts(String token) {
    Assertions.assertTrue(ADNameSampleStream.isAlphaNumeric(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "guarda-chuva", "R$", "a b", "\u00BD", "\uD83D\uDE00"})
  void testIsAlphaNumericRejects(String token) {
    Assertions.assertFalse(ADNameSampleStream.isAlphaNumeric(token));
  }

  private static Stream<Arguments> hyphenatedTokens() {
    return Stream.of(
        Arguments.of("guarda-", new String[] {"guarda", null, null}),
        Arguments.of("a-", new String[] {"a", null, null}),
        Arguments.of("-chuva", new String[] {null, "chuva", ""}),
        Arguments.of("-chuva2!", new String[] {null, "chuva", "2!"}),
        Arguments.of("guarda-chuva", new String[] {"guarda", "chuva", ""}),
        Arguments.of("guarda-chuva-sol", new String[] {"guarda", "chuva", "-sol"}),
        Arguments.of("São-Paulo", new String[] {"São", "Paulo", ""}),
        // supplementary-plane letters are letters, a combining mark ends the letter run
        Arguments.of("\uD801\uDC12-\uD801\uDC3A", new String[] {"\uD801\uDC12", "\uD801\uDC3A", ""}),
        Arguments.of("e\u0301-a", new String[] {null, null, null}));
  }

  @ParameterizedTest
  @MethodSource("hyphenatedTokens")
  void testMatchHyphenatedToken(String token, String[] expected) {
    String[] actual = ADNameSampleStream.matchHyphenatedToken(token);
    if (expected[0] == null && expected[1] == null && expected[2] == null) {
      Assertions.assertNull(actual);
    } else {
      Assertions.assertArrayEquals(expected, actual);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"-", "--", "-1", "1-", "a1-b", "a-1", "a--b", "ab", "a -"})
  void testMatchHyphenatedTokenRejects(String token) {
    Assertions.assertNull(ADNameSampleStream.matchHyphenatedToken(token));
  }

  @ParameterizedTest
  @CsvSource({"<NER:PROP>, PROP", "<PROP>, PROP", "<>, ''", "<NER:>, ''", "<a<b>, a<b",
      "<NER:NER:X>, NER:X", "<ner:PROP>, ner:PROP", "<\uD83D\uDE00>, \uD83D\uDE00"})
  void testTagContent(String tag, String expected) {
    Assertions.assertEquals(expected, ADNameSampleStream.tagContent(tag));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "<", ">", "PROP", "<PROP", "PROP>"})
  void testTagContentRejects(String tag) {
    Assertions.assertNull(ADNameSampleStream.tagContent(tag));
  }

  private static ObjectStream<String> lineStream(List<String> lines) {
    Iterator<String> iterator = lines.iterator();
    return new ObjectStream<>() {
      @Override
      public String read() {
        return iterator.hasNext() ? iterator.next() : null;
      }
    };
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "1001|SOURCE: ref=\"x\"",
      "LIT-1|SOURCE: ref=\"x\"",
      "CIE1|SOURCE: source=\"text\""
  })
  void testTextIdFromCorpusMetadata(String sentenceId, String source) throws IOException {
    List<String> lines = List.of("<s>", source, sentenceId + " Olá .", "STA:fcl",
        "=H:intj(\"olá\" <x>)\tOlá", ".", "</s>");
    try (ADNameSampleStream stream = new ADNameSampleStream(lineStream(lines), false)) {
      NameSample sample = stream.read();
      Assertions.assertNotNull(sample);
      Assertions.assertArrayEquals(new String[] {"Olá", "."}, sample.getSentence());
      Assertions.assertNull(stream.read());
    }
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      // no digits after the prefix
      "LIT|SOURCE: ref=\"x\"",
      // no source attribute
      "CIE1|SOURCE: ref=\"x\"",
      // no digits
      "AX|SOURCE: ref=\"x\""
  })
  void testInvalidMetadataIsRejected(String sentenceId, String source) throws IOException {
    List<String> lines = List.of("<s>", source, sentenceId + " Olá .", "</s>");
    try (ADNameSampleStream stream = new ADNameSampleStream(lineStream(lines), false)) {
      RuntimeException e = Assertions.assertThrows(RuntimeException.class, stream::read);
      Assertions.assertTrue(e.getMessage().startsWith("Invalid metadata: " + sentenceId + " p="));
    }
  }
}
