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

import opennlp.tools.commons.Internal;
import opennlp.tools.util.StringUtil;

/**
 * Reads the hex code point notation of Unicode data files and similar code point tables: one
 * code point as hex digits ({@code 1F600}), a sequence as hex digits separated by whitespace
 * ({@code 1F468 200D 1F469}), and an inclusive range as two code points joined by two dots
 * ({@code 1F3FB..1F3FF}). Also removes the {@code #} comments these files carry.
 */
@Internal
public final class HexCodePoints {

  /** The separator between the first and the last code point of a range. */
  private static final String RANGE_SEPARATOR = "..";

  /** The character that starts a comment running to the end of the line. */
  private static final char COMMENT_MARKER = '#';

  private static final int RADIX = 16;

  private HexCodePoints() {
  }

  /**
   * Removes a trailing comment from a data file line.
   *
   * @param line The raw line. Must not be {@code null}.
   * @return The line up to but excluding the first {@code #}, or {@code line}
   *     itself when it has none. Surrounding whitespace is kept.
   * @throws IllegalArgumentException Thrown if {@code line} is {@code null}.
   */
  public static String stripComment(String line) {
    if (line == null) {
      throw new IllegalArgumentException("line must not be null");
    }
    final int hash = line.indexOf(COMMENT_MARKER);
    return hash < 0 ? line : line.substring(0, hash);
  }

  /**
   * Parses the hex digits between two indexes as one code point.
   *
   * @param hex The text holding the digits. Must not be {@code null}.
   * @param start The index of the first digit.
   * @param end The index after the last digit.
   * @return The code point.
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null}, the region lies
   *     outside {@code hex}, is empty, holds a character that is not a hex digit, or names a
   *     value outside {@code [0, U+10FFFF]}.
   */
  public static int parseCodePoint(CharSequence hex, int start, int end) {
    requireNonNullHex(hex);
    if (start < 0 || end > hex.length()) {
      throw new IllegalArgumentException("Region [" + start + ", " + end
          + ") outside of: " + hex);
    }
    if (end <= start) {
      throw new IllegalArgumentException("Empty code point in: " + hex);
    }
    final int codePoint;
    try {
      if (Character.digit(hex.charAt(start), RADIX) < 0) {
        // Integer.parseInt accepts a leading sign, which is not a hex digit
        throw new NumberFormatException();
      }
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
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null} or empty, holds a
   *     character that is not a hex digit, or names a value outside {@code [0, U+10FFFF]}.
   */
  public static int parseCodePoint(CharSequence hex) {
    requireNonNullHex(hex);
    return parseCodePoint(hex, 0, hex.length());
  }

  /**
   * Decodes a sequence of hex code points separated by runs of Unicode whitespace into the
   * characters they name. Leading and trailing whitespace is ignored.
   *
   * @param hex The sequence. Must not be {@code null}.
   * @return The decoded characters, in order.
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null}, holds no code point,
   *     or one of its code points is malformed.
   */
  public static String decodeSequence(CharSequence hex) {
    final StringBuilder decoded = new StringBuilder();
    for (String token : codePointTokens(hex)) {
      decoded.appendCodePoint(parseCodePoint(token));
    }
    return decoded.toString();
  }

  /**
   * Decodes a sequence of hex code points separated by exactly one {@code separator} each into
   * the characters they name. Two adjacent separators or a leading separator leave an empty
   * code point, which is rejected; trailing separators are ignored.
   *
   * @param hex The sequence. Must not be {@code null}.
   * @param separator The character between two code points. Must not be a surrogate.
   * @return The decoded characters, in order.
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null} or empty,
   *     {@code separator} is a surrogate, or one of the code points is malformed.
   */
  public static String decodeSequence(CharSequence hex, char separator) {
    requireNonNullHex(hex);
    final StringBuilder decoded = new StringBuilder();
    for (String token : StringUtil.split(hex, separator)) {
      decoded.appendCodePoint(parseCodePoint(token));
    }
    return decoded.toString();
  }

  /**
   * Splits a sequence into its hex code point tokens.
   *
   * @param hex The sequence. Must not be {@code null}.
   * @return The tokens in order, at least one.
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null} or holds no token.
   */
  public static String[] codePointTokens(CharSequence hex) {
    requireNonNullHex(hex);
    final String[] tokens = StringUtil.splitOnUnicodeWhitespace(hex);
    if (tokens.length == 0) {
      throw new IllegalArgumentException("Empty code point sequence: \"" + hex + "\"");
    }
    return tokens;
  }

  /**
   * Parses one code point or an inclusive {@code XXXX..YYYY} range.
   *
   * @param hex The code point or range. Must not be {@code null}.
   * @return The first and the last code point of the range; both the same for one code point.
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null}, a code point is
   *     malformed, or the range ends before it starts.
   */
  public static int[] parseRange(CharSequence hex) {
    requireNonNullHex(hex);
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

  /**
   * Finds the first {@link #RANGE_SEPARATOR}.
   *
   * @param hex The code point or range.
   * @return The index of the separator, or {@code -1} when there is none.
   */
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

  /**
   * Rejects a {@code null} hex argument.
   *
   * @param hex The argument to check.
   * @throws IllegalArgumentException Thrown if {@code hex} is {@code null}.
   */
  private static void requireNonNullHex(CharSequence hex) {
    if (hex == null) {
      throw new IllegalArgumentException("hex must not be null");
    }
  }
}
