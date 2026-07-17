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
package opennlp.wordnet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MorphyExceptionsTest {

  static MorphyExceptions fixture() {
    try {
      return MorphyExceptions.load(WndbReaderTest.fixtureDirectory());
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected IOException reading the fixture lists", e);
    }
  }

  /**
   * Writes the standard one-entry exception list for each part of speech into
   * {@code directory}. Tests that need a variation overwrite or delete individual files
   * afterwards.
   *
   * @param directory The directory to receive {@code noun.exc}, {@code verb.exc},
   *                  {@code adj.exc}, and {@code adv.exc}.
   * @throws IOException Thrown if writing a file fails.
   */
  private static void writeStandardLists(Path directory) throws IOException {
    Files.writeString(directory.resolve("noun.exc"), "mice mouse\n");
    Files.writeString(directory.resolve("verb.exc"), "went go\n");
    Files.writeString(directory.resolve("adj.exc"), "better good\n");
    Files.writeString(directory.resolve("adv.exc"), "best well\n");
  }

  @ParameterizedTest
  @CsvSource(nullValues = "unknown", value = {
      "mice, NOUN, mouse",
      "went, VERB, go",
      "better, ADJECTIVE, good",
      "best, ADVERB, well",
      // Entries are part-of-speech scoped: went is only a verb exception.
      "went, NOUN, unknown",
      "dog, NOUN, unknown",
  })
  void testLookupPerPartOfSpeech(String form, WordNetPOS pos, String lemma) {
    final List<String> expected = lemma == null ? List.of() : List.of(lemma);
    assertEquals(expected, fixture().lookup(form, pos));
  }

  @Test
  void testLookupFoldsCase() {
    assertEquals(List.of("mouse"), fixture().lookup("Mice", WordNetPOS.NOUN));
    assertEquals(List.of("mouse"), fixture().lookup("MICE", WordNetPOS.NOUN));
  }

  @Test
  void testLookupRejectsNulls() {
    final MorphyExceptions exceptions = fixture();
    assertThrows(IllegalArgumentException.class,
        () -> exceptions.lookup(null, WordNetPOS.NOUN));
    assertThrows(IllegalArgumentException.class, () -> exceptions.lookup("mice", null));
  }

  @Test
  void testLoadRejectsNullAndMissingDirectory(@TempDir Path tempDir) {
    assertThrows(IllegalArgumentException.class, () -> MorphyExceptions.load(null));
    assertThrows(IllegalArgumentException.class,
        () -> MorphyExceptions.load(tempDir.resolve("absent")));
  }

  @Test
  void testLoadRejectsMissingFile(@TempDir Path tempDir) throws IOException {
    writeStandardLists(tempDir);
    Files.delete(tempDir.resolve("adv.exc"));
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> MorphyExceptions.load(tempDir));
    assertTrue(e.getMessage().contains("adv.exc"));
  }

  @Test
  void testLoadRejectsMalformedLine(@TempDir Path tempDir) throws IOException {
    writeStandardLists(tempDir);
    Files.writeString(tempDir.resolve("noun.exc"), "mice mouse\nlonely\n");
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> MorphyExceptions.load(tempDir));
    assertTrue(e.getMessage().contains("noun.exc"));
    assertTrue(e.getMessage().contains("line 2"));
  }

  @Test
  void testMultipleBaseFormsKeepFileOrder(@TempDir Path tempDir) throws IOException {
    writeStandardLists(tempDir);
    Files.writeString(tempDir.resolve("noun.exc"), "axes axis ax\n");
    assertEquals(List.of("axis", "ax"),
        MorphyExceptions.load(tempDir).lookup("axes", WordNetPOS.NOUN));
  }
}
