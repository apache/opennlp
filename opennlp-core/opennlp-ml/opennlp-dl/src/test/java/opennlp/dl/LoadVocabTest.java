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
    assertEquals(0, vocab.get("Ġtoken"));
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

  @Test
  void testMalformedJsonVocabFileIsRejected() throws IOException {
    final File tempFile = File.createTempFile("vocab-malformed", ".json");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), "{\"a\": 1, \"b\": }");

    assertThrows(IllegalArgumentException.class, () -> AbstractDL.loadVocabFile(tempFile));
  }
}
