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
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.Span;

/**
 * Tests the frequency-driven segmenter against a project-authored miniature lexicon;
 * no external lexicon data is involved.
 *
 * <p>Source strings are written as Unicode escapes to keep this file ASCII-only; the
 * class works over the same miniature Chinese frequency lexicon as the sibling usage
 * example, and its Javadoc spells out each fixture word.</p>
 */
public class UnigramSegmenterTest {

  /** UTF-8 lead byte with the required continuation byte omitted. */
  private static final byte TRUNCATED_UTF8_LEAD_BYTE = (byte) 0xC3;

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

  @ParameterizedTest(name = "lexicon content \"{0}\"")
  @ValueSource(strings = {"word\n", "word abc\n", "word 0\n", "\n\n"})
  void testMalformedLexiconsFailLoud(String lexicon) {
    Assertions.assertThrows(IOException.class, () -> UnigramSegmenter.load(
        new ByteArrayInputStream(lexicon.getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8));
  }

  @Test
  void testEntryLimit() {
    final String lexicon = "first 1\nsecond 1\n";
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> UnigramSegmenter.load(
            new ByteArrayInputStream(lexicon.getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8, 1));
    Assertions.assertEquals("lexicon entry count exceeds safe limit of 1", e.getMessage());
  }

  @Test
  void testCountTotalOverflow() {
    final String lexicon = "first " + Long.MAX_VALUE + "\nsecond 1\n";
    final IOException e = Assertions.assertThrows(IOException.class,
        () -> UnigramSegmenter.load(
            new ByteArrayInputStream(lexicon.getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8));
    Assertions.assertEquals("lexicon count total overflows at line 2", e.getMessage());
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> UnigramSegmenter.load((Path) null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> UnigramSegmenter.load((Path) null, StandardCharsets.UTF_8));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> UnigramSegmenter.load(Path.of("words.txt"), null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> UnigramSegmenter.load((InputStream) null, StandardCharsets.UTF_8));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> UnigramSegmenter.load(new ByteArrayInputStream(new byte[0]), null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> segmenter.tokenize(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> segmenter.tokenizePos(null));
  }

  /**
   * Verifies that the unknown-character fallback advances one code point instead of one
   * code unit: a supplementary character absent from the lexicon comes back as one span
   * over its surrogate pair, and no span boundary occurs inside it.
   */
  @Test
  void testUnknownSupplementaryCharacterIsNeverSplit() {
    // U+20BB7, a CJK extension B ideograph, written as its surrogate pair
    final String text = "\uD842\uDFB7\uD842\uDFB7";
    final Span[] spans = segmenter.tokenizePos(text);
    for (final Span span : spans) {
      Assertions.assertEquals(0, span.getStart() % 2,
          "span must start on a code point boundary: " + span);
      Assertions.assertEquals(0, span.getEnd() % 2,
          "span must end on a code point boundary: " + span);
    }
    int covered = 0;
    for (final Span span : spans) {
      covered += span.length();
    }
    Assertions.assertEquals(text.length(), covered);
  }

  /**
   * Pins Unicode-whitespace trimming of lexicon lines: a leading ideographic space
   * (U+3000), common in hand-edited CJK text files, is stripped like ASCII whitespace,
   * so the entry loads rather than failing as a malformed count.
   */
  @Test
  void testLeadingIdeographicSpaceIsTrimmed() throws IOException {
    // U+3000 ideographic space, then the fixture word U+6211 and its count
    final String lexicon = "\u3000\u6211 5000 r\n";
    final UnigramSegmenter loaded = UnigramSegmenter.load(
        new ByteArrayInputStream(lexicon.getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);
    Assertions.assertArrayEquals(new String[] {"\u6211"}, loaded.tokenize("\u6211"));
  }

  @Test
  void testRejectsMalformedLexiconEncoding() {
    final byte[] malformed = {'w', TRUNCATED_UTF8_LEAD_BYTE, ' ', '1', '\n'};

    final IOException e = Assertions.assertThrows(IOException.class,
        () -> UnigramSegmenter.load(
            new ByteArrayInputStream(malformed), StandardCharsets.UTF_8));

    Assertions.assertInstanceOf(MalformedInputException.class, e);
    Assertions.assertEquals("Input length = 1", e.getMessage());
  }

  @Test
  void testLongLexiconWordLoads() throws IOException {
    final String word = "a".repeat(20_000);

    final UnigramSegmenter loaded = UnigramSegmenter.load(
        new ByteArrayInputStream((word + " 1\n").getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);

    Assertions.assertArrayEquals(new String[] {word}, loaded.tokenize(word));
  }
}
