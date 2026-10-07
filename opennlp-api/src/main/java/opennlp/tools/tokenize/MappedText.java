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

package opennlp.tools.tokenize;

import java.util.Arrays;

import opennlp.tools.util.ParamChecks;

/**
 * The normalized text with the original-text range for each character. Characters inserted by
 * the pipeline (isolation spaces) use an empty range at the insertion point.
 *
 * <p>The buffers {@link #chars}, {@link #starts} and {@link #ends} are exposed for fast,
 * allocation-free reads; only the first {@link #length} entries of each are valid. They grow on
 * demand, so the capacity given to the constructor is a sizing hint, not a limit.</p>
 */
final class MappedText {

  /**
   * The largest capacity the buffers grow to. Some JVMs reserve header words in arrays, so
   * allocating close to {@link Integer#MAX_VALUE} elements can fail even with enough memory.
   */
  private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

  /** The smallest capacity the buffers grow to from empty. */
  private static final int MIN_GROWTH = 16;

  char[] chars;
  int[] starts;
  int[] ends;
  int length;

  /**
   * Creates an empty mapping.
   *
   * @param capacity The expected number of UTF-16 code units. Must not be negative. Zero is
   *     allowed and allocates nothing up front; the buffers grow on the first {@code add}.
   *     Since it is only a sizing hint, a value above {@link #MAX_CAPACITY} is clamped to it.
   * @throws IllegalArgumentException Thrown if {@code capacity} is negative.
   */
  MappedText(int capacity) {
    ParamChecks.requireNonNegative(capacity, "capacity");
    final int initial = Math.min(capacity, MAX_CAPACITY);
    chars = new char[initial];
    starts = new int[initial];
    ends = new int[initial];
  }

  /** {@return the number of code units the buffers hold before they have to grow} */
  int capacity() {
    return chars.length;
  }

  /**
   * Appends one UTF-16 code unit with its source range. The range is not validated: the
   * pipeline passes ranges of the original text, and an empty range ({@code originalStart ==
   * originalEnd}) marks an inserted character.
   *
   * @param c The code unit to append.
   * @param originalStart The start of the source range in the original text.
   * @param originalEnd The exclusive end of the source range in the original text.
   * @throws IllegalStateException Thrown if the mapping already holds the maximum number of
   *     code units an array can hold.
   */
  void add(char c, int originalStart, int originalEnd) {
    if (length == chars.length) {
      grow();
    }
    chars[length] = c;
    starts[length] = originalStart;
    ends[length] = originalEnd;
    length++;
  }

  /**
   * Appends every UTF-16 code unit of a string, each with the same source range.
   *
   * @param s The string to append. Must not be {@code null}.
   * @param originalStart The start of the source range in the original text.
   * @param originalEnd The exclusive end of the source range in the original text.
   * @see #add(char, int, int)
   */
  void add(String s, int originalStart, int originalEnd) {
    for (int i = 0; i < s.length(); i++) {
      add(s.charAt(i), originalStart, originalEnd);
    }
  }

  /**
   * Appends one code point, as one or two UTF-16 code units that share the same source range.
   *
   * @param codePoint A valid Unicode code point.
   * @param originalStart The start of the source range in the original text.
   * @param originalEnd The exclusive end of the source range in the original text.
   * @see #add(char, int, int)
   */
  void addCodePoint(int codePoint, int originalStart, int originalEnd) {
    if (Character.isBmpCodePoint(codePoint)) {
      add((char) codePoint, originalStart, originalEnd);
    } else {
      add(Character.highSurrogate(codePoint), originalStart, originalEnd);
      add(Character.lowSurrogate(codePoint), originalStart, originalEnd);
    }
  }

  /**
   * Returns the code point at an index of the mapped UTF-16 buffer. A high surrogate followed
   * by a low surrogate within {@link #length} forms one supplementary code point; an unpaired
   * surrogate is returned as is.
   *
   * @param index The index of a code unit, in {@code [0, length)}.
   * @return The code point that starts at {@code index}.
   * @throws IndexOutOfBoundsException Thrown if {@code index} is outside {@code [0, length)}.
   */
  int codePointAt(int index) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException("index " + index + " is outside [0, " + length + ")");
    }
    final char c = chars[index];
    if (Character.isHighSurrogate(c) && index + 1 < length
        && Character.isLowSurrogate(chars[index + 1])) {
      return Character.toCodePoint(c, chars[index + 1]);
    }
    return c;
  }

  /** {@return the mapped character content, the first {@link #length} code units} */
  String text() {
    return new String(chars, 0, length);
  }

  /**
   * Doubles the buffers, starting at {@value #MIN_GROWTH} and capped at {@link #MAX_CAPACITY}.
   *
   * @throws IllegalStateException Thrown if the buffers are already at {@link #MAX_CAPACITY}.
   */
  private void grow() {
    if (length >= MAX_CAPACITY) {
      throw new IllegalStateException("mapped text exceeds " + MAX_CAPACITY + " code units");
    }
    final int capacity = (int) Math.min(MAX_CAPACITY, Math.max(MIN_GROWTH, 2L * length));
    chars = Arrays.copyOf(chars, capacity);
    starts = Arrays.copyOf(starts, capacity);
    ends = Arrays.copyOf(ends, capacity);
  }
}
