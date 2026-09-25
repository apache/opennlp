/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import opennlp.tools.util.normalizer.CodePointSet;
import opennlp.tools.util.normalizer.UnicodeWhitespace;

/**
 * An immutable policy for testing token eligibility against explicit character sets.
 *
 * <p>A token consists of one or more letter or digit bases. A letter may be followed by zero or
 * more marks. A mark cannot start a token or follow a digit. The supplied sets express a caller's
 * tokenization policy; this class does not infer Unicode character categories.</p>
 *
 * <p>This predicate tests a complete candidate without finding token boundaries or changing
 * its text. It does not configure {@link TokenizerME} or tokenizer training. Applications
 * can retain the category sets to reconstruct the same policy.</p>
 */
public final class TokenizerCharacterPolicy {

  private static final TokenizerCharacterPolicy ASCII = new TokenizerCharacterPolicy(
      CodePointSet.ofRange('A', 'Z').union(CodePointSet.ofRange('a', 'z')),
      CodePointSet.ofRange('0', '9'), CodePointSet.of());

  private final CodePointSet letters;
  private final CodePointSet digits;
  private final CodePointSet marks;

  private TokenizerCharacterPolicy(CodePointSet letters, CodePointSet digits, CodePointSet marks) {
    this.letters = letters;
    this.digits = digits;
    this.marks = marks;
  }

  /**
   * Creates a policy from explicit, pairwise-disjoint Unicode scalar-value sets.
   *
   * @param letters Code points treated as letters.
   * @param digits Code points treated as digits.
   * @param marks Code points permitted after a letter or another mark.
   * @return The immutable policy.
   * @throws IllegalArgumentException Thrown if a set is {@code null}, the base sets are both
   *     empty, a code point occurs in more than one set, or a set contains whitespace or a
   *     surrogate code point.
   */
  public static TokenizerCharacterPolicy of(
      CodePointSet letters, CodePointSet digits, CodePointSet marks) {
    if (letters == null || digits == null || marks == null) {
      throw new IllegalArgumentException("Character sets must not be null");
    }
    if (letters.isEmpty() && digits.isEmpty()) {
      throw new IllegalArgumentException("At least one letter or digit is required");
    }
    validateSet("letters", letters);
    validateSet("digits", digits);
    validateSet("marks", marks);
    requireDisjoint("letters", letters, "digits", digits);
    requireDisjoint("letters", letters, "marks", marks);
    requireDisjoint("digits", digits, "marks", marks);
    return new TokenizerCharacterPolicy(letters, digits, marks);
  }

  /** {@return a policy for ASCII letters and digits, with no marks} */
  public static TokenizerCharacterPolicy ascii() {
    return ASCII;
  }

  /**
   * Tests whether the entire input matches this policy.
   *
   * @param input The characters to test.
   * @return {@code true} if the input is a non-empty token accepted by this policy.
   * @throws IllegalArgumentException Thrown if {@code input} is {@code null}.
   */
  public boolean test(CharSequence input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    if (input.isEmpty()) {
      return false;
    }

    boolean markAllowed = false;
    for (int offset = 0; offset < input.length();) {
      char first = input.charAt(offset);
      final int codePoint;
      if (first >= Character.MIN_HIGH_SURROGATE && first <= Character.MAX_HIGH_SURROGATE) {
        if (offset + 1 >= input.length() || input.charAt(offset + 1) < Character.MIN_LOW_SURROGATE
            || input.charAt(offset + 1) > Character.MAX_LOW_SURROGATE) {
          return false;
        }
        codePoint = Character.toCodePoint(first, input.charAt(offset + 1));
        offset += 2;
      } else if (first >= Character.MIN_LOW_SURROGATE && first <= Character.MAX_LOW_SURROGATE) {
        return false;
      } else {
        codePoint = first;
        offset++;
      }

      if (letters.contains(codePoint)) {
        markAllowed = true;
      } else if (digits.contains(codePoint)) {
        markAllowed = false;
      } else if (!markAllowed || !marks.contains(codePoint)) {
        return false;
      }
    }
    return true;
  }

  /** {@return the immutable letter set} */
  public CodePointSet getLetters() {
    return letters;
  }

  /** {@return the immutable digit set} */
  public CodePointSet getDigits() {
    return digits;
  }

  /** {@return the immutable mark set} */
  public CodePointSet getMarks() {
    return marks;
  }

  /** Validates scalar values against the fixed Unicode whitespace definition. */
  private static void validateSet(String name, CodePointSet set) {
    for (int codePoint : set.toArray()) {
      if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
        throw new IllegalArgumentException(name + " contains a surrogate code point");
      }
      if (UnicodeWhitespace.isWhitespace(codePoint)) {
        throw new IllegalArgumentException(name + " contains Unicode whitespace");
      }
    }
  }

  /** Checks category overlap by visiting only the smaller set. */
  private static void requireDisjoint(
      String firstName, CodePointSet first, String secondName, CodePointSet second) {
    CodePointSet smaller = first.size() <= second.size() ? first : second;
    CodePointSet larger = smaller == first ? second : first;
    for (int codePoint : smaller.toArray()) {
      if (larger.contains(codePoint)) {
        throw new IllegalArgumentException(
            firstName + " and " + secondName + " overlap at U+" + Integer.toHexString(codePoint));
      }
    }
  }
}
