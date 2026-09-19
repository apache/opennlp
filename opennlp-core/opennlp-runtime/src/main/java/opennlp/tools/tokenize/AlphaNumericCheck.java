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

package opennlp.tools.tokenize;

import java.util.regex.Pattern;

import opennlp.tools.util.normalizer.CodePointSet;

/**
 * Decides whether a token is alphanumeric under the alphanumeric {@link Pattern} of a
 * tokenizer model. The pattern is user-supplied, stored in the model manifest, and read back
 * as a regular expression. A pattern of the form {@code ^[...]+$} with a class of plain
 * characters and simple ranges, the form of all built-in language defaults, is evaluated as a
 * {@link CodePointSet} lookup. Other patterns are evaluated by the regular expression engine.
 * Tokens with unpaired surrogates are never eligible for the alphanumeric shortcut,
 * regardless of the pattern's syntax or flags. Other tokens are checked against the
 * configured character set or custom pattern.
 */
final class AlphaNumericCheck {

  private static final String CLASS_PREFIX = "^[";
  private static final String CLASS_SUFFIX = "]+$";

  private final Pattern pattern;
  private final CodePointSet characters;

  /**
   * Creates the check for a pattern, using a set lookup when the pattern has the supported form.
   *
   * @param pattern The alphanumeric pattern. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code pattern} is {@code null}.
   */
  AlphaNumericCheck(Pattern pattern) {
    if (pattern == null) {
      throw new IllegalArgumentException("pattern must not be null");
    }
    this.pattern = pattern;
    this.characters = pattern.flags() == 0 ? parseCharacterClass(pattern.pattern()) : null;
  }

  /**
   * Tests a token.
   *
   * @param token The token. Must not be {@code null}.
   * @return {@code true} if the token is well-formed UTF-16 and matches the pattern in full.
   * @throws IllegalArgumentException Thrown if {@code token} is {@code null}.
   */
  boolean test(CharSequence token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    if (characters != null && token.isEmpty()) {
      return false;
    }
    for (int i = 0; i < token.length();) {
      final int codePoint = Character.codePointAt(token, i);
      if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE
          || characters != null && !characters.contains(codePoint)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return characters != null || pattern.matcher(token).matches();
  }

  /**
   * Tells whether the check runs as a set lookup. This method exists so tests can pin which
   * patterns take the lookup. Both paths reject malformed UTF-16.
   *
   * @return {@code true} for a set lookup, {@code false} when the pattern is evaluated as
   *         a regular expression.
   */
  boolean isCharacterSet() {
    return characters != null;
  }

  /**
   * Reads a pattern of the form {@code ^[...]+$} into the set of code points its class accepts.
   * Inside the class only plain characters and ranges written as {@code x-y} are understood; a
   * hyphen in first or last position, or directly after a range, is a plain hyphen. A range
   * over the surrogate block skips that block, since an unpaired surrogate is not a
   * character. Escapes, negation, nested classes, intersections, and characters outside
   * the Basic Multilingual Plane are left to the engine.
   *
   * @param regex The pattern text.
   * @return The accepted code points, or {@code null} if the pattern is not of that form.
   */
  private CodePointSet parseCharacterClass(String regex) {
    if (!regex.startsWith(CLASS_PREFIX) || !regex.endsWith(CLASS_SUFFIX)
        || regex.length() <= CLASS_PREFIX.length() + CLASS_SUFFIX.length()) {
      return null;
    }
    final String body = regex.substring(CLASS_PREFIX.length(), regex.length() - CLASS_SUFFIX.length());
    CodePointSet characters = CodePointSet.of();
    int i = 0;
    while (i < body.length()) {
      final char c = body.charAt(i);
      if (!isLiteral(c)) {
        return null;
      }
      if (i + 2 < body.length() && body.charAt(i + 1) == '-') {
        final char to = body.charAt(i + 2);
        if (!isLiteral(to) || to < c) {
          return null;
        }
        characters = characters.union(rangeWithoutSurrogates(c, to));
        i += 3;
      } else {
        characters = characters.union(CodePointSet.of(c));
        i++;
      }
    }
    return characters;
  }

  /**
   * The code points of a range, without the surrogate block. Neither end is a surrogate, so
   * a range that overlaps the block covers all of it.
   *
   * @param from The first character of the range.
   * @param to The last character of the range, not smaller than {@code from}.
   * @return The set of the range.
   */
  private CodePointSet rangeWithoutSurrogates(char from, char to) {
    if (from <= Character.MAX_SURROGATE && to >= Character.MIN_SURROGATE) {
      return CodePointSet.ofRange(from, Character.MIN_SURROGATE - 1)
          .union(CodePointSet.ofRange(Character.MAX_SURROGATE + 1, to));
    }
    return CodePointSet.ofRange(from, to);
  }

  /**
   * Tests whether a character stands for itself inside a character class.
   *
   * @param c The character.
   * @return {@code false} for class syntax and for surrogates, {@code true} otherwise.
   */
  private boolean isLiteral(char c) {
    return c != '[' && c != ']' && c != '\\' && c != '^' && c != '&' && !Character.isSurrogate(c);
  }
}
