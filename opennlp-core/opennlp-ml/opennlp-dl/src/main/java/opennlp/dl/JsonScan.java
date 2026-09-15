/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import opennlp.tools.commons.Internal;
import opennlp.tools.util.StringUtil;

/**
 * A cursor-based reader for the small JSON files the deep-learning components use: vocabularies
 * and model configurations. It follows the grammar of
 * <a href="https://www.rfc-editor.org/rfc/rfc8259">RFC 8259</a> for structure, whitespace, and
 * string escapes, and reads an object as a list of {@link Member members} without building a
 * document tree. It is lenient in one respect: a control character inside a string is kept as
 * content instead of being rejected, so a label wrapped over two lines still reads.
 * Malformed text is reported with the offset at which reading stopped.
 */
@Internal
public final class JsonScan {

  private static final String TRUE = "true";
  private static final String FALSE = "false";
  private static final String NULL = "null";

  private JsonScan() {
  }

  /**
   * A member of a JSON object.
   *
   * @param key The unescaped key.
   * @param valueStart The offset of the first character of the value.
   * @param valueEnd The offset after the last character of the value.
   */
  public record Member(String key, int valueStart, int valueEnd) {
  }

  /**
   * Reads the members of the single object that is the document.
   *
   * @param text The JSON text. Must not be {@code null}.
   * @return The members in document order, an empty list for an empty object.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}, does not
   *     consist of one object, or is malformed at any position.
   */
  public static List<Member> document(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    final int start = skipWhitespace(text, 0);
    expect(text, start, '{');
    final List<Member> members = new ArrayList<>();
    final int end = endOfObject(text, start, members);
    final int trailing = skipWhitespace(text, end);
    if (trailing < text.length()) {
      throw malformed(text, trailing, "content after the object");
    }
    return Collections.unmodifiableList(members);
  }

