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
 * A cursor parser for the JSON header of a safetensors file: a flat object of tensor name to a
 * {@code dtype}/{@code shape}/{@code data_offsets} record, plus an optional {@code __metadata__}
 * string map. Not a general-purpose JSON parser; it fails loud on anything outside that shape.
 */
final class SafetensorsHeaderParser {

  private static final String METADATA_KEY = "__metadata__";

  private final JsonCursor cursor;

  /** Wraps the header text in a cursor; driven by {@link #parse(String)}. */
  private SafetensorsHeaderParser(String text) {
    this.cursor = new JsonCursor(text, "safetensors header");
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

  /** {@return the parsed header: its tensors in header order and the {@code __metadata__} map} */
  private Result parseTop() {
    final List<TensorInfo> tensors = new ArrayList<>();
    Map<String, String> metadata = Map.of();
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();
    if (cursor.peek() == '}') {
      cursor.consume();
      requireEnd();
      return new Result(tensors, metadata);
    }
    while (true) {
      cursor.skipWhitespace();
      final String key = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      if (METADATA_KEY.equals(key)) {
        metadata = parseStringMap();
      } else {
        tensors.add(parseTensorInfo(key));
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        break;
      }
      throw cursor.malformed("Expected ',' or '}' after a header entry, got '" + next + "'");
    }
    requireEnd();
    return new Result(tensors, metadata);
  }

  /**
   * Requires the rest of the header to be whitespace only. Trailing whitespace is legal (writers
   * space-pad the header to align the data section); other trailing content is a length mismatch.
   */
  private void requireEnd() {
    cursor.requireEnd("Trailing content after the header object");
  }

  /**
   * {@return one tensor's metadata, parsed from its header record}
   *
   * @param name The tensor's name, the key it was declared under.
   */
  private TensorInfo parseTensorInfo(String name) {
    cursor.expect('{');
    String dtype = null;
    int[] shape = null;
    long dataOffsetBegin = -1;
    long dataOffsetEnd = -1;
    cursor.skipWhitespace();
    while (cursor.peek() != '}') {
      cursor.skipWhitespace();
      final String field = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (field) {
        case "dtype" -> dtype = cursor.parseString();
        case "shape" -> shape = parseIntArray();
        case "data_offsets" -> {
          final long[] offsets = parseLongArray();
          if (offsets.length != 2) {
            throw cursor.malformed("Tensor '" + name + "' data_offsets must have exactly 2 "
                + "elements, got " + offsets.length);
          }
          dataOffsetBegin = offsets[0];
          dataOffsetEnd = offsets[1];
        }
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        cursor.skipWhitespace();
        continue;
      }
      if (next == '}') {
        if (dtype == null || shape == null || dataOffsetBegin < 0) {
          throw cursor.malformed("Tensor '" + name
              + "' is missing dtype, shape, or data_offsets");
        }
        return new TensorInfo(name, dtype, shape, dataOffsetBegin, dataOffsetEnd);
      }
      throw cursor.malformed("Expected ',' or '}' in tensor '" + name + "', got '" + next + "'");
    }
    throw cursor.malformed("Tensor '" + name + "' has an empty object; missing dtype, shape, "
        + "and data_offsets");
  }

  /** {@return a JSON object of string values, used for the {@code __metadata__} map} */
  private Map<String, String> parseStringMap() {
    final Map<String, String> map = new LinkedHashMap<>();
    cursor.expect('{');
    cursor.skipWhitespace();
    if (cursor.peek() == '}') {
      cursor.consume();
      return map;
    }
    while (true) {
      cursor.skipWhitespace();
      final String key = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      map.put(key, cursor.parseString());
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        return map;
      }
      throw cursor.malformed("Expected ',' or '}' in __metadata__, got '" + next + "'");
    }
  }

  /**
   * {@return a JSON array of non-negative integers as an {@code int[]}}
   *
   * @throws IllegalArgumentException Thrown if any element is outside the {@code int} range.
   */
  private int[] parseIntArray() {
    final long[] longs = parseLongArray();
    final int[] ints = new int[longs.length];
    for (int i = 0; i < longs.length; i++) {
      if (longs[i] < 0 || longs[i] > Integer.MAX_VALUE) {
        throw cursor.malformed("Shape dimension out of int range: " + longs[i]);
      }
      ints[i] = (int) longs[i];
    }
    return ints;
  }

  /** {@return a JSON array of integers as a {@code long[]}} */
  private long[] parseLongArray() {
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<Long> values = new ArrayList<>();
    if (cursor.peek() == ']') {
      cursor.consume();
      return new long[0];
    }
    while (true) {
      cursor.skipWhitespace();
      values.add(cursor.parseLong());
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        break;
      }
      throw cursor.malformed("Expected ',' or ']' in a number array, got '" + next + "'");
    }
    final long[] array = new long[values.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = values.get(i);
    }
    return array;
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
