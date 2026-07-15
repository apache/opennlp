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

package opennlp.tools.tokenize.lattice;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.StringUtil;

/**
 * An immutable, in-memory dictionary in the mecab directory format: lexicon entries
 * from the {@code *.csv} files, connection costs from {@code matrix.def}, character
 * categories from {@code char.def}, and unknown-word templates from {@code unk.def},
 * loaded from a user-supplied dictionary directory. The reader is a clean-room
 * implementation of the documented format; no dictionary data is bundled or downloaded
 * by this class, so the dictionaries' own licenses never attach to this library.
 *
 * <p>The same format serves multiple languages: the Japanese IPADIC and UniDic
 * distributions and the Korean mecab-ko-dic all load through this one reader, with the
 * feature columns passed through untouched because their schemas differ.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @see LatticeTokenizer
 * @since 3.0.0
 */
public final class MecabDictionary {

  /** One lexicon or unknown-word entry. */
  record WordEntry(int leftId, int rightId, int cost, List<String> features) {
  }

  /** One character category's unknown-word behavior from {@code char.def}. */
  record Category(String name, boolean invoke, boolean group, int length) {
  }

  private final Map<String, List<WordEntry>> lexicon;
  private final int maxSurfaceLength;
  private final short[] connectionCosts;
  private final int rightSize;
  private final Map<String, Category> categories;
  private final String[] categoryOfChar;
  private final Map<String, List<WordEntry>> unknownEntries;

  private MecabDictionary(Map<String, List<WordEntry>> lexicon, int maxSurfaceLength,
      short[] connectionCosts, int rightSize, Map<String, Category> categories,
      String[] categoryOfChar, Map<String, List<WordEntry>> unknownEntries) {
    this.lexicon = lexicon;
    this.maxSurfaceLength = maxSurfaceLength;
    this.connectionCosts = connectionCosts;
    this.rightSize = rightSize;
    this.categories = categories;
    this.categoryOfChar = categoryOfChar;
    this.unknownEntries = unknownEntries;
  }

  /**
   * Loads a dictionary directory encoded in UTF-8.
   *
   * @param directory The unpacked dictionary directory. Must not be {@code null}.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if reading fails or a file is malformed.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null}.
   */
  public static MecabDictionary load(Path directory) throws IOException {
    return load(directory, StandardCharsets.UTF_8);
  }

  /**
   * Loads a dictionary directory.
   *
   * @param directory The unpacked dictionary directory holding the {@code *.csv}
   *                  lexicon files, {@code matrix.def}, {@code char.def}, and
   *                  {@code unk.def}. Must not be {@code null}.
   * @param charset The encoding the distribution uses, for example UTF-8 or EUC-JP.
   *                Must not be {@code null}.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if reading fails, a required file is missing, or a file
   *         is malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static MecabDictionary load(Path directory, Charset charset) throws IOException {
    if (directory == null || charset == null) {
      throw new IllegalArgumentException("directory and charset must not be null");
    }
    final Map<String, List<WordEntry>> lexicon = new HashMap<>();
    int maxSurface = 0;
    try (DirectoryStream<Path> csvFiles = Files.newDirectoryStream(directory, "*.csv")) {
      for (final Path csv : csvFiles) {
        maxSurface = Math.max(maxSurface, readLexicon(csv, charset, lexicon));
      }
    }
    if (lexicon.isEmpty()) {
      throw new IOException("no lexicon entries found under " + directory);
    }

    final List<String> matrixLines = readLines(directory.resolve("matrix.def"), charset);
    if (matrixLines.isEmpty()) {
      throw new IOException("empty matrix.def under " + directory);
    }
    final String[] header = splitWhitespace(matrixLines.get(0));
    if (header.length != 2) {
      throw new IOException("malformed matrix.def header: " + matrixLines.get(0));
    }
    final int leftSize = parseInt(header[0], "matrix.def", 1);
    final int rightSize = parseInt(header[1], "matrix.def", 1);
    final short[] costs = new short[leftSize * rightSize];
    for (int i = 1; i < matrixLines.size(); i++) {
      final String line = matrixLines.get(i).trim();
      if (line.isEmpty()) {
        continue;
      }
      final String[] fields = splitWhitespace(line);
      if (fields.length != 3) {
        throw new IOException("malformed matrix.def line " + (i + 1));
      }
      final int right = parseInt(fields[0], "matrix.def", i + 1);
      final int left = parseInt(fields[1], "matrix.def", i + 1);
      costs[right * rightSize + left] = (short) parseInt(fields[2], "matrix.def", i + 1);
    }

    final Map<String, Category> categories = new HashMap<>();
    final String[] categoryOfChar = new String[Character.MAX_VALUE + 1];
    readCharacterDefinition(directory.resolve("char.def"), charset, categories,
        categoryOfChar);

    final Map<String, List<WordEntry>> unknown = new HashMap<>();
    readLexicon(directory.resolve("unk.def"), charset, unknown);

    return new MecabDictionary(lexicon, maxSurface, costs, rightSize, categories,
        categoryOfChar, unknown);
  }

  /** Reads one lexicon-format CSV file; returns the longest surface seen. */
  private static int readLexicon(Path file, Charset charset,
      Map<String, List<WordEntry>> target) throws IOException {
    int maxSurface = 0;
    int lineNumber = 0;
    for (final String line : readLines(file, charset)) {
      lineNumber++;
      if (line.isEmpty()) {
        continue;
      }
      final List<String> fields = splitCsv(line);
      if (fields.size() < 4) {
        throw new IOException("malformed entry at " + file + " line " + lineNumber);
      }
      final String surface = fields.get(0);
      if (surface.isEmpty()) {
        continue;
      }
      final WordEntry entry = new WordEntry(
          parseInt(fields.get(1), file.toString(), lineNumber),
          parseInt(fields.get(2), file.toString(), lineNumber),
          parseInt(fields.get(3), file.toString(), lineNumber),
          List.copyOf(fields.subList(4, fields.size())));
      target.computeIfAbsent(surface, key -> new ArrayList<>(1)).add(entry);
      maxSurface = Math.max(maxSurface, surface.length());
    }
    return maxSurface;
  }

