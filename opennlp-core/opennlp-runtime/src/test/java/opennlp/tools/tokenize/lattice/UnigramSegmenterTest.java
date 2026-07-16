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
 */
public class UnigramSegmenterTest {

  private static final String LEXICON = String.join("\n",
      "我 5000 r",
      "来到 2000 v",
      "北京 3000 ns",
      "清华大学 800 nt",
      "清华 400 ns",
      "华大 100 ns",
      "大学 1500 n",
      "的 9000 uj",
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
        new String[] {"我", "来到", "北京", "清华大学"},
        segmenter.tokenize("我来到北京清华大学"));
  }

  @Test
  void testSpansStayInOriginalCoordinates() {
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 3), new Span(3, 5), new Span(5, 9)},
        segmenter.tokenizePos("我来到北京清华大学"));
  }

  @Test
  void testUnknownCharactersFallBackToSingles() {
    Assertions.assertArrayEquals(
        new String[] {"我", "爱", "北京"},
        segmenter.tokenize("我爱北京"));
  }

  @Test
  void testWhitespaceSeparates() {
    Assertions.assertArrayEquals(
        new String[] {"北京", "大学"},
        segmenter.tokenize("北京 大学"));
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
    Assertions.assertArrayEquals(new String[] {"我"}, segmenter.tokenize("我"));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1)}, segmenter.tokenizePos("我"));
    Assertions.assertArrayEquals(new String[] {"爱"}, segmenter.tokenize("爱"));
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1)}, segmenter.tokenizePos("爱"));
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
        new String[] {"我", "爱", "清华大学"},
        segmenter.tokenize("我爱清华大学"));
    Assertions.assertArrayEquals(new Span[] {
        new Span(0, 1), new Span(1, 2), new Span(2, 6)},
        segmenter.tokenizePos("我爱清华大学"));
  }

  /**
   * Verifies that spans keep original text coordinates when the content does not
   * start at position zero because of leading whitespace.
   */
  @Test
  void testSpansStayOriginalAfterLeadingWhitespace() {
    final String text = "  我来到北京";
    Assertions.assertArrayEquals(
        new String[] {"我", "来到", "北京"},
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
}