  /**
   * Reads the members of the object that starts at an offset.
   *
   * @param text The JSON text. Must not be {@code null}.
   * @param brace The offset of the opening brace.
   * @return The members in document order, an empty list for an empty object.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}, {@code brace} does
   *     not carry an opening brace, or the object is malformed.
   */
  public static List<Member> members(String text, int brace) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    expect(text, brace, '{');
    final List<Member> members = new ArrayList<>();
    endOfObject(text, brace, members);
    return Collections.unmodifiableList(members);
  }

  /**
   * Finds the member with a key in a list of members. A later member with the same key
   * counts, as with a map that is filled in document order.
   *
   * @param members The members.
   * @param key The unescaped key.
   * @return The last member with that key, or {@code null} if there is none.
   */
  public static Member member(List<Member> members, String key) {
    Member found = null;
    for (Member m : members) {
      if (m.key().equals(key)) {
        found = m;
      }
    }
    return found;
  }

  /**
   * Tests whether the value of a member is an object.
   *
   * @param text The JSON text.
   * @param member The member.
   * @return {@code true} if the value starts with an opening brace.
   */
  public static boolean isObject(String text, Member member) {
    return text.charAt(member.valueStart()) == '{';
  }

  /**
   * Reads the value of a member as a string.
   *
   * @param text The JSON text.
   * @param member The member.
   * @return The unescaped string.
   * @throws IllegalArgumentException Thrown if the value is not a string literal.
   */
  public static String stringValue(String text, Member member) {
    if (text.charAt(member.valueStart()) != '"') {
      throw new IllegalArgumentException("Value of \"" + member.key() + "\" must be a string: "
          + text.substring(member.valueStart(), member.valueEnd()));
    }
    return unescape(text, member.valueStart() + 1, member.valueEnd() - 1);
  }

  /**
   * Reads the value of a member as a non-negative integer: a run of ASCII digits with no sign,
   * fraction, or exponent that fits into an {@code int}.
   *
   * @param text The JSON text.
   * @param member The member.
   * @return The integer.
   * @throws IllegalArgumentException Thrown if the value is not such an integer.
   */
  public static int nonNegativeIntValue(String text, Member member) {
    final int start = member.valueStart();
    final int end = member.valueEnd();
    if (StringUtil.endOfAsciiDigits(text, start) != end) {
      throw new IllegalArgumentException("Value of \"" + member.key()
          + "\" must be a non-negative integer: " + text.substring(start, end));
    }
    try {
      return Integer.parseInt(text, start, end, 10);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Value of \"" + member.key()
          + "\" does not fit into an int: " + text.substring(start, end), e);
    }
  }

  /**
   * Finds the closing quote of a string literal, honoring backslash escapes: a backslash and
   * the character after it do not close the literal.
   *
   * @param text The JSON text.
   * @param openQuote The offset of the opening quote.
   * @return The offset of the first unescaped quote after {@code openQuote}, or {@code -1} if
   *     the literal is not closed.
   */
  static int closingQuote(String text, int openQuote) {
    int i = openQuote + 1;
    while (i < text.length()) {
      final char c = text.charAt(i);
      if (c == '"') {
        return i;
      }
      i += c == '\\' ? 2 : 1;
    }
    return -1;
  }

  /**
   * Replaces the escapes of a string literal with the characters they stand for.
   *
   * @param text The JSON text.
   * @param start The offset after the opening quote.
   * @param end The offset of the closing quote.
   * @return The decoded string.
   * @throws IllegalArgumentException Thrown if an escape is not one of the nine RFC 8259
   *     escapes, or a {@code \\u} escape is not followed by four hexadecimal digits.
   */
  static String unescape(String text, int start, int end) {
    final StringBuilder result = new StringBuilder(end - start);
    int i = start;
    while (i < end) {
      final char c = text.charAt(i);
      if (c != '\\') {
        result.append(c);
        i++;
        continue;
      }
      if (i + 1 >= end) {
        throw malformed(text, i, "a backslash must be followed by an escape character");
      }
      final char escaped = text.charAt(i + 1);
      switch (escaped) {
        case '"' -> result.append('"');
        case '\\' -> result.append('\\');
        case '/' -> result.append('/');
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case 'u' -> {
          if (i + 6 > end || !isHexDigits(text, i + 2, i + 6)) {
            throw malformed(text, i, "\\u must be followed by four hexadecimal digits");
          }
          result.append((char) Integer.parseInt(text, i + 2, i + 6, 16));
          i += 4;
        }
        default -> throw malformed(text, i, "unknown escape \\" + escaped);
      }
      i += 2;
    }
    return result.toString();
  }

  /**
   * Skips a colon that may be surrounded by whitespace.
   *
   * @param text The JSON text.
   * @param from The offset to start at.
   * @return The offset of the first non-whitespace character after the colon, which may be
   *     the length of the text, or {@code -1} if the first non-whitespace character at or
   *     after {@code from} is not a colon.
   */
  static int afterColon(String text, int from) {
    final int colon = skipWhitespace(text, from);
    if (colon >= text.length() || text.charAt(colon) != ':') {
      return -1;
    }
    return skipWhitespace(text, colon + 1);
  }

  /**
   * Skips JSON whitespace: space, tab, line feed, and carriage return. Other Unicode spaces
   * are content, not separators.
   *
   * @param text The JSON text.
   * @param from The offset to start at.
   * @return The offset of the first non-whitespace character at or after {@code from}, or the
   *     length of the text if only whitespace remains.
   */
  static int skipWhitespace(String text, int from) {
    int i = from;
    while (i < text.length() && isJsonWhitespace(text.charAt(i))) {
      i++;
    }
    return i;
  }

  /**
   * Finds the end of the value that starts at an offset: a string, a number, an object, an
   * array, or one of the literals {@code true}, {@code false}, and {@code null}.
   *
   * @param text The JSON text.
   * @param from The offset of the first character of the value.
   * @return The offset after the value.
   * @throws IllegalArgumentException Thrown if no well-formed value starts at {@code from}.
   */
  static int endOfValue(String text, int from) {
    if (from >= text.length()) {
      throw malformed(text, from, "expected a value");
    }
    final char c = text.charAt(from);
    if (c == '"') {
      final int close = closingQuote(text, from);
      if (close < 0) {
        throw malformed(text, from, "unterminated string");
      }
      return close + 1;
    }
    if (c == '{') {
      return endOfObject(text, from, null);
    }
    if (c == '[') {
      return endOfArray(text, from);
    }
    if (c == '-' || StringUtil.isAsciiDigit(c)) {
      return endOfNumber(text, from);
    }
    for (String literal : new String[] {TRUE, FALSE, NULL}) {
      if (text.startsWith(literal, from)) {
        return from + literal.length();
      }
    }
    throw malformed(text, from, "expected a value");
  }

  /**
   * Scans an object, collecting its members when a sink is given.
   *
   * @param text The JSON text.
   * @param brace The offset of the opening brace.
   * @param sink The list to add the members to, or {@code null} to only find the end.
   * @return The offset after the closing brace.
   * @throws IllegalArgumentException Thrown if the object is malformed.
   */
  private static int endOfObject(String text, int brace, List<Member> sink) {
    int i = skipWhitespace(text, brace + 1);
    if (i < text.length() && text.charAt(i) == '}') {
      return i + 1;
    }
    while (true) {
      expect(text, i, '"');
      final int keyEnd = closingQuote(text, i);
      if (keyEnd < 0) {
        throw malformed(text, i, "unterminated string");
      }
      final int valueStart = afterColon(text, keyEnd + 1);
      if (valueStart < 0) {
        throw malformed(text, skipWhitespace(text, keyEnd + 1), "expected ':'");
      }
      final int valueEnd = endOfValue(text, valueStart);
      if (sink != null) {
        sink.add(new Member(unescape(text, i + 1, keyEnd), valueStart, valueEnd));
      }
      i = skipWhitespace(text, valueEnd);
      if (i < text.length() && text.charAt(i) == '}') {
        return i + 1;
      }
      expect(text, i, ',');
      i = skipWhitespace(text, i + 1);
    }
  }

  /**
   * Scans an array.
   *
   * @param text The JSON text.
   * @param bracket The offset of the opening bracket.
   * @return The offset after the closing bracket.
   * @throws IllegalArgumentException Thrown if the array is malformed.
   */
  private static int endOfArray(String text, int bracket) {
    int i = skipWhitespace(text, bracket + 1);
    if (i < text.length() && text.charAt(i) == ']') {
      return i + 1;
    }
    while (true) {
      i = skipWhitespace(text, endOfValue(text, i));
      if (i < text.length() && text.charAt(i) == ']') {
        return i + 1;
      }
      expect(text, i, ',');
      i = skipWhitespace(text, i + 1);
    }
  }

  /**
   * Scans a number: an optional minus, an integer part without leading zeros, an optional
   * fraction, and an optional exponent.
   *
   * @param text The JSON text.
   * @param from The offset of the first character of the number.
   * @return The offset after the number.
   * @throws IllegalArgumentException Thrown if the number is malformed.
   */
  private static int endOfNumber(String text, int from) {
    int i = from;
    if (text.charAt(i) == '-') {
      i++;
    }
    if (i < text.length() && text.charAt(i) == '0') {
      i++;
    } else {
      final int digitsEnd = StringUtil.endOfAsciiDigits(text, i);
      if (digitsEnd == i) {
        throw malformed(text, i, "expected a digit");
      }
      i = digitsEnd;
    }
    if (i < text.length() && text.charAt(i) == '.') {
      final int fractionEnd = StringUtil.endOfAsciiDigits(text, i + 1);
      if (fractionEnd == i + 1) {
        throw malformed(text, i + 1, "expected a digit");
      }
      i = fractionEnd;
    }
    if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
      i++;
      if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
        i++;
      }
      final int exponentEnd = StringUtil.endOfAsciiDigits(text, i);
      if (exponentEnd == i) {
        throw malformed(text, i, "expected a digit");
      }
      i = exponentEnd;
    }
    return i;
  }

  /**
   * Checks for an expected character.
   *
   * @param text The JSON text.
   * @param at The offset.
   * @param expected The character that must be there.
   * @throws IllegalArgumentException Thrown if the offset is beyond the end or carries another
   *     character.
   */
  private static void expect(String text, int at, char expected) {
    if (at >= text.length() || text.charAt(at) != expected) {
      throw malformed(text, at, "expected '" + expected + "'");
    }
  }

  /**
   * Builds the exception for malformed text.
   *
   * @param text The JSON text.
   * @param at The offset reading stopped at.
   * @param reason What was expected there.
   * @return The exception to throw.
   */
  private static IllegalArgumentException malformed(String text, int at, String reason) {
    final String found = at >= text.length() ? "end of text"
        : "'" + text.substring(at, Math.min(text.length(), at + 20)) + "'";
    return new IllegalArgumentException(
        "Malformed JSON at offset " + at + ", " + reason + ", found " + found);
  }

  /**
   * Tests for JSON whitespace.
   *
   * @param c The character.
   * @return {@code true} for space, tab, line feed, or carriage return.
   */
  private static boolean isJsonWhitespace(char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  }

  /**
   * Tests whether a range consists of hexadecimal digits only.
   *
   * @param text The JSON text.
   * @param from The offset the range starts at (inclusive).
   * @param to The offset the range ends at (exclusive).
   * @return {@code true} if each character is {@code 0} to {@code 9}, {@code a} to {@code f},
   *     or {@code A} to {@code F}.
   */
  private static boolean isHexDigits(String text, int from, int to) {
    for (int i = from; i < to; i++) {
      final char c = text.charAt(i);
      if (!(StringUtil.isAsciiDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }
}
