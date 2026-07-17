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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WndbReaderTest {

  private static final String DOG_ID = "wndb-00001075-n";
  private static final String CANID_ID = "wndb-00001160-n";

  static Path fixtureDirectory() {
    final URL url = WndbReaderTest.class.getResource("mini-wndb");
    assertNotNull(url, "Fixture directory mini-wndb must be on the test classpath");
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Unexpected fixture URI: " + url, e);
    }
  }

  static LexicalKnowledgeBase fixture() {
    try {
      return WndbReader.read(fixtureDirectory());
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected IOException reading the WNDB fixture", e);
    }
  }

  @Test
  void testLookupReturnsSynsetWithAllComponents() {
    final List<Synset> senses = fixture().lookup("dog", WordNetPOS.NOUN);
    assertEquals(1, senses.size());
    final Synset dog = senses.get(0);
    assertEquals(DOG_ID, dog.id());
    assertEquals(WordNetPOS.NOUN, dog.pos());
    assertEquals(List.of("dog", "domestic dog"), dog.lemmas());
    assertEquals("a domesticated canid", dog.gloss());
    assertEquals(List.of(CANID_ID), dog.related(WordNetRelation.HYPERNYM));
  }

  @Test
  void testLookupFoldsCaseAndUnderscore() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals(DOG_ID, lexicon.lookup("Domestic_Dog", WordNetPOS.NOUN).get(0).id());
    assertEquals(DOG_ID, lexicon.lookup("DOG", WordNetPOS.NOUN).get(0).id());
  }

  @Test
  void testLookupKeepsIndexSenseOrder() {
    assertEquals(List.of("wndb-00001427-n", "wndb-00001669-n"),
        fixture().lookup("run", WordNetPOS.NOUN).stream().map(Synset::id).toList());
  }

  @Test
  void testRelationNavigation() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals(List.of(DOG_ID), lexicon.related(CANID_ID, WordNetRelation.HYPONYM));
    assertEquals(List.of("wndb-00001075-v", "wndb-00001171-v"),
        lexicon.related("wndb-00001324-v", WordNetRelation.HYPONYM));
    assertEquals(List.of("wndb-00001075-v"),
        lexicon.related("wndb-00001427-n", WordNetRelation.DERIVATIONALLY_RELATED));
  }

  @Test
  void testRelationTargetSharesCanonicalIdInstance() {
    final LexicalKnowledgeBase lexicon = fixture();
    final String target = lexicon.synset(DOG_ID).orElseThrow()
        .related(WordNetRelation.HYPERNYM).get(0);
    // Not just equal: the identical instance from the synset table, so a loaded lexicon keeps
    // one copy of each id no matter how many pointers reference it.
    assertSame(lexicon.synset(CANID_ID).orElseThrow().id(), target);
  }

  @Test
  void testLexicalPointersSurfaceAtSynsetLevel() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals(List.of("wndb-00001141-a"),
        lexicon.related("wndb-00001075-a", WordNetRelation.ANTONYM));
    assertEquals(List.of("wndb-00001075-a"),
        lexicon.related("wndb-00001141-a", WordNetRelation.ANTONYM));
  }

  @Test
  void testSatelliteNormalizesToAdjectiveAndMarkerIsStripped() {
    final LexicalKnowledgeBase lexicon = fixture();
    final Synset large = lexicon.lookup("large", WordNetPOS.ADJECTIVE).get(0);
    assertEquals(WordNetPOS.ADJECTIVE, large.pos());
    assertEquals(List.of("wndb-00001211-a"), large.related(WordNetRelation.SIMILAR_TO));
    // short is stored as short(p); the syntactic marker is not part of the lemma.
    assertEquals(List.of("short"),
        lexicon.lookup("short", WordNetPOS.ADJECTIVE).get(0).lemmas());
  }

  @Test
  void testVerbGroupPointerMapsToVerbGroup(@TempDir Path tempDir) throws IOException {
    // The fixture has no $ pointer, so the VERB_GROUP mapping is pinned against a minimal
    // constructed database whose byte offsets are computed, not hard-coded: every offset field
    // is exactly eight digits, so the second line's position is independent of the digit values.
    writeEmptyDb(tempDir, "noun", "adj", "adv");
    final String template =
        "00000000 29 v 01 sing 0 001 $ XXXXXXXX v 0000 00 | produce musical tones";
    final String off2 = String.format(Locale.ROOT, "%08d", template.length() + 1);
    final String line1 = template.replace("XXXXXXXX", off2);
    final String line2 = off2 + " 29 v 01 chant 0 001 $ 00000000 v 0000 00 | sing monotonously";
    Files.writeString(tempDir.resolve("data.verb"), line1 + "\n" + line2 + "\n",
        StandardCharsets.ISO_8859_1);
    Files.writeString(tempDir.resolve("index.verb"),
        "chant v 1 1 $ 1 0 " + off2 + "\nsing v 1 1 $ 1 0 00000000\n",
        StandardCharsets.ISO_8859_1);
    final LexicalKnowledgeBase lexicon = WndbReader.read(tempDir);
    assertEquals(List.of("wndb-" + off2 + "-v"),
        lexicon.related("wndb-00000000-v", WordNetRelation.VERB_GROUP));
    assertEquals(List.of("wndb-00000000-v"),
        lexicon.related("wndb-" + off2 + "-v", WordNetRelation.VERB_GROUP));
  }

  @Test
  void testUnknownLemmaOrSynsetIsEmpty() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertTrue(lexicon.lookup("zebra", WordNetPOS.NOUN).isEmpty());
    assertTrue(lexicon.synset("wndb-99999999-n").isEmpty());
  }

  @Test
  void testRejectsNullAndMissingDirectory(@TempDir Path tempDir) {
    assertThrows(IllegalArgumentException.class, () -> WndbReader.read(null));
    assertThrows(IllegalArgumentException.class,
        () -> WndbReader.read(tempDir.resolve("absent")));
  }

  @Test
  void testRejectsMissingDatabaseFile(@TempDir Path tempDir) throws IOException {
    copyFixture(tempDir);
    Files.delete(tempDir.resolve("data.verb"));
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("data.verb"));
  }

  @Test
  void testRejectsIndexOffsetWithoutDataLine(@TempDir Path tempDir) throws IOException {
    copyFixture(tempDir);
    mutate(tempDir, "index.noun", line -> line.startsWith("berry ")
        ? line.replace("00001564", "00001565") : line);
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("berry"));
    assertTrue(e.getMessage().contains("00001565"));
  }

  @Test
  void testRejectsDataOffsetFieldMismatch(@TempDir Path tempDir) throws IOException {
    copyFixture(tempDir);
    mutate(tempDir, "data.noun",
        line -> line.replace("00001503 03 n 01 box", "00001504 03 n 01 box"));
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("disagrees"));
  }

  @Test
  void testRejectsTruncatedDataLine(@TempDir Path tempDir) throws IOException {
    copyFixture(tempDir);
    mutate(tempDir, "data.noun", line -> line.startsWith("00001564")
        ? line.substring(0, line.indexOf(" 000 |")) : line);
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("data.noun"));
    assertTrue(e.getMessage().contains("Truncated"));
  }

  @Test
  void testRejectsUndeclaredPointerSymbol(@TempDir Path tempDir) throws IOException {
    copyFixture(tempDir);
    mutate(tempDir, "data.noun", line -> line.replace("001 @ 00001160 n 0000",
        "001 ? 00001160 n 0000"));
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("Undeclared pointer symbol: ?"));
  }

  @Test
  void testRejectsPointerToNonexistentSynset(@TempDir Path tempDir) throws IOException {
    copyFixture(tempDir);
    mutate(tempDir, "data.noun", line -> line.replace("001 @ 00001160 n 0000",
        "001 @ 00009999 n 0000"));
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("wndb-00009999-n"));
  }

  @Test
  void testDanglingPointerErrorNamesPointerLine(@TempDir Path tempDir) throws IOException {
    // A constructed database with no preamble, so the dangling pointer sits on a known line
    // and the error message can be pinned to name it.
    writeEmptyDb(tempDir, "noun", "adj", "adv");
    Files.writeString(tempDir.resolve("data.verb"),
        "00000000 29 v 01 sing 0 001 $ 00009999 v 0000 00 | produce musical tones\n",
        StandardCharsets.ISO_8859_1);
    Files.writeString(tempDir.resolve("index.verb"), "sing v 1 1 $ 1 0 00000000\n",
        StandardCharsets.ISO_8859_1);
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> WndbReader.read(tempDir));
    assertTrue(e.getMessage().contains("wndb-00009999-v"));
    assertTrue(e.getMessage().contains("line 1"));
  }

  private static void writeEmptyDb(Path directory, String... suffixes) throws IOException {
    for (final String suffix : suffixes) {
      Files.writeString(directory.resolve("data." + suffix), "");
      Files.writeString(directory.resolve("index." + suffix), "");
    }
  }

  private static void copyFixture(Path target) throws IOException {
    try (var files = Files.list(fixtureDirectory())) {
      for (final Path file : files.toList()) {
        Files.copy(file, target.resolve(file.getFileName().toString()));
      }
    }
  }

  // Applies a line transformation to one fixture file. The mutations only ever keep or shrink
  // line lengths of the affected line's own fields, so surrounding offsets stay valid.
  private static void mutate(Path directory, String fileName, UnaryOperator<String> edit)
      throws IOException {
    final Path file = directory.resolve(fileName);
    final List<String> lines = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
    final StringBuilder out = new StringBuilder();
    for (final String line : lines) {
      out.append(edit.apply(line)).append('\n');
    }
    Files.writeString(file, out.toString(), StandardCharsets.ISO_8859_1);
  }
}
