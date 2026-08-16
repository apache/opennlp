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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the three interchange file forms: the dictionary TSV, the passage JSON
 * Lines, and the vocabulary TSV, including round trips, the authored fixture files, and
 * the malformed-content contract ({@link InvalidFormatException}).
 */
public class InterchangeFilesTest {

  private static Path fixture(String name, Path dir) throws IOException {
    final Path file = dir.resolve(name);
    try (InputStream in = InterchangeFilesTest.class
        .getResourceAsStream("/opennlp/embeddings/corpus/" + name)) {
      Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return file;
  }

  @Test
  void testDictionaryFixtureReads(@TempDir Path dir) throws Exception {
    final List<DictionaryEntry> entries =
        DictionaryEntry.readTsv(fixture("mini-dictionary.tsv", dir));
    assertEquals(12, entries.size());
    assertEquals("HABEAS CORPUS", entries.get(6).headword());
  }

  @Test
  void testDictionaryTsvRoundTrips(@TempDir Path dir) throws Exception {
    final List<DictionaryEntry> entries = List.of(
        new DictionaryEntry("A QUO", "from which; an invented round-trip definition."),
        new DictionaryEntry("ZONE", "an invented closing entry."));
    final Path file = dir.resolve("dictionary.tsv");
    DictionaryEntry.writeTsv(entries, file);
    assertEquals(entries, DictionaryEntry.readTsv(file));
  }

  @Test
  void testDictionaryTsvRejectsMalformedLines(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("broken.tsv");
    Files.writeString(file, "NO TAB ON THIS LINE\n");
    assertThrows(InvalidFormatException.class, () -> DictionaryEntry.readTsv(file));
    Files.writeString(file, "TWO\tTABS\tHERE\n");
    assertThrows(InvalidFormatException.class, () -> DictionaryEntry.readTsv(file));
  }

  @Test
  void testDictionaryEntryValidation() {
    assertThrows(IllegalArgumentException.class, () -> new DictionaryEntry(null, "x def"));
    assertThrows(IllegalArgumentException.class, () -> new DictionaryEntry(" ", "x def"));
    assertThrows(IllegalArgumentException.class, () -> new DictionaryEntry("X", "a\tb"));
    assertThrows(IllegalArgumentException.class, () -> new DictionaryEntry("X", "a\nb"));
  }

  @Test
  void testPassageFixtureReads(@TempDir Path dir) throws Exception {
    final List<CasePassage> passages =
        CasePassage.readJsonl(fixture("mini-passages.jsonl", dir));
    assertEquals(6, passages.size());
    assertEquals("Alder v. Birch", passages.get(0).caseName());
    assertEquals("1 Fict. 1", passages.get(0).cite());
    assertTrue(passages.get(0).text().contains("habeas corpus"));
  }

  @Test
  void testPassageJsonlRoundTripsEscapes(@TempDir Path dir) throws Exception {
    final List<CasePassage> passages = List.of(new CasePassage(
        "9-0-0", "Quote \"v.\" Backslash\\Case", "9 Fict. 9", "1904-01-01", "9",
        "A text with a\ttab, a \"quote\", and a\nline break."));
    final Path file = dir.resolve("passages.jsonl");
    CasePassage.writeJsonl(passages, file);
    assertEquals(passages, CasePassage.readJsonl(file));
  }

  @Test
  void testPassageJsonlRejectsMalformedLines(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("broken.jsonl");
    Files.writeString(file, "not json\n");
    assertThrows(InvalidFormatException.class, () -> CasePassage.readJsonl(file));
    Files.writeString(file, "{\"id\": \"1\"}\n");
    assertThrows(InvalidFormatException.class, () -> CasePassage.readJsonl(file));
  }

  @Test
  void testVocabularyTsvRoundTrips(@TempDir Path dir) throws Exception {
    final List<TermCount> terms = List.of(
        new TermCount("habeas corpus", 12, true),
        new TermCount("unused headword", 0, true),
        new TermCount("court", 40, false));
    final Path file = dir.resolve("vocabulary.tsv");
    TermCount.writeTsv(terms, file);
    assertEquals(terms, TermCount.readTsv(file));
  }

  @Test
  void testVocabularyTsvRejectsMalformedLines(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("broken.tsv");
    Files.writeString(file, "term\tnot-a-number\tcorpus\n");
    assertThrows(InvalidFormatException.class, () -> TermCount.readTsv(file));
    Files.writeString(file, "term\t3\tneither\n");
    assertThrows(InvalidFormatException.class, () -> TermCount.readTsv(file));
    Files.writeString(file, "term\t3\n");
    assertThrows(InvalidFormatException.class, () -> TermCount.readTsv(file));
  }

  @Test
  void testTermCountValidation() {
    assertThrows(IllegalArgumentException.class, () -> new TermCount(null, 1, true));
    assertThrows(IllegalArgumentException.class, () -> new TermCount(" ", 1, true));
    assertThrows(IllegalArgumentException.class, () -> new TermCount("x", -1, true));
    assertThrows(IllegalArgumentException.class, () -> new TermCount("a\tb", 1, true));
  }

  @Test
  void testCasePassageValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> new CasePassage(null, "c", "", "", "1", "text"));
    assertThrows(IllegalArgumentException.class,
        () -> new CasePassage(" ", "c", "", "", "1", "text"));
    assertThrows(IllegalArgumentException.class,
        () -> new CasePassage("1", "c", "", "", "1", " "));
    assertThrows(IllegalArgumentException.class,
        () -> new CasePassage("1", null, "", "", "1", "text"));
  }
}
