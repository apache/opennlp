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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the CAP volume reader against zips authored in the test: case metadata
 * extraction, official-citation selection, paragraph packing, and the
 * malformed-content contract.
 */
public class CapVolumeReaderTest {

  private static Path zip(Path dir, String name, String... cases) throws IOException {
    final Path file = dir.resolve(name);
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
      for (int i = 0; i < cases.length; i++) {
        out.putNextEntry(new ZipEntry(String.format("json/%04d-01.json", i + 1)));
        out.write(cases[i].getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
      }
    }
    return file;
  }

  private static String caseJson(long id, String name, String citations, String... opinions) {
    final StringBuilder ops = new StringBuilder();
    for (String opinion : opinions) {
      if (ops.length() > 0) {
        ops.append(',');
      }
      final StringBuilder text = new StringBuilder();
      Json.appendString(text, opinion);
      ops.append("{\"type\": \"majority\", \"text\": ").append(text).append('}');
    }
    return "{\"id\": " + id + ", \"name_abbreviation\": \"" + name + "\", "
        + "\"decision_date\": \"1906-01-02\", \"citations\": " + citations + ", "
        + "\"casebody\": {\"opinions\": [" + ops + "]}}";
  }

  @Test
  void testReadsCasesWithOfficialCites(@TempDir Path dir) throws Exception {
    final Path volume = zip(dir, "200.zip",
        caseJson(11, "Alder v. Birch",
            "[{\"type\": \"parallel\", \"cite\": \"26 S. Ct. 1\"},"
                + " {\"type\": \"official\", \"cite\": \"200 U.S. 1\"}]",
            "First paragraph of the only opinion.\nSecond paragraph of it."),
        caseJson(12, "Crown v. Dole",
            "[{\"type\": \"parallel\", \"cite\": \"26 S. Ct. 9\"}]",
            "An opinion in a case without an official citation."));

    final List<CasePassage> passages = CapVolumeReader.read(volume);
    assertEquals(2, passages.size());
    assertEquals("11-0-0", passages.get(0).id());
    assertEquals("Alder v. Birch", passages.get(0).caseName());
    assertEquals("200 U.S. 1", passages.get(0).cite());
    assertEquals("200", passages.get(0).volume());
    assertEquals("First paragraph of the only opinion. Second paragraph of it.",
        passages.get(0).text());
    // Without an official citation the first citation stands in.
    assertEquals("26 S. Ct. 9", passages.get(1).cite());
  }

  @Test
  void testPacksParagraphsToTheSoftTarget() {
    final String paragraph = "word ".repeat(100).strip();  // ~500 chars
    final List<String> passages =
        CapVolumeReader.passagesOf(paragraph + "\n" + paragraph + "\n" + paragraph);
    // Two paragraphs fit under the 1200-char target; the third opens a new passage.
    assertEquals(2, passages.size());
    assertTrue(passages.get(0).length() > CapVolumeReader.TARGET_CHARS / 2);
  }

  @Test
  void testCutsAnOverlongParagraphAtASpaceBeforeTheHardMaximum() {
    final String paragraph = "word ".repeat(600).strip();  // ~3000 chars, no newlines
    final List<String> passages = CapVolumeReader.passagesOf(paragraph);
    assertEquals(2, passages.size());
    assertTrue(passages.get(0).length() < CapVolumeReader.HARD_MAX_CHARS);
    assertTrue(passages.get(0).endsWith("word"));
  }

  @Test
  void testHardCutBoundIsExclusive() {
    // A space exactly at the hard maximum must not be chosen as the cut.
    final String paragraph =
        "x".repeat(CapVolumeReader.HARD_MAX_CHARS) + " tail of the fixture paragraph";
    final List<String> passages = CapVolumeReader.passagesOf(paragraph);
    assertEquals(CapVolumeReader.HARD_MAX_CHARS, passages.get(0).length());
    assertEquals("tail of the fixture paragraph", passages.get(1));
  }

  @Test
  void testMalformedCaseJsonIsRejected(@TempDir Path dir) throws IOException {
    final Path volume = zip(dir, "7.zip", "{\"id\": 1}");
    assertThrows(InvalidFormatException.class, () -> CapVolumeReader.read(volume));
  }

  @Test
  void testNullZipIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> CapVolumeReader.read(null));
  }
}
