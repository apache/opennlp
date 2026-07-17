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
import java.util.Arrays;
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

  /**
   * One frozen node of the lexicon trie: children are held as a sorted character
   * array with a parallel node array and found by binary search, so a descent never
   * boxes the character the way a map keyed by {@link Character} would on every step
   * of the innermost matching loop.
   */
  private static final class TrieNode {

    private final char[] keys;
    private final TrieNode[] nodes;
    private final List<WordEntry> entries;

    private TrieNode(char[] keys, TrieNode[] nodes, List<WordEntry> entries) {
      this.keys = keys;
      this.nodes = nodes;
      this.entries = entries;
    }

    /**
     * Descends one character.
     *
     * @param c The next surface character.
     * @return The child node, or {@code null} when no surface continues with {@code c}.
     */
    private TrieNode child(char c) {
      final int index = Arrays.binarySearch(keys, c);
      return index >= 0 ? nodes[index] : null;
    }
  }

  /** One mutable trie node during construction, frozen into a {@link TrieNode}. */
  private static final class TrieBuilderNode {

    private final Map<Character, TrieBuilderNode> children = new HashMap<>();
    private List<WordEntry> entries;

    /** Freezes this node and its subtree into the sorted-array form. */
    private TrieNode freeze() {
      final char[] keys = new char[children.size()];
      int i = 0;
      for (final Character key : children.keySet()) {
        keys[i++] = key;
      }
      Arrays.sort(keys);
      final TrieNode[] nodes = new TrieNode[keys.length];
      for (int k = 0; k < keys.length; k++) {
        nodes[k] = children.get(keys[k]).freeze();
      }
      return new TrieNode(keys, nodes, entries);
    }
  }

  /**
   * The {@code char.def} code point to category name mapping, over the whole Unicode
   * code point range.
   *
   * <p>The Basic Multilingual Plane is held in a directly indexed array, which is one
   * reference per BMP code point and is the entire mapping for a BMP-only dictionary.
   * The supplementary planes are held as a sorted, non-overlapping range table searched
   * by binary search, because dictionaries map them in a handful of large blocks: a
   * directly indexed array over all of Unicode would spend more than four megabytes of
   * references to say what a few range rows already say.</p>
   */
  private static final class CategoryTable {

    private final Category[] bmp;
    private final int[] rangeStart;
    private final int[] rangeEnd;
    private final Category[] rangeCategory;

    private CategoryTable(Category[] bmp, int[] rangeStart, int[] rangeEnd,
        Category[] rangeCategory) {
      this.bmp = bmp;
      this.rangeStart = rangeStart;
      this.rangeEnd = rangeEnd;
      this.rangeCategory = rangeCategory;
    }

    /**
     * Looks up the category a {@code char.def} mapping gives a code point. The table
     * holds the {@link Category} instances themselves, so a lookup on the tokenizer's
     * per-character path costs one array read or one binary search, never a name map
     * access, and two code points of one category share one instance to compare by
     * identity.
     *
     * @param codePoint The code point to classify.
     * @return The category, or {@code null} when no mapping covers the code point.
     */
    private Category categoryOf(int codePoint) {
      if (codePoint <= Character.MAX_VALUE) {
        return bmp[codePoint];
      }
      int low = 0;
      int high = rangeStart.length - 1;
      while (low <= high) {
        final int middle = (low + high) >>> 1;
        if (codePoint < rangeStart[middle]) {
          high = middle - 1;
        } else if (codePoint > rangeEnd[middle]) {
          low = middle + 1;
        } else {
          return rangeCategory[middle];
        }
      }
      return null;
    }
  }

  /**
   * Collects {@code char.def} mappings in file order and folds them into a
   * {@link CategoryTable}, giving a later mapping precedence over an earlier one that
   * covers the same code point, which is what direct indexing does for the BMP.
   */
  private static final class CategoryTableBuilder {

    private final String[] bmp = new String[Character.MAX_VALUE + 1];
    private final List<int[]> bounds = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    /**
     * Records one inclusive code point range's category.
     *
     * @param from The first code point of the range.
     * @param to The last code point of the range, inclusive.
     * @param category The category name to give the range. Must not be {@code null}.
     */
    private void map(int from, int to, String category) {
      for (int c = from; c <= Math.min(to, Character.MAX_VALUE); c++) {
        bmp[c] = category;
      }
      if (to > Character.MAX_VALUE) {
        bounds.add(new int[] {Math.max(from, Character.MAX_VALUE + 1), to});
        names.add(category);
      }
    }

    /**
     * Folds the recorded mappings into their lookup table.
     *
     * @return The table. Never {@code null}.
     */
    private CategoryTable build(Map<String, Category> categories) throws IOException {
      // Cut the supplementary ranges at every boundary they introduce, so that each
      // resulting elementary interval is covered by a single winning range and the
      // table stays sorted and non-overlapping for binary search.
      final int[] edges = new int[bounds.size() * 2];
      for (int i = 0; i < bounds.size(); i++) {
        edges[i * 2] = bounds.get(i)[0];
        edges[i * 2 + 1] = bounds.get(i)[1] + 1;
      }
      Arrays.sort(edges);
      final List<int[]> intervals = new ArrayList<>();
      final List<String> winners = new ArrayList<>();
      for (int i = 0; i < edges.length - 1; i++) {
        if (edges[i] == edges[i + 1]) {
          continue;
        }
        final String winner = lastCovering(edges[i]);
        if (winner == null) {
          continue;
        }
        final int previous = intervals.size() - 1;
        if (previous >= 0 && intervals.get(previous)[1] == edges[i] - 1
            && winners.get(previous).equals(winner)) {
          intervals.get(previous)[1] = edges[i + 1] - 1;
        } else {
          intervals.add(new int[] {edges[i], edges[i + 1] - 1});
          winners.add(winner);
        }
      }
      final int[] starts = new int[intervals.size()];
      final int[] ends = new int[intervals.size()];
      for (int i = 0; i < intervals.size(); i++) {
        starts[i] = intervals.get(i)[0];
        ends[i] = intervals.get(i)[1];
      }
      final Category[] resolvedBmp = new Category[bmp.length];
      for (int c = 0; c < bmp.length; c++) {
        if (bmp[c] != null) {
          resolvedBmp[c] = resolve(bmp[c], categories, c);
        }
      }
      final Category[] resolvedRanges = new Category[winners.size()];
      for (int i = 0; i < winners.size(); i++) {
        resolvedRanges[i] = resolve(winners.get(i), categories, starts[i]);
      }
      return new CategoryTable(resolvedBmp, starts, ends, resolvedRanges);
    }

    /**
     * Resolves a mapped category name against the defined categories, so a mapping to
     * a name the {@code char.def} category section never defined fails at load with
     * the offending code point instead of falling back silently at lookup time.
     */
    private static Category resolve(String name, Map<String, Category> categories,
        int codePoint) throws IOException {
      final Category category = categories.get(name);
      if (category == null) {
        throw new IOException(String.format(
            "char.def maps U+%04X to the undefined category %s", codePoint, name));
      }
      return category;
    }

    /**
     * Finds the category of the last recorded range covering a code point.
     *
     * @param codePoint The code point to look up.
     * @return The category name, or {@code null} when no recorded range covers it.
     */
    private String lastCovering(int codePoint) {
      for (int i = bounds.size() - 1; i >= 0; i--) {
        final int[] range = bounds.get(i);
        if (codePoint >= range[0] && codePoint <= range[1]) {
          return names.get(i);
        }
      }
      return null;
    }
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

  private final TrieNode lexicon;
  private final int maxSurfaceLength;
  private final short[] connectionCosts;
  private final int rightSize;
  private final Map<String, Category> categories;
  private final CategoryTable categoryTable;
  private final Category defaultCategory;
  private final Map<String, List<WordEntry>> unknownEntries;

  private MecabDictionary(TrieNode lexicon, int maxSurfaceLength,
      short[] connectionCosts, int rightSize, Map<String, Category> categories,
      CategoryTable categoryTable, Map<String, List<WordEntry>> unknownEntries) {
    this.lexicon = lexicon;
    this.maxSurfaceLength = maxSurfaceLength;
    this.connectionCosts = connectionCosts;
    this.rightSize = rightSize;
    this.categories = categories;
    this.categoryTable = categoryTable;
    this.defaultCategory = categories.get("DEFAULT");
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
   * @throws IOException Thrown if reading fails, a required file is missing, a file is
   *         malformed, or a lexicon entry's context ids are outside the
   *         {@code matrix.def} dimensions.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static MecabDictionary load(Path directory, Charset charset) throws IOException {
    if (directory == null || charset == null) {
      throw new IllegalArgumentException("directory and charset must not be null");
    }
    // The connection matrix is read first because its dimensions are what every
    // lexicon entry's context ids have to be inside of.
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
    if (leftSize < 1 || rightSize < 1) {
      throw new IOException("matrix.def dimensions must be positive, got "
          + leftSize + " " + rightSize);
    }
    final long cells = (long) leftSize * rightSize;
    if (cells > Integer.MAX_VALUE) {
      throw new IOException("matrix.def dimensions " + leftSize + " x " + rightSize
          + " overflow the addressable connection matrix");
    }
    final short[] costs = new short[(int) cells];
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
      if (right < 0 || right >= leftSize || left < 0 || left >= rightSize) {
        throw new IOException("malformed matrix.def line " + (i + 1)
            + ": context ids " + right + " " + left
            + " are outside the declared dimensions " + leftSize + " " + rightSize);
      }
      final int cost = parseInt(fields[2], "matrix.def", i + 1);
      if (cost < Short.MIN_VALUE || cost > Short.MAX_VALUE) {
        throw new IOException("malformed matrix.def line " + (i + 1)
            + ": connection cost " + cost + " is outside the 16-bit range the"
            + " format defines");
      }
      costs[right * rightSize + left] = (short) cost;
    }

    final Map<String, List<WordEntry>> lexicon = new HashMap<>();
    int maxSurface = 0;
    try (DirectoryStream<Path> csvFiles = Files.newDirectoryStream(directory, "*.csv")) {
      for (final Path csv : csvFiles) {
        maxSurface = Math.max(maxSurface,
            readLexicon(csv, charset, lexicon, leftSize, rightSize));
      }
    }
    if (lexicon.isEmpty()) {
      throw new IOException("no lexicon entries found under " + directory);
    }

    final Map<String, Category> categories = new HashMap<>();
    final CategoryTableBuilder categoryTable = new CategoryTableBuilder();
    readCharacterDefinition(directory.resolve("char.def"), charset, categories,
        categoryTable);
    final Map<String, List<WordEntry>> unknown = new HashMap<>();
    readLexicon(directory.resolve("unk.def"), charset, unknown, leftSize, rightSize);

    return new MecabDictionary(buildTrie(lexicon), maxSurface, costs, rightSize,
        categories, categoryTable.build(categories), unknown);
  }

  /** Folds the surface-keyed lexicon into a character trie for prefix search. */
  private static TrieNode buildTrie(Map<String, List<WordEntry>> lexicon) {
    final TrieBuilderNode root = new TrieBuilderNode();
    for (final Map.Entry<String, List<WordEntry>> entry : lexicon.entrySet()) {
      TrieBuilderNode node = root;
      final String surface = entry.getKey();
      for (int i = 0; i < surface.length(); i++) {
        node = node.children.computeIfAbsent(surface.charAt(i),
            key -> new TrieBuilderNode());
      }
      node.entries = List.copyOf(entry.getValue());
    }
    return root.freeze();
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
   * @return The length in characters of the longest surface form read.
   * @throws IOException Thrown if the file is missing, an entry is malformed, or an
   *         entry's context id is outside the matrix dimensions.
   */
  private static int readLexicon(Path file, Charset charset,
      Map<String, List<WordEntry>> target, int leftSize, int rightSize)
      throws IOException {
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
      final int leftId = parseInt(fields.get(1), file.toString(), lineNumber);
      final int rightId = parseInt(fields.get(2), file.toString(), lineNumber);
      if (leftId < 0 || leftId >= rightSize) {
        throw new IOException("malformed entry at " + file + " line " + lineNumber
            + ": left context id " + leftId + " is outside the matrix.def dimensions "
            + leftSize + " " + rightSize);
      }
      if (rightId < 0 || rightId >= leftSize) {
        throw new IOException("malformed entry at " + file + " line " + lineNumber
            + ": right context id " + rightId + " is outside the matrix.def dimensions "
            + leftSize + " " + rightSize);
      }
      final WordEntry entry = new WordEntry(leftId, rightId,
          parseInt(fields.get(3), file.toString(), lineNumber),
          List.copyOf(fields.subList(4, fields.size())));
      target.computeIfAbsent(surface, key -> new ArrayList<>(1)).add(entry);
      maxSurface = Math.max(maxSurface, surface.length());
    }
    return maxSurface;
  }

  /** Reads char.def: category behavior lines and code point mapping lines. */
  private static void readCharacterDefinition(Path file, Charset charset,
      Map<String, Category> categories, CategoryTableBuilder categoryTable)
      throws IOException {
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
        if (from > to) {
          throw new IOException("code point range descends at " + file + " line "
              + lineNumber);
        }
        categoryTable.map(from, to, fields[1]);
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
    TrieNode node = lexicon;
    for (int i = 0; i < surface.length() && node != null; i++) {
      node = node.child(surface.charAt(i));
    }
    return node == null ? null : node.entries;
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
    TrieNode node = lexicon;
    for (int i = from; i < to; i++) {
      node = node.child(text.charAt(i));
      if (node == null) {
        return;
      }
      if (node.entries != null) {
        consumer.accept(i - from + 1, node.entries);
      }
    }
  }

  /** @return The length in characters of the longest surface form in the lexicon. */
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
   * Reads a required dictionary file into its lines, accepting LF and CRLF endings.
   *
   * @param file The file to read.
   * @param charset The encoding to decode with.
   * @return The lines without their line terminators. Never {@code null}.
   * @throws IOException Thrown if the file is missing or reading fails.
   */
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

  /**
   * Removes a trailing {@code #} comment from a {@code char.def} line.
   *
   * @param line The raw line.
   * @return The line up to but excluding the first {@code #}, or the whole line when
   *         there is none.
   */
  private static String stripComment(String line) {
    final int hash = line.indexOf('#');
    return hash < 0 ? line : line.substring(0, hash);
  }

  /**
   * Splits a lexicon line at every comma. Surfaces containing commas are not
   * representable, matching the plain-text lexicon format.
   *
   * @param line The line to split.
   * @return The fields in order, empty fields included. Never {@code null}.
   */
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
      return Integer.parseInt(text.trim());
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
   * @throws IOException Thrown if the field is not a valid hexadecimal code point or
   *         names a value no Unicode code point has.
   */
  private static int parseCodePoint(String text, Path file, int lineNumber)
      throws IOException {
    final int codePoint;
    try {
      codePoint = Integer.parseInt(text.trim().substring(2), 16);
    } catch (RuntimeException e) {
      throw new IOException("malformed code point in " + file + " line " + lineNumber, e);
    }
    if (!Character.isValidCodePoint(codePoint)) {
      throw new IOException("code point out of range in " + file + " line " + lineNumber);
    }
    return codePoint;
  }
}
