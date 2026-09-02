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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.ResourceLimits;
import opennlp.tools.util.StringUtil;

/**
 * An immutable, in-memory dictionary in the
 * <a href="https://taku910.github.io/mecab/">MeCab</a> directory format: lexicon entries
 * from the {@code *.csv} files, connection costs from {@code matrix.def}, character
 * categories from {@code char.def}, and unknown-word templates from {@code unk.def},
 * loaded from a user-supplied dictionary directory. No dictionary data is bundled or
 * downloaded by this class.
 *
 * <p>The same format serves multiple languages: the Japanese
 * <a href="https://sourceforge.net/projects/mecab/">IPADIC</a> and
 * <a href="https://clrd.ninjal.ac.jp/unidic/">UniDic</a> distributions and the Korean
 * <a href="https://bitbucket.org/eunjeon/mecab-ko-dic">mecab-ko-dic</a> all load
 * through this one reader, with the feature columns passed through untouched because
 * their schemas differ.</p>
 *
 * <p>Each instance keeps about 0.75 MB of category tables keyed by the 16-bit code-unit
 * space, so load once and share. Lexicon CSV files under the dictionary directory are
 * read in sorted path order so tie-breaking is stable across file systems. Connection
 * costs must cover every declared matrix cell; missing pairs are rejected rather than
 * treated as cost zero. Matrix dimensions and the lexicon entry count are bounded by
 * {@link ResourceLimits#MAX_ENTRIES}, and the matrix cell count by
 * {@link ResourceLimits#MAX_MATRIX_CELLS}. Lexicon CSV fields may be
 * MeCab-quoted with {@code ""} escapes. An {@code unk.def} template must name a
 * category {@code char.def} defined.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @see LatticeTokenizer
 * @since 3.0.0
 */
public final class MecabDictionary {

  /**
   * The category name every {@code char.def} must define; unmapped code points and
   * unknown-word handling fall back to it.
   */
  static final String DEFAULT_CATEGORY = "DEFAULT";

  private static final String MATRIX_DEF = "matrix.def";
  private static final String CHAR_DEF = "char.def";
  private static final String UNK_DEF = "unk.def";

  static final String LEXICON_EXTENSION = ".csv";
  static final String DEFINITION_EXTENSION = ".def";
  static final String CONFIGURATION_FILE = "dicrc";
  private static final String LEXICON_GLOB = "*" + LEXICON_EXTENSION;
  private static final char COMMENT_MARKER = '#';

  /** The prefix a {@code char.def} code point field carries, in either letter case. */
  private static final String HEX_PREFIX = "0x";

  /** The separator between the two ends of a {@code char.def} code point range. */
  private static final String RANGE_SEPARATOR = "..";

  /** The {@code char.def} field value that turns a category flag on. */
  private static final String FLAG_ON = "1";

  /** The {@code char.def} field value that turns a category flag off. */
  private static final String FLAG_OFF = "0";

  /**
   * One lexicon or unknown-word entry.
   *
   * @param leftId The left context id, an index into the connection matrix.
   * @param rightId The right context id, an index into the connection matrix.
   * @param cost The entry's own cost.
   * @param features The entry's feature columns, in file order.
   */
  record WordEntry(int leftId, int rightId, int cost, List<String> features) {
  }

  /**
   * One character category's unknown-word behavior from {@code char.def}.
   *
   * @param name The category name.
   * @param invoke Whether unknown-word candidates are generated even where the lexicon
   *               matched.
   * @param group Whether a whole run of same-category characters is offered as one
   *              candidate.
   * @param length How many leading characters of the run are offered as candidates.
   */
  record Category(String name, boolean invoke, boolean group, int length) {
  }

  /** Receives one common-prefix match during {@link #prefixMatches}. */
  interface PrefixMatchConsumer {

    /**
     * Accepts one match.
     *
     * @param length The matched surface length in characters.
     * @param entries The lexicon entries for that surface.
     */
    void accept(int length, List<WordEntry> entries);
  }

