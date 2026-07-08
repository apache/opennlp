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
 * Shared code-point decoding for forward (and reverse) cursor scans over {@link CharSequence} text.
 *
 * <p>A {@code char} that is not a high surrogate is treated as its own code point, so BMP text
 * decodes without {@link Character#codePointAt(CharSequence, int)} on every step.</p>
 */
public final class CodePoints {

  /** A code point at an index together with its UTF-16 width. */
  public record At(int codePoint, int charCount) {

    /** {@return the index immediately after this code point} */
    public int nextIndex(int index) {
      return index + charCount;
    }

    /** {@return the index immediately before this code point when read from the end} */
    public int previousIndex(int index) {
      return index - charCount;
    }
  }

  private CodePoints() {
  }

  /**
   * Reads the code point starting at {@code index}.
   *
   * @param text  The text.
   * @param index The UTF-16 index; must be in {@code [0, text.length())}.
   * @return The decoded code point and its char width.
   */
  public static At at(CharSequence text, int index) {
    final char c = text.charAt(index);
    if (!Character.isHighSurrogate(c)) {
      return new At(c, 1);
    }
    final int codePoint = Character.codePointAt(text, index);
    return new At(codePoint, Character.charCount(codePoint));
  }

  /**
   * Reads the code point ending at {@code index} (exclusive end offset).
   *
   * @param text  The text.
   * @param index The UTF-16 index after the code point; must be in {@code (0, text.length()]}.
   * @return The decoded code point and its char width.
   */
  public static At before(CharSequence text, int index) {
    final char c = text.charAt(index - 1);
    if (!Character.isLowSurrogate(c)) {
      return new At(c, 1);
    }
    final int codePoint = Character.codePointBefore(text, index);
    return new At(codePoint, Character.charCount(codePoint));
  }
}
