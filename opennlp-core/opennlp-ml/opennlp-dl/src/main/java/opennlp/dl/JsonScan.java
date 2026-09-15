/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import opennlp.tools.commons.Internal;
import opennlp.tools.util.StringUtil;

/**
 * Cursor helpers for picking string literals, colons, and digit runs out of the small,
 * HuggingFace-shaped JSON files the deep-learning components read (vocabularies and
 * model configurations). The helpers work on offsets into the text and never build a
 * document tree; they are not a general-purpose JSON parser. Whitespace means the six
 * ASCII whitespace characters, and digits the ten ASCII digits.
 */
@Internal
public final class JsonScan {

  private JsonScan() {
  }

  /**
   * Finds the closing quote of a string literal, honoring backslash escapes: a backslash
   * and the character after it never close the literal.
   *
   * @param text The text to scan.
   * @param openQuote The offset of the opening quote.
   * @return The offset of the first unescaped quote after {@code openQuote}, or {@code -1}
   *     if there is none or a backslash is followed by a line terminator or the end of the
   *     text.
   */
  static int closingQuote(String text, int openQuote) {
    int i = openQuote + 1;
    while (i < text.length()) {
      final char c = text.charAt(i);
      if (c == '"') {
        return i;
      }
      if (c == '\\') {
        if (i + 1 >= text.length() || StringUtil.isLineTerminator(text.charAt(i + 1))) {
          return -1;
        }
        i += 2;
      } else {
        i++;
      }
    }
    return -1;
  }

  /**
   * Finds the closing quote of a string literal that must not span lines. Escapes are not
   * honored, so a backslash-quote pair closes the literal.
   *
   * @param text The text to scan.
   * @param openQuote The offset of the opening quote.
   * @return The offset of the first quote after {@code openQuote}, or {@code -1} if there is
   *     none or a line terminator comes before it.
   */
  public static int closingQuoteOnLine(String text, int openQuote) {
    for (int i = openQuote + 1; i < text.length(); i++) {
      final char c = text.charAt(i);
      if (c == '"') {
        return i;
      }
      if (StringUtil.isLineTerminator(c)) {
        return -1;
      }
    }
    return -1;
  }

  /**
   * Skips a colon that may be surrounded by whitespace.
   *
   * @param text The text to scan.
   * @param from The offset to start at.
   * @return The offset of the first non-whitespace character after the colon, which may be
   *     the length of the text, or {@code -1} if the first non-whitespace character at or
   *     after {@code from} is not a colon.
   */
  public static int afterColon(String text, int from) {
    final int colon = skipWhitespace(text, from);
    if (colon >= text.length() || text.charAt(colon) != ':') {
      return -1;
    }
    return skipWhitespace(text, colon + 1);
  }

  /**
   * Skips JSON whitespace: space, tab, line feed, and carriage return per RFC 8259. Other
   * Unicode spaces are content here, not separators.
   *
   * @param text The text to scan.
   * @param from The offset to start at.
   * @return The offset of the first non-whitespace character at or after {@code from}, or the
   *     length of the text if only whitespace remains.
   */
  static int skipWhitespace(String text, int from) {
    int i = from;
    while (i < text.length() && isJsonWhitespace(text.charAt(i))) {
      i++;
    }
    return i;
  }

  /**
   * Tests for JSON whitespace.
   *
   * @param c The character.
   * @return {@code true} for space, tab, line feed, or carriage return.
   */
  private static boolean isJsonWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }
}
