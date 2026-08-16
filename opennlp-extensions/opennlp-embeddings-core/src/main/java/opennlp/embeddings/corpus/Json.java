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

  private final String text;
  private final String inputName;
  private int position;

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
   */
  static void appendString(StringBuilder out, String value) {
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
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

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

  private String string() throws InvalidFormatException {
    expect('"');
    final StringBuilder value = new StringBuilder();
    while (true) {
      final char c = consume();
      if (c == '"') {
        return value.toString();
      }
      if (c != '\\') {
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

  private Object number() throws InvalidFormatException {
    final int start = position;
    if (peek() == '-') {
      position++;
    }
    boolean integral = true;
    while (position < text.length()) {
      final char c = text.charAt(position);
      if (c >= '0' && c <= '9') {
        position++;
      } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
        integral = false;
        position++;
      } else {
        break;
      }
    }
    final String token = text.substring(start, position);
    try {
      // Deliberately not a ternary: mixed Long/Double operands would promote the
      // integral branch to double and box every whole number as Double.
      if (integral) {
        return Long.parseLong(token);
      }
      return Double.parseDouble(token);
    } catch (NumberFormatException e) {
      throw malformed("Invalid number '" + token + "'");
    }
  }

  private Object literal(String literal, Object value) throws InvalidFormatException {
    if (!text.startsWith(literal, position)) {
      throw malformed("Invalid literal at position " + position);
    }
    position += literal.length();
    return value;
  }

  private void skipWhitespace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  private char peek() throws InvalidFormatException {
    if (position >= text.length()) {
      throw malformed("Unexpected end of input");
    }
    return text.charAt(position);
  }

  private char consume() throws InvalidFormatException {
    final char c = peek();
    position++;
    return c;
  }

  private void expect(char c) throws InvalidFormatException {
    final char actual = consume();
    if (actual != c) {
      throw malformed("Expected '" + c + "', got '" + actual + "'");
    }
  }

  private InvalidFormatException malformed(String message) {
    return new InvalidFormatException(message + " in " + inputName);
  }
}
