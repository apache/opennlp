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

package opennlp.embeddings.corpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.InvalidFormatException;

/**
 * A minimal JSON reader and string writer for the corpus interchange files. Values parse
 * to {@link Map} (object), {@link List} (array), {@link String}, {@link Long} or
 * {@link Double} (number), {@link Boolean}, or {@code null}.
 */
final class Json {

  private static final String HEX_DIGITS = "0123456789abcdef";

  private final String text;
  private final String inputName;
  private int position;

  /**
   * Creates a parser for one named input.
   *
   * @param text The JSON text.
   * @param inputName The input name used in error messages.
   */
  private Json(String text, String inputName) {
    this.text = text;
    this.inputName = inputName;
  }

  /**
   * Parses one complete JSON value.
   *
   * @param text The JSON text. Must not be {@code null}.
   * @param inputName The input name used in error messages. Must not be {@code null}.
   * @return The parsed value. May be {@code null} for the JSON {@code null} literal.
   * @throws InvalidFormatException Thrown if the text is not a single well-formed JSON
   *         value.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  static Object parse(String text, String inputName) throws InvalidFormatException {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    if (inputName == null) {
      throw new IllegalArgumentException("inputName must not be null");
    }
    final Json parser = new Json(text, inputName);
    final Object value = parser.value();
    parser.skipWhitespace();
    if (parser.position < text.length()) {
      throw parser.malformed("Trailing content after the JSON value");
    }
    return value;
  }

  /**
   * Appends a JSON string literal, quoted and escaped, to a builder.
   *
   * @param out The target builder. Must not be {@code null}.
   * @param value The string value to encode. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   */
  static void appendString(StringBuilder out, String value) {
    if (out == null) {
      throw new IllegalArgumentException("out must not be null");
    }
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20 || Character.isSurrogate(c)) {
            appendUnicodeEscape(out, c);
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  /**
   * Appends one UTF-16 code unit as a JSON Unicode escape.
   *
   * @param out The target builder.
   * @param value The code unit.
   */
  private static void appendUnicodeEscape(StringBuilder out, char value) {
    out.append('\\').append('u')
        .append(HEX_DIGITS.charAt((value >>> 12) & 0x0f))
        .append(HEX_DIGITS.charAt((value >>> 8) & 0x0f))
        .append(HEX_DIGITS.charAt((value >>> 4) & 0x0f))
        .append(HEX_DIGITS.charAt(value & 0x0f));
  }

  /**
   * Parses the value at the current position.
   *
   * @return The parsed value.
   * @throws InvalidFormatException Thrown if the value is malformed.
   */
  private Object value() throws InvalidFormatException {
    skipWhitespace();
    final char c = peek();
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> number();
    };
  }

  /**
   * Parses an object at the current position.
   *
   * @return The parsed members in input order.
   * @throws InvalidFormatException Thrown if the object is malformed or repeats a
   *         member name.
   */
  private Map<String, Object> object() throws InvalidFormatException {
    expect('{');
    final Map<String, Object> object = new LinkedHashMap<>();
    skipWhitespace();
    if (peek() == '}') {
      position++;
      return object;
    }
    while (true) {
      skipWhitespace();
      final String key = string();
      skipWhitespace();
      expect(':');
      if (object.containsKey(key)) {
        throw malformed("Duplicate object member '" + key + "'");
      }
      object.put(key, value());
      skipWhitespace();
      final char next = consume();
      if (next == '}') {
        return object;
      }
      if (next != ',') {
        throw malformed("Expected ',' or '}' in object, got '" + next + "'");
      }
    }
  }

  /**
   * Parses an array at the current position.
   *
   * @return The parsed values in input order.
   * @throws InvalidFormatException Thrown if the array is malformed.
   */
  private List<Object> array() throws InvalidFormatException {
    expect('[');
    final List<Object> array = new ArrayList<>();
    skipWhitespace();
    if (peek() == ']') {
      position++;
      return array;
    }
    while (true) {
      array.add(value());
      skipWhitespace();
      final char next = consume();
      if (next == ']') {
        return array;
      }
      if (next != ',') {
        throw malformed("Expected ',' or ']' in array, got '" + next + "'");
      }
    }
  }

