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
 * A {@link CharSequenceNormalizer} implementation that shrinks repeated whitespace and repeated
 * characters in text, in three steps:
 * <ol>
 *   <li>Each run of two or more ASCII whitespace characters (tab, line feed, vertical tab,
 *       form feed, carriage return, or space) collapses to a single space. A lone whitespace
 *       character is kept as it is.</li>
 *   <li>Each run of three or more repeats of one code point shrinks to two copies of its first
 *       occurrence, comparing repeats case-insensitively over ASCII; a run never starts on a
 *       line terminator. For example, {@code "coooool"} becomes {@code "cool"}.</li>
 *   <li>Every leading and trailing character at or below {@code U+0020} is dropped, the same
 *       rule {@link String#trim()} applies.</li>
 * </ol>
 */
public class ShrinkCharSequenceNormalizer implements CharSequenceNormalizer {

  private static final long serialVersionUID = 7042871810299585743L;

  /** The line terminator code points; a repeat run never starts on one. */
  private static final CodePointSet LINE_TERMINATORS =
      CodePointSet.of(0x000A, 0x000D, 0x0085, 0x2028, 0x2029);

  private static final ShrinkCharSequenceNormalizer INSTANCE = new ShrinkCharSequenceNormalizer();

  public static ShrinkCharSequenceNormalizer getInstance() {
    return INSTANCE;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CharSequence normalize(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    final CharSequence shrunk = shrinkRepeatedCodePoints(shrinkWhitespace(text));
    // Drops every leading and trailing char at or below U+0020, without copying when nothing
    // changed anywhere in the pipeline.
    int start = 0;
    int end = shrunk.length();
    while (start < end && shrunk.charAt(start) <= ' ') {
      start++;
    }
    while (end > start && shrunk.charAt(end - 1) <= ' ') {
      end--;
    }
    if (start == 0 && end == shrunk.length()) {
      return shrunk;
    }
    return shrunk.subSequence(start, end).toString();
  }

  /**
   * Replaces each run of two or more ASCII whitespace characters with a single space; a lone
   * whitespace character stays as it is.
   *
   * @param text The text to scan; never null.
   * @return The input itself when no run was found, otherwise the shrunk copy.
   */
  private CharSequence shrinkWhitespace(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (AsciiChars.WHITESPACE.contains(codePoint)) {
        int runEnd = i + 1; // members are single-char ASCII
        while (runEnd < length && AsciiChars.WHITESPACE.contains(text.charAt(runEnd))) {
          runEnd++;
        }
        if (runEnd - i >= 2) {
          if (out == null) {
            out = new StringBuilder(length).append(text, 0, i);
          }
          out.append(' ');
        } else if (out != null) {
          out.append(text.charAt(i));
        }
        i = runEnd;
      } else {
        if (out != null) {
          out.appendCodePoint(codePoint);
        }
        i += Character.charCount(codePoint);
      }
    }
    return out == null ? text : out.toString();
  }

  /**
   * Shrinks each run of three or more repeats of one code point to two copies of its first
   * occurrence. The run's first code point must not be a line terminator, every repeat
   * advances by the first occurrence's char count while comparing whole code points ASCII
   * case-insensitively, and a failed attempt resumes at the next char.
   *
   * @param text The text to scan; never null.
   * @return The input itself when no run was found, otherwise the shrunk copy.
   */
  private CharSequence shrinkRepeatedCodePoints(CharSequence text) {
    final int length = text.length();
    StringBuilder out = null;
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      if (!LINE_TERMINATORS.contains(codePoint)) {
        final int charCount = Character.charCount(codePoint);
        int repeats = 0;
        int end = i + charCount;
        while (end + charCount <= length
            && AsciiChars.caseInsensitiveEquals(Character.codePointAt(text, end), codePoint)) {
          end += charCount;
          repeats++;
        }
        if (repeats >= 2) {
          if (out == null) {
            out = new StringBuilder(length).append(text, 0, i);
          }
          out.append(text, i, i + charCount).append(text, i, i + charCount);
          i = end;
          continue;
        }
      }
      if (out != null) {
        out.append(text.charAt(i));
      }
      i++;
    }
    return out == null ? text : out.toString();
  }

}
