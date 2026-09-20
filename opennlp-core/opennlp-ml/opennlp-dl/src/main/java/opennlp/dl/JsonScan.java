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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.Internal;
import opennlp.tools.util.StringUtil;

/**
 * Reads the JSON files of the deep-learning components, vocabularies and model configurations,
 * in one pass over the text. Structure, whitespace, numbers, and string escapes follow
 * <a href="https://www.rfc-editor.org/rfc/rfc8259">RFC 8259</a>, with three additions: a byte
 * order mark as the first character is skipped, as
 * <a href="https://www.rfc-editor.org/rfc/rfc8259#section-8.1">section 8.1</a> allows; the
 * control characters {@code U+0000} to {@code U+001F} are accepted as content inside a string,
 * in keys as well as in values; and the values {@code NaN}, {@code Infinity}, and
 * {@code -Infinity}, which Python's {@code json} module writes by default, are accepted where a
 * value is skipped, never where one is read. Nesting is bounded by memory, not by the call
 * stack. Malformed text is reported as an {@link IllegalArgumentException} whose message names
 * the offset at which reading stopped.
 *
 * <p>{@link #stringObject(String, String)} is the only API of this class; its other members
 * serve the classes of this package.
 */
@Internal(since = "3.0.0")
public final class JsonScan {

  private static final String TRUE = "true";
  private static final String FALSE = "false";
  private static final String NULL = "null";
  private static final String NAN = "NaN";
  private static final String INFINITY = "Infinity";
  private static final String NEGATIVE_INFINITY = "-Infinity";
  private static final String[] LITERALS = {TRUE, FALSE, NULL, NAN, INFINITY, NEGATIVE_INFINITY};

  private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
  private static final char OBJECT_OPEN = '{';
  private static final char OBJECT_CLOSE = '}';
  private static final char ARRAY_OPEN = '[';
  private static final char ARRAY_CLOSE = ']';
  private static final char QUOTE = '"';
  private static final char BACKSLASH = '\\';
  private static final char UNICODE_ESCAPE = 'u';

  /** The escape characters other than {@code u} that may follow a backslash. */
  private static final String SIMPLE_ESCAPES = "\"\\/bfnrt";
  /** The length of a two-character escape such as {@code \n}. */
  private static final int ESCAPE_LENGTH = 2;
  /** The number of hexadecimal digits of a {@code \\u} escape. */
  private static final int UNICODE_ESCAPE_DIGITS = 4;
  /** The length of a {@code \\u} escape including the backslash and the {@code u}. */
  private static final int UNICODE_ESCAPE_LENGTH = ESCAPE_LENGTH + UNICODE_ESCAPE_DIGITS;
  /** The number of characters quoted in the message for malformed text. */
  private static final int MESSAGE_CONTEXT_LENGTH = 20;

  private static final String TEXT_MUST_NOT_BE_NULL = "text must not be null";

  private JsonScan() {
  }

  /**
   * A member of a JSON object.
   *
   * @param key The unescaped key. Must not be {@code null}.
   * @param valueStart The offset of the first character of the value, not negative.
   * @param valueEnd The offset after the last character of the value, not before
   *     {@code valueStart}.
   * @throws IllegalArgumentException Thrown if {@code key} is {@code null} or the offsets are
   *     not such a range.
   */
  record Member(String key, int valueStart, int valueEnd) {

    Member {
      if (key == null) {
        throw new IllegalArgumentException("key must not be null");
      }
      if (valueStart < 0 || valueEnd < valueStart) {
        throw new IllegalArgumentException(
            "value range must be non-negative and ordered: " + valueStart + ".." + valueEnd);
      }
    }
  }

  /**
   * Reads a top-level member whose value is an object of strings, such as the {@code id2label}
   * map of a model configuration. Keys and values are decoded from their escapes. A later entry
   * for the same key replaces an earlier one, and a later member with the same name replaces
   * an earlier one. A member with that name inside a nested value is not considered.
   *
   * @param text The JSON text: one object, or blank text (JSON whitespace only, after an
   *     optional byte order mark), which is read as an empty object. Must not be {@code null}.
   * @param key The unescaped name of the member. Must not be {@code null}.
   * @return The entries of that object, or an empty map if there is no such top-level member.
   * @throws IllegalArgumentException Thrown if {@code text} or {@code key} is {@code null},
   *     if {@code text} is neither blank nor a single well-formed JSON object, or if the member
   *     is not an object whose values are all strings. The message names the offset or the key.
   */
  public static Map<String, String> stringObject(String text, String key) {
    requireText(text);
    if (key == null) {
      throw new IllegalArgumentException("key must not be null");
    }
    final Map<String, String> entries = new HashMap<>();
    if (isBlank(text)) {
      return entries;
    }
    final Member object = member(document(text), key);
    if (object == null) {
      return entries;
    }
    if (!isObject(text, object)) {
      throw new IllegalArgumentException("\"" + key + "\" must be an object: "
          + text.substring(object.valueStart(), object.valueEnd()));
    }
    for (Member entry : members(text, object.valueStart())) {
      entries.put(entry.key(), stringValue(text, entry));
    }
    return entries;
  }

