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

package opennlp.embeddings.corpus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Bouvier HTML entry rules against the authored fixture file: headword
 * capture, continuation paragraphs, page-furniture and short-definition filtering,
 * entity decoding, and first-occurrence deduplication across a directory.
 */
public class BouvierDictionaryParserTest {

  private static String fixtureHtml;

  @BeforeAll
  static void loadFixture() throws IOException {
    try (InputStream in = BouvierDictionaryParserTest.class
        .getResourceAsStream("/opennlp/embeddings/corpus/mini-bouvier.htm")) {
      fixtureHtml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void testParsesEntriesAndFiltersFurniture() {
    final List<DictionaryEntry> entries = BouvierDictionaryParser.parse(fixtureHtml);

    // TEST ACT, TENANT, and T & E survive; the title paragraph and SHORT do not.
    assertEquals(3, entries.size());
    assertEquals("TEST ACT", entries.get(0).headword());
    assertTrue(entries.get(0).definition().startsWith("an invented statute"));
    assertEquals("TENANT", entries.get(1).headword());
    assertEquals("T & E", entries.get(2).headword());
  }

  @Test
  void testContinuationParagraphExtendsThePreviousEntry() {
    final List<DictionaryEntry> entries = BouvierDictionaryParser.parse(fixtureHtml);
    assertTrue(entries.get(1).definition()
        .endsWith("2. The second clause of the tenant entry, appended as a continuation paragraph."));
  }

  @Test
  void testNonHeadwordBoldParagraphContinuesInsteadOfStartingAnEntry() {
    final List<DictionaryEntry> entries = BouvierDictionaryParser.parse(fixtureHtml);
    assertTrue(entries.get(2).definition()
        .endsWith("is not a headword, so this text continues the T & E entry."));
  }

  @Test
  void testDecodesNumericCharacterReferences() {
    final List<DictionaryEntry> entries = BouvierDictionaryParser.parse(
        "<p><b>DASH</b>, a &#150; b &#8212; c &#x2013; d, with padding to pass the filter.</p>");
    assertEquals(1, entries.size());
    // The C1 range maps through Windows-1252, so &#150; is an en dash.
    assertEquals("a \u2013 b \u2014 c \u2013 d, with padding to pass the filter.",
        entries.get(0).definition());
  }

  @Test
  void testParseDirectoryDeduplicatesByFirstOccurrence(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("a.htm"),
        "<p><b>ALPHA</b>, the first file's definition of the alpha fixture entry.</p>");
    Files.writeString(dir.resolve("b.htm"),
        "<p><b>ALPHA</b>, the second file's definition, which first-wins must drop.</p>"
            + "<p><b>BETA</b>, the second file's beta entry with a long enough definition.</p>");

    final List<DictionaryEntry> entries = BouvierDictionaryParser.parseDirectory(dir);
    assertEquals(2, entries.size());
    assertEquals("ALPHA", entries.get(0).headword());
    assertTrue(entries.get(0).definition().startsWith("the first file's"));
    assertEquals("BETA", entries.get(1).headword());
  }

  @Test
  void testEmptyDirectoryIsRejected(@TempDir Path dir) {
    assertThrows(IOException.class, () -> BouvierDictionaryParser.parseDirectory(dir));
  }

  @Test
  void testNullArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> BouvierDictionaryParser.parse(null));
    assertThrows(IllegalArgumentException.class,
        () -> BouvierDictionaryParser.parseDirectory(null));
  }
}
