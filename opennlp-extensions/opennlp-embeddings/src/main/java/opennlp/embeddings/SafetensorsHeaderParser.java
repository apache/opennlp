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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A cursor parser for the JSON header of a safetensors file. Purpose-built for the header's
 * fixed, shallow shape (a flat object of tensor name to a {@code dtype}/{@code shape}/
 * {@code data_offsets} record, plus an optional {@code __metadata__} string map), not a
 * general-purpose JSON parser: no floating-point numbers, no arbitrary nesting depth, no
 * comments. This is the same discipline used by every other data-file cursor parser in the
 * project (no regular expressions, fail loud on malformed input).
 */
final class SafetensorsHeaderParser {

  private static final String METADATA_KEY = "__metadata__";

  private final String text;
  private int position;

  private SafetensorsHeaderParser(String text) {
    this.text = text;
  }

  /**
   * Parses a safetensors header.
   *
   * @param headerJson The header's JSON text, decoded from the file's header bytes. Must not be
   *                   {@code null}.
   * @return The parse result: the declared tensors, in header order, and the
   *     {@code __metadata__} string map (empty when the header has none).
   * @throws IllegalArgumentException Thrown if {@code headerJson} is {@code null} or malformed.
   */
  static Result parse(String headerJson) {
    if (headerJson == null) {
      throw new IllegalArgumentException("HeaderJson must not be null");
    }
    final SafetensorsHeaderParser parser = new SafetensorsHeaderParser(headerJson);
    return parser.parseTop();
  }