  /**
   * Reads the members of the single object that is the document.
   *
   * @param text The JSON text. Must not be {@code null}.
   * @return The members in document order, an empty list for an empty object.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}, does not
   *     consist of one object, or is malformed at any position.
   */
  static List<Member> document(String text) {
    requireText(text);
    final int start = skipWhitespace(text, afterByteOrderMark(text));
    expect(text, start, OBJECT_OPEN);
    final List<Member> members = new ArrayList<>();
    final int end = endOfContainer(text, start, members);
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
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}, {@code brace} is
   *     not the offset of an opening brace inside the text, or the object is malformed.
   */
  static List<Member> members(String text, int brace) {
    requireText(text);
    if (brace < 0 || brace >= text.length()) {
      throw new IllegalArgumentException(
          "brace must be an offset inside the text, not " + brace);
    }
    expect(text, brace, OBJECT_OPEN);
    final List<Member> members = new ArrayList<>();
    endOfContainer(text, brace, members);
    return Collections.unmodifiableList(members);
  }

  /**
   * Finds the member with a key in a list of members. A later member with the same key
   * counts, as with a map that is filled in document order.
   *
   * @param members The members. Must not be {@code null}.
   * @param key The unescaped key. Must not be {@code null}.
   * @return The last member with that key, or {@code null} if there is none.
   * @throws IllegalArgumentException Thrown if {@code members} or {@code key} is {@code null}.
   */
  static Member member(List<Member> members, String key) {
    if (members == null) {
      throw new IllegalArgumentException("members must not be null");
    }
    if (key == null) {
      throw new IllegalArgumentException("key must not be null");
    }
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
   * @param text The JSON text. Must not be {@code null}.
   * @param member The member, inside the text. Must not be {@code null}.
   * @return {@code true} if the value starts with an opening brace.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or the member lies
   *     outside the text.
   */
  static boolean isObject(String text, Member member) {
    requireMember(text, member);
    return text.charAt(member.valueStart()) == OBJECT_OPEN;
  }

  /**
   * Reads the value of a member as a string.
   *
   * @param text The JSON text. Must not be {@code null}.
   * @param member The member, inside the text. Must not be {@code null}.
   * @return The unescaped string.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, the member lies
   *     outside the text, or the value is not a string literal.
   */
  static String stringValue(String text, Member member) {
    requireMember(text, member);
    if (text.charAt(member.valueStart()) != QUOTE) {
      throw new IllegalArgumentException("Value of \"" + member.key() + "\" must be a string: "
          + text.substring(member.valueStart(), member.valueEnd()));
    }
    return unescape(text, member.valueStart() + 1, member.valueEnd() - 1);
  }

  /**
   * Reads the value of a member as a non-negative integer: a run of ASCII digits with no sign,
   * fraction, or exponent that fits into an {@code int}.
   *
   * @param text The JSON text. Must not be {@code null}.
   * @param member The member, inside the text. Must not be {@code null}.
   * @return The integer.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, the member lies
   *     outside the text, or the value is not such an integer.
   */
  static int nonNegativeIntValue(String text, Member member) {
    requireMember(text, member);
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
   * Tests whether the text holds nothing but JSON whitespace after an optional byte order mark.
   *
   * @param text The JSON text.
   * @return {@code true} if there is no content.
   */
  private static boolean isBlank(String text) {
    return skipWhitespace(text, afterByteOrderMark(text)) == text.length();
  }

  /**
   * Skips a byte order mark at the start of the text.
   *
   * @param text The JSON text.
   * @return {@code 1} if the text starts with a byte order mark, {@code 0} otherwise.
   */
  private static int afterByteOrderMark(String text) {
    return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? 1 : 0;
  }

  /**
   * Finds the closing quote of a string literal, checking each escape on the way: a backslash
   * must be followed by one of the escape characters of RFC 8259, and {@code \\u} by four
   * hexadecimal digits.
   *
   * @param text The JSON text.
   * @param openQuote The offset of the opening quote.
   * @return The offset of the closing quote.
   * @throws IllegalArgumentException Thrown if the literal is not closed or holds a bad escape.
   */
  private static int closingQuote(String text, int openQuote) {
    int i = openQuote + 1;
    while (i < text.length()) {
      final char c = text.charAt(i);
      if (c == QUOTE) {
        return i;
      }
      if (c != BACKSLASH) {
        i++;
      } else if (i + 1 >= text.length()) {
        break;
      } else {
        i = afterEscape(text, i);
      }
    }
    throw malformed(text, openQuote, "unterminated string");
  }

  /**
   * Checks the escape that starts at a backslash.
   *
   * @param text The JSON text.
   * @param backslash The offset of the backslash, which is not the last character.
   * @return The offset after the escape.
   * @throws IllegalArgumentException Thrown if the escape is not one of RFC 8259.
   */
  private static int afterEscape(String text, int backslash) {
    final char escaped = text.charAt(backslash + 1);
    if (escaped == UNICODE_ESCAPE) {
      final int end = backslash + UNICODE_ESCAPE_LENGTH;
      if (end > text.length() || !isHexDigits(text, backslash + ESCAPE_LENGTH, end)) {
        throw malformed(text, backslash, "\\u must be followed by four hexadecimal digits");
      }
      return end;
    }
    if (SIMPLE_ESCAPES.indexOf(escaped) < 0) {
      throw malformed(text, backslash, "unknown escape \\" + escaped);
    }
    return backslash + ESCAPE_LENGTH;
  }

  /**
   * Replaces the escapes of a string literal with the characters they stand for.
   *
   * @param text The JSON text.
   * @param start The offset after the opening quote.
   * @param end The offset of the closing quote.
   * @return The decoded string.
   * @throws IllegalArgumentException Thrown if an escape is not one of RFC 8259.
   */
  private static String unescape(String text, int start, int end) {
    final int firstBackslash = text.indexOf(BACKSLASH, start, end);
    if (firstBackslash < 0 || firstBackslash >= end) {
      return text.substring(start, end);
    }
    final StringBuilder result = new StringBuilder(end - start);
    result.append(text, start, firstBackslash);
    int i = firstBackslash;
    while (i < end) {
      final char c = text.charAt(i);
      if (c != BACKSLASH) {
        result.append(c);
        i++;
        continue;
      }
      if (i + 1 >= end) {
        throw malformed(text, i, "a backslash must be followed by an escape character");
      }
      final char escaped = text.charAt(i + 1);
      switch (escaped) {
        case QUOTE -> result.append(QUOTE);
        case BACKSLASH -> result.append(BACKSLASH);
        case '/' -> result.append('/');
        case 'b' -> result.append('\b');
        case 'f' -> result.append('\f');
        case 'n' -> result.append('\n');
        case 'r' -> result.append('\r');
        case 't' -> result.append('\t');
        case UNICODE_ESCAPE -> {
          final int escapeEnd = i + UNICODE_ESCAPE_LENGTH;
          if (escapeEnd > end || !isHexDigits(text, i + ESCAPE_LENGTH, escapeEnd)) {
            throw malformed(text, i, "\\u must be followed by four hexadecimal digits");
          }
          result.append((char) HexFormat.fromHexDigits(text, i + ESCAPE_LENGTH, escapeEnd));
          i += UNICODE_ESCAPE_DIGITS;
        }
        default -> throw malformed(text, i, "unknown escape \\" + escaped);
      }
      i += ESCAPE_LENGTH;
    }
    return result.toString();
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
  private static int skipWhitespace(String text, int from) {
    int i = from;
    while (i < text.length() && isJsonWhitespace(text.charAt(i))) {
      i++;
    }
    return i;
  }

  /**
   * Scans an object or an array, and every value nested in it, without recursion. When a sink
   * is given, the container is an object and its direct members are added to the sink.
   *
   * @param text The JSON text.
   * @param open The offset of the opening brace or bracket.
   * @param sink The list to add the members of the object to, or {@code null} to only find
   *     the end.
   * @return The offset after the closing brace or bracket.
   * @throws IllegalArgumentException Thrown if the container or a value in it is malformed.
   */
  private static int endOfContainer(String text, int open, List<Member> sink) {
    final Deque<Boolean> objects = new ArrayDeque<>();
    String key = null;
    int valueStart = 0;
    int i = open;
    boolean enter = true;
    boolean elementExpected = false;
    while (true) {
      if (enter) {
        enter = false;
        objects.push(text.charAt(i) == OBJECT_OPEN);
        i = skipWhitespace(text, i + 1);
        elementExpected = !isCloser(text, i, objects.peek());
        if (elementExpected) {
          continue;
        }
        i++;
        objects.pop();
        if (objects.isEmpty()) {
          return i;
        }
      } else if (elementExpected) {
        if (objects.peek()) {
          expect(text, i, QUOTE);
          final int keyEnd = closingQuote(text, i);
          if (sink != null && objects.size() == 1) {
            key = unescape(text, i + 1, keyEnd);
          }
          final int colon = skipWhitespace(text, keyEnd + 1);
          expect(text, colon, ':');
          i = skipWhitespace(text, colon + 1);
        }
        if (sink != null && objects.size() == 1) {
          valueStart = i;
        }
        if (i < text.length() && (text.charAt(i) == OBJECT_OPEN || text.charAt(i) == ARRAY_OPEN)) {
          enter = true;
          continue;
        }
        i = endOfScalar(text, i);
        elementExpected = false;
      } else {
        if (sink != null && objects.size() == 1) {
          sink.add(new Member(key, valueStart, i));
        }
        i = skipWhitespace(text, i);
        if (isCloser(text, i, objects.peek())) {
          i++;
          objects.pop();
          if (objects.isEmpty()) {
            return i;
          }
          continue;
        }
        expect(text, i, ',');
        i = skipWhitespace(text, i + 1);
        elementExpected = true;
      }
    }
  }

  /**
   * Tests whether the character at an offset closes the innermost container.
   *
   * @param text The JSON text.
   * @param at The offset.
   * @param object {@code true} if the innermost container is an object, {@code false} for an
   *     array.
   * @return {@code true} if the offset carries the matching closing brace or bracket.
   */
  private static boolean isCloser(String text, int at, boolean object) {
    return at < text.length() && text.charAt(at) == (object ? OBJECT_CLOSE : ARRAY_CLOSE);
  }

  /**
   * Finds the end of the scalar value that starts at an offset: a string, a number, or one of
   * the literals {@code true}, {@code false}, {@code null}, {@code NaN}, {@code Infinity}, and
   * {@code -Infinity}.
   *
   * @param text The JSON text.
   * @param from The offset of the first character of the value.
   * @return The offset after the value.
   * @throws IllegalArgumentException Thrown if no well-formed scalar starts at {@code from}.
   */
  private static int endOfScalar(String text, int from) {
    if (from >= text.length()) {
      throw malformed(text, from, "expected a value");
    }
    final char c = text.charAt(from);
    if (c == QUOTE) {
      return closingQuote(text, from) + 1;
    }
    for (String literal : LITERALS) {
      if (text.startsWith(literal, from)) {
        return from + literal.length();
      }
    }
    if (c == '-' || StringUtil.isAsciiDigit(c)) {
      return endOfNumber(text, from);
    }
    throw malformed(text, from, "expected a value");
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
        : "'" + text.substring(at, Math.min(text.length(), at + MESSAGE_CONTEXT_LENGTH)) + "'";
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
   * @return {@code true} if each character is a hexadecimal digit of either case.
   */
  private static boolean isHexDigits(String text, int from, int to) {
    for (int i = from; i < to; i++) {
      if (!HexFormat.isHexDigit(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks the text argument.
   *
   * @param text The JSON text.
   * @throws IllegalArgumentException Thrown if it is {@code null}.
   */
  private static void requireText(String text) {
    if (text == null) {
      throw new IllegalArgumentException(TEXT_MUST_NOT_BE_NULL);
    }
  }

  /**
   * Checks a member against the text it must lie in.
   *
   * @param text The JSON text.
   * @param member The member.
   * @throws IllegalArgumentException Thrown if either is {@code null} or the member's value
   *     range reaches beyond the text.
   */
  private static void requireMember(String text, Member member) {
    requireText(text);
    if (member == null) {
      throw new IllegalArgumentException("member must not be null");
    }
    if (member.valueEnd() > text.length() || member.valueStart() >= text.length()) {
      throw new IllegalArgumentException("member must lie inside the text: " + member);
    }
  }
}
