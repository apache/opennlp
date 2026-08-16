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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.InvalidFormatException;

/**
 * One law dictionary entry: a headword and its definition.
 *
 * <p>The on-disk interchange form is a TSV file with one entry per line,
 * {@code HEADWORD<TAB>definition}. Headwords keep their original casing and may contain
 * spaces ("HABEAS CORPUS"), periods, hyphens, and apostrophes. Definitions are
 * whitespace-collapsed single lines.</p>
 *
 * @param headword The dictionary headword. Must not be {@code null} or blank and must
 *                 not contain a tab or line break.
 * @param definition The definition text. Must not be {@code null} or blank and must not
 *                   contain a tab or line break.
 *
 * @since 3.0.0
 */
public record DictionaryEntry(String headword, String definition) {

  /**
   * Validates the entry.
   *
   * @throws IllegalArgumentException Thrown if either component is {@code null}, blank,
   *         or contains a tab or line break.
   */
  public DictionaryEntry {
    requireCell(headword, "headword");
    requireCell(definition, "definition");
  }

  /**
   * Writes entries in the TSV interchange form.
   *
   * @param entries The entries to write. Must not be {@code null} or contain
   *                {@code null}.
   * @param file The target file, replaced if present. Must not be {@code null}.
   * @throws IOException Thrown if writing fails.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or
   *         {@code entries} contains {@code null}.
   */
  public static void writeTsv(List<DictionaryEntry> entries, Path file) throws IOException {
    if (entries == null) {
      throw new IllegalArgumentException("entries must not be null");
    }
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      for (DictionaryEntry entry : entries) {
        if (entry == null) {
          throw new IllegalArgumentException("entries must not contain null");
        }
        out.write(entry.headword());
        out.write('\t');
        out.write(entry.definition());
        out.write('\n');
      }
    }
  }

  /**
   * Reads entries from the TSV interchange form.
   *
   * @param file The file to read. Must not be {@code null}.
   * @return The entries in file order. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if a line does not hold exactly one tab.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}.
   */
  public static List<DictionaryEntry> readTsv(Path file)
      throws IOException, InvalidFormatException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    final List<DictionaryEntry> entries = new ArrayList<>();
    try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = in.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        final int tab = line.indexOf('\t');
        if (tab < 0 || line.indexOf('\t', tab + 1) >= 0) {
          throw new InvalidFormatException(
              "Line " + lineNumber + " of " + file + " must hold exactly one tab");
        }
        entries.add(new DictionaryEntry(line.substring(0, tab), line.substring(tab + 1)));
      }
    }
    return entries;
  }

  private static void requireCell(String value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    if (value.indexOf('\t') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
      throw new IllegalArgumentException(name + " must not contain a tab or line break");
    }
  }
}
