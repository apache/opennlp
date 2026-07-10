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

/**
 * Reads single top-level fields out of a small flat JSON configuration file (a model's
 * {@code config.json} or {@code tokenizer_config.json}) without a JSON library dependency,
 * sharing {@link JsonCursor}'s scanning primitives with the safetensors header parser. Only
 * what the model-directory loader needs is implemented: top-level boolean look-ups. Every
 * other field, of any type and nesting, is skipped structurally, and nested occurrences of the
 * looked-up name never match (a top-level field is what the configuration formats define).
 */
final class FlatJsonFields {

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
   * @throws IllegalArgumentException Thrown if the file is not a well-formed JSON object, the
   *     field appears more than once, or its value is neither a boolean nor {@code null}.
   * @throws IOException Thrown if reading the file fails.
   */
  static Boolean topLevelBoolean(Path file, String field) throws IOException {
    final String json = Files.readString(file);
    final JsonCursor cursor = new JsonCursor(json, file.getFileName().toString());
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();
    Boolean value = null;
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
          if (cursor.consumeLiteral("true")) {
            value = Boolean.TRUE;
          } else if (cursor.consumeLiteral("false")) {
            value = Boolean.FALSE;
          } else if (!cursor.consumeLiteral("null")) {
            throw cursor.malformed("Field '" + field + "' must be a boolean or null");
          }
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
}
