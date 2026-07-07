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
package opennlp.tools.util.normalizer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiAnnotationsTest {

  private static final String[] ATTRIBUTES = {EmojiAnnotation.NAME, EmojiAnnotation.SENTIMENT,
      EmojiAnnotation.ENTITY_TYPE, EmojiAnnotation.CATEGORY};

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static Map<String, EmojiAnnotation> bundled() throws IOException {
    try (InputStream in = EmojiAnnotations.class.getResourceAsStream("emoji-annotations.txt")) {
      return EmojiAnnotations.parse(in);
    }
  }

  private static Map<String, EmojiAnnotation> parse(String data) throws IOException {
    return EmojiAnnotations.parse(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
  }

  // --- audits of the bundled data ---

  @Test
  void everyBundledRecordCarriesAllFourAttributes() throws IOException {
    // The coverage claim in the data file header: a record is complete, never a partial row set,
    // so a consumer can rely on any of the four dimensions once a symbol is annotated at all.
    for (final EmojiAnnotation annotation : bundled().values()) {
      for (final String attribute : ATTRIBUTES) {
        assertTrue(annotation.attribute(attribute).isPresent(),
            "Missing attribute '" + attribute + "' for symbol: " + annotation.symbol());
      }
    }
  }

  @Test
  void everyBundledValueCarriesItsPinnedProvenance() throws IOException {
    // The licensing audit: names come from the CLDR short names, entity type and category from
    // the emoji-test.txt group/subgroup headers, and sentiment is ALWAYS a project judgment
    // (UNSPECIFIED). A sentiment row with any other source would mean third-party sentiment data
    // (for example the CC BY-SA Emoji Sentiment Ranking) leaked into the bundled file.
    final Map<String, String> expectedSource = Map.of(
        EmojiAnnotation.NAME, "CLDR:annotation",
        EmojiAnnotation.SENTIMENT, "UNSPECIFIED",
        EmojiAnnotation.ENTITY_TYPE, "UCD:emoji-test",
        EmojiAnnotation.CATEGORY, "UCD:emoji-test");
    for (final EmojiAnnotation annotation : bundled().values()) {
      for (final Map.Entry<String, EmojiAnnotation.Value> entry :
          annotation.attributes().entrySet()) {
        final EmojiAnnotation.Value value = entry.getValue();
        assertEquals(expectedSource.get(entry.getKey()), value.source(),
            "Unexpected source for " + annotation.symbol() + " " + entry.getKey());
        assertTrue(!value.notes().isEmpty(),
            "Empty notes for " + annotation.symbol() + " " + entry.getKey());
      }
    }
  }

  @Test
  void everyEmojiFoldSourceIsAnnotated() throws IOException {
    // The cross-file coverage claim: every pictograph emoji-emoticons.txt can fold has a record
    // here (presentation selector stripped), so the fold layer and the annotation layer never
    // disagree about which symbols the project understands.
    final Map<String, EmojiAnnotation> annotations = bundled();
    final EmojiEmoticons.Tables tables;
    try (InputStream in = EmojiEmoticons.class.getResourceAsStream("emoji-emoticons.txt")) {
      tables = EmojiEmoticons.parse(in);
    }
    for (final List<EmojiEmoticons.Mapping> candidates : tables.emojiToEmoticon().values()) {
      for (final EmojiEmoticons.Mapping mapping : candidates) {
        final String key = mapping.source().replace(cp(0xFE0F), "");
        assertTrue(annotations.containsKey(key),
            "Emoji fold source has no annotation record: " + mapping);
      }
    }
  }

  @Test
  void bundledRecordAndRowCountsAreAudited() throws IOException {
    // Locks the bundled counts so a data edit trips this test for a conscious bump, the same
    // discipline as the emoji-emoticons.txt and CaseFolding.txt audits.
    final Map<String, EmojiAnnotation> annotations = bundled();
    assertEquals(40, annotations.size());
    int rowCount = 0;
    for (final EmojiAnnotation annotation : annotations.values()) {
      rowCount += annotation.attributes().size();
    }
    assertEquals(160, rowCount);
  }

  @Test
  void bundledValuesAreWithinTheirTypedDomains() throws IOException {
    // The typed accessors must never throw for bundled data: sentiment parses into -2..2 and the
    // enum-backed attributes resolve to constants. Also asserts values are printable ASCII, the
    // same discipline as the data file itself.
    for (final EmojiAnnotation annotation : bundled().values()) {
      final int sentiment = annotation.sentiment().orElseThrow();
      assertTrue(sentiment >= -2 && sentiment <= 2,
          "Sentiment outside -2..2 for " + annotation.symbol());
      annotation.entityType().orElseThrow();
      annotation.category().orElseThrow();
      final String name = annotation.name().orElseThrow();
      name.chars().forEach(c ->
          assertTrue(c >= 0x20 && c < 0x7F, "Non-ASCII-printable name: " + name));
    }
  }

  @Test
  void everyBundledSymbolStartsBeyondAscii() throws IOException {
    // The feature generators fast-path tokens whose first char is ASCII without touching the
    // annotation layer; this audit keeps that guard sound. It holds structurally: annotatable
    // symbols are pictographs, and flags (regional indicators, the waving black flag) are
    // supplementary-plane sequences handled by the derived layer.
    for (final String symbol : bundled().keySet()) {
      assertTrue(symbol.charAt(0) > 0x7F, "ASCII-leading annotated symbol: " + symbol);
    }
  }

  // --- lookup behavior ---

  @Test
  void lookupResolvesATypedRecord() {
    final EmojiAnnotation annotation = EmojiAnnotations.lookup(cp(0x1F642)).orElseThrow();
    assertEquals(cp(0x1F642), annotation.symbol());
    assertEquals("slightly smiling face", annotation.name().orElseThrow());
    assertEquals(1, annotation.sentiment().orElseThrow());
    assertEquals(EmojiEntityType.FACE, annotation.entityType().orElseThrow());
    assertEquals(EmojiCategory.SMILEYS_AND_EMOTION, annotation.category().orElseThrow());
  }

  @Test
  void lookupStripsThePresentationSelector() {
    // U+2764 U+FE0F (emoji presentation) and bare U+2764 are the same symbol; the bundled rows
    // are keyed without the selector.
    final EmojiAnnotation withSelector =
        EmojiAnnotations.lookup(cp(0x2764) + cp(0xFE0F)).orElseThrow();
    final EmojiAnnotation bare = EmojiAnnotations.lookup(cp(0x2764)).orElseThrow();
    assertEquals(bare, withSelector);
    assertEquals("red heart", withSelector.name().orElseThrow());
  }

  @Test
  void lookupOfUnannotatedTextIsEmpty() {
    assertEquals(Optional.empty(), EmojiAnnotations.lookup("word"));
    assertEquals(Optional.empty(), EmojiAnnotations.lookup(":-)"));
    assertEquals(Optional.empty(), EmojiAnnotations.lookup(""));
    // A bare presentation selector strips to an empty key.
    assertEquals(Optional.empty(), EmojiAnnotations.lookup(cp(0xFE0F)));
    // Flags are handled by the derived layer, never by bundled rows.
    assertEquals(Optional.empty(), EmojiAnnotations.lookup(cp(0x1F1E9) + cp(0x1F1EA)));
  }

  @Test
  void lookupFailsLoudOnNull() {
    assertThrows(IllegalArgumentException.class, () -> EmojiAnnotations.lookup(null));
  }

  // --- fail-loud parsing ---

  @Test
  void parseFailsLoudOnWrongFieldCount() {
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; name ; slightly smiling face ; CLDR:annotation\n"));
  }

  @Test
  void parseFailsLoudOnUnknownAttribute() {
    // An unknown attribute is corruption, not extensibility: the data and the code move together.
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; shortName ; slightly smiling face ; CLDR:annotation ; n\n"));
  }

  @Test
  void parseFailsLoudOnMalformedHex() {
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F64X ; name ; slightly smiling face ; CLDR:annotation ; n\n"));
  }

  @Test
  void parseFailsLoudOnEmptyFields() {
    assertThrows(IllegalArgumentException.class,
        () -> parse(" ; name ; slightly smiling face ; CLDR:annotation ; n\n"));
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; name ; ; CLDR:annotation ; n\n"));
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; name ; slightly smiling face ; ; n\n"));
  }

  @Test
  void parseFailsLoudOnSentimentOutsideTheCoarseScale() {
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; sentiment ; 3 ; UNSPECIFIED ; n\n"));
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; sentiment ; -3 ; UNSPECIFIED ; n\n"));
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; sentiment ; positive ; UNSPECIFIED ; n\n"));
  }

  @Test
  void parseFailsLoudOnUnrecognizedEnumValues() {
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; entityType ; SMILEY ; UCD:emoji-test ; n\n"));
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; category ; EMOTION ; UCD:emoji-test ; n\n"));
  }

  @Test
  void parseFailsLoudOnDuplicateAttributeRows() {
    assertThrows(IllegalArgumentException.class,
        () -> parse("1F642 ; sentiment ; 1 ; UNSPECIFIED ; first\n"
            + "1F642 ; sentiment ; 2 ; UNSPECIFIED ; duplicate\n"));
  }

  @Test
  void parseSkipsCommentAndBlankLinesAndKeepsSemicolonsInNotes() throws IOException {
    final Map<String, EmojiAnnotation> parsed = parse("# comment\n\n   \n"
        + "1F642 ; name ; slightly smiling face ; CLDR:annotation ; note; with semicolon\n");
    assertEquals(1, parsed.size());
    assertEquals("note; with semicolon",
        parsed.get(cp(0x1F642)).attribute(EmojiAnnotation.NAME).orElseThrow().notes());
  }

  // --- record validation (records are also user-constructable, for tests and custom joins) ---

  @Test
  void recordConstructionFailsLoudOnBadArguments() {
    final Map<String, EmojiAnnotation.Value> attributes = Map.of(EmojiAnnotation.NAME,
        new EmojiAnnotation.Value("slightly smiling face", "CLDR:annotation", ""));
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotation(null, attributes));
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotation("", attributes));
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotation(cp(0x1F642), null));
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotation.Value("", "s", ""));
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotation.Value("v", "", ""));
    assertThrows(IllegalArgumentException.class, () -> new EmojiAnnotation.Value("v", "s", null));
  }

  @Test
  void typedAccessorsFailLoudOnValuesOutsideTheirDomain() {
    // Only reachable through hand-built records; the loader validates bundled data. A quietly
    // swallowed conversion here would surface as a silently missing feature downstream.
    final EmojiAnnotation annotation = new EmojiAnnotation(cp(0x1F642), Map.of(
        EmojiAnnotation.SENTIMENT, new EmojiAnnotation.Value("happy", "UNSPECIFIED", ""),
        EmojiAnnotation.ENTITY_TYPE, new EmojiAnnotation.Value("SMILEY", "UNSPECIFIED", ""),
        EmojiAnnotation.CATEGORY, new EmojiAnnotation.Value("EMOTION", "UNSPECIFIED", "")));
    assertThrows(IllegalStateException.class, annotation::sentiment);
    assertThrows(IllegalStateException.class, annotation::entityType);
    assertThrows(IllegalStateException.class, annotation::category);
    assertThrows(IllegalArgumentException.class, () -> annotation.attribute(null));
  }
}
