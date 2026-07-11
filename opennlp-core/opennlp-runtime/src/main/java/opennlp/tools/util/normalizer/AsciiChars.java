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
 * ASCII character helpers shared by the normalizer implementations of this package, so the
 * character definitions cannot drift apart between classes.
 */
final class AsciiChars {

  /**
   * The six ASCII whitespace characters: tab ({@code U+0009}), line feed ({@code U+000A}),
   * vertical tab ({@code U+000B}), form feed ({@code U+000C}), carriage return
   * ({@code U+000D}), and space ({@code U+0020}).
   */
  static final CodePointSet WHITESPACE =
      CodePointSet.ofRange(0x0009, 0x000D).union(CodePointSet.of(0x0020));

  private AsciiChars() {
  }

  /**
   * {@return {@code c} lower cased if it is an ASCII capital letter, otherwise {@code c}
   * unchanged}
   *
   * @param c The char to lower case.
   */
  static char toLower(char c) {
    return c >= 'A' && c <= 'Z' ? (char) (c + 0x20) : c;
  }

  /**
   * {@return {@code codePoint} lower cased if it is an ASCII capital letter, otherwise
   * {@code codePoint} unchanged}
   *
   * @param codePoint The code point to lower case.
   */
  static int toLower(int codePoint) {
    return codePoint >= 'A' && codePoint <= 'Z' ? codePoint + 0x20 : codePoint;
  }

  /**
   * {@return whether {@code a} and {@code b} are equal, comparing ASCII letters
   * case-insensitively}
   *
   * @param a The first char.
   * @param b The second char.
   */
  static boolean caseInsensitiveEquals(char a, char b) {
    return toLower(a) == toLower(b);
  }

  /**
   * {@return whether {@code a} and {@code b} are equal, comparing ASCII letters
   * case-insensitively}
   *
   * @param a The first code point.
   * @param b The second code point.
   */
  static boolean caseInsensitiveEquals(int a, int b) {
    return toLower(a) == toLower(b);
  }
}
