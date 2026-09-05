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
package opennlp.embeddings;

import opennlp.tools.util.InvalidFormatException;

/**
 * Cursor shared by the small JSON readers in this package. It parses scalar values and can skip
 * one value of any type. Each reader handles its expected input structure. Malformed input raises
 * an {@link InvalidFormatException} that includes the input name and offset.
 */
final class JsonCursor {

  private static final int MAX_NESTING_DEPTH = 128;

  private final String text;
  private final String inputName;
  private int position;

  /**
   * Creates a cursor positioned at the start of the given JSON text.
   *
   * @param text      The JSON text to scan. Must not be {@code null}.
   * @param inputName What the text is (for error messages), e.g. {@code "safetensors header"}
   *                  or a file name.
   */
  JsonCursor(String text, String inputName) {
    this.text = text;
    this.inputName = inputName;
  }

  /** Advances the cursor past any run of whitespace. */
  void skipWhitespace() {
    while (position < text.length()) {
      final char c = text.charAt(position);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return;
      }
      position++;
    }
  }

  /** {@return the cursor's current offset into the text, for readers that capture raw spans} */
  int position() {
    return position;
  }

  /**
   * {@return the character at the cursor without advancing}
   *
   * @throws InvalidFormatException Thrown if the cursor is at the end of the input.
   */
  char peek() throws InvalidFormatException {
    if (position >= text.length()) {
      throw malformed("Unexpected end of input");
    }
    return text.charAt(position);
  }

  /**
   * {@return the character at the cursor, advancing past it}
   *
   * @throws InvalidFormatException Thrown if the cursor is at the end of the input.
   */
  char consume() throws InvalidFormatException {
    final char c = peek();
    position++;
    return c;
  }

  /**
   * Consumes the next character, requiring it to be {@code c}.
   *
   * @param c The expected character.
   * @throws InvalidFormatException Thrown if the next character is not {@code c}.
   */
  void expect(char c) throws InvalidFormatException {
    final char actual = consume();
    if (actual != c) {
      throw malformed("Expected '" + c + "', got '" + actual + "'");
    }
  }

  /**
   * Consumes the given literal (for example {@code "true"}) when it starts at the cursor,
   * leaving the cursor untouched when it does not.
   *
   * @param literal The literal to match.
   * @return {@code true} when the literal was consumed.
   */
  boolean consumeLiteral(String literal) {
    if (text.startsWith(literal, position)) {
      position += literal.length();
      return true;
    }
    return false;
  }

  /**
   * Requires the rest of the input to be whitespace only.
   *
   * @param message What to report when other content follows.
   * @throws InvalidFormatException Thrown if non-whitespace content follows the cursor.
   */
  void requireEnd(String message) throws InvalidFormatException {
    skipWhitespace();
    if (position < text.length()) {
      throw malformed(message);
    }
  }

  /**
   * {@return the JSON string starting at the cursor, with escapes decoded}
   *
   * @throws InvalidFormatException Thrown if the string is unterminated or has a bad escape.
   */
  String parseString() throws InvalidFormatException {
    expect('"');
    final StringBuilder value = new StringBuilder();
    while (true) {
      if (position >= text.length()) {
        throw malformed("Unterminated string");
      }
      final char c = text.charAt(position++);
      if (c == '"') {
        return value.toString();
      }
      if (c == '\\') {
        value.append(parseEscape());
      } else if (c <= 0x1F) {
        throw malformed("Unescaped control character in a string");
      } else {
        value.append(c);
      }
    }
  }

  /** {@return the character named by the escape sequence following a backslash} */
  private char parseEscape() throws InvalidFormatException {
    if (position >= text.length()) {
      throw malformed("Unterminated escape sequence");
    }
    final char escape = text.charAt(position++);
    return switch (escape) {
      case '"' -> '"';
      case '\\' -> '\\';
      case '/' -> '/';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> parseUnicodeEscape();
      default -> throw malformed("Unknown escape sequence: \\" + escape);
    };
  }

  /** {@return the character named by a {@code \\uXXXX} escape} */
  private char parseUnicodeEscape() throws InvalidFormatException {
    if (position + 4 > text.length()) {
      throw malformed("Truncated \\u escape sequence");
    }
    final String hex = text.substring(position, position + 4);
    position += 4;
    // JSON escape digits are limited to the ASCII hexadecimal characters.
    int value = 0;
    for (int i = 0; i < 4; i++) {
      final int digit = hexadecimalValue(hex.charAt(i));
      if (digit < 0) {
        throw malformed("Malformed \\u escape sequence: " + hex);
      }
      value = (value << 4) | digit;
    }
    return (char) value;
  }

  /** {@return the value of an ASCII hexadecimal digit, or {@code -1} for another character} */
  private int hexadecimalValue(char c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return -1;
  }

  /**
   * Skips one JSON number, holding it to the grammar (optional minus, digits, optional fraction,
   * optional signed exponent). This validation also applies to skipped fields.
   */
  private void skipNumber() throws InvalidFormatException {
    skipIntegerPart();
    if (position < text.length() && text.charAt(position) == '.') {
      position++;
      if (position >= text.length() || !isAsciiDigit(text.charAt(position))) {
        throw malformed("Malformed number: digit expected after the decimal point");
      }
      while (position < text.length() && isAsciiDigit(text.charAt(position))) {
        position++;
      }
    }
    if (position < text.length()
        && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
      position++;
      if (position < text.length()
          && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
        position++;
      }
      if (position >= text.length() || !isAsciiDigit(text.charAt(position))) {
        throw malformed("Malformed number: digit expected in the exponent");
      }
      while (position < text.length() && isAsciiDigit(text.charAt(position))) {
        position++;
      }
    }
  }

  /**
   * Skips the optional sign and integer part of a JSON number.
   *
   * @throws InvalidFormatException Thrown if the integer part is absent or has a leading zero.
   */
  private void skipIntegerPart() throws InvalidFormatException {
    if (peek() == '-') {
      position++;
    }
    if (position >= text.length() || !isAsciiDigit(text.charAt(position))) {
      throw malformed("Malformed number");
    }
    if (text.charAt(position) == '0') {
      position++;
      if (position < text.length() && isAsciiDigit(text.charAt(position))) {
        throw malformed("Malformed number: leading zeros are not allowed");
      }
      return;
    }
    while (position < text.length() && isAsciiDigit(text.charAt(position))) {
      position++;
    }
  }

  /** {@return whether {@code c} is an ASCII decimal digit} */
  private boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /**
   * {@return the integer starting at the cursor, parsed as a {@code long}}
   *
   * @throws InvalidFormatException Thrown if no integer is present or it overflows a long.
   */
  long parseLong() throws InvalidFormatException {
    final int start = position;
    skipIntegerPart();
    try {
      return Long.parseLong(text.substring(start, position));
    } catch (NumberFormatException e) {
      throw malformed("Malformed integer: " + text.substring(start, position));
    }
  }

  /**
   * {@return the finite JSON number starting at the cursor, parsed as a {@code double}}
   *
   * @throws InvalidFormatException Thrown if no JSON number is present or its value is not
   *     finite.
   */
  double parseDouble() throws InvalidFormatException {
    final int start = position;
    skipNumber();
    final String number = text.substring(start, position);
    try {
      final double value = Double.parseDouble(number);
      if (!Double.isFinite(value)) {
        throw malformed("Number is not finite: " + number);
      }
      return value;
    } catch (NumberFormatException e) {
      throw malformed("Malformed number: " + number);
    }
  }

  /**
   * {@return the JSON boolean starting at the cursor}
   *
   * @throws InvalidFormatException Thrown if the next value is not {@code true} or
   *     {@code false}.
   */
  boolean parseBoolean() throws InvalidFormatException {
    if (consumeLiteral("true")) {
      return true;
    }
    if (consumeLiteral("false")) {
      return false;
    }
    throw malformed("Expected a boolean");
  }

  /**
   * Skips one JSON value of any type (string, number, array, object, true/false/null), allowing a
   * reader to ignore unknown fields.
   */
  void skipValue() throws InvalidFormatException {
    skipValue(0);
  }

  /**
   * Skips one JSON value at the given container depth.
   *
   * @param depth The number of enclosing arrays and objects.
   * @throws InvalidFormatException Thrown if the value is malformed or nested too deeply.
   */
  private void skipValue(int depth) throws InvalidFormatException {
    skipWhitespace();
    final char c = peek();
    if (c == '"') {
      parseString();
    } else if (c == '[') {
      requireContainerDepth(depth);
      position++;
      skipWhitespace();
      if (peek() != ']') {
        while (true) {
          skipValue(depth + 1);
          skipWhitespace();
          final char next = consume();
          if (next == ',') {
            skipWhitespace();
            continue;
          }
          if (next == ']') {
            return;
          }
          throw malformed("Expected ',' or ']' while skipping an array, got '" + next + "'");
        }
      }
      position++;
    } else if (c == '{') {
      requireContainerDepth(depth);
      position++;
      skipWhitespace();
      if (peek() != '}') {
        while (true) {
          skipWhitespace();
          parseString();
          skipWhitespace();
          expect(':');
          skipValue(depth + 1);
          skipWhitespace();
          final char next = consume();
          if (next == ',') {
            continue;
          }
          if (next == '}') {
            return;
          }
          throw malformed("Expected ',' or '}' while skipping an object, got '" + next + "'");
        }
      }
      position++;
    } else if (c == '-' || Character.isDigit(c)) {
      skipNumber();
    } else if (consumeLiteral("true") || consumeLiteral("false") || consumeLiteral("null")) {
      // consumed, nothing to record
    } else {
      throw malformed("Unexpected character while skipping a value: '" + c + "'");
    }
  }

  /**
   * Rejects a container whose contents would exceed the nesting limit.
   *
   * @param depth The number of enclosing arrays and objects.
   * @throws InvalidFormatException Thrown at the nesting limit.
   */
  private void requireContainerDepth(int depth) throws InvalidFormatException {
    if (depth >= MAX_NESTING_DEPTH) {
      throw malformed("JSON nesting depth exceeds " + MAX_NESTING_DEPTH);
    }
  }

  /**
   * {@return an exception naming the input and the cursor offset}
   *
   * @param message What was wrong at the cursor.
   */
  InvalidFormatException malformed(String message) {
    return new InvalidFormatException(
        "Malformed " + inputName + " at offset " + position + ": " + message);
  }
}
