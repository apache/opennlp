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
package opennlp.subword.sentencepiece;

/**
 * A caller's text encoded as UTF-8, keeping the map from every byte offset back to the UTF-16
 * offset it came from.
 *
 * <p>The pipeline runs in UTF-8 byte space, but the spans reported to the caller must be UTF-16
 * offsets into the original {@code CharSequence}; this map converts them. An unpaired surrogate,
 * which UTF-8 cannot represent, is encoded as U+FFFD.</p>
 */
final class Utf8Text {

  private final byte[] bytes;
  private final int byteLength;
  // Null for pure-ASCII text, where byte offsets equal UTF-16 offsets.
  private final int[] byteToChar;
  private final int charLength;

  /**
   * Wraps an encoded buffer and its offset map.
   *
   * @param bytes      The UTF-8 buffer.
   * @param byteLength The number of valid bytes in {@code bytes}.
   * @param byteToChar The byte-to-UTF-16 offset map, or null for pure-ASCII text.
   * @param charLength The length of the original text in UTF-16 units.
   */
  private Utf8Text(byte[] bytes, int byteLength, int[] byteToChar, int charLength) {
    this.bytes = bytes;
    this.byteLength = byteLength;
    this.byteToChar = byteToChar;
    this.charLength = charLength;
  }

  /**
   * Encodes text.
   *
   * @param text The text to encode; must not be null.
   * @return The encoded view.
   */
  static Utf8Text of(CharSequence text) {
    final int charLength = text.length();
    // The common case: pure ASCII, where the bytes are the chars and the map is the identity.
    int ascii = 0;
    while (ascii < charLength && text.charAt(ascii) < 0x80) {
      ascii++;
    }
    if (ascii == charLength) {
      final byte[] exact = new byte[charLength];
      for (int i = 0; i < charLength; i++) {
        exact[i] = (byte) text.charAt(i);
      }
      return new Utf8Text(exact, charLength, null, charLength);
    }

    final byte[] bytes = new byte[charLength * 3 + 1];
    final int[] byteToChar = new int[charLength * 3 + 2];
    int b = 0;
    int c = 0;
    while (c < charLength) {
      int codePoint = text.charAt(c);
      int charCount = 1;
      if (Character.isHighSurrogate((char) codePoint) && c + 1 < charLength
          && Character.isLowSurrogate(text.charAt(c + 1))) {
        codePoint = Character.toCodePoint((char) codePoint, text.charAt(c + 1));
        charCount = 2;
      } else if (Character.isSurrogate((char) codePoint)) {
        // An unpaired surrogate has no UTF-8 form; U+FFFD keeps the encoding total.
        codePoint = 0xFFFD;
      }
      final int start = b;
      if (codePoint < 0x80) {
        bytes[b++] = (byte) codePoint;
      } else if (codePoint < 0x800) {
        bytes[b++] = (byte) (0xC0 | codePoint >>> 6);
        bytes[b++] = (byte) (0x80 | codePoint & 0x3F);
      } else if (codePoint < 0x10000) {
        bytes[b++] = (byte) (0xE0 | codePoint >>> 12);
        bytes[b++] = (byte) (0x80 | codePoint >>> 6 & 0x3F);
        bytes[b++] = (byte) (0x80 | codePoint & 0x3F);
      } else {
        bytes[b++] = (byte) (0xF0 | codePoint >>> 18);
        bytes[b++] = (byte) (0x80 | codePoint >>> 12 & 0x3F);
        bytes[b++] = (byte) (0x80 | codePoint >>> 6 & 0x3F);
        bytes[b++] = (byte) (0x80 | codePoint & 0x3F);
      }
      for (int i = start; i < b; i++) {
        byteToChar[i] = c;
      }
      c += charCount;
    }
    byteToChar[b] = charLength;
    return new Utf8Text(bytes, b, byteToChar, charLength);
  }

  /** {@return the UTF-8 buffer; valid up to {@link #byteLength()}} */
  byte[] bytes() {
    return bytes;
  }

  /** {@return the number of valid bytes in {@link #bytes()}} */
  int byteLength() {
    return byteLength;
  }

  /** {@return the length of the original text in UTF-16 units} */
  int charLength() {
    return charLength;
  }

  /**
   * Maps a byte offset to the UTF-16 offset of the character containing it.
   *
   * @param byteOffset An offset in {@code [0, bytes().length]}.
   * @return The UTF-16 offset; the text length for the end offset.
   */
  int charOffset(int byteOffset) {
    return byteToChar == null ? byteOffset : byteToChar[byteOffset];
  }
}
