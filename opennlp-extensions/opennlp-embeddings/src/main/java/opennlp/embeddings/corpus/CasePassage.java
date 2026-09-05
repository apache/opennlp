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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.InvalidFormatException;

/**
 * One court opinion passage: a coherent run of paragraphs from one opinion, small enough
 * to pool into a single embedding.
 *
 * <p>The on-disk interchange form is JSON Lines, one object per passage with the string
 * fields {@code id}, {@code case}, {@code cite}, {@code date}, {@code vol}, and
 * {@code text}.</p>
 *
 * @param id The passage identifier. Must not be {@code null} or blank. The CAP reader uses
 *           {@code <caseid>-<opinion>-<seq>}.
 * @param caseName The abbreviated case name. Must not be {@code null}.
 * @param cite The official citation, e.g. {@code 200 U.S. 1}, or empty when unavailable.
 *             Must not be {@code null}.
 * @param date The decision date as recorded by the reporter. Must not be {@code null}.
 * @param volume The reporter volume. Must not be {@code null}.
 * @param text The passage text. Must not be {@code null} or blank.
 *
 * @since 3.0.0
 */
public record CasePassage(String id, String caseName, String cite, String date,
                          String volume, String text) {

  /**
   * Validates the passage.
   *
   * @throws IllegalArgumentException Thrown if a component is {@code null}, or
   *         {@code id} or {@code text} is blank.
   */
  public CasePassage {
    requireNotNull(id, "id");
    requireNotNull(caseName, "caseName");
    requireNotNull(cite, "cite");
    requireNotNull(date, "date");
    requireNotNull(volume, "volume");
    requireNotNull(text, "text");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (text.isBlank()) {
      throw new IllegalArgumentException("text must not be blank");
    }
  }

  /**
   * Writes passages in the JSON Lines interchange form.
   *
   * @param passages The passages to write. Must not be {@code null} or contain
   *                 {@code null}.
   * @param file The target file, replaced if present. Must not be {@code null}.
   * @throws IOException Thrown if writing fails.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or
   *         {@code passages} contains {@code null}.
   */
  public static void writeJsonl(List<CasePassage> passages, Path file) throws IOException {
    if (passages == null) {
      throw new IllegalArgumentException("passages must not be null");
    }
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    for (CasePassage passage : passages) {
      if (passage == null) {
        throw new IllegalArgumentException("passages must not contain null");
      }
    }
    try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      final StringBuilder line = new StringBuilder();
      for (CasePassage passage : passages) {
        line.setLength(0);
        line.append("{\"id\": ");
        Json.appendString(line, passage.id());
        line.append(", \"case\": ");
        Json.appendString(line, passage.caseName());
        line.append(", \"cite\": ");
        Json.appendString(line, passage.cite());
        line.append(", \"date\": ");
        Json.appendString(line, passage.date());
        line.append(", \"vol\": ");
        Json.appendString(line, passage.volume());
        line.append(", \"text\": ");
        Json.appendString(line, passage.text());
        line.append("}\n");
        out.write(line.toString());
      }
    }
  }

  /**
   * Reads passages from the JSON Lines interchange form.
   *
   * @param file The file to read. Must not be {@code null}.
   * @return The passages in file order. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if a line is not a JSON object with the six
   *         string fields.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}.
   */
  public static List<CasePassage> readJsonl(Path file)
      throws IOException, InvalidFormatException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    final List<CasePassage> passages = new ArrayList<>();
    try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = in.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        final String inputName = file + " line " + lineNumber;
        final Object value = Json.parse(line, inputName);
        if (!(value instanceof Map<?, ?> object)) {
          throw new InvalidFormatException("Expected a JSON object in " + inputName);
        }
        try {
          passages.add(new CasePassage(
              stringField(object, "id", inputName),
              stringField(object, "case", inputName),
              stringField(object, "cite", inputName),
              stringField(object, "date", inputName),
              stringField(object, "vol", inputName),
              stringField(object, "text", inputName)));
        } catch (IllegalArgumentException e) {
          throw new InvalidFormatException("Invalid passage in " + inputName, e);
        }
      }
    } catch (CharacterCodingException e) {
      throw new InvalidFormatException("Invalid UTF-8 in " + file, e);
    }
    return passages;
  }

  /**
   * Reads one required string field.
   *
   * @param object The parsed object.
   * @param field The field name.
   * @param inputName The input name used in error messages.
   * @return The field value.
   * @throws InvalidFormatException Thrown if the field is absent or is not a string.
   */
  private static String stringField(Map<?, ?> object, String field, String inputName)
      throws InvalidFormatException {
    final Object value = object.get(field);
    if (!(value instanceof String string)) {
      throw new InvalidFormatException(
          "Missing or non-string field '" + field + "' in " + inputName);
    }
    return string;
  }

  /**
   * Validates a record component.
   *
   * @param value The component value.
   * @param name The component name used in error messages.
   * @throws IllegalArgumentException Thrown if {@code value} is {@code null}.
   */
  private static void requireNotNull(Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
  }
}
