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

/**
 * Cursor primitives shared by this package's purpose-built JSON readers
 * ({@link SafetensorsHeaderParser}, {@link FlatJsonFields}): string and integer scalars,
 * literals, whitespace, and skipping one value of any type. Deliberately not a general JSON
 * library: no floating-point decoding, no document model; each reader drives the cursor over
 * its own known-shape input and fails loud on anything else, with the input's name and the
 * offending offset in every message.
 */
final class JsonCursor {

  private final String text;
  private final String inputName;
  private int position;

  /**
   * @param text      The JSON text to scan. Must not be {@code null}.
   * @param inputName What the text is (for error messages), e.g. {@code "safetensors header"}
   *                  or a file name.
   */
  JsonCursor(String text, String inputName) {
    this.text = text;
    this.inputName = inputName;
  }

  void skipWhitespace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  char peek() {
    if (position >= text.length()) {
      throw malformed("Unexpected end of input");
    }
    return text.charAt(position);
  }

  char consume() {
    final char c = peek();
    position++;
    return c;
  }

  void expect(char c) {
    final char actual = consume();
    if (actual != c) {
      throw malformed("Expected '" + c + "', got '" + actual + "'");
    }
  }

  /** Consumes the given literal (e.g. {@code "true"}) if it starts here; returns whether. */
  boolean consumeLiteral(String literal) {
    if (text.startsWith(literal, position)) {
      position += literal.length();
      return true;
    }
    return false;
  }

  /** Requires the rest of the input to be whitespace only. */
  void requireEnd(String message) {
    skipWhitespace();
    if (position < text.length()) {
      throw malformed(message);
    }
  }

  String parseString() {
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
      } else {
        value.append(c);
      }
    }
  }

  private char parseEscape() {
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

  private char parseUnicodeEscape() {
    if (position + 4 > text.length()) {
      throw malformed("Truncated \\u escape sequence");
    }
    final String hex = text.substring(position, position + 4);
    position += 4;
    // Each of the four characters must be a hex digit; Integer.parseInt alone would also
    // accept a sign and silently decode the wrong character.
    int value = 0;
    for (int i = 0; i < 4; i++) {
      final int digit = Character.digit(hex.charAt(i), 16);
      if (digit < 0) {
        throw malformed("Malformed \\u escape sequence: " + hex);
      }
      value = (value << 4) | digit;
    }
    return (char) value;
  }

  // Skips one number, holding it to the JSON grammar (optional minus, digits, optional
  // fraction, optional signed exponent) so malformed input fails loud even in skipped fields.
  private void skipNumber() {
    if (peek() == '-') {
      position++;
    }
    if (position >= text.length() || !Character.isDigit(text.charAt(position))) {
      throw malformed("Malformed number");
    }
    while (position < text.length() && Character.isDigit(text.charAt(position))) {
      position++;
    }
    if (position < text.length() && text.charAt(position) == '.') {
      position++;
      if (position >= text.length() || !Character.isDigit(text.charAt(position))) {
        throw malformed("Malformed number: digit expected after the decimal point");
      }
      while (position < text.length() && Character.isDigit(text.charAt(position))) {
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
      if (position >= text.length() || !Character.isDigit(text.charAt(position))) {
        throw malformed("Malformed number: digit expected in the exponent");
      }
      while (position < text.length() && Character.isDigit(text.charAt(position))) {
        position++;
      }
    }
  }

  long parseLong() {
    final int start = position;
    if (peek() == '-') {
      position++;
    }
    if (position >= text.length() || !Character.isDigit(text.charAt(position))) {
      throw malformed("Expected an integer");
    }
    while (position < text.length() && Character.isDigit(text.charAt(position))) {
      position++;
    }
    try {
      return Long.parseLong(text.substring(start, position));
    } catch (NumberFormatException e) {
      throw malformed("Malformed integer: " + text.substring(start, position));
    }
  }

  // Skips one JSON value of any type (string, number, array, object, true/false/null); used
  // for fields a reader does not care about, so unknown additions never break it.
  void skipValue() {
    skipWhitespace();
    final char c = peek();
    if (c == '"') {
      parseString();
    } else if (c == '[') {
      position++;
      skipWhitespace();
      if (peek() != ']') {
        while (true) {
          skipValue();
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
      position++;
      skipWhitespace();
      if (peek() != '}') {
        while (true) {
          skipWhitespace();
          parseString();
          skipWhitespace();
          expect(':');
          skipValue();
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

  IllegalArgumentException malformed(String message) {
    return new IllegalArgumentException(
        "Malformed " + inputName + " at offset " + position + ": " + message);
  }
}
