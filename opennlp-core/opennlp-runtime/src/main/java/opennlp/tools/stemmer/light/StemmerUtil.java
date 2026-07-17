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
package opennlp.tools.stemmer.light;

/**
 * Buffer helpers shared by the light and minimal stemmers, adapted from Apache Lucene's
 * analysis-common module. All operate on a {@code char[]} prefix of the given length, the
 * in-place convention the stemming algorithms use.
 */
final class StemmerUtil {

  private StemmerUtil() {
  }

  /**
   * Checks whether the buffer ends with a suffix.
   *
   * @param s      The input buffer.
   * @param len    The filled length of the buffer.
   * @param suffix The suffix to test.
   * @return {@code true} if the buffer ends with {@code suffix}.
   */
  static boolean endsWith(char[] s, int len, String suffix) {
    final int suffixLen = suffix.length();
    if (suffixLen > len) {
      return false;
    }
    for (int i = suffixLen - 1; i >= 0; i--) {
      if (s[len - (suffixLen - i)] != suffix.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Deletes one character in place.
   *
   * @param s   The input buffer.
   * @param pos The position of the character to delete; must be less than {@code len}.
   * @param len The filled length of the buffer.
   * @return The filled length after the deletion.
   */
  static int delete(char[] s, int pos, int len) {
    if (pos < len - 1) {
      System.arraycopy(s, pos + 1, s, pos, len - pos - 1);
    }
    return len - 1;
  }

  /**
   * Checks whether a character is one of the unaccented vowel letters {@code a}, {@code e},
   * {@code i}, {@code o}, {@code u} or {@code y}. This is the vowel test shared by the Finnish
   * and Hungarian light stemming algorithms.
   *
   * @param ch The character to test.
   * @return {@code true} if {@code ch} is one of the six vowel letters.
   */
  static boolean isVowel(char ch) {
    switch (ch) {
      case 'a':
      case 'e':
      case 'i':
      case 'o':
      case 'u':
      case 'y':
        return true;
      default:
        return false;
    }
  }
}
