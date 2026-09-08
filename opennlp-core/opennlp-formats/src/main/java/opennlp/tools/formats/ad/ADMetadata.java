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

package opennlp.tools.formats.ad;

import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser;

/**
 * Reads the ids from the metadata of an Arvores Deitadas sentence, which differs between
 * corpora. The metadata is one line; every method treats metadata with a line terminator as
 * invalid.
 */
final class ADMetadata {

  private static final String PARAGRAPH_PREFIX = "p=";
  private static final String SOURCE_PREFIX = "source=\"";

  private ADMetadata() {
  }

  /**
   * Parses the text id and the paragraph id: the text id is the ASCII digit run after any
   * leading ASCII letters and hyphens, the paragraph id the digit run after the first
   * {@code p=} that at least one digit follows.
   *
   * @param meta The metadata.
   * @return The text id and the paragraph id, or {@code null} if either is missing.
   */
  static int[] parseTextAndParagraph(String meta) {
    int[] spans = scanTextAndParagraph(meta);
    if (spans == null) {
      return null;
    }
    return new int[] {Integer.parseInt(meta.substring(spans[0], spans[1])),
        Integer.parseInt(meta.substring(spans[2], spans[3]))};
  }

  /**
   * Reads the digits of the text id, see {@link #parseTextAndParagraph(String)}.
   *
   * @param meta The metadata.
   * @return The digits, or {@code null} if the text id or the paragraph id is missing.
   */
  static String textId(String meta) {
    int[] spans = scanTextAndParagraph(meta);
    return spans == null ? null : meta.substring(spans[0], spans[1]);
  }

  /**
   * Reads the ASCII letters and hyphens before the text id, which name the text in literary
   * corpora.
   *
   * @param meta The metadata.
   * @return The prefix, or {@code null} if it is empty or the text id or the paragraph id is
   *         missing.
   */
  static String textPrefix(String meta) {
    int[] spans = scanTextAndParagraph(meta);
    return spans == null || spans[0] == 0 ? null : meta.substring(0, spans[0]);
  }

  /**
   * Reads the source: the text between the first {@code source="} and the next double quote.
   *
   * @param meta The metadata.
   * @return The source, or {@code null} if the metadata has none.
   */
  static String source(String meta) {
    if (hasLineTerminator(meta)) {
      return null;
    }
    int start = meta.indexOf(SOURCE_PREFIX);
    if (start == -1) {
      return null;
    }
    start += SOURCE_PREFIX.length();
    int end = meta.indexOf('"', start);
    return end == -1 ? null : meta.substring(start, end);
  }

  /**
   * Scans the text id and the paragraph id, see {@link #parseTextAndParagraph(String)}.
   *
   * @param meta The metadata.
   * @return The start and end of the text id and the start and end of the paragraph id, or
   *         {@code null} if either is missing.
   */
  private static int[] scanTextAndParagraph(String meta) {
    int i = 0;
    while (i < meta.length() && (isAsciiLetter(meta.charAt(i)) || meta.charAt(i) == '-')) {
      i++;
    }
    int textStart = i;
    while (i < meta.length() && isAsciiDigit(meta.charAt(i))) {
      i++;
    }
    if (i == textStart || hasLineTerminator(meta)) {
      return null;
    }
    int textEnd = i;
    int from = textEnd;
    while (true) {
      int prefix = meta.indexOf(PARAGRAPH_PREFIX, from);
      if (prefix == -1) {
        return null;
      }
      int paragraphStart = prefix + PARAGRAPH_PREFIX.length();
      int paragraphEnd = paragraphStart;
      while (paragraphEnd < meta.length() && isAsciiDigit(meta.charAt(paragraphEnd))) {
        paragraphEnd++;
      }
      if (paragraphEnd > paragraphStart) {
        return new int[] {textStart, textEnd, paragraphStart, paragraphEnd};
      }
      from = prefix + 1;
    }
  }

  /**
   * Tests whether the metadata contains a line terminator.
   *
   * @param meta The metadata.
   * @return {@code true} if it does.
   */
  private static boolean hasLineTerminator(String meta) {
    return SentenceParser.indexOfLineTerminator(meta, 0) < meta.length();
  }

  /**
   * Tests for an ASCII letter.
   *
   * @param c The character.
   * @return {@code true} for {@code a} to {@code z} or {@code A} to {@code Z}.
   */
  private static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  /**
   * Tests for an ASCII digit.
   *
   * @param c The character.
   * @return {@code true} for {@code 0} to {@code 9}.
   */
  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
