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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WnLmfReaderTest {

  /**
   * Loads the miniature WN-LMF document from the test classpath into a lexicon.
   *
   * @return The loaded fixture lexicon.
   */
  static LexicalKnowledgeBase fixture() {
    try (InputStream in = WnLmfReaderTest.class.getResourceAsStream("mini-wn-lmf.xml")) {
      assertNotNull(in, "Fixture mini-wn-lmf.xml must be on the test classpath");
      return WnLmfReader.read(in, "mini-wn-lmf.xml");
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected IOException from a classpath stream", e);
    }
  }

  private static LexicalKnowledgeBase parse(String document) throws IOException {
    return WnLmfReader.read(
        new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)), "inline.xml");
  }

  private static String wrap(String body) {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<LexicalResource>\n"
        + "<Lexicon id=\"t\" label=\"t\" language=\"en\" version=\"1\">\n"
        + body + "\n</Lexicon>\n</LexicalResource>\n";
  }

  @Test
  void testLookupReturnsSynsetWithAllComponents() {
    final List<Synset> senses = fixture().lookup("dog", WordNetPOS.NOUN);
    assertEquals(1, senses.size());
    final Synset dog = senses.get(0);
    assertEquals("mini-n1", dog.id());
    assertEquals(WordNetPOS.NOUN, dog.pos());
    assertEquals(List.of("dog", "domestic dog"), dog.lemmas());
    assertEquals("a domesticated canid", dog.gloss());
    assertEquals(List.of("mini-n2"), dog.related(WordNetRelation.HYPERNYM));
  }

  @Test
  void testLookupFoldsCaseAndUnderscore() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals("mini-n1", lexicon.lookup("Domestic_Dog", WordNetPOS.NOUN).get(0).id());
    assertEquals("mini-n1", lexicon.lookup("DOG", WordNetPOS.NOUN).get(0).id());
  }

  @Test
  void testLookupKeepsSenseOrder() {
    final List<Synset> runSenses = fixture().lookup("run", WordNetPOS.NOUN);
    assertEquals(List.of("mini-n5", "mini-n9"),
        runSenses.stream().map(Synset::id).toList());
  }

  @Test
  void testPreservesMultipleDefinitions() throws IOException {
    final LexicalKnowledgeBase lexicon = parse(
        wrap("<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
            + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
            + "<Synset id=\"t-1\" partOfSpeech=\"n\">"
            + "<Definition>a feline</Definition>"
            + "<Definition>a domesticated cat</Definition></Synset>"));

    assertEquals("a feline; a domesticated cat",
        lexicon.lookup("cat", WordNetPOS.NOUN).get(0).gloss());
  }

  @Test
  void testLookupIsPosScoped() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals(1, lexicon.lookup("run", WordNetPOS.VERB).size());
    assertTrue(lexicon.lookup("dog", WordNetPOS.VERB).isEmpty());
    assertFalse(lexicon.contains("walk", WordNetPOS.NOUN));
    assertTrue(lexicon.contains("walk", WordNetPOS.VERB));
  }

  @Test
  void testRelationNavigation() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals(List.of("mini-n1"), lexicon.related("mini-n2", WordNetRelation.HYPONYM));
    assertEquals(List.of("mini-v1", "mini-v2"),
        lexicon.related("mini-v4", WordNetRelation.HYPONYM));
    assertEquals(List.of("mini-v4"), lexicon.related("mini-v1", WordNetRelation.HYPERNYM));
  }

  @Test
  void testRelationTargetSharesCanonicalIdInstance() {
    final LexicalKnowledgeBase lexicon = fixture();
    final String target = lexicon.synset("mini-n1").orElseThrow()
        .related(WordNetRelation.HYPERNYM).get(0);
    // Relation targets reuse the id instance from the synset table.
    assertSame(lexicon.synset("mini-n2").orElseThrow().id(), target);
  }

  @Test
  void testSenseRelationsAreRepresentedAtSynsetLevel() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertEquals(List.of("mini-a2"), lexicon.related("mini-a1", WordNetRelation.ANTONYM));
    assertEquals(List.of("mini-a1"), lexicon.related("mini-a2", WordNetRelation.ANTONYM));
    assertEquals(List.of("mini-v1"),
        lexicon.related("mini-n5", WordNetRelation.DERIVATIONALLY_RELATED));
    assertEquals(List.of("mini-n5"),
        lexicon.related("mini-v1", WordNetRelation.DERIVATIONALLY_RELATED));
  }

  @Test
  void testSatelliteNormalizesToAdjective() {
    final List<Synset> senses = fixture().lookup("large", WordNetPOS.ADJECTIVE);
    assertEquals(1, senses.size());
    assertEquals(WordNetPOS.ADJECTIVE, senses.get(0).pos());
    assertEquals(List.of("mini-a4"), fixture().related("mini-a3", WordNetRelation.SIMILAR_TO));
    assertEquals(List.of("mini-a3"), fixture().related("mini-a4", WordNetRelation.SIMILAR_TO));
  }

  @Test
  void testSimilarOnVerbSynsetMapsToVerbGroup() throws IOException {
    // Documents derived from Princeton data express verb groups as similar on verb synsets;
    // the fixture only carries similar on adjectives, so this pins the verb branch directly.
    final LexicalKnowledgeBase lexicon = parse(wrap(
        "<LexicalEntry id=\"t-sing-v\"><Lemma writtenForm=\"sing\" partOfSpeech=\"v\"/>"
            + "<Sense id=\"t-sing-v-1\" synset=\"t-v1\"/></LexicalEntry>"
            + "<LexicalEntry id=\"t-chant-v\"><Lemma writtenForm=\"chant\" partOfSpeech=\"v\"/>"
            + "<Sense id=\"t-chant-v-1\" synset=\"t-v2\"/></LexicalEntry>"
            + "<Synset id=\"t-v1\" partOfSpeech=\"v\">"
            + "<Definition>produce musical tones</Definition>"
            + "<SynsetRelation relType=\"similar\" target=\"t-v2\"/></Synset>"
            + "<Synset id=\"t-v2\" partOfSpeech=\"v\">"
            + "<Definition>sing monotonously</Definition></Synset>"));
    assertEquals(List.of("t-v2"), lexicon.related("t-v1", WordNetRelation.VERB_GROUP));
    assertTrue(lexicon.related("t-v1", WordNetRelation.SIMILAR_TO).isEmpty());
  }

  @Test
  void testUnknownLemmaOrSynsetIsEmpty() {
    final LexicalKnowledgeBase lexicon = fixture();
    assertTrue(lexicon.lookup("zebra", WordNetPOS.NOUN).isEmpty());
    assertTrue(lexicon.synset("mini-n99").isEmpty());
  }

  @Test
  void testReadPath(@TempDir Path tempDir) throws IOException {
    final Path file = tempDir.resolve("tiny.xml");
    Files.writeString(file, wrap(
        "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
            + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
            + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition></Synset>"));
    final LexicalKnowledgeBase lexicon = WnLmfReader.read(file);
    assertEquals("a feline", lexicon.lookup("cat", WordNetPOS.NOUN).get(0).gloss());
  }

  @Test
  void testReadPathRejectsNullAndMissing(@TempDir Path tempDir) {
    assertThrows(IllegalArgumentException.class, () -> WnLmfReader.read((Path) null));
    assertThrows(IllegalArgumentException.class,
        () -> WnLmfReader.read(tempDir.resolve("absent.xml")));
  }

  @Test
  void testReadStreamRejectsNulls() {
    assertThrows(IllegalArgumentException.class, () -> WnLmfReader.read(null, "x"));
    assertThrows(IllegalArgumentException.class,
        () -> WnLmfReader.read(new ByteArrayInputStream(new byte[0]), null));
  }

  @Test
  void testReadDoesNotCloseInputStream() throws IOException {
    final boolean[] closed = {false};
    final InputStream in = new ByteArrayInputStream(wrap("").getBytes(StandardCharsets.UTF_8)) {
      @Override
      public void close() throws IOException {
        closed[0] = true;
        super.close();
      }
    };

    WnLmfReader.read(in, "inline.xml");

    assertFalse(closed[0]);
    in.close();
    assertTrue(closed[0]);
  }

  @Test
  void testStreamReadFailurePropagatesAsIOException() {
    final InputStream failing = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("Simulated stream failure");
      }
    };
    final IOException e =
        assertThrows(IOException.class, () -> WnLmfReader.read(failing, "failing.xml"));
    // Preserve an I/O failure instead of reporting malformed XML.
    assertFalse(e instanceof InvalidFormatException);
  }

  @Test
  void testSkipsDoctypeDeclaration() throws IOException {
    // The reserved domain makes an attempted external DTD fetch fail the test.
    final String document = "<?xml version=\"1.0\"?>\n"
        + "<!DOCTYPE LexicalResource SYSTEM \"http://example.invalid/WN-LMF-1.3.dtd\">\n"
        + "<LexicalResource><Lexicon id=\"t\" label=\"t\" language=\"en\" version=\"1\">"
        + "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
        + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
        + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition></Synset>"
        + "</Lexicon></LexicalResource>";
    final LexicalKnowledgeBase lexicon = parse(document);
    assertEquals("a feline", lexicon.lookup("cat", WordNetPOS.NOUN).get(0).gloss());
  }

  @Test
  void testInternalSubsetEntityIsNeverExpanded(@TempDir Path tempDir) throws IOException {
    // Expanding this entity would expose the temporary file's contents.
    final Path secret = tempDir.resolve("secret.txt");
    Files.writeString(secret, "xxe-marker-should-never-appear");
    final String document = "<?xml version=\"1.0\"?>\n"
        + "<!DOCTYPE LexicalResource [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>\n"
        + "<LexicalResource><Lexicon id=\"t\" label=\"t\" language=\"en\" version=\"1\">"
        + "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"&xxe;\" partOfSpeech=\"n\"/>"
        + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
        + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition></Synset>"
        + "</Lexicon></LexicalResource>";
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> parse(document));
    assertFalse(e.getMessage().contains("xxe-marker-should-never-appear"));
  }

  @Test
  void testRejectsTruncatedDocument() {
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> parse("<?xml version=\"1.0\"?>\n<LexicalResource><Lexicon id=\"t\""));
    assertTrue(e.getMessage().contains("inline.xml"));
  }

  /**
   * One rejected document per structural rule the reader enforces: the document body (wrapped
   * in the standard resource envelope) and the fragments its rejection message must contain.
   *
   * @return The (description, document body, expected message fragments) cases.
   */
  static Stream<Arguments> rejectedDocuments() {
    return Stream.of(
        Arguments.of(Named.of("sense without synset attribute",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\"/></LexicalEntry>"),
            List.of("synset")),
        Arguments.of(Named.of("sense to undeclared synset",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-9\"/></LexicalEntry>"),
            List.of("t-9")),
        Arguments.of(Named.of("relation to undeclared synset",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "<SynsetRelation relType=\"hypernym\" target=\"t-9\"/></Synset>"),
            List.of("t-9")),
        Arguments.of(Named.of("unknown relation type",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "<SynsetRelation relType=\"quasi_synonym\" target=\"t-1\"/></Synset>"),
            List.of("quasi_synonym")),
        Arguments.of(Named.of("unknown part of speech",
            "<LexicalEntry id=\"t-cat-x\"><Lemma writtenForm=\"cat\" partOfSpeech=\"x\"/>"
                + "<Sense id=\"t-cat-x-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"),
            List.of("x")),
        Arguments.of(Named.of("duplicate synset id",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a repeat</Definition>"
                + "</Synset>"),
            List.of("Duplicate synset id t-1")),
        Arguments.of(Named.of("duplicate lexical entry id",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"dog\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-dog-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"),
            List.of("Duplicate lexical entry id t-cat-n")),
        Arguments.of(Named.of("duplicate sense id",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-2\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"
                + "<Synset id=\"t-2\" partOfSpeech=\"n\"><Definition>a second</Definition>"
                + "</Synset>"),
            List.of("Duplicate sense id t-cat-n-1")),
        Arguments.of(Named.of("synset member pos mismatch",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"v\"><Definition>a feline</Definition>"
                + "</Synset>"),
            List.of("t-cat-n", "VERB", "NOUN")),
        Arguments.of(Named.of("synset member assigned to another synset",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
                + "<LexicalEntry id=\"t-dog-n\"><Lemma writtenForm=\"dog\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-dog-n-1\" synset=\"t-2\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\" members=\"t-dog-n-1\"/>"
                + "<Synset id=\"t-2\" partOfSpeech=\"n\" members=\"t-dog-n-1\"/>"),
            List.of("t-dog-n-1", "t-1", "t-2")),
        Arguments.of(Named.of("sense relation to undeclared sense",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\">"
                + "<SenseRelation relType=\"antonym\" target=\"t-ghost-1\"/></Sense>"
                + "</LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"),
            List.of("t-ghost-1")),
        Arguments.of(Named.of("lemma outside lexical entry",
            "<Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"),
            List.of("Lemma outside a LexicalEntry")),
        Arguments.of(Named.of("duplicate lemma",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Lemma writtenForm=\"dog\" partOfSpeech=\"n\"/></LexicalEntry>"),
            List.of("Duplicate Lemma in LexicalEntry t-cat-n")),
        Arguments.of(Named.of("nested lexical entry",
            "<LexicalEntry id=\"t-outer-n\"><Lemma writtenForm=\"outer\" partOfSpeech=\"n\"/>"
                + "<LexicalEntry id=\"t-inner-n\"><Lemma writtenForm=\"inner\" "
                + "partOfSpeech=\"n\"/></LexicalEntry></LexicalEntry>"),
            List.of("Nested LexicalEntry inside t-outer-n")),
        Arguments.of(Named.of("sense before lemma",
            "<LexicalEntry id=\"t-cat-n\"><Sense id=\"t-cat-n-1\" synset=\"t-1\"/>"
                + "<Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"),
            List.of("Sense before its entry's Lemma")),
        Arguments.of(Named.of("nested sense",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\">"
                + "<Sense id=\"t-cat-n-2\" synset=\"t-1\"/></Sense></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"/>"),
            List.of("Nested Sense inside t-cat-n-1")),
        Arguments.of(Named.of("nested synset",
            "<Synset id=\"t-1\" partOfSpeech=\"n\">"
                + "<Synset id=\"t-2\" partOfSpeech=\"n\"/></Synset>"),
            List.of("Nested Synset inside t-1")),
        Arguments.of(Named.of("definition outside synset",
            "<Definition>orphaned definition</Definition>"),
            List.of("Definition outside a Synset")),
        Arguments.of(Named.of("sense relation outside sense",
            "<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
                + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/>"
                + "<SenseRelation relType=\"antonym\" target=\"t-cat-n-1\"/></LexicalEntry>"
                + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
                + "</Synset>"),
            List.of("SenseRelation outside a Sense")),
        Arguments.of(Named.of("synset relation outside synset",
            "<SynsetRelation relType=\"hypernym\" target=\"t-1\"/>"),
            List.of("SynsetRelation outside a Synset")));
  }

  @ParameterizedTest
  @MethodSource("rejectedDocuments")
  void testRejectsStructurallyInvalidDocument(String body,
      List<String> expectedMessageFragments) {
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> parse(wrap(body)));
    for (final String fragment : expectedMessageFragments) {
      assertTrue(e.getMessage().contains(fragment),
          () -> "Rejection message must contain '" + fragment + "' but was: " + e.getMessage());
    }
  }

  @Test
  void testSkipsOtherRelationTypeOnSenseRelation() throws IOException {
    final LexicalKnowledgeBase lexicon = parse(
        wrap("<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
            + "<Sense id=\"t-cat-n-1\" synset=\"t-1\">"
            + "<SenseRelation relType=\"other\" target=\"t-cat-n-1\"/></Sense></LexicalEntry>"
            + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition></Synset>"));
    assertTrue(lexicon.synset("t-1").orElseThrow().relations().isEmpty());
  }

  @Test
  void testSkipsOtherRelationTypeOnSynsetRelation() throws IOException {
    // The DTD permits relType="other" on SynsetRelation too, and several OMW-family wordnets
    // emit it; it is skipped exactly like the SenseRelation case, not rejected.
    final LexicalKnowledgeBase lexicon = parse(
        wrap("<LexicalEntry id=\"t-cat-n\"><Lemma writtenForm=\"cat\" partOfSpeech=\"n\"/>"
            + "<Sense id=\"t-cat-n-1\" synset=\"t-1\"/></LexicalEntry>"
            + "<Synset id=\"t-1\" partOfSpeech=\"n\"><Definition>a feline</Definition>"
            + "<SynsetRelation relType=\"other\" target=\"t-1\"/></Synset>"));
    assertTrue(lexicon.synset("t-1").orElseThrow().relations().isEmpty());
  }

}
