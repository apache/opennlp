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

import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Predicate;

import opennlp.tools.util.ParamChecks;
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
 *
 * @since 3.0.0
 */
public final class TokenizerCharacterPolicy implements Predicate<CharSequence> {

  private static final String CODE_POINT_FORMAT = "U+%04X";
  private static final String LETTERS_FIELD = "letters=";
  private static final String DIGITS_FIELD = ";digits=";
  private static final String MARKS_FIELD = ";marks=";
  private static final char RANGE_SEPARATOR = ',';
  private static final char RANGE_DASH = '-';
  private static final HexFormat UPPER_HEX = HexFormat.of().withUpperCase();
  private static final int MIN_HEX_DIGITS = 4;
  private static final int BITS_PER_HEX_DIGIT = 4;

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
    ParamChecks.requireNonNullArg(letters, "letters");
    ParamChecks.requireNonNullArg(digits, "digits");
    ParamChecks.requireNonNullArg(marks, "marks");
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
   * Parses the text form written by {@link #toString()}: the three fields
   * {@code letters=}, {@code ;digits=} and {@code ;marks=} in that order, each followed by
   * zero or more comma separated hex code points ({@code 0041}) or inclusive ranges
   * ({@code 0041-005A}). Hex digits may use either case; no prefix or whitespace is accepted.
   *
   * @param text The text form to read.
   * @return The policy the text describes.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null} or not of that
   *     form, or if the sets it describes are rejected by
   *     {@link #of(CodePointSet, CodePointSet, CodePointSet)}.
   */
  public static TokenizerCharacterPolicy parse(String text) {
    ParamChecks.requireNonNullArg(text, "text");
    final int digitsAt = text.indexOf(DIGITS_FIELD);
    final int marksAt = digitsAt < 0 ? -1 : text.indexOf(MARKS_FIELD, digitsAt + DIGITS_FIELD.length());
    if (!text.startsWith(LETTERS_FIELD) || digitsAt < 0 || marksAt < 0) {
      throw malformed(text, "expected the fields " + LETTERS_FIELD + DIGITS_FIELD + MARKS_FIELD);
    }
    return of(parseRanges(text, LETTERS_FIELD.length(), digitsAt),
        parseRanges(text, digitsAt + DIGITS_FIELD.length(), marksAt),
        parseRanges(text, marksAt + MARKS_FIELD.length(), text.length()));
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
    ParamChecks.requireNonNullArg(input, "input");
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

  /**
   * {@inheritDoc}
   *
   * <p>Two policies are equal if their letter, digit and mark sets are equal.</p>
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof TokenizerCharacterPolicy other && letters.equals(other.letters)
        && digits.equals(other.digits) && marks.equals(other.marks);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Consistent with {@link #equals(Object)}: computed from the letter, digit and mark sets.</p>
   */
  @Override
  public int hashCode() {
    return Objects.hash(letters, digits, marks);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Writes the text form read by {@link #parse(String)}, for example
   * {@code letters=0041-005A,0061-007A;digits=0030-0039;marks=} for {@link #ascii()}: runs of
   * adjacent code points become ranges, and each code point is written with at least four
   * upper-case hex digits.</p>
   */
  @Override
  public String toString() {
    final StringBuilder text = new StringBuilder();
    appendRanges(text.append(LETTERS_FIELD), letters);
    appendRanges(text.append(DIGITS_FIELD), digits);
    appendRanges(text.append(MARKS_FIELD), marks);
    return text.toString();
  }

  /** Appends the members of {@code set} as comma separated code points and ranges. */
  private static void appendRanges(StringBuilder text, CodePointSet set) {
    final int[] members = set.toArray();
    int first = 0;
    while (first < members.length) {
      int last = first;
      while (last + 1 < members.length && members[last + 1] == members[last] + 1) {
        last++;
      }
      if (first > 0) {
        text.append(RANGE_SEPARATOR);
      }
      appendHex(text, members[first]);
      if (last > first) {
        appendHex(text.append(RANGE_DASH), members[last]);
      }
      first = last + 1;
    }
  }

  /** Appends {@code codePoint} as upper-case hex with at least {@link #MIN_HEX_DIGITS} digits. */
  private static void appendHex(StringBuilder text, int codePoint) {
    final String hex = UPPER_HEX.toHexDigits(codePoint);
    int start = 0;
    while (start < hex.length() - MIN_HEX_DIGITS && hex.charAt(start) == '0') {
      start++;
    }
    text.append(hex, start, hex.length());
  }

  /** Reads the comma separated code points and ranges in {@code text[from, to)}. */
  private static CodePointSet parseRanges(String text, int from, int to) {
    CodePointSet members = CodePointSet.of();
    if (from == to) {
      return members;
    }
    int offset = from;
    while (true) {
      int separator = text.indexOf(RANGE_SEPARATOR, offset);
      if (separator < 0 || separator > to) {
        separator = to;
      }
      members = members.union(parseRange(text, offset, separator));
      if (separator == to) {
        return members;
      }
      offset = separator + 1;
    }
  }

  /** Reads one code point or one inclusive range in {@code text[from, to)}. */
  private static CodePointSet parseRange(String text, int from, int to) {
    final int dash = text.indexOf(RANGE_DASH, from);
    if (dash < 0 || dash >= to) {
      return CodePointSet.of(parseHex(text, from, to));
    }
    final int low = parseHex(text, from, dash);
    final int high = parseHex(text, dash + 1, to);
    if (low > high) {
      throw malformed(text, "descending range '" + text.substring(from, to) + "'");
    }
    return CodePointSet.ofRange(low, high);
  }

  /** Reads the hex code point in {@code text[from, to)}. */
  private static int parseHex(String text, int from, int to) {
    if (from >= to) {
      throw malformed(text, "empty code point at offset " + from);
    }
    int value = 0;
    for (int i = from; i < to; i++) {
      final char c = text.charAt(i);
      if (!HexFormat.isHexDigit(c)) {
        throw malformed(text, "'" + text.substring(from, to) + "' is not a hex code point");
      }
      value = value << BITS_PER_HEX_DIGIT | HexFormat.fromHexDigit(c);
      if (value > Character.MAX_CODE_POINT) {
        throw malformed(text, "'" + text.substring(from, to) + "' is beyond U+10FFFF");
      }
    }
    return value;
  }

  /** {@return the exception for a text form that cannot be read} */
  private static IllegalArgumentException malformed(String text, String detail) {
    return new IllegalArgumentException("Malformed character policy '" + text + "': " + detail);
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
