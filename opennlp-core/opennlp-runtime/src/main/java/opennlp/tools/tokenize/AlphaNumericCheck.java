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
 * Decides whether a token is alphanumeric under a tokenizer model's alphanumeric
 * {@link Pattern}. A pattern of the shape {@code ^[...]+$} whose class holds only literal
 * characters and simple ranges, which covers every built-in language default, is evaluated
 * as a character set lookup. Any other pattern is evaluated by the regular expression engine,
 * so the result is the same as {@code pattern.matcher(token).matches()} in both cases.
 * Regex evaluation is the documented exception and applies only outside plain JDK
 * character-class patterns.
 * A {@code null} pattern yields a check that rejects every token, so callers documenting
 * a nullable pattern keep working with skipping disabled or enabled. Set lookups never
 * accept surrogate code units or supplementary characters, which matches the engine for
 * every built-in language default.
 */
final class AlphaNumericCheck {

  private static final String CLASS_PREFIX = "^[";
  private static final String CLASS_SUFFIX = "]+$";

  private static final int SURROGATE_MIN = Character.MIN_SURROGATE;
  private static final int SURROGATE_MAX = Character.MAX_SURROGATE;

  private final BitSet characters;
  private final Pattern pattern;

  private AlphaNumericCheck(BitSet characters, Pattern pattern) {
    this.characters = characters;
    this.pattern = pattern;
  }

  /**
   * Creates the check for a non-null pattern, using a set lookup when eligible.
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
   * @param pattern The alphanumeric pattern, or {@code null} for a check that rejects every token.
   * @return A check that accepts exactly the tokens the pattern matches as a whole.
   */
  static AlphaNumericCheck of(Pattern pattern) {
    if (pattern == null) {
      return new AlphaNumericCheck(new BitSet(), null);
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
   * Reads a pattern of the shape {@code ^[...]+$} into the set of characters its class accepts.
   * Inside the class only literal characters and ranges written as {@code x-y} are understood; a
   * hyphen in first or last position is literal. A range spanning the surrogate block leaves
   * that block out, since the shipped patterns never match it. Escapes, negation, nested
   * classes, intersections, and anything outside the Basic Multilingual Plane make the pattern
   * ineligible.
   *
   * @param regex The pattern text.
   * @return The accepted characters, or {@code null} if the pattern is not of that shape.
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
   * Adds a literal range to the set, leaving out surrogate code units when the range spans them.
   * Both ends are literal characters, so neither is a surrogate and an intersecting range
   * always spans the whole surrogate block.
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
