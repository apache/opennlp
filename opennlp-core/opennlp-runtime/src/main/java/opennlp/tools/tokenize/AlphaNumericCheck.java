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

import java.util.BitSet;
import java.util.regex.Pattern;

/**
 * Decides whether a token is alphanumeric under the alphanumeric {@link Pattern} of a
 * tokenizer model. The pattern is user-supplied, stored in the model manifest, and read back
 * as a regular expression. A pattern of the form {@code ^[...]+$} with a class of plain
 * characters and simple ranges, the form of all built-in language defaults, is evaluated as a
 * character set lookup. Other patterns are evaluated by the regular expression engine.
 *
 * <p>Both ways give the result of {@code pattern.matcher(token).matches()}, with one exception:
 * a set lookup rejects an unpaired surrogate, which is not a character, where the engine
 * accepts it as a code point inside a range that spans the surrogate block.
 * Supplementary-plane characters are never in a set, and a class that names one is evaluated
 * by the engine.
 */
final class AlphaNumericCheck {

  private static final String CLASS_PREFIX = "^[";
  private static final String CLASS_SUFFIX = "]+$";

  private static final int SURROGATE_MIN = Character.MIN_SURROGATE;
  private static final int SURROGATE_MAX = Character.MAX_SURROGATE;

  private final BitSet characters;
  private final Pattern pattern;

  /**
   * Creates the check, using a set lookup when the pattern has the supported form.
   *
   * @param pattern The alphanumeric pattern. Must not be {@code null}.
   */
  private AlphaNumericCheck(Pattern pattern) {
    if (pattern.flags() != 0) {
      characters = null;
      this.pattern = pattern;
    }
    else {
      final BitSet parsed = parseCharacterClass(pattern.pattern());
      characters = parsed;
      this.pattern = parsed == null ? pattern : null;
    }
  }

  /**
   * Creates the check for a pattern.
   *
   * @param pattern The alphanumeric pattern. Must not be {@code null}.
   * @return A check that accepts the tokens the pattern matches as a whole.
   * @throws IllegalArgumentException If {@code pattern} is {@code null}.
   */
  static AlphaNumericCheck of(Pattern pattern) {
    if (pattern == null) {
      throw new IllegalArgumentException("pattern must not be null");
    }
    return new AlphaNumericCheck(pattern);
  }

  /**
   * Tests a token.
   *
   * @param token The token.
   * @return {@code true} if the whole token matches the pattern.
   */
  boolean test(CharSequence token) {
    if (characters == null) {
      return pattern.matcher(token).matches();
    }
    if (token.isEmpty()) {
      return false;
    }
    for (int i = 0; i < token.length();) {
      final int codePoint = Character.codePointAt(token, i);
      if (codePoint > Character.MAX_VALUE || !characters.get(codePoint)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  /**
   * Tells whether the check runs as a character set lookup.
   *
   * @return {@code true} for a set lookup, {@code false} when the pattern is evaluated as
   *         a regular expression.
   */
  boolean isCharacterSet() {
    return characters != null;
  }

  /**
   * Reads a pattern of the form {@code ^[...]+$} into the set of characters its class accepts.
   * Inside the class only plain characters and ranges written as {@code x-y} are understood; a
   * hyphen in first or last position, or directly after a range, is a plain hyphen. A range
   * over the surrogate block skips that block. Escapes, negation, nested classes,
   * intersections, and characters outside the Basic Multilingual Plane are left to the engine.
   *
   * @param regex The pattern text.
   * @return The accepted characters, or {@code null} if the pattern is not of that form.
   */
  private BitSet parseCharacterClass(String regex) {
    if (!regex.startsWith(CLASS_PREFIX) || !regex.endsWith(CLASS_SUFFIX)
        || regex.length() <= CLASS_PREFIX.length() + CLASS_SUFFIX.length()) {
      return null;
    }
    final String body = regex.substring(CLASS_PREFIX.length(), regex.length() - CLASS_SUFFIX.length());
    final BitSet characters = new BitSet();
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
        setRange(characters, c, to);
        i += 3;
      } else {
        characters.set(c);
        i++;
      }
    }
    return characters;
  }

  /**
   * Adds a range to the set, skipping the surrogate code units when the range spans them.
   * Neither end is a surrogate, so a range that overlaps the block covers all of it.
   *
   * @param characters The set to fill.
   * @param from The first character of the range.
   * @param to The last character of the range, not smaller than {@code from}.
   */
  private void setRange(BitSet characters, char from, char to) {
    if (from <= SURROGATE_MAX && to >= SURROGATE_MIN) {
      characters.set(from, SURROGATE_MIN);
      characters.set(SURROGATE_MAX + 1, to + 1);
    }
    else {
      characters.set(from, to + 1);
    }
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