  private final DoubleArrayLexicon lexicon;
  private final short[] connectionCosts;
  private final int rightSize;
  private final CategoryTable categoryTable;
  private final Category defaultCategory;
  private final Map<String, List<WordEntry>> unknownEntries;

  private MecabDictionary(DoubleArrayLexicon lexicon,
      short[] connectionCosts, int rightSize, Map<String, Category> categories,
      CategoryTable categoryTable, Map<String, List<WordEntry>> unknownEntries) {
    this.lexicon = lexicon;
    this.connectionCosts = connectionCosts;
    this.rightSize = rightSize;
    this.categoryTable = categoryTable;
    this.defaultCategory = categories.get(DEFAULT_CATEGORY);
    final Map<String, List<WordEntry>> copy = new HashMap<>(unknownEntries.size());
    for (final Map.Entry<String, List<WordEntry>> entry : unknownEntries.entrySet()) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    this.unknownEntries = Map.copyOf(copy);
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
   * @throws IOException Thrown if reading fails, a required file is missing, a file is
   *         malformed, or a lexicon entry's context ids are outside the
   *         {@code matrix.def} dimensions.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static MecabDictionary load(Path directory, Charset charset) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("directory must not be null");
    }
    if (charset == null) {
      throw new IllegalArgumentException("charset must not be null");
    }
    // The connection matrix is read first because its dimensions are what every
    // lexicon entry's context ids have to be inside of.
    final Path matrixFile = directory.resolve(MATRIX_DEF);
    if (!Files.exists(matrixFile)) {
      throw new IOException("required dictionary file is missing: " + matrixFile);
    }
    final int leftSize;
    final int rightSize;
    final short[] costs;
    final int cellCount;
    try (BufferedReader reader = Files.newBufferedReader(matrixFile, charset)) {
      final String rawHeader = reader.readLine();
      if (rawHeader == null) {
        throw new IOException("empty " + MATRIX_DEF + " under " + directory);
      }
      final String headerLine = StringUtil.trimUnicodeWhitespace(rawHeader);
      if (headerLine.isEmpty()) {
        throw new IOException("empty " + MATRIX_DEF + " under " + directory);
      }
      final String[] header = splitWhitespace(headerLine);
      if (header.length != 2) {
        throw new IOException("malformed " + MATRIX_DEF + " header: " + headerLine);
      }
      leftSize = parseInt(header[0], MATRIX_DEF, 1);
      rightSize = parseInt(header[1], MATRIX_DEF, 1);
      if (leftSize < 1 || rightSize < 1) {
        throw new IOException(MATRIX_DEF + " dimensions must be positive, got "
            + leftSize + " " + rightSize);
      }
      if (leftSize > ResourceLimits.MAX_ENTRIES
          || rightSize > ResourceLimits.MAX_ENTRIES) {
        throw new IOException(MATRIX_DEF + " dimensions " + leftSize + " x " + rightSize
            + " exceed safe limit of " + ResourceLimits.MAX_ENTRIES);
      }
      final long cells = (long) leftSize * rightSize;
      if (cells > Integer.MAX_VALUE) {
        throw new IOException(MATRIX_DEF + " dimensions " + leftSize + " x " + rightSize
            + " overflow the addressable connection matrix");
      }
      if (cells > ResourceLimits.MAX_MATRIX_CELLS) {
        throw new IOException(MATRIX_DEF + " dimensions " + leftSize + " x " + rightSize
            + " exceed safe limit of " + ResourceLimits.MAX_MATRIX_CELLS);
      }
      cellCount = (int) cells;
      costs = new short[cellCount];
      // leftSize bounds right-context ids and rightSize bounds left-context ids, matching
      // MeCab's connector.h layout (the names read transposed against the id names).
      final BitSet filled = new BitSet(cellCount);
      int lineNumber = 1;
      String raw;
      while ((raw = reader.readLine()) != null) {
        lineNumber++;
        final String line = StringUtil.trimUnicodeWhitespace(raw);
        if (line.isEmpty()) {
          continue;
        }
        final String[] fields = splitWhitespace(line);
        if (fields.length != 3) {
          throw new IOException("malformed " + MATRIX_DEF + " line " + lineNumber);
        }
        final int right = parseInt(fields[0], MATRIX_DEF, lineNumber);
        final int left = parseInt(fields[1], MATRIX_DEF, lineNumber);
        if (right < 0 || right >= leftSize || left < 0 || left >= rightSize) {
          throw new IOException("malformed " + MATRIX_DEF + " line " + lineNumber
              + ": context ids " + right + " " + left
              + " are outside the declared dimensions " + leftSize + " " + rightSize);
        }
        final int cost = parseInt(fields[2], MATRIX_DEF, lineNumber);
        if (cost < Short.MIN_VALUE || cost > Short.MAX_VALUE) {
          throw new IOException("malformed " + MATRIX_DEF + " line " + lineNumber
              + ": connection cost " + cost + " is outside the 16-bit range the"
              + " format defines");
        }
        final int index = right * rightSize + left;
        costs[index] = (short) cost;
        filled.set(index);
      }
      if (filled.cardinality() != cellCount) {
        throw new IOException(MATRIX_DEF + " declares " + leftSize + " x " + rightSize
            + " connection costs but only " + filled.cardinality()
            + " pairs are listed");
      }
    }

