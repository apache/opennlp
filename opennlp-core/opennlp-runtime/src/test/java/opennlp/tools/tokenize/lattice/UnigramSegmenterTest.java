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

package opennlp.tools.tokenize.lattice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

/**
 * Tests the frequency-driven segmenter against a project-authored miniature lexicon;
 * no external lexicon data is involved.
 *
 * <p>Source strings are written as Unicode escapes to keep this file ASCII-only; the
 * class works over the same miniature Chinese frequency lexicon as the sibling usage
 * example, whose javadoc spells out each fixture word.</p>
 */
public class UnigramSegmenterTest {

  private static final String LEXICON = String.join("\n",
      "\u6211 5000 r",
      "\u6765\u5230 2000 v",
      "\u5317\u4EAC 3000 ns",
      "\u6E05\u534E\u5927\u5B66 800 nt",
      "\u6E05\u534E 400 ns",
      "\u534E\u5927 100 ns",
      "\u5927\u5B66 1500 n",
      "\u7684 9000 uj",
      "");

  private static UnigramSegmenter segmenter;

  @BeforeAll
  static void loadLexicon() throws IOException {
    segmenter = UnigramSegmenter.load(
        new ByteArrayInputStream(LEXICON.getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);
  }

  @Test
  void testPrefersWholeWordsOverFragments() {
    Assertions.assertArrayEquals(
        new String[] {"\u6211", "\u6765\u5230", "\u5317\u4EAC", "\u6E05\u534E\u5927\u5B66"},
        segmenter.tokenize("\u6211\u6765\u5230\u5317\u4EAC\u6E05\u534E\u5927\u5B66"));
  }

  @Test
  void testSpansStayInOriginalCoordinates() {
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 3), new Span(3, 5), new Span(5, 9)},
        segmenter.tokenizePos("\u6211\u6765\u5230\u5317\u4EAC\u6E05\u534E\u5927\u5B66"));
  }

  @Test
  void testUnknownCharactersFallBackToSingles() {
    Assertions.assertArrayEquals(
        new String[] {"\u6211", "\u7231", "\u5317\u4EAC"},
        segmenter.tokenize("\u6211\u7231\u5317\u4EAC"));
  }

  @Test
  void testWhitespaceSeparates() {
    Assertions.assertArrayEquals(
        new String[] {"\u5317\u4EAC", "\u5927\u5B66"},
        segmenter.tokenize("\u5317\u4EAC \u5927\u5B66"));
    Assertions.assertEquals(0, segmenter.tokenizePos("  ").length);
  }

  /**
   * Verifies that empty input yields empty results from both views of the segmenter.
   */
  @Test
  void testEmptyInputYieldsEmptyResults() {
    Assertions.assertArrayEquals(new String[0], segmenter.tokenize(""));
    Assertions.assertArrayEquals(new Span[0], segmenter.tokenizePos(""));
  }

  /**
   * Verifies single-character input for a listed word and for a character the
   * lexicon does not know: both come back as exactly one token covering
   * {@code [0, 1)}.
   */
  @Test
  void testSingleCharacterInput() {
    Assertions.assertArrayEquals(new String[] {"\u6211"}, segmenter.tokenize("\u6211"));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1)}, segmenter.tokenizePos("\u6211"));
    Assertions.assertArrayEquals(new String[] {"\u7231"}, segmenter.tokenize("\u7231"));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1)}, segmenter.tokenizePos("\u7231"));
  }

  /**
   * Verifies input made entirely of characters absent from the lexicon: every
   * character becomes its own single-character token, since only the unknown
   * fallback is available.
   */
  @Test
  void testEntirelyUnknownInputFallsBackToSingleCharacters() {
    Assertions.assertArrayEquals(
        new String[] {"x", "y", "z"},
        segmenter.tokenize("xyz"));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 2), new Span(2, 3)},
        segmenter.tokenizePos("xyz"));
  }

  /**
   * Verifies a mixed run of known and unknown text inside one whitespace-free
   * stretch: the unknown character becomes a single token while the listed words
   * around it, including the longest listed compound, stay intact.
   */
  @Test
  void testMixedKnownAndUnknownRuns() {
    Assertions.assertArrayEquals(
        new String[] {"\u6211", "\u7231", "\u6E05\u534E\u5927\u5B66"},
        segmenter.tokenize("\u6211\u7231\u6E05\u534E\u5927\u5B66"));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 2), new Span(2, 6)},
        segmenter.tokenizePos("\u6211\u7231\u6E05\u534E\u5927\u5B66"));
  }

  /**
   * Verifies that spans keep original text coordinates when the content does not
   * start at position zero because of leading whitespace.
   */
  @Test
  void testSpansStayOriginalAfterLeadingWhitespace() {
    final String text = "  \u6211\u6765\u5230\u5317\u4EAC";
    Assertions.assertArrayEquals(
        new String[] {"\u6211", "\u6765\u5230", "\u5317\u4EAC"},
        segmenter.tokenize(text));
    Assertions.assertArrayEquals(new Span[] {
        new Span(2, 3), new Span(3, 5), new Span(5, 7)},
        segmenter.tokenizePos(text));
  }

  @Test
  void testMalformedLexiconsFailLoud() {
    Assertions.assertThrows(IOException.class, () -> UnigramSegmenter.load(
        new ByteArrayInputStream("word\n".getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class, () -> UnigramSegmenter.load(
        new ByteArrayInputStream("word abc\n".getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class, () -> UnigramSegmenter.load(
        new ByteArrayInputStream("word 0\n".getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8));
    Assertions.assertThrows(IOException.class, () -> UnigramSegmenter.load(
        new ByteArrayInputStream("\n\n".getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8));
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> UnigramSegmenter.load((java.nio.file.Path) null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> segmenter.tokenizePos(null));
  }

  /**
   * Verifies that the unknown-character fallback advances one code point, never one
   * code unit: a supplementary character absent from the lexicon comes back as one
   * span over both of its surrogate halves, and no span boundary ever lands between
   * them.
   */
  @org.junit.jupiter.api.Test
  void testUnknownSupplementaryCharacterIsNeverSplit() {
    // U+20BB7, a CJK extension B ideograph, written as its surrogate pair
    final String text = "\uD842\uDFB7\uD842\uDFB7";
    final opennlp.tools.util.Span[] spans = segmenter.tokenizePos(text);
    for (final opennlp.tools.util.Span span : spans) {
      Assertions.assertEquals(0, span.getStart() % 2,
          "span must start on a code point boundary: " + span);
      Assertions.assertEquals(0, span.getEnd() % 2,
          "span must end on a code point boundary: " + span);
    }
    int covered = 0;
    for (final opennlp.tools.util.Span span : spans) {
      covered += span.length();
    }
    Assertions.assertEquals(text.length(), covered);
  }
}
