/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoadVocabTest {

  private File getResource(String name) throws IOException {
    try (InputStream is = Objects.requireNonNull(
        getClass().getResourceAsStream("/opennlp/dl/" + name))) {
      final File tempFile = File.createTempFile("vocab-test-", "-" + name);
      tempFile.deleteOnExit();
      Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      return tempFile;
    }
  }

  @Test
  void testLoadPlainTextVocab() throws IOException {
    final Map<String, Integer> vocab = AbstractDL.loadVocabFile(getResource("vocab-plain.txt"));

    assertNotNull(vocab);
    assertEquals(6, vocab.size());
    assertEquals(0, vocab.get("[CLS]"));
    assertEquals(1, vocab.get("[SEP]"));
    assertEquals(2, vocab.get("[UNK]"));
    assertEquals(3, vocab.get("hello"));
    assertEquals(4, vocab.get("world"));
    assertEquals(5, vocab.get("##ing"));
  }

  @Test
  void testLoadJsonVocab() throws IOException {
    final Map<String, Integer> vocab = AbstractDL.loadVocabFile(getResource("vocab.json"));

    assertNotNull(vocab);
    assertEquals(6, vocab.size());
    assertEquals(0, vocab.get("[CLS]"));
    assertEquals(1, vocab.get("[SEP]"));
    assertEquals(2, vocab.get("[UNK]"));
    assertEquals(3, vocab.get("hello"));
    assertEquals(4, vocab.get("world"));
    assertEquals(5, vocab.get("##ing"));
  }

  @Test
  void testJsonVocabWithEscapedCharacters() throws IOException {
    final File tempFile = File.createTempFile("vocab-escaped", ".json");
    tempFile.deleteOnExit();

    Files.writeString(tempFile.toPath(),
        "{\"hello\\\"world\": 0, \"back\\\\slash\": 1}");

    final Map<String, Integer> vocab = AbstractDL.loadVocabFile(tempFile);

    assertNotNull(vocab);
    assertEquals(2, vocab.size());
    assertEquals(0, vocab.get("hello\"world"));
    assertEquals(1, vocab.get("back\\slash"));
  }

  @Test
  void testJsonVocabWithUnicodeEscapedCharacters() throws IOException {
    final File tempFile = File.createTempFile("vocab-unicode", ".json");
    tempFile.deleteOnExit();

    Files.writeString(tempFile.toPath(),
        "{\"\\u0120token\": 0, \"line\\rbreak\": 1, \"form\\ffeed\": 2}");

    final Map<String, Integer> vocab = AbstractDL.loadVocabFile(tempFile);

    assertNotNull(vocab);
    assertEquals(3, vocab.size());
    assertEquals(0, vocab.get("\u0120token"));
    assertEquals(1, vocab.get("line\rbreak"));
    assertEquals(2, vocab.get("form\ffeed"));
  }

  @Test
  void testJsonVocabRejectsInvalidEscapedCharacters() throws IOException {
    final File tempFile = File.createTempFile("vocab-invalid-escape", ".json");
    tempFile.deleteOnExit();

    Files.writeString(tempFile.toPath(), "{\"bad\\xescape\": 0}");

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.loadVocabFile(tempFile));
  }

  @Test
  void testJsonAndPlainTextVocabProduceSameResult() throws IOException {
    final Map<String, Integer> plainVocab = AbstractDL.loadVocabFile(getResource("vocab-plain.txt"));
    final Map<String, Integer> jsonVocab = AbstractDL.loadVocabFile(getResource("vocab.json"));

    assertEquals(plainVocab, jsonVocab);
  }

  static Stream<Arguments> jsonVocabs() {
    return Stream.of(
        Arguments.of("{}", Map.of()),
        Arguments.of("{\"a\": 1, \"b\": 2}", Map.of("a", 1, "b", 2)),
        Arguments.of(" \t\r\n{\"a\"\n:\r\n  3\t}\n", Map.of("a", 3)),
        Arguments.of("{\"a\":0}", Map.of("a", 0)),
        Arguments.of("{\"a\": 2147483647}", Map.of("a", Integer.MAX_VALUE)),
        Arguments.of("{\"a\\\"b\": 1}", Map.of("a\"b", 1)),
        Arguments.of("{\"a\\\\\": 1}", Map.of("a\\", 1)),
        Arguments.of("{\"\\u0120x\": 7, \"\\u00e9\": 8}", Map.of("\u0120x", 7, "\u00E9", 8)),
        Arguments.of("{\"\uD83D\uDE00\": 1}", Map.of("\uD83D\uDE00", 1)),
        // a raw line break inside a token is content
        Arguments.of("{\"a\nb\": 1}", Map.of("a\nb", 1)),
        Arguments.of("{\"\": 1}", Map.of("", 1)),
        // a later entry for the same token, written or escaped, overwrites the earlier one
        Arguments.of("{\"a\": 1, \"a\": 2}", Map.of("a", 2)),
        Arguments.of("{\"a\": 1, \"\\u0061\": 2}", Map.of("a", 2)));
  }

  @ParameterizedTest
  @MethodSource("jsonVocabs")
  void testLoadJsonVocab(String json, Map<String, Integer> expected) {
    assertEquals(expected, AbstractDL.loadJsonVocab(json));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      // not one object
      "", " ", "[]", "\"a\"", "{\"a\": 1}{}", "{\"a\": 1} x",
      // malformed structure
      "{\"a\": 1", "{\"a\" 1}", "{\"a\": 1 \"b\": 2}", "{\"a\": 1,}", "{a: 1}", "\"a\":1\"b\":2",
      // whitespace outside strings is only the four RFC 8259 characters
      "{\"a\":\u00A05}", "{\"a\":\t4\u000B}",
      // values that are not non-negative integers
      "{\"a\": 1.5}", "{\"a\": -1}", "{\"a\": 12abc}", "{\"a\": \u0661}", "{\"a\": \"1\"}",
      "{\"a\": 99999999999}", "{\"a\": {\"b\": 1}}", "{\"a\": [1]}", "{\"a\": null}",
      // invalid escapes in a token
      "{\"a\\q\": 1}", "{\"\\\uD83D\uDE00\": 2}", "{\"\\u12\": 3}", "{\"\\u+123\": 3}",
      "{\"a\\\nb\": 1}"})
  void testLoadJsonVocabRejectsMalformedText(String json) {
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.loadJsonVocab(json));
  }

  @Test
  void testLoadJsonVocabRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.loadJsonVocab(null));
  }

  @Test
  void testLoadJsonVocabMessageNamesTheToken() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.loadJsonVocab("{\"ok\": 1, \"bad\": -1}"));
    assertTrue(e.getMessage().contains("\"bad\""), e.getMessage());
  }

  private static final String TOKENIZER_JSON = """
      {
        "version": "1.0",
        "truncation": null,
        "added_tokens": [
          {"id": 0, "content": "[PAD]", "special": true},
          {"id": 1, "content": "[UNK]", "special": true}
        ],
        "normalizer": {"type": "BertNormalizer", "lowercase": true},
        "model": {
          "type": "WordPiece",
          "unk_token": "[UNK]",
          "vocab_size": 5,
          "vocab": {"[PAD]": 0, "[UNK]": 1, "hello": 2, "##ing": 3, "\\u0120x": 4}
        }
      }
      """;

  @Test
  void testLoadJsonVocabReadsTheTokenizerJsonLayout() {
    // model.vocab is the vocabulary; the added_tokens ids and vocab_size are not entries
    assertEquals(Map.of("[PAD]", 0, "[UNK]", 1, "hello", 2, "##ing", 3, "\u0120x", 4),
        AbstractDL.loadJsonVocab(TOKENIZER_JSON));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      // an int-valued top-level member makes the top-level object the vocabulary
      "{\"a\": 1, \"model\": {\"vocab\": {\"b\": 2}}}",
      // model or vocab missing, or not an object
      "{\"model\": {\"type\": \"WordPiece\"}}", "{\"model\": \"x\"}",
      "{\"model\": {\"vocab\": [\"a\"]}}", "{\"vocab\": {\"a\": 1}}",
      // the same strict rules apply inside model.vocab
      "{\"model\": {\"vocab\": {\"a\": \"1\"}}}", "{\"model\": {\"vocab\": {\"a\": -1}}}",
      "{\"model\": {\"vocab\": {\"a\": 1,}}}"})
  void testLoadJsonVocabRejectsOtherNestedLayouts(String json) {
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.loadJsonVocab(json));
  }

  @Test
  void testMalformedJsonVocabFileIsRejected() throws IOException {
    final File tempFile = File.createTempFile("vocab-malformed", ".json");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "{\"a\": 1, \"b\": }");

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.loadVocabFile(tempFile));
  }

  @Test
  void testLoadJsonVocabSkipsALeadingByteOrderMark() {
    assertEquals(Map.of("a", 1), AbstractDL.loadJsonVocab("\uFEFF{\"a\": 1}"));
    assertEquals(Map.of("[PAD]", 0, "[UNK]", 1, "hello", 2, "##ing", 3, "\u0120x", 4),
        AbstractDL.loadJsonVocab("\uFEFF" + TOKENIZER_JSON));
  }

  @Test
  void testJsonVocabFileWithAByteOrderMarkIsReadAsJson() throws IOException {
    final File tempFile = File.createTempFile("vocab-bom", ".json");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "\uFEFF{\"a\": 0, \"b\": 1}\n");

    assertEquals(Map.of("a", 0, "b", 1), AbstractDL.loadVocabFile(tempFile));
  }

  @Test
  void testPlainTextVocabFileWithAByteOrderMarkKeepsTheFirstToken() throws IOException {
    final File tempFile = File.createTempFile("vocab-bom", ".txt");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "\uFEFF[CLS]\n[SEP]\n");

    assertEquals(Map.of("[CLS]", 0, "[SEP]", 1), AbstractDL.loadVocabFile(tempFile));
  }

  private static final Map<String, Integer> TOKENIZER_VOCAB =
      Map.of("[PAD]", 0, "[UNK]", 1, "hello", 2, "##ing", 3, "\u0120x", 4);

  static Stream<Arguments> tokenizerLayouts() {
    return Stream.of(
        Arguments.of(TOKENIZER_JSON.replace("\n", "\r\n"), TOKENIZER_VOCAB),
        Arguments.of(TOKENIZER_JSON.replace("\n", "").replace("  ", ""), TOKENIZER_VOCAB),
        // added_tokens ids that collide with vocab ids, before or after the model, are not entries
        Arguments.of("{\"added_tokens\": [{\"id\": 2, \"content\": \"[NEW]\", \"special\": true}],"
            + " \"model\": {\"vocab\": {\"a\": 0, \"b\": 2}}}", Map.of("a", 0, "b", 2)),
        Arguments.of("{\"model\": {\"vocab\": {\"a\": 0, \"b\": 2}},"
            + " \"added_tokens\": [{\"id\": 0, \"content\": \"a\"}, {\"id\": 7, \"content\": \"q\"}]}",
            Map.of("a", 0, "b", 2)),
        // an empty vocab object is an empty vocabulary
        Arguments.of("{\"model\": {\"vocab\": {}}}", Map.of()),
        Arguments.of("{\"model\": {\"type\": \"BPE\", \"vocab\": { }, \"merges\": []}}", Map.of()),
        // vocab objects elsewhere, at any depth, are not the vocabulary
        Arguments.of("{\"normalizer\": {\"a\": {\"b\": {\"c\": [[[{\"vocab\": {\"x\": 9}}]]]}}},"
            + " \"model\": {\"decoder\": {\"vocab\": {\"y\": 8}}, \"merges\": [[\"a\", \"b\"]],"
            + " \"vocab\": {\"z\": 1}}}", Map.of("z", 1)),
        // a later model, a later vocab, or a later token wins
        Arguments.of("{\"model\": {\"vocab\": {\"a\": 1}}, \"model\": {\"vocab\": {\"a\": 2}}}",
            Map.of("a", 2)),
        Arguments.of("{\"model\": {\"vocab\": {\"a\": 1}, \"vocab\": {\"b\": 3}}}", Map.of("b", 3)),
        Arguments.of("{\"model\": {\"vocab\": {\"a\": 1, \"a\": 5, \"\\u0061\": 6}}}", Map.of("a", 6)),
        // the member names may be written with escapes
        Arguments.of("{\"\\u006dodel\": {\"voc\\u0061b\": {\"a\": 1}}}", Map.of("a", 1)),
        Arguments.of("{ \"model\"\t:\t{ \"vocab\"\r\n:\r\n{ \"a\" : 1 } } }", Map.of("a", 1)),
        Arguments.of("{\"model\": {\"vocab\": {\"a\": 2147483647}}}", Map.of("a", Integer.MAX_VALUE)));
  }

  @ParameterizedTest
  @MethodSource("tokenizerLayouts")
  void testLoadJsonVocabTokenizerLayouts(String json, Map<String, Integer> expected) {
    assertEquals(expected, AbstractDL.loadJsonVocab(json));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2147483648", "4294967296", "9223372036854775808",
      "12345678901234567890"})
  void testLoadJsonVocabNamesTheTokenOfAnIdThatDoesNotFit(String id) {
    for (String json : new String[] {"{\"big\": " + id + "}",
        "{\"model\": {\"vocab\": {\"big\": " + id + "}}}"}) {
      final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
          () -> AbstractDL.loadJsonVocab(json), json);
      assertTrue(e.getMessage().contains("\"big\""), e.getMessage());
      assertTrue(e.getMessage().contains("does not fit into an int"), e.getMessage());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\": 00}", "{\"a\": 0123}", "{\"model\": {\"vocab\": {\"a\": 01}}}"})
  void testLoadJsonVocabRejectsLeadingZeros(String json) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.loadJsonVocab(json));
    assertTrue(e.getMessage().contains("offset "), e.getMessage());
  }

  static Stream<Arguments> tokenizerJsonPrefixes() {
    final String text = TOKENIZER_JSON.strip();
    return Stream.iterate(0, n -> n + 1).limit(text.length())
        .map(n -> Arguments.of(n, text.substring(0, n)));
  }

  @ParameterizedTest(name = "cut at {0}")
  @MethodSource("tokenizerJsonPrefixes")
  void testLoadJsonVocabRejectsATruncatedTokenizerJson(int length, String prefix) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.loadJsonVocab(prefix));
    assertTrue(e.getMessage().contains("offset "), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "x", "\uFEFF", ",", "\"vocab\"", "{\"model\": {\"vocab\": {}}}"})
  void testLoadJsonVocabRejectsContentAfterTheTokenizerJson(String trailing) {
    final String json = TOKENIZER_JSON + trailing;
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.loadJsonVocab(json));
    assertTrue(e.getMessage().contains("offset " + TOKENIZER_JSON.length() + ","), e.getMessage());
    assertTrue(e.getMessage().contains("content after the object"), e.getMessage());
  }

  @Test
  void testJsonVocabFileWithWindowsLineEndings() throws IOException {
    final File tempFile = File.createTempFile("vocab-crlf", ".json");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "{\r\n  \"a\": 0,\r\n  \"b\": 1\r\n}\r\n");

    assertEquals(Map.of("a", 0, "b", 1), AbstractDL.loadVocabFile(tempFile));
  }

  @Test
  void testPlainTextVocabFileWithWindowsLineEndings() throws IOException {
    final File tempFile = File.createTempFile("vocab-crlf", ".txt");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "[CLS]\r\n[SEP]\r\nhello\r\n");

    assertEquals(Map.of("[CLS]", 0, "[SEP]", 1, "hello", 2), AbstractDL.loadVocabFile(tempFile));
  }

  @Test
  void testMalformedJsonVocabFileIsReportedAsAnInvalidFormat() throws IOException {
    final File tempFile = File.createTempFile("vocab-malformed", ".json");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "{\"a\": 1, \"b\": }");

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> AbstractDL.loadVocabFile(tempFile));
    assertTrue(e.getMessage().contains(tempFile.getName()), e.getMessage());
    assertTrue(e.getMessage().contains("offset "), e.getMessage());
  }

  @Test
  void testJsonVocabFileWithAnInvalidEscapeIsReportedAsAnInvalidFormat() throws IOException {
    final File tempFile = File.createTempFile("vocab-invalid-escape", ".json");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "{\"bad\\xescape\": 0}");

    assertThrows(InvalidFormatException.class, () -> AbstractDL.loadVocabFile(tempFile));
  }

  private static final String UNIGRAM_TOKENIZER_JSON = "{\"version\": \"1.0\", \"model\": {\"type\": "
      + "\"Unigram\", \"unk_id\": 0, \"vocab\": [[\"<unk>\", 0.0], [\"a\", -1.5]]}}";

  @Test
  void testLoadJsonVocabNamesTheUnsupportedUnigramLayout() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.loadJsonVocab(UNIGRAM_TOKENIZER_JSON));
    assertTrue(e.getMessage().contains("model.vocab"), e.getMessage());
    assertTrue(e.getMessage().contains("Unigram"), e.getMessage());
  }

  @Test
  void testLoadJsonVocabRejectsANonFiniteId() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.loadJsonVocab("{\"ok\": 1, \"bad\": NaN}"));
    assertTrue(e.getMessage().contains("\"bad\""), e.getMessage());
  }
}