    final Map<String, List<WordEntry>> lexicon = new HashMap<>();
    final List<Path> csvFiles = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, LEXICON_GLOB)) {
      for (final Path csv : stream) {
        csvFiles.add(csv);
      }
    }
    Collections.sort(csvFiles);
    final int[] entryCount = {0};
    for (final Path csv : csvFiles) {
      readLexicon(csv, charset, lexicon, leftSize, rightSize, entryCount);
    }
    if (lexicon.isEmpty()) {
      throw new IOException("no lexicon entries found under " + directory);
    }

    final Map<String, Category> categories = new HashMap<>();
    final CategoryTable.Builder categoryTable = new CategoryTable.Builder();
    readCharacterDefinition(directory.resolve(CHAR_DEF), charset, categories,
        categoryTable);
    final Map<String, List<WordEntry>> unknown = new HashMap<>();
    final Path unkFile = directory.resolve(UNK_DEF);
    readLexicon(unkFile, charset, unknown, leftSize, rightSize, new int[] {0});
    for (final String category : unknown.keySet()) {
      if (!categories.containsKey(category)) {
        throw new IOException(
            UNK_DEF + " names the undefined category " + category + ": " + unkFile);
      }
    }

    return new MecabDictionary(DoubleArrayLexicon.build(lexicon), costs,
        rightSize, categories, categoryTable.build(categories), unknown);
  }

  /**
   * Reads one lexicon-format CSV file, rejecting any entry whose context ids the
   * connection matrix cannot be indexed with.
   *
   * @param file The file to read.
   * @param charset The encoding to decode with.
   * @param target Receives the entries, keyed by surface form.
   * @param leftSize The first {@code matrix.def} dimension, which bounds right context
   *                 ids.
   * @param rightSize The second {@code matrix.def} dimension, which bounds left context
   *                  ids.
   * @param entryCount A one-element running total of entries read so far, shared across
   *                   the lexicon files of one load.
   * @throws IOException Thrown if the file is missing, an entry is malformed, an
   *         entry's context id is outside the matrix dimensions, or the running entry
   *         count exceeds {@link ResourceLimits#MAX_ENTRIES}.
   */
  private static void readLexicon(Path file, Charset charset,
      Map<String, List<WordEntry>> target, int leftSize, int rightSize, int[] entryCount)
      throws IOException {
    if (!Files.exists(file)) {
      throw new IOException("required dictionary file is missing: " + file);
    }
    int lineNumber = 0;
    try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
      String line;
      while ((line = reader.readLine()) != null) {
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
        final int leftId = parseInt(fields.get(1), file.toString(), lineNumber);
        final int rightId = parseInt(fields.get(2), file.toString(), lineNumber);
        if (leftId < 0 || leftId >= rightSize) {
          throw new IOException("malformed entry at " + file + " line " + lineNumber
              + ": left context id " + leftId + " is outside the " + MATRIX_DEF
              + " dimensions " + leftSize + " " + rightSize);
        }
        if (rightId < 0 || rightId >= leftSize) {
          throw new IOException("malformed entry at " + file + " line " + lineNumber
              + ": right context id " + rightId + " is outside the " + MATRIX_DEF
              + " dimensions " + leftSize + " " + rightSize);
        }
        if (entryCount[0] >= ResourceLimits.MAX_ENTRIES) {
          throw new IOException("lexicon entry count exceeds safe limit of "
              + ResourceLimits.MAX_ENTRIES);
        }
        entryCount[0]++;
        final WordEntry entry = new WordEntry(leftId, rightId,
            parseInt(fields.get(3), file.toString(), lineNumber),
            List.copyOf(fields.subList(4, fields.size())));
        target.computeIfAbsent(surface, key -> new ArrayList<>(1)).add(entry);
      }
    }
  }

  /**
   * Reads {@code char.def}: the category behavior lines and the code point mapping
   * lines, in file order, so that a later mapping wins over an earlier one.
   *
   * @param file The file to read.
   * @param charset The encoding to decode with.
   * @param categories Receives the defined categories, keyed by name.
   * @param categoryTable Receives the code point to category name mappings.
   * @throws IOException Thrown if the file is missing, a line is malformed, a code
   *         point is outside the Unicode range, a range descends, or the file defines
   *         no {@code DEFAULT} category.
   */
  private static void readCharacterDefinition(Path file, Charset charset,
      Map<String, Category> categories, CategoryTable.Builder categoryTable)
      throws IOException {
    if (!Files.exists(file)) {
      throw new IOException("required dictionary file is missing: " + file);
    }
    int lineNumber = 0;
    try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
      String raw;
      while ((raw = reader.readLine()) != null) {
        lineNumber++;
        final String line = StringUtil.trimUnicodeWhitespace(stripComment(raw));
        if (line.isEmpty()) {
          continue;
        }
        final String[] fields = splitWhitespace(line);
        if (fields[0].regionMatches(true, 0, HEX_PREFIX, 0, HEX_PREFIX.length())) {
          final int rangeSeparator = fields[0].indexOf(RANGE_SEPARATOR);
          final int from;
          final int to;
          if (rangeSeparator >= 0) {
            from = parseCodePoint(fields[0].substring(0, rangeSeparator), file,
                lineNumber);
            to = parseCodePoint(
                fields[0].substring(rangeSeparator + RANGE_SEPARATOR.length()), file,
                lineNumber);
          } else {
            from = parseCodePoint(fields[0], file, lineNumber);
            to = from;
          }
          if (fields.length < 2) {
            throw new IOException(
                "mapping without category at " + file + " line " + lineNumber);
          }
          if (from > to) {
            throw new IOException("code point range descends at " + file + " line "
                + lineNumber);
          }
          categoryTable.map(from, to, fields[1]);
        } else {
          if (fields.length < 4) {
            throw new IOException(
                "malformed category at " + file + " line " + lineNumber);
          }
          if (!isFlag(fields[1]) || !isFlag(fields[2])) {
            throw new IOException(
                "malformed category flag at " + file + " line " + lineNumber);
          }
          final int length = parseInt(fields[3], file.toString(), lineNumber);
          if (length < 0) {
            throw new IOException(
                "category LENGTH must not be negative at " + file + " line "
                    + lineNumber);
          }
          categories.put(fields[0], new Category(fields[0],
              FLAG_ON.equals(fields[1]), FLAG_ON.equals(fields[2]), length));
        }
      }
    }
    if (!categories.containsKey(DEFAULT_CATEGORY)) {
      throw new IOException(
          CHAR_DEF + " defines no " + DEFAULT_CATEGORY + " category: " + file);
    }
  }

  /**
   * Reports every lexicon surface starting at a text position, walking the trie once
   * with no substring allocation.
   *
   * @param text The text being segmented.
   * @param from The position surfaces must start at.
   * @param to The exclusive end of the searchable stretch.
   * @param consumer Receives each match.
   */
  void prefixMatches(String text, int from, int to, PrefixMatchConsumer consumer) {
    lexicon.prefixMatches(text, from, to, consumer);
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
   * Classifies a character by code point, so that a character outside the Basic
   * Multilingual Plane is classified as the one character it is rather than as its two
   * surrogates.
   *
   * @param codePoint The code point to classify.
   * @return Its category, falling back to {@code DEFAULT} when no {@code char.def}
   *         mapping covers the code point. Never {@code null}.
   */
  Category categoryOf(int codePoint) {
    final Category category = categoryTable.categoryOf(codePoint);
    return category != null ? category : defaultCategory;
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

  /**
   * Removes a trailing {@code #} comment from a {@code char.def} line.
   *
   * @param line The raw line.
   * @return The line up to but excluding the first {@code #}, or the whole line when
   *         there is none.
   */
  private static String stripComment(String line) {
    final int hash = line.indexOf(COMMENT_MARKER);
    return hash < 0 ? line : line.substring(0, hash);
  }

  /**
   * Reports whether a {@code char.def} category flag field is exactly {@code 0} or
   * {@code 1}.
   *
   * @param field The flag field text.
   * @return {@code true} when the field is a recognized flag value.
   */
  private static boolean isFlag(String field) {
    return FLAG_ON.equals(field) || FLAG_OFF.equals(field);
  }

  /**
   * Splits a lexicon line on commas, honoring MeCab-style {@code "..."} quoting with
   * {@code ""} escapes inside a quoted field.
   *
   * @param line The line to split.
   * @return The fields in order, empty fields included. Never {@code null}.
   */
  private static List<String> splitCsv(String line) {
    final List<String> fields = new ArrayList<>();
    final StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      final char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        fields.add(field.toString());
        field.setLength(0);
      } else {
        field.append(c);
      }
    }
    fields.add(field.toString());
    return fields;
  }

  /**
   * Splits a line into its whitespace-separated fields.
   *
   * @param line The line to split.
   * @return The non-empty fields in order. Never {@code null}.
   */
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

  /**
   * Parses a decimal integer field, reporting the file and line on failure.
   *
   * @param text The field text.
   * @param file The file being read, for the error message.
   * @param lineNumber The line being read, for the error message.
   * @return The parsed value.
   * @throws IOException Thrown if the field is not a valid integer.
   */
  private static int parseInt(String text, String file, int lineNumber)
      throws IOException {
    try {
      return Integer.parseInt(StringUtil.trimUnicodeWhitespace(text));
    } catch (NumberFormatException e) {
      throw new IOException("malformed number in " + file + " line " + lineNumber, e);
    }
  }

  /**
   * Parses a {@code 0x}-prefixed hexadecimal code point from {@code char.def}.
   *
   * @param text The field text including the {@code 0x} prefix.
   * @param file The file being read, for the error message.
   * @param lineNumber The line being read, for the error message.
   * @return The parsed code point, which may be in a supplementary plane.
   * @throws IOException Thrown if the field is shorter than the prefix, is not a valid
   *         hexadecimal number, or names a value no Unicode code point has.
   */
  private static int parseCodePoint(String text, Path file, int lineNumber)
      throws IOException {
    final int codePoint;
    try {
      codePoint = Integer.parseInt(
          StringUtil.trimUnicodeWhitespace(text).substring(HEX_PREFIX.length()), 16);
    } catch (RuntimeException e) {
      throw new IOException("malformed code point in " + file + " line " + lineNumber, e);
    }
    if (!Character.isValidCodePoint(codePoint)) {
      throw new IOException("code point out of range in " + file + " line " + lineNumber);
    }
    return codePoint;
  }
}
