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

import java.util.function.Predicate;

import opennlp.tools.util.ArgumentChecks;
import opennlp.tools.util.normalizer.CodePointSet;
import opennlp.tools.util.normalizer.UnicodeWhitespace;

/**
 * An immutable policy for testing token eligibility against explicit character sets.
 *
 * <p>A token consists of one or more letters or digits. Each letter or digit may be followed by
 * zero or more marks. A mark cannot start a token.
 * The supplied sets express a caller's tokenization policy; this class does not infer Unicode
 * character categories.</p>
 *
 * <p>This predicate tests a complete candidate without finding token boundaries or changing
 * its text.</p>
 */
public final class TokenizerCharacterPolicy implements Predicate<CharSequence> {

  private static final String CODE_POINT_FORMAT = "U+%04X";

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
   * Creates a policy from explicit, pairwise-disjoint code point sets. Whitespace separates
   * tokens and so cannot be part of one; every other non-surrogate code point is left to the
   * caller.
   *
   * @param letters Code points treated as letters.
   * @param digits Code points treated as digits.
   * @param marks Code points permitted after a letter, a digit, or another mark.
   * @return The immutable policy.
   * @throws IllegalArgumentException Thrown if a set is {@code null}, {@code letters} and
   *     {@code digits} are both empty, a code point occurs in more than one set, or a set
   *     contains a surrogate code point or {@link UnicodeWhitespace} member.
   */
  public static TokenizerCharacterPolicy of(
      CodePointSet letters, CodePointSet digits, CodePointSet marks) {
    ArgumentChecks.requireNonNullArg(letters, "letters");
    ArgumentChecks.requireNonNullArg(digits, "digits");
    ArgumentChecks.requireNonNullArg(marks, "marks");
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
   * {@inheritDoc}
   *
   * <p>Returns {@code true} if the entire input is a non-empty token accepted by this policy.
   * Malformed UTF-16 input (an unpaired or reversed surrogate) returns {@code false}.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code input} is {@code null}.
   */
  @Override
  public boolean test(CharSequence input) {
    ArgumentChecks.requireNonNullArg(input, "input");
    if (input.isEmpty()) {
      return false;
    }

    for (int offset = 0; offset < input.length();) {
      final boolean first = offset == 0;
      char unit = input.charAt(offset);
      final int codePoint;
      if (Character.isHighSurrogate(unit)) {
        if (offset + 1 >= input.length() || !Character.isLowSurrogate(input.charAt(offset + 1))) {
          return false;
        }
        codePoint = Character.toCodePoint(unit, input.charAt(offset + 1));
        offset += 2;
      } else if (Character.isLowSurrogate(unit)) {
        return false;
      } else {
        codePoint = unit;
        offset++;
      }

      if (!letters.contains(codePoint) && !digits.contains(codePoint)
          && (first || !marks.contains(codePoint))) {
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

  /** Throws if {@code set} contains a surrogate code point or {@link UnicodeWhitespace} member. */
  private static void validateSet(String name, CodePointSet set) {
    for (int codePoint = Character.MIN_SURROGATE; codePoint <= Character.MAX_SURROGATE;
         codePoint++) {
      if (set.contains(codePoint)) {
        throw new IllegalArgumentException(name + " contains the surrogate code point "
            + String.format(CODE_POINT_FORMAT, codePoint));
      }
    }
    for (int codePoint : UnicodeWhitespace.codePoints()) {
      if (set.contains(codePoint)) {
        throw new IllegalArgumentException(name + " contains the whitespace code point "
            + String.format(CODE_POINT_FORMAT, codePoint));
      }
    }
  }

  /** Throws if {@code first} and {@code second} share a code point. */
  private static void requireDisjoint(
      String firstName, CodePointSet first, String secondName, CodePointSet second) {
    CodePointSet smaller = first.size() <= second.size() ? first : second;
    CodePointSet larger = smaller == first ? second : first;
    for (int codePoint : smaller.toArray()) {
      if (larger.contains(codePoint)) {
        throw new IllegalArgumentException(
            firstName + " and " + secondName + " overlap at "
                + String.format(CODE_POINT_FORMAT, codePoint));
      }
    }
  }
}