  private Result parseTop() {
    final List<TensorInfo> tensors = new ArrayList<>();
    Map<String, String> metadata = Map.of();
    skipWhitespace();
    expect('{');
    skipWhitespace();
    if (peek() == '}') {
      position++;
      requireEnd();
      return new Result(tensors, metadata);
    }
    while (true) {
      skipWhitespace();
      final String key = parseString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      if (METADATA_KEY.equals(key)) {
        metadata = parseStringMap();
      }
      else {
        tensors.add(parseTensorInfo(key));
      }
      skipWhitespace();
      final char next = consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        break;
      }
      throw malformed("Expected ',' or '}' after a header entry, got '" + next + "'");
    }
    requireEnd();
    return new Result(tensors, metadata);
  }

  // Trailing whitespace is legal (writers space-pad the header to align the data section), but
  // any other trailing content means the declared header length and the JSON disagree.
  private void requireEnd() {
    skipWhitespace();
    if (position < text.length()) {
      throw malformed("Trailing content after the header object");
    }
  }

  private TensorInfo parseTensorInfo(String name) {
    expect('{');
    String dtype = null;
    int[] shape = null;
    long dataOffsetBegin = -1;
    long dataOffsetEnd = -1;
    skipWhitespace();
    while (peek() != '}') {
      skipWhitespace();
      final String field = parseString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      switch (field) {
        case "dtype" -> dtype = parseString();
        case "shape" -> shape = parseIntArray();
        case "data_offsets" -> {
          final long[] offsets = parseLongArray();
          if (offsets.length != 2) {
            throw malformed("Tensor '" + name + "' data_offsets must have exactly 2 elements, "
                + "got " + offsets.length);
          }
          dataOffsetBegin = offsets[0];
          dataOffsetEnd = offsets[1];
        }
        default -> skipValue();
      }
      skipWhitespace();
      final char next = consume();
      if (next == ',') {
        skipWhitespace();
        continue;
      }
      if (next == '}') {
        if (dtype == null || shape == null || dataOffsetBegin < 0) {
          throw malformed("Tensor '" + name
              + "' is missing dtype, shape, or data_offsets");
        }
        return new TensorInfo(name, dtype, shape, dataOffsetBegin, dataOffsetEnd);
      }
      throw malformed("Expected ',' or '}' in tensor '" + name + "', got '" + next + "'");
    }
    throw malformed("Tensor '" + name + "' has an empty object; missing dtype, shape, "
        + "and data_offsets");
  }

  private Map<String, String> parseStringMap() {
    final Map<String, String> map = new LinkedHashMap<>();
    expect('{');
    skipWhitespace();
    if (peek() == '}') {
      position++;
      return map;
    }
    while (true) {
      skipWhitespace();
      final String key = parseString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      map.put(key, parseString());
      skipWhitespace();
      final char next = consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        return map;
      }
      throw malformed("Expected ',' or '}' in __metadata__, got '" + next + "'");
    }
  }

  private int[] parseIntArray() {
    final long[] longs = parseLongArray();
    final int[] ints = new int[longs.length];
    for (int i = 0; i < longs.length; i++) {
      if (longs[i] < 0 || longs[i] > Integer.MAX_VALUE) {
        throw malformed("Shape dimension out of int range: " + longs[i]);
      }
      ints[i] = (int) longs[i];
    }
    return ints;
  }

  private long[] parseLongArray() {
    expect('[');
    skipWhitespace();
    final List<Long> values = new ArrayList<>();
    if (peek() == ']') {
      position++;
      return new long[0];
    }
    while (true) {
      skipWhitespace();
      values.add(parseLong());
      skipWhitespace();
      final char next = consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        break;
      }
      throw malformed("Expected ',' or ']' in a number array, got '" + next + "'");
    }
    final long[] array = new long[values.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = values.get(i);
    }
    return array;
  }

  private long parseLong() {
    final int start = position;
    if (peek() == '-') {
      position++;
    }
    if (position >= text.length() || !Character.isDigit(text.charAt(position))) {
      throw malformed("Expected a non-negative integer");
    }
    while (position < text.length() && Character.isDigit(text.charAt(position))) {
      position++;
    }
    try {
      return Long.parseLong(text.substring(start, position));
    }
    catch (NumberFormatException e) {
      throw malformed("Malformed integer: " + text.substring(start, position));
    }
  }

  private String parseString() {
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
      }
      else {
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
    try {
      return (char) Integer.parseInt(hex, 16);
    }
    catch (NumberFormatException e) {
      throw malformed("Malformed \\u escape sequence: " + hex);
    }
  }

  // Skips one JSON value of any type (string, number, array, object, true/false/null); used for
  // header fields the reader does not care about (safetensors may add fields over time).
  private void skipValue() {
    skipWhitespace();
    final char c = peek();
    if (c == '"') {
      parseString();
    }
    else if (c == '[') {
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
    }
    else if (c == '{') {
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
    }
    else if (c == '-' || Character.isDigit(c)) {
      position++;
      while (position < text.length() && "0123456789.eE+-".indexOf(text.charAt(position)) >= 0) {
        position++;
      }
    }
    else if (text.startsWith("true", position)) {
      position += 4;
    }
    else if (text.startsWith("false", position)) {
      position += 5;
    }
    else if (text.startsWith("null", position)) {
      position += 4;
    }
    else {
      throw malformed("Unexpected character while skipping a value: '" + c + "'");
    }
  }

  private void skipWhitespace() {
    while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
      position++;
    }
  }

  private char peek() {
    if (position >= text.length()) {
      throw malformed("Unexpected end of header");
    }
    return text.charAt(position);
  }

  private char consume() {
    final char c = peek();
    position++;
    return c;
  }

  private void expect(char c) {
    final char actual = consume();
    if (actual != c) {
      throw malformed("Expected '" + c + "', got '" + actual + "'");
    }
  }

  private IllegalArgumentException malformed(String message) {
    return new IllegalArgumentException(
        "Malformed safetensors header at offset " + position + ": " + message);
  }

  /**
   * The parsed header: the declared tensors, in header order, and the {@code __metadata__}
   * string map.
   *
   * @param tensors  The declared tensors, in header order. Never {@code null}.
   * @param metadata The {@code __metadata__} string map, empty when the header has none. Never
   *                 {@code null}.
   */
  record Result(List<TensorInfo> tensors, Map<String, String> metadata) {
  }
}
