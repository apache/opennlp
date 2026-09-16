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

import opennlp.tools.util.StringUtil;

/**
 * Reads the ids from the metadata of an Arvores Deitadas sentence, which differs between
 * corpora.
 */
final class ADMetadata {

  private static final String PARAGRAPH_PREFIX = "p=";
  private static final String SOURCE_PREFIX = "source=\"";
  private static final char HYPHEN = '-';
  private static final char QUOTE = '"';

  /**
   * The ids of a sentence.
   *
   * @param text The text id.
   * @param paragraph The paragraph id.
   */
  record TextAndParagraph(int text, int paragraph) {
  }

  /**
   * Where the digit runs of the two ids lie in the metadata, each as an inclusive start and
   * an exclusive end.
   */
  private record IdSpans(int textStart, int textEnd, int paragraphStart, int paragraphEnd) {
  }

  private ADMetadata() {
  }

  /**
   * Parses the text id and the paragraph id: the text id is the ASCII digit run that directly
   * follows the leading ASCII letters and hyphens, the paragraph id the digit run after the
   * first {@code p=} that at least one digit follows.
   *
   * @param meta The metadata.
   * @return The two ids, or {@code null} if either is missing or does not fit into an
   *         {@code int}.
   */
  static TextAndParagraph parseTextAndParagraph(String meta) {
    IdSpans spans = scanTextAndParagraph(meta);
    if (spans == null) {
      return null;
    }
    int text = parseDigits(meta, spans.textStart(), spans.textEnd());
    int paragraph = parseDigits(meta, spans.paragraphStart(), spans.paragraphEnd());
    return text == -1 || paragraph == -1 ? null : new TextAndParagraph(text, paragraph);
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
    IdSpans spans = scanTextAndParagraph(meta);
    return spans == null || spans.textStart() == 0 ? null : meta.substring(0, spans.textStart());
  }

  /**
   * Reads the source: the text between the first {@code source="} and the next double quote.
   *
   * @param meta The metadata.
   * @return The source, or {@code null} if the metadata has none.
   */
  static String source(String meta) {
    int start = meta.indexOf(SOURCE_PREFIX);
    if (start == -1) {
      return null;
    }
    start += SOURCE_PREFIX.length();
    int end = meta.indexOf(QUOTE, start);
    return end == -1 ? null : meta.substring(start, end);
  }

  /**
   * Scans the text id and the paragraph id, see {@link #parseTextAndParagraph(String)}.
   *
   * @param meta The metadata.
   * @return The spans of the two ids, or {@code null} if either is missing.
   */
  private static IdSpans scanTextAndParagraph(String meta) {
    int i = 0;
    while (i < meta.length() && (StringUtil.isAsciiLetter(meta.charAt(i)) || meta.charAt(i) == HYPHEN)) {
      i++;
    }
    int textStart = i;
    int textEnd = StringUtil.endOfAsciiDigits(meta, i);
    if (textEnd == textStart) {
      return null;
    }
    int from = textEnd;
    while (true) {
      int prefix = meta.indexOf(PARAGRAPH_PREFIX, from);
      if (prefix == -1) {
        return null;
      }
      int paragraphStart = prefix + PARAGRAPH_PREFIX.length();
      int paragraphEnd = StringUtil.endOfAsciiDigits(meta, paragraphStart);
      if (paragraphEnd > paragraphStart) {
        return new IdSpans(textStart, textEnd, paragraphStart, paragraphEnd);
      }
      from = prefix + 1;
    }
  }

  /**
   * Reads a run of ASCII digits as a number.
   *
   * @param meta The metadata.
   * @param start The inclusive start of the run.
   * @param end The exclusive end of the run.
   * @return The number, or -1 if it does not fit into an {@code int}.
   */
  private static int parseDigits(String meta, int start, int end) {
    int value = 0;
    for (int i = start; i < end; i++) {
      int digit = meta.charAt(i) - '0';
      if (value > (Integer.MAX_VALUE - digit) / 10) {
        return -1;
      }
      value = value * 10 + digit;
    }
    return value;
  }

}