  /** Reads char.def: category behavior lines and code point mapping lines. */
  private static void readCharacterDefinition(Path file, Charset charset,
      Map<String, Category> categories, String[] categoryOfChar) throws IOException {
    int lineNumber = 0;
    for (final String raw : readLines(file, charset)) {
      lineNumber++;
      final String line = stripComment(raw).trim();
      if (line.isEmpty()) {
        continue;
      }
      final String[] fields = splitWhitespace(line);
      if (fields[0].startsWith("0x") || fields[0].startsWith("0X")) {
        final int rangeSeparator = fields[0].indexOf("..");
        final int from;
        final int to;
        if (rangeSeparator >= 0) {
          from = parseCodePoint(fields[0].substring(0, rangeSeparator), file, lineNumber);
          to = parseCodePoint(fields[0].substring(rangeSeparator + 2), file, lineNumber);
        } else {
          from = parseCodePoint(fields[0], file, lineNumber);
          to = from;
        }
        if (fields.length < 2) {
          throw new IOException("mapping without category at " + file + " line " + lineNumber);
        }
        for (int c = from; c <= to && c <= Character.MAX_VALUE; c++) {
          categoryOfChar[c] = fields[1];
        }
      } else {
        if (fields.length < 4) {
          throw new IOException("malformed category at " + file + " line " + lineNumber);
        }
        categories.put(fields[0], new Category(fields[0],
            "1".equals(fields[1]), "1".equals(fields[2]),
            parseInt(fields[3], file.toString(), lineNumber)));
      }
    }
    if (!categories.containsKey("DEFAULT")) {
      throw new IOException("char.def defines no DEFAULT category: " + file);
    }
  }

  /**
   * Looks up the lexicon entries for an exact surface form.
   *
   * @param surface The surface form.
   * @return The entries, or {@code null} when the surface is not listed.
   */
  List<WordEntry> lookup(String surface) {
    return lexicon.get(surface);
  }

  /** @return The longest surface form in the lexicon, bounding prefix enumeration. */
  int maxSurfaceLength() {
    return maxSurfaceLength;
  }

  /**
   * Reads the connection cost between two adjacent nodes.
   *
   * @param rightId The right context id of the earlier node.
   * @param leftId The left context id of the later node.
   * @return The connection cost.
   */
  int connectionCost(int rightId, int leftId) {
    return connectionCosts[rightId * rightSize + leftId];
  }

  /**
   * Classifies a character.
   *
   * @param c The character.
   * @return Its category, falling back to {@code DEFAULT}. Never {@code null}.
   */
  Category categoryOf(char c) {
    final String name = categoryOfChar[c];
    final Category category = name == null ? null : categories.get(name);
    return category != null ? category : categories.get("DEFAULT");
  }

  /**
   * Looks up the unknown-word templates of a category.
   *
   * @param category The category name.
   * @return The templates, or {@code null} when the category has none.
   */
  List<WordEntry> unknownEntries(String category) {
    return unknownEntries.get(category);
  }

  private static List<String> readLines(Path file, Charset charset) throws IOException {
    if (!Files.exists(file)) {
      throw new IOException("required dictionary file is missing: " + file);
    }
    final String content = new String(Files.readAllBytes(file), charset);
    final List<String> lines = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= content.length(); i++) {
      if (i == content.length() || content.charAt(i) == '\n') {
        int end = i;
        if (end > start && content.charAt(end - 1) == '\r') {
          end--;
        }
        lines.add(content.substring(start, end));
        start = i + 1;
      }
    }
    return lines;
  }

  private static String stripComment(String line) {
    final int hash = line.indexOf('#');
    return hash < 0 ? line : line.substring(0, hash);
  }

  private static List<String> splitCsv(String line) {
    final List<String> fields = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || line.charAt(i) == ',') {
        fields.add(line.substring(start, i));
        start = i + 1;
      }
    }
    return fields;
  }

  private static String[] splitWhitespace(String line) {
    final List<String> parts = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || StringUtil.isWhitespace(line.charAt(i))) {
        if (start >= 0) {
          parts.add(line.substring(start, i));
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
    }
    return parts.toArray(new String[0]);
  }

  private static int parseInt(String text, String file, int lineNumber)
      throws IOException {
    try {
      return Integer.parseInt(text.trim());
    } catch (NumberFormatException e) {
      throw new IOException("malformed number in " + file + " line " + lineNumber, e);
    }
  }

  private static int parseCodePoint(String text, Path file, int lineNumber)
      throws IOException {
    try {
      return Integer.parseInt(text.trim().substring(2), 16);
    } catch (RuntimeException e) {
      throw new IOException("malformed code point in " + file + " line " + lineNumber, e);
    }
  }
}
