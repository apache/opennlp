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
