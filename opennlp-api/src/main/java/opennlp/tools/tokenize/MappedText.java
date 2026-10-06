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

/**
 * The normalized text with the original-text range for each character. Characters inserted by
 * the pipeline (isolation spaces) use an empty range at the insertion point.
 */
final class MappedText {
  char[] chars;
  int[] starts;
  int[] ends;
  int length;

  /** Creates an empty mapping with space for the expected number of UTF-16 code units. */
  MappedText(int capacity) {
    chars = new char[capacity];
    starts = new int[capacity];
    ends = new int[capacity];
  }

  /** Adds one UTF-16 code unit with a source range. */
  void add(char c, int originalStart, int originalEnd) {
    if (length == chars.length) {
      final int capacity = Math.max(16, length * 2);
      chars = Arrays.copyOf(chars, capacity);
      starts = Arrays.copyOf(starts, capacity);
      ends = Arrays.copyOf(ends, capacity);
    }
    chars[length] = c;
    starts[length] = originalStart;
    ends[length] = originalEnd;
    length++;
  }

  /** Adds a string with one source range shared by all code units. */
  void add(String s, int originalStart, int originalEnd) {
    for (int i = 0; i < s.length(); i++) {
      add(s.charAt(i), originalStart, originalEnd);
    }
  }

  /** Adds one code point with a source range shared by all code units. */
  void addCodePoint(int codePoint, int originalStart, int originalEnd) {
    if (Character.isBmpCodePoint(codePoint)) {
      add((char) codePoint, originalStart, originalEnd);
    } else {
      add(Character.highSurrogate(codePoint), originalStart, originalEnd);
      add(Character.lowSurrogate(codePoint), originalStart, originalEnd);
    }
  }

  /** Returns a code point from the mapped UTF-16 buffer. */
  int codePointAt(int index) {
    final char c = chars[index];
    if (Character.isHighSurrogate(c) && index + 1 < length
        && Character.isLowSurrogate(chars[index + 1])) {
      return Character.toCodePoint(c, chars[index + 1]);
    }
    return c;
  }

  /** Returns the mapped character content. */
  String text() {
    return new String(chars, 0, length);
  }
}
