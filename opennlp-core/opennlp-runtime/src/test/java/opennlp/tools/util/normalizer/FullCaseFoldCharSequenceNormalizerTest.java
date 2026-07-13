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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FullCaseFoldCharSequenceNormalizerTest {

  private static FullCaseFoldCharSequenceNormalizer norm() {
    return FullCaseFoldCharSequenceNormalizer.getInstance();
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void getInstanceReturnsTheSharedSingleton() {
    assertSame(norm(), norm());
  }

  @Test
  void emptyInputNormalizesToEmpty() {
    assertEquals("", norm().normalize("").toString());
    assertEquals("", norm().normalizeAligned("").normalizedString());
  }

  @Test
  void nullInputIsRejectedAtThePublicBoundary() {
    assertThrows(IllegalArgumentException.class, () -> norm().normalize(null));
    assertThrows(IllegalArgumentException.class, () -> norm().normalizeAligned(null));
  }

  @Test
  void commonFoldLowercasesAscii() {
    assertEquals("abc", norm().normalize("ABC").toString());
  }

  @Test
  void fullFoldExpandsSharpSAndLigatures() {
    assertEquals("ss", norm().normalize("\u00df").toString());   // LATIN SMALL LETTER SHARP S
    assertEquals("ff", norm().normalize("\uFB00").toString());   // LATIN SMALL LIGATURE FF
    assertEquals("masse", norm().normalize("Ma\u00dfe").toString());
  }

  @Test
  void nonMembersPassThrough() {
    assertEquals("already lower", norm().normalize("already lower").toString());
    final String emoji = new String(Character.toChars(0x1F600)); // supplementary, not a fold member
    assertEquals(emoji, norm().normalize(emoji).toString());
  }

  @Test
  void alignedNormalizedMatchesNormalize() {
    final String in = "Ma\u00dfe";
    assertEquals(norm().normalize(in).toString(), norm().normalizeAligned(in).normalizedString());
  }

  @Test
  void alignmentMapsExpansionBackToTheSource() {
    // "Masse" from "Ma<sharp-s>e": the two-char "ss" (output 2..4) maps back to the single sharp s
    // at input index 2, so an offset-sensitive caller recovers the original character.
    final AlignedText at = norm().normalizeAligned("Ma\u00dfe");
    assertEquals("masse", at.normalizedString());
    assertEquals(new Span(2, 3), at.toOriginalSpan(2, 4));
  }

  @Test
  void parseFailsLoudOnLineWithNoFieldSeparators() {
    // A line with content but no ';' at all (fewer than 3 fields) is a structural error distinct
    // from a malformed individual field, and must fail loud the same way.
    final String data = "not case folding data at all\n";
    assertThrows(IllegalArgumentException.class, () -> FullCaseFoldCharSequenceNormalizer.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void statusRowOrderForTheSameSourceDoesNotMatter() {
    // Pins that a skipped S/T row never erases the loaded C/F mapping, in either row order.
    final String loadedThenSkipped = "0049; C; 0069; # loaded row first\n"
        + "0049; T; 0131; # skipped row second, must not overwrite\n";
    final String skippedThenLoaded = "1E9E; S; 00DF; # skipped row first, must not block the load\n"
        + "1E9E; F; 0073 0073; # loaded row second\n";
    final Map<Integer, String> a;
    final Map<Integer, String> b;
    try {
      a = FullCaseFoldCharSequenceNormalizer.parse(
          new ByteArrayInputStream(loadedThenSkipped.getBytes(StandardCharsets.UTF_8)));
      b = FullCaseFoldCharSequenceNormalizer.parse(
          new ByteArrayInputStream(skippedThenLoaded.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError("a loaded row paired with a skipped row must not fail", e);
    }
    assertEquals("i", a.get(0x0049));
    assertEquals("ss", b.get(0x1E9E));
  }

  @Test
  void realDataDistinguishesFullFoldFromTheSkippedTurkicMapping() throws Exception {
    // LATIN CAPITAL LETTER I WITH DOT ABOVE (U+0130) has both an F row (i + combining dot above,
    // the full fold) and a T row (plain i, Turkic-only, skipped). Loading the real bundled resource
    // must keep the F row's two-code-point value, not the simpler skipped T value.
    final Map<Integer, String> table;
    try (InputStream in =
             FullCaseFoldCharSequenceNormalizer.class.getResourceAsStream("CaseFolding.txt")) {
      table = FullCaseFoldCharSequenceNormalizer.parse(in);
    }
    assertEquals(cp(0x0069) + cp(0x0307), table.get(0x0130));
  }

  @Test
  void parseFailsLoudOnMalformedHex() {
    // A structurally-malformed row (non-hex source) fails loud naming the resource and line rather
    // than surfacing a raw NumberFormatException, the same contract as the sibling loaders.
    final String data = "0041; C; 0061; # valid\n"
        + "004X; C; 0061; # malformed source\n";
    assertThrows(IllegalArgumentException.class, () -> FullCaseFoldCharSequenceNormalizer.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void composesIntoTheOffsetAwarePipeline() {
    // A contract-then-expand chain: collapse the double space (contracting), then full case fold
    // (expanding). buildAligned composes the two alignments, so a hit in the output maps all the way
    // back to the original document coordinates.
    final OffsetAwareNormalizer pipeline =
        TextNormalizer.builder().whitespace().fullCaseFold().buildAligned();
    final AlignedText at = pipeline.normalizeAligned("A  \u00df");
    assertEquals("a ss", at.normalizedString());
    assertEquals(new Span(3, 4), at.toOriginalSpan(2, 4)); // "ss" maps back to the original sharp s
  }

  @Test
  void identityPipelinePreservesEveryOffset() {
    // Length-preserving case: whitespace folding then full case folding on already-folded,
    // single-spaced input is a no-op, so the composed alignment is the identity and every offset
    // maps back to itself.
    final OffsetAwareNormalizer pipeline =
        TextNormalizer.builder().whitespace().fullCaseFold().buildAligned();
    final AlignedText at = pipeline.normalizeAligned("plain text");
    assertEquals("plain text", at.normalizedString());
    assertEquals(new Span(6, 10), at.toOriginalSpan(6, 10)); // "text" maps to itself
  }

  @Test
  void tableLoadsEveryCommonAndFullFolding() throws Exception {
    // Completeness audit against the bundled CaseFolding.txt 17.0.0: the C (common) and F (full)
    // rows, so a data update that adds, drops, or changes a fold trips this test for a conscious bump.
    final Map<Integer, String> table;
    try (InputStream in =
             FullCaseFoldCharSequenceNormalizer.class.getResourceAsStream("CaseFolding.txt")) {
      table = FullCaseFoldCharSequenceNormalizer.parse(in);
    }
    assertEquals(1585, table.size()); // 1481 common + 104 full
    assertEquals("a", table.get(0x0041));
    assertEquals("ss", table.get(0x00DF));
    assertEquals("ff", table.get(0xFB00));
  }

  @Test
  void supplementaryPlaneFoldRoundTripsThroughAlignment() {
    // DESERET CAPITAL LETTER LONG I (U+10400, a surrogate pair) common-folds 1:1 to its supplementary
    // lowercase (U+10428), also a surrogate pair. Both source and target are two UTF-16 units, so this
    // exercises the map on a code point beyond the BMP and confirms charCount/appendCodePoint stay in
    // step across a supplementary substitution, not just the more common BMP case.
    final String source = cp(0x10400);
    final String target = cp(0x10428);
    assertEquals(target, norm().normalize(source).toString());
    final AlignedText at = norm().normalizeAligned(source);
    assertEquals(target, at.normalizedString());
    assertEquals(new Span(0, 2), at.toOriginalSpan(0, 2));
  }

  @Test
  void tripleCodePointExpansionMapsAsOneBlock() {
    // GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS (U+0390) fully folds to three BMP code points
    // (iota + combining diaeresis + combining tonos), a 1-to-3 expansion rather than the more common
    // 1-to-2 (sharp s). The whole three-character run must map back to the single source character.
    final String source = cp(0x0390);
    final String expected = cp(0x03B9) + cp(0x0308) + cp(0x0301);
    assertEquals(expected, norm().normalize(source).toString());
    final AlignedText at = norm().normalizeAligned("x" + source + "y");
    assertEquals("x" + expected + "y", at.normalizedString());
    assertEquals(new Span(1, 2), at.toOriginalSpan(1, 4)); // the 3-char expansion maps to one source char
  }

  @Test
  void consecutiveExpansionsEachMapToTheirOwnSource() {
    // Two adjacent expanding folds back to back ("ssss" from two sharp s characters) must not blur
    // together into a single run; each half of the output must map back to its own source character,
    // not the other one's or a span spanning both.
    final AlignedText at = norm().normalizeAligned(cp(0x00DF) + cp(0x00DF));
    assertEquals("ssss", at.normalizedString());
    assertEquals(new Span(0, 1), at.toOriginalSpan(0, 2));
    assertEquals(new Span(1, 2), at.toOriginalSpan(2, 4));
  }

  @Test
  void parseSkipsSimpleAndTurkicStatusRowsWithoutCountingOrFailing() {
    // S (simple) and T (Turkic) status rows are recognized and intentionally excluded from the full
    // fold, as opposed to being silently-dropped unrecognized data (see the next test): a source code
    // point whose only rows are S/T must simply be absent from the table, not present and not an error.
    final String data = "0049; T; 0131; # Turkic dotless i, must not be loaded\n";
    final Map<Integer, String> table;
    try {
      table = FullCaseFoldCharSequenceNormalizer.parse(
          new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError("S/T rows must be skipped, not fail", e);
    }
    assertEquals(0, table.size());
  }

  @Test
  void parseFailsLoudOnUnrecognizedStatus() {
    // A status letter that is neither a known full-fold status (C, F) nor a known-and-skipped one
    // (S, T) indicates corrupted or unexpected bundled data and must fail loud rather than being
    // silently dropped like a legitimate S/T row.
    final String data = "0041; C; 0061; # valid\n"
        + "0042; X; 0062; # unrecognized status\n";
    assertThrows(IllegalArgumentException.class, () -> FullCaseFoldCharSequenceNormalizer.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void parseFailsLoudOnEmptyTargetField() {
    // A row with a present-but-empty target column (e.g. from truncated data) must fail loud rather
    // than silently mapping the source to the empty string.
    final String data = "0041; F; ; # empty target\n";
    assertThrows(IllegalArgumentException.class, () -> FullCaseFoldCharSequenceNormalizer.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void trailingSemicolonBeforeACommentParsesAsThreeFields() {
    // The real CaseFolding.txt always ends a data line with a trailing ';' immediately before the
    // comment (e.g. "0041; C; 0061; # ..."); this relies on String.split's default trailing-empty-
    // string removal to still yield exactly 3 fields. Locks in that behavior explicitly.
    final String data = "0041; C; 0061; # trailing semicolon before the comment\n";
    final Map<Integer, String> table;
    try {
      table = FullCaseFoldCharSequenceNormalizer.parse(
          new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new AssertionError("a normally-formatted line must not be treated as malformed", e);
    }
    assertEquals(1, table.size());
    assertEquals("a", table.get(0x0041));
  }
}