  /**
   * Parses a string at the current position.
   *
   * @return The decoded string.
   * @throws InvalidFormatException Thrown if the string or an escape is malformed.
   */
  private String string() throws InvalidFormatException {
    expect('"');
    final StringBuilder value = new StringBuilder();
    while (true) {
      final char c = consume();
      if (c == '"') {
        return value.toString();
      }
      if (c != '\\') {
        if (c < 0x20) {
          throw malformed("Unescaped control character in string");
        }
        value.append(c);
        continue;
      }
      final char escaped = consume();
      switch (escaped) {
        case '"', '\\', '/' -> value.append(escaped);
        case 'b' -> value.append('\b');
        case 'f' -> value.append('\f');
        case 'n' -> value.append('\n');
        case 'r' -> value.append('\r');
        case 't' -> value.append('\t');
        case 'u' -> {
          if (position + 4 > text.length()) {
            throw malformed("Truncated unicode escape");
          }
          final String hex = text.substring(position, position + 4);
          try {
            value.append((char) Integer.parseInt(hex, 16));
          } catch (NumberFormatException e) {
            throw malformed("Invalid unicode escape '\\u" + hex + "'");
          }
          position += 4;
        }
        default -> throw malformed("Invalid escape '\\" + escaped + "'");
      }
    }
  }

  /**
   * Parses a JSON number at the current position.
   *
   * @return A {@link Long} for an integer or a finite {@link Double} otherwise.
   * @throws InvalidFormatException Thrown if the number is malformed or cannot be
   *         represented by the return type.
   */
  private Object number() throws InvalidFormatException {
    final int start = position;
    if (peek() == '-') {
      position++;
    }
    if (position >= text.length()) {
      throw malformed("Invalid number '" + text.substring(start, position) + "'");
    }
    final char firstDigit = text.charAt(position);
    if (firstDigit == '0') {
      position++;
      if (position < text.length()) {
        final char next = text.charAt(position);
        if (next >= '0' && next <= '9') {
          throw malformed("Leading zero in number at position " + start);
        }
      }
    } else if (firstDigit >= '1' && firstDigit <= '9') {
      position++;
      while (position < text.length()) {
        final char digit = text.charAt(position);
        if (digit < '0' || digit > '9') {
          break;
        }
        position++;
      }
    } else {
      throw malformed("Invalid number at position " + start);
    }

    boolean integral = true;
    if (position < text.length() && text.charAt(position) == '.') {
      integral = false;
      position++;
      final int fractionStart = position;
      while (position < text.length()) {
        final char digit = text.charAt(position);
        if (digit < '0' || digit > '9') {
          break;
        }
        position++;
      }
      if (position == fractionStart) {
        throw malformed("Missing digit after decimal point at position " + start);
      }
    }
    if (position < text.length()
        && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
      integral = false;
      position++;
      if (position < text.length()
          && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
        position++;
      }
      final int exponentStart = position;
      while (position < text.length()) {
        final char digit = text.charAt(position);
        if (digit < '0' || digit > '9') {
          break;
        }
        position++;
      }
      if (position == exponentStart) {
        throw malformed("Missing exponent digit at position " + start);
      }
    }
    final String token = text.substring(start, position);
    try {
      if (integral) {
        return Long.parseLong(token);
      }
      final double value = Double.parseDouble(token);
      if (!Double.isFinite(value)) {
        throw malformed("Number is outside the supported range at position " + start);
      }
      return value;
    } catch (NumberFormatException e) {
      throw malformed("Invalid number '" + token + "'");
    }
  }

  /**
   * Parses a fixed literal at the current position.
   *
   * @param literal The expected source text.
   * @param value The value represented by the literal.
   * @return {@code value}.
   * @throws InvalidFormatException Thrown if the literal is absent.
   */
  private Object literal(String literal, Object value) throws InvalidFormatException {
    if (!text.startsWith(literal, position)) {
      throw malformed("Invalid literal at position " + position);
    }
    position += literal.length();
    return value;
  }

  /** Advances past JSON space, tab, carriage return, and line feed characters. */
  private void skipWhitespace() {
    while (position < text.length()) {
      final char c = text.charAt(position);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return;
      }
      position++;
    }
  }

  /**
   * Returns the current character without advancing.
   *
   * @return The current character.
   * @throws InvalidFormatException Thrown at the end of the input.
   */
  private char peek() throws InvalidFormatException {
    if (position >= text.length()) {
      throw malformed("Unexpected end of input");
    }
    return text.charAt(position);
  }

  /**
   * Returns the current character and advances one position.
   *
   * @return The consumed character.
   * @throws InvalidFormatException Thrown at the end of the input.
   */
  private char consume() throws InvalidFormatException {
    final char c = peek();
    position++;
    return c;
  }

  /**
   * Consumes one expected character.
   *
   * @param c The expected character.
   * @throws InvalidFormatException Thrown if another character occurs.
   */
  private void expect(char c) throws InvalidFormatException {
    final char actual = consume();
    if (actual != c) {
      throw malformed("Expected '" + c + "', got '" + actual + "'");
    }
  }

  /**
   * Creates a format exception that identifies the input.
   *
   * @param message The error description.
   * @return The exception.
   */
  private InvalidFormatException malformed(String message) {
    return new InvalidFormatException(message + " in " + inputName);
  }
}
