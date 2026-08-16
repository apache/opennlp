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
 * One learned vocabulary term with its corpus frequency.
 *
 * <p>The on-disk interchange form is a TSV file with one term per line,
 * {@code term<TAB>count<TAB>source}, where source is {@code dictionary} for terms kept
 * because a law dictionary lists them and {@code corpus} for terms kept by frequency.
 * Terms are case-folded; multi-word terms join their words with single spaces.</p>
 *
 * @param term The folded term, e.g. {@code habeas corpus}. Must not be {@code null} or
 *             blank and must not contain a tab or line break.
 * @param count The number of occurrences counted in the corpus. Must not be negative;
 *              a dictionary term the corpus never uses carries zero.
 * @param fromDictionary Whether the term was kept because the dictionary lists it.
 *
 * @since 3.0.0
 */
public record TermCount(String term, long count, boolean fromDictionary) {

  private static final String SOURCE_DICTIONARY = "dictionary";
  private static final String SOURCE_CORPUS = "corpus";

  /**
   * Validates the term.
   *
   * @throws IllegalArgumentException Thrown if {@code term} is {@code null}, blank, or
   *         contains a tab or line break, or {@code count} is negative.
   */
  public TermCount {
    if (term == null) {
      throw new IllegalArgumentException("term must not be null");
    }
    if (term.isBlank()) {
      throw new IllegalArgumentException("term must not be blank");
    }
    if (term.indexOf('\t') >= 0 || term.indexOf('\n') >= 0 || term.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("term must not contain a tab or line break");
    }
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative: " + count);
    }
  }

  /**
   * Writes terms in the TSV interchange form.
   *
   * @param terms The terms to write. Must not be {@code null} or contain {@code null}.
   * @param file The target file, replaced if present. Must not be {@code null}.
   * @throws IOException Thrown if writing fails.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or
   *         {@code terms} contains {@code null}.
   */
  public static void writeTsv(List<TermCount> terms, Path file) throws IOException {
    if (terms == null) {
      throw new IllegalArgumentException("terms must not be null");
    }
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      for (TermCount term : terms) {
        if (term == null) {
          throw new IllegalArgumentException("terms must not contain null");
        }
        out.write(term.term());
        out.write('\t');
        out.write(Long.toString(term.count()));
        out.write('\t');
        out.write(term.fromDictionary() ? SOURCE_DICTIONARY : SOURCE_CORPUS);
        out.write('\n');
      }
    }
  }

  /**
   * Reads terms from the TSV interchange form.
   *
   * @param file The file to read. Must not be {@code null}.
   * @return The terms in file order. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if a line does not hold term, count, and
   *         source separated by tabs, with a non-negative count and a known source.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}.
   */
  public static List<TermCount> readTsv(Path file)
      throws IOException, InvalidFormatException {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    final List<TermCount> terms = new ArrayList<>();
    try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = in.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        final String[] cells = line.split("\t", -1);
        if (cells.length != 3) {
          throw new InvalidFormatException(
              "Line " + lineNumber + " of " + file + " must hold three tab-separated cells");
        }
        final long count;
        try {
          count = Long.parseLong(cells[1]);
        } catch (NumberFormatException e) {
          throw new InvalidFormatException(
              "Invalid count '" + cells[1] + "' on line " + lineNumber + " of " + file);
        }
        final boolean fromDictionary = switch (cells[2]) {
          case SOURCE_DICTIONARY -> true;
          case SOURCE_CORPUS -> false;
          default -> throw new InvalidFormatException(
              "Unknown source '" + cells[2] + "' on line " + lineNumber + " of " + file);
        };
        if (count < 0) {
          throw new InvalidFormatException(
              "Negative count on line " + lineNumber + " of " + file);
        }
        terms.add(new TermCount(cells[0], count, fromDictionary));
      }
    }
    return terms;
  }
}
