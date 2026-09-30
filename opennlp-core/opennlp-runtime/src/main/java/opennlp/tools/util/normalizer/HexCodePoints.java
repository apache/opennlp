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

/**
 * Reads the hex code point notation of the Unicode data files bundled in this package: one code
 * point as hex digits ({@code 1F600}), a sequence as hex digits separated by single spaces
 * ({@code 1F468 200D 1F469}), and an inclusive range as two code points joined by two dots
 * ({@code 1F3FB..1F3FF}). Shared by the data loaders and their tests, so the parsing cannot
 * drift apart between them.
 */
final class HexCodePoints {

  /** The separator between the code points of a sequence. */
  static final char SEQUENCE_SEPARATOR = ' ';

  /** The separator between the first and the last code point of a range. */
  static final String RANGE_SEPARATOR = "..";

  private static final int RADIX = 16;

  private HexCodePoints() {
  }

  /**
   * Parses the hex digits between two indexes as one code point.
   *
   * @param hex The text holding the digits. Must not be {@code null}.
   * @param start The index of the first digit.
   * @param end The index after the last digit.
   * @return The code point.
   * @throws IllegalArgumentException Thrown if the region is empty, holds a character that is
   *     not a hex digit, or names a value outside {@code [0, U+10FFFF]}.
   */
  static int parseCodePoint(CharSequence hex, int start, int end) {
    if (end <= start) {
      throw new IllegalArgumentException("Empty code point in: " + hex);
    }
    final int codePoint;
    try {
      codePoint = Integer.parseInt(hex, start, end, RADIX);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid hex code point '"
          + hex.subSequence(start, end) + "' in: " + hex, e);
    }
    if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
      throw new IllegalArgumentException("Code point out of range '"
          + hex.subSequence(start, end) + "' in: " + hex);
    }
    return codePoint;
  }

  /**
   * Parses hex digits as one code point.
   *
   * @param hex The digits. Must not be {@code null}.
   * @return The code point.
   * @throws IllegalArgumentException Thrown if {@code hex} is empty, holds a character that is
   *     not a hex digit, or names a value outside {@code [0, U+10FFFF]}.
   */
  static int parseCodePoint(CharSequence hex) {
    return parseCodePoint(hex, 0, hex.length());
  }

  /**
   * Decodes a sequence of hex code points separated by single spaces into the characters they
   * name.
   *
   * @param hex The sequence. Must not be {@code null}.
   * @return The decoded characters, in order.
   * @throws IllegalArgumentException Thrown if the sequence is empty or one of its code points
   *     is malformed.
   */
  static String decodeSequence(CharSequence hex) {
    final StringBuilder decoded = new StringBuilder();
    final int length = hex.length();
    int tokenStart = 0;
    for (int i = 0; i <= length; i++) {
      if (i == length || hex.charAt(i) == SEQUENCE_SEPARATOR) {
        decoded.appendCodePoint(parseCodePoint(hex, tokenStart, i));
        tokenStart = i + 1;
      }
    }
    return decoded.toString();
  }

  /**
   * Parses one code point or an inclusive {@code XXXX..YYYY} range.
   *
   * @param hex The code point or range. Must not be {@code null}.
   * @return The first and the last code point of the range; both the same for one code point.
   * @throws IllegalArgumentException Thrown if a code point is malformed or the range ends
   *     before it starts.
   */
  static int[] parseRange(CharSequence hex) {
    final int dots = indexOfRangeSeparator(hex);
    if (dots < 0) {
      final int codePoint = parseCodePoint(hex);
      return new int[] {codePoint, codePoint};
    }
    final int first = parseCodePoint(hex, 0, dots);
    final int last = parseCodePoint(hex, dots + RANGE_SEPARATOR.length(), hex.length());
    if (first > last) {
      throw new IllegalArgumentException("Descending code point range: " + hex);
    }
    return new int[] {first, last};
  }

  private static int indexOfRangeSeparator(CharSequence hex) {
    final int last = hex.length() - RANGE_SEPARATOR.length();
    for (int i = 0; i <= last; i++) {
      if (hex.charAt(i) == RANGE_SEPARATOR.charAt(0)
          && hex.charAt(i + 1) == RANGE_SEPARATOR.charAt(1)) {
        return i;
      }
    }
    return -1;
  }
}
