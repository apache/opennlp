/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WnLmfResourceTest {

  private static final String DC_NAMESPACE =
      "https://globalwordnet.github.io/schemas/dc/";

  @TempDir
  Path tempDir;

  @Test
  void testReadsRepresentativeMultilingualResourceWithoutMergingLexicons() throws IOException {
    final WnLmfResource resource = multilingualFixture();
    assertEquals(List.of("omw-it", "omw-es", "omw-sv"),
        resource.lexicons().stream().map(WnLmfLexicon::id).toList());

    final WnLmfLexicon italian = resource.lexicon("omw-it").orElseThrow();
    assertEquals("it", italian.language());
    assertEquals("2.0", italian.version());
    assertEquals("https://creativecommons.org/licenses/by/3.0/",
        italian.metadata().get(new QName("license")));
    assertEquals("Open Multilingual Wordnet",
        italian.metadata().get(new QName(DC_NAMESPACE, "publisher")));
    assertEquals(List.of(new WnLmfDependency("omw-en", "2.0")), italian.dependencies());
    assertEquals("omw-it-02084071-n",
        italian.knowledgeBase().lookup("cane", WordNetPOS.NOUN).get(0).id());
    assertEquals(List.of("cane"), italian.knowledgeBase()
        .synset("omw-it-02084071-n").orElseThrow().lemmas());

    assertEquals("omw-es-02084071-n", resource.lexicon("omw-es").orElseThrow()
        .knowledgeBase().lookup("perro", WordNetPOS.NOUN).get(0).id());
    assertEquals("omw-sv-02084071-n", resource.lexicon("omw-sv").orElseThrow()
        .knowledgeBase().lookup("hund", WordNetPOS.NOUN).get(0).id());

    assertTrue(italian.knowledgeBase().lookup("perro", WordNetPOS.NOUN).isEmpty());
    assertTrue(resource.lexicon("missing").isEmpty());
  }

  @Test
  void testSingleLexiconConvenienceRejectsMultiLexiconResource() {
    try (InputStream in = fixtureStream()) {
      final InvalidFormatException error = assertThrows(InvalidFormatException.class,
          () -> WnLmfReader.read(in, "omw-multilingual.xml"));
      assertTrue(error.getMessage().contains("contains 3 lexicons"));
      assertTrue(error.getMessage().contains("readResource"));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void testReadResourcePathPreservesLexiconBoundaries() throws IOException {
    final Path resourceFile = tempDir.resolve("multilingual.xml");
    try (InputStream in = fixtureStream()) {
      Files.copy(in, resourceFile);
    }

    final WnLmfResource resource = WnLmfReader.readResource(resourceFile);
    assertEquals(List.of("omw-it", "omw-es", "omw-sv"),
        resource.lexicons().stream().map(WnLmfLexicon::id).toList());
  }

  @Test
  void testReadResourceRejectsInvalidPathAndStreamArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> WnLmfReader.readResource((Path) null));
    assertThrows(IllegalArgumentException.class,
        () -> WnLmfReader.readResource(tempDir.resolve("missing.xml")));
    assertThrows(IllegalArgumentException.class,
        () -> WnLmfReader.readResource((InputStream) null, "null.xml"));
    assertThrows(IllegalArgumentException.class,
        () -> WnLmfReader.readResource(bytes("<LexicalResource/>"), null));
  }

  @Test
  void testReadResourceRejectsDuplicateLexiconIds() {
    final String document = "<LexicalResource>" + tinyLexicon("same", "en", "cat")
        + tinyLexicon("same", "de", "Katze") + "</LexicalResource>";
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(document), "duplicates.xml"));
    assertTrue(error.getMessage().contains("Duplicate lexicon id same"));
  }

  @Test
  void testReadResourceRejectsDuplicateXmlIdsAcrossLexicons() {
    final String first = tinyLexicon("one", "en", "cat");
    final String second = tinyLexicon("two", "de", "Katze")
        .replace("two-entry", "one-entry");
    final String document = "<LexicalResource>" + first + second + "</LexicalResource>";
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(document), "duplicate-xml-id.xml"));
    assertTrue(error.getMessage().contains("Duplicate lexical entry id one-entry"));
  }

  @Test
  void testReadResourceRejectsLexicalContentOutsideLexicon() {
    final String document = "<LexicalResource><LexicalEntry id=\"bad\">"
        + "<Lemma writtenForm=\"bad\" partOfSpeech=\"n\"/>"
        + "</LexicalEntry></LexicalResource>";
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(document), "outside.xml"));
    assertTrue(error.getMessage().contains("LexicalEntry outside a Lexicon"));
  }

  @Test
  void testReadResourceRejectsUnsupportedLexiconExtensionClearly() {
    final String document = "<LexicalResource><LexiconExtension id=\"extra\" label=\"extra\" "
        + "language=\"en\" version=\"1\"><Extends id=\"base\" version=\"1\"/>"
        + "</LexiconExtension></LexicalResource>";
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(document), "extension.xml"));
    assertTrue(error.getMessage().contains("LexiconExtension is not supported"));
  }

  @Test
  void testReadResourceRejectsMissingRequiredLexiconMetadata() {
    final String document = "<LexicalResource><Lexicon id=\"missing\" language=\"en\" "
        + "version=\"1\"/></LexicalResource>";
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(document), "metadata.xml"));
    assertTrue(error.getMessage().contains("Lexicon is missing required attribute label"));
  }

  @Test
  void testPreservesMultipleDependenciesInSourceOrder() throws IOException {
    final String document = "<LexicalResource><Lexicon id=\"dependent\" label=\"dependent\" "
        + "language=\"en\" version=\"1\">"
        + "<Requires ref=\"base\" version=\"2\"/>"
        + "<Requires ref=\"domain\" version=\"3\"/>"
        + "</Lexicon></LexicalResource>";
    final WnLmfLexicon lexicon =
        WnLmfReader.readResource(bytes(document), "dependencies.xml").lexicons().get(0);
    assertEquals(List.of(
        new WnLmfDependency("base", "2"),
        new WnLmfDependency("domain", "3")), lexicon.dependencies());
  }

  @Test
  void testLexiconWithoutRequiresHasNoDependencies() throws IOException {
    final String document = "<LexicalResource>" + tinyLexicon("standalone", "en", "cat")
        + "</LexicalResource>";
    final WnLmfLexicon lexicon =
        WnLmfReader.readResource(bytes(document), "standalone.xml").lexicons().get(0);
    assertTrue(lexicon.dependencies().isEmpty());
  }

  @Test
  void testDependenciesDoNotLeakBetweenLexicons() throws IOException {
    final String dependent = "<Lexicon id=\"dependent\" label=\"dependent\" "
        + "language=\"en\" version=\"1\"><Requires ref=\"base\" version=\"2\"/>"
        + "</Lexicon>";
    final String document = "<LexicalResource>" + dependent
        + tinyLexicon("standalone", "de", "Katze") + "</LexicalResource>";
    final WnLmfResource resource =
        WnLmfReader.readResource(bytes(document), "dependency-scope.xml");
    assertEquals(List.of(new WnLmfDependency("base", "2")),
        resource.lexicons().get(0).dependencies());
    assertTrue(resource.lexicons().get(1).dependencies().isEmpty());
  }

  @Test
  void testReadResourceRejectsRequiresOutsideLexicon() {
    final String document = "<LexicalResource><Requires ref=\"base\" version=\"1\"/>"
        + tinyLexicon("one", "en", "cat") + "</LexicalResource>";
    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(document), "outside-requires.xml"));
    assertTrue(error.getMessage().contains("Requires outside a Lexicon"));
  }

  @Test
  void testReadResourceRejectsIncompleteRequires() {
    final String missingRef = "<LexicalResource><Lexicon id=\"one\" label=\"one\" "
        + "language=\"en\" version=\"1\"><Requires version=\"1\"/>"
        + "</Lexicon></LexicalResource>";
    final InvalidFormatException refError = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(missingRef), "missing-ref.xml"));
    assertTrue(refError.getMessage().contains("Requires is missing required attribute ref"));

    final String missingVersion = "<LexicalResource><Lexicon id=\"one\" label=\"one\" "
        + "language=\"en\" version=\"1\"><Requires ref=\"base\"/>"
        + "</Lexicon></LexicalResource>";
    final InvalidFormatException versionError = assertThrows(InvalidFormatException.class,
        () -> WnLmfReader.readResource(bytes(missingVersion), "missing-version.xml"));
    assertTrue(versionError.getMessage().contains(
        "Requires is missing required attribute version"));
  }

  @Test
  void testPreservesUnlexicalizedSynsetUsedByRealOmwResources() throws IOException {
    final String document = "<LexicalResource><Lexicon id=\"unlex\" label=\"unlex\" "
        + "language=\"it\" version=\"1\"><LexicalEntry id=\"entry\">"
        + "<Lemma writtenForm=\"evento\" partOfSpeech=\"v\"/>"
        + "<Sense id=\"sense\" synset=\"lexicalized\"/></LexicalEntry>"
        + "<Synset id=\"lexicalized\" partOfSpeech=\"v\">"
        + "<SynsetRelation relType=\"hypernym\" target=\"unlexicalized\"/></Synset>"
        + "<Synset id=\"unlexicalized\" partOfSpeech=\"v\" lexicalized=\"false\"/>"
        + "</Lexicon></LexicalResource>";
    final WnLmfLexicon lexicon =
        WnLmfReader.readResource(bytes(document), "unlexicalized.xml").lexicons().get(0);
    assertTrue(lexicon.knowledgeBase().synset("unlexicalized").orElseThrow().lemmas().isEmpty());
  }

  @Test
  void testResourceAndLexiconContractsAreDefensive() {
    final WnLmfLexicon source = multilingualFixture().lexicon("omw-it").orElseThrow();
    final Map<QName, String> metadata = new HashMap<>();
    metadata.put(new QName("license"), "test-license");
    final List<WnLmfDependency> dependencies = new ArrayList<>();
    dependencies.add(new WnLmfDependency("base", "1"));
    final WnLmfLexicon lexicon = new WnLmfLexicon(
        "id", "label", "it", "1", metadata, dependencies, source.knowledgeBase());
    metadata.put(new QName("mutated"), "yes");
    dependencies.clear();
    assertEquals(Map.of(new QName("license"), "test-license"), lexicon.metadata());
    assertEquals(List.of(new WnLmfDependency("base", "1")), lexicon.dependencies());
    assertThrows(UnsupportedOperationException.class,
        () -> lexicon.metadata().put(new QName("x"), "y"));
    assertThrows(UnsupportedOperationException.class, () -> lexicon.dependencies().clear());

    final List<WnLmfLexicon> lexicons = new ArrayList<>(List.of(lexicon));
    final WnLmfResource resource = new WnLmfResource(lexicons);
    lexicons.clear();
    assertEquals(1, resource.lexicons().size());
    assertThrows(UnsupportedOperationException.class, () -> resource.lexicons().clear());
    assertThrows(IllegalArgumentException.class, () -> resource.lexicon(null));
  }

  @Test
  void testResourceAndLexiconContractsRejectInvalidComponents() {
    final WnLmfLexicon source = multilingualFixture().lexicon("omw-it").orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> new WnLmfResource(List.of()));
    assertThrows(IllegalArgumentException.class, () -> new WnLmfResource(
        java.util.Collections.singletonList(null)));
    assertThrows(IllegalArgumentException.class, () -> new WnLmfResource(List.of(source, source)));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon(null, "label", "it", "1", Map.of(), List.of(),
            source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "", "it", "1", Map.of(), List.of(),
            source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "label", "", "1", Map.of(), List.of(),
            source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "label", "it", "", Map.of(), List.of(),
            source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "label", "it", "1", null, List.of(),
            source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "label", "it", "1", Map.of(), null,
            source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "label", "it", "1", Map.of(),
            java.util.Collections.singletonList(null), source.knowledgeBase()));
    assertThrows(IllegalArgumentException.class,
        () -> new WnLmfLexicon("id", "label", "it", "1", Map.of(), List.of(), null));
    assertThrows(IllegalArgumentException.class, () -> new WnLmfDependency(null, "1"));
    assertThrows(IllegalArgumentException.class, () -> new WnLmfDependency("", "1"));
    assertThrows(IllegalArgumentException.class, () -> new WnLmfDependency("base", null));
    assertThrows(IllegalArgumentException.class, () -> new WnLmfDependency("base", ""));
  }

  private static WnLmfResource multilingualFixture() {
    try (InputStream in = fixtureStream()) {
      return WnLmfReader.readResource(in, "omw-multilingual.xml");
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected fixture read failure", e);
    }
  }

  private static InputStream fixtureStream() {
    final InputStream in = WnLmfResourceTest.class.getResourceAsStream("omw-multilingual.xml");
    assertNotNull(in, "Fixture omw-multilingual.xml must be on the test classpath");
    return in;
  }

  private static ByteArrayInputStream bytes(String document) {
    return new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8));
  }

  private static String tinyLexicon(String id, String language, String lemma) {
    return "<Lexicon id=\"" + id + "\" label=\"" + id + "\" language=\"" + language
        + "\" version=\"1\"><LexicalEntry id=\"" + id + "-entry\">"
        + "<Lemma writtenForm=\"" + lemma + "\" partOfSpeech=\"n\"/>"
        + "<Sense id=\"" + id + "-sense\" synset=\"" + id + "-synset\"/>"
        + "</LexicalEntry><Synset id=\"" + id + "-synset\" partOfSpeech=\"n\"/>"
        + "</Lexicon>";
  }
}
