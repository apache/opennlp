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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import opennlp.tools.util.InvalidFormatException;

/**
 * Reads single top-level fields out of a small flat JSON configuration file (a model's
 * {@code config.json} or {@code tokenizer_config.json}) without a JSON library dependency. Only
 * top-level scalar look-ups are implemented; every other field is skipped structurally, and a
 * nested occurrence of the looked-up name never matches.
 */
final class FlatJsonFields {

  /** The JSON null literal, accepted in place of any looked-up value. */
  private static final String NULL_LITERAL = "null";

  /** Not instantiable. */
  private FlatJsonFields() {
  }

  /**
   * Reads one top-level boolean field from a JSON object file.
   *
   * @param file  The JSON file, a single top-level object. Must not be {@code null} and must
   *              exist.
   * @param field The top-level field name to read. Must not be {@code null}.
   * @return The field's value, or {@code null} when the field is absent or explicitly JSON
   *     {@code null} (the formats treat those the same: fall back to the default).
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws InvalidFormatException Thrown if the file is not a well-formed JSON object, the
   *     field appears more than once, or its value is neither a boolean nor {@code null}.
   * @throws IOException Thrown if reading the file fails.
   */
  static Boolean topLevelBoolean(Path file, String field) throws IOException {
    return topLevelField(file, field, cursor -> {
      if (cursor.consumeLiteral("true")) {
        return Boolean.TRUE;
      }
      if (cursor.consumeLiteral("false")) {
        return Boolean.FALSE;
      }
      if (cursor.consumeLiteral(NULL_LITERAL)) {
        return null;
      }
      throw cursor.malformed("Field '" + field + "' must be a boolean or null");
    });
  }

  /**
   * Reads one top-level string field from a JSON object file.
   *
   * @param file  The JSON file, a single top-level object. Must not be {@code null} and must
   *              exist.
   * @param field The top-level field name to read. Must not be {@code null}.
   * @return The field's value, or {@code null} when the field is absent or explicitly JSON
   *     {@code null} (the formats treat those the same: fall back to the default).
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws InvalidFormatException Thrown if the file is not a well-formed JSON object, the
   *     field appears more than once, or its value is neither a string nor {@code null}.
   * @throws IOException Thrown if reading the file fails.
   */
  static String topLevelString(Path file, String field) throws IOException {
    return topLevelField(file, field, cursor -> {
      if (cursor.consumeLiteral(NULL_LITERAL)) {
        return null;
      }
      if (cursor.peek() == '"') {
        return cursor.parseString();
      }
      throw cursor.malformed("Field '" + field + "' must be a string or null");
    });
  }

  /**
   * Walks a JSON object file's top-level fields, skipping every field but {@code field} and
   * handing that one's value to {@code valueReader}.
   *
   * @param file        The JSON file, a single top-level object. Must not be {@code null} and
   *                    must exist.
   * @param field       The top-level field name to read. Must not be {@code null}.
   * @param valueReader Reads the matched field's value off the cursor.
   * @param <T>         The value type the reader produces.
   * @return The field's value, or {@code null} when the field is absent.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}.
   * @throws InvalidFormatException Thrown if the file is not a well-formed JSON object or the
   *     field appears more than once.
   * @throws IOException Thrown if reading the file fails.
   */
  private static <T> T topLevelField(Path file, String field, ValueReader<T> valueReader)
      throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    if (field == null) {
      throw new IllegalArgumentException("field must not be null");
    }
    final String json = Files.readString(file);
    final JsonCursor cursor = new JsonCursor(json, file.getFileName().toString());
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();
    T value = null;
    boolean seen = false;
    if (cursor.peek() == '}') {
      cursor.consume();
    } else {
      while (true) {
        cursor.skipWhitespace();
        final String key = cursor.parseString();
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        if (field.equals(key)) {
          if (seen) {
            throw cursor.malformed("Field '" + field + "' appears more than once");
          }
          seen = true;
          value = valueReader.read(cursor);
        } else {
          cursor.skipValue();
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          continue;
        }
        if (next == '}') {
          break;
        }
        throw cursor.malformed("Expected ',' or '}' after a field, got '" + next + "'");
      }
    }
    cursor.requireEnd("Trailing content after the top-level object");
    return value;
  }

  /**
   * Decodes the value of the looked-up field, positioned at its first character.
   *
   * @param <T> The value type produced.
   */
  @FunctionalInterface
  private interface ValueReader<T> {

    /**
     * Reads one value off the cursor.
     *
     * @param cursor The cursor, positioned at the value's first character.
     * @return The decoded value, or {@code null} for a JSON {@code null}.
     * @throws InvalidFormatException Thrown if the value is malformed or not of the expected
     *     type.
     */
    T read(JsonCursor cursor) throws InvalidFormatException;
  }
}
