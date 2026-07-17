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

package opennlp.tools.stemmer.hunspell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.StringUtil;

/**
 * An immutable, in-memory Hunspell-format dictionary: the word list of a {@code .dic}
 * file and the prefix and suffix rules of its {@code .aff} companion, loaded from
 * user-supplied files. The engine is a clean-room implementation of the documented
 * format; no dictionary data is bundled, so the dictionaries' own licenses never attach
 * to this library.
 *
 * <p>Supported affix features: {@code PFX} and {@code SFX} rules with strip strings,
 * character-class conditions, and cross-product combination of one prefix with one
 * suffix; twofold suffixes through the continuation classes on suffix rules;
 * {@code FLAG} modes {@code char} (default), {@code UTF-8}, {@code long}, and
 * {@code num}; the {@code SET} encoding declaration. Compounding and conversion tables
 * are not
 * interpreted in this version; rules using them simply do not fire, so unsupported
 * analyses are missed rather than invented.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @see HunspellStemmer
 * @see HunspellStemmerFactory
 * @since 3.0.0
 */
public final class HunspellDictionary {

  /**
   * One parsed affix rule. {@code affix} is the surface material the rule adds to the
   * stem, {@code strip} is the stem material the rule replaces (restored during
   * analysis), {@code crossProduct} states whether the rule may combine with an affix
   * of the opposite kind, and {@code continuation} lists the flags of further affixes
   * that may stack on top of this one.
   */
  record Affix(int flag, boolean crossProduct, String strip, String affix,
      AffixCondition condition, int[] continuation) {

    /**
     * Checks whether a further affix may stack on this one.
     *
     * @param otherFlag The stacking affix's flag.
     * @return {@code true} if this affix's continuation classes allow it.
     */
    boolean allowsContinuation(int otherFlag) {
      for (final int candidate : continuation) {
        if (candidate == otherFlag) {
          return true;
        }
      }
      return false;
    }
  }

  private final Map<String, List<int[]>> entries;
  private final List<Affix> prefixes;
  private final List<Affix> suffixes;
  private final Map<Character, List<Affix>> suffixesByLast;
  private final List<Affix> suffixesWithoutMaterial;
  private final Map<Character, List<Affix>> prefixesByFirst;
  private final List<Affix> prefixesWithoutMaterial;

  private HunspellDictionary(Map<String, List<int[]>> entries, List<Affix> prefixes,
      List<Affix> suffixes) {
    this.entries = entries;
    this.prefixes = prefixes;
    this.suffixes = suffixes;
    // Undoing a suffix requires the word to end with the rule's affix material, so
    // only rules whose material ends in the word's last character can ever apply;
    // the same holds for prefixes and the first character. Bucketing by that
    // boundary character turns the per-word rule scan from the whole inventory into
    // the one bucket plus the strip-only rules, whose empty material matches
    // everywhere.
    this.suffixesByLast = new HashMap<>();
    this.suffixesWithoutMaterial = new ArrayList<>();
    for (final Affix suffix : suffixes) {
      final String material = suffix.affix();
      if (material.isEmpty()) {
        suffixesWithoutMaterial.add(suffix);
      } else {
        suffixesByLast.computeIfAbsent(material.charAt(material.length() - 1),
            key -> new ArrayList<>()).add(suffix);
      }
    }
    this.prefixesByFirst = new HashMap<>();
    this.prefixesWithoutMaterial = new ArrayList<>();
    for (final Affix prefix : prefixes) {
      final String material = prefix.affix();
      if (material.isEmpty()) {
        prefixesWithoutMaterial.add(prefix);
      } else {
        prefixesByFirst.computeIfAbsent(material.charAt(0),
            key -> new ArrayList<>()).add(prefix);
      }
    }
  }

  /**
   * Loads a dictionary from its two files.
   *
   * @param affixFile The {@code .aff} affix file. Must not be {@code null}.
   * @param dictionaryFile The {@code .dic} word list. Must not be {@code null}.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if reading fails or a file is malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static HunspellDictionary load(Path affixFile, Path dictionaryFile)
      throws IOException {
    if (affixFile == null || dictionaryFile == null) {
      throw new IllegalArgumentException("affixFile and dictionaryFile must not be null");
    }
    try (InputStream affix = Files.newInputStream(affixFile);
         InputStream dictionary = Files.newInputStream(dictionaryFile)) {
      return load(affix, dictionary);
    }
  }

  /**
   * Loads a dictionary from its two streams.
   *
   * @param affixStream The {@code .aff} affix content. Must not be {@code null}. Not
   *                    closed.
   * @param dictionaryStream The {@code .dic} word list content. Must not be
   *                         {@code null}. Not closed.
   * @return The loaded dictionary. Never {@code null}.
   * @throws IOException Thrown if reading fails or the content is malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static HunspellDictionary load(InputStream affixStream,
      InputStream dictionaryStream) throws IOException {
    if (affixStream == null || dictionaryStream == null) {
      throw new IllegalArgumentException("streams must not be null");
    }
    final byte[] affixBytes = readAll(affixStream);
    final Charset charset = declaredCharset(affixBytes);
    final AffixFile affix = parseAffix(new String(affixBytes, charset));
    final Map<String, List<int[]>> entries = parseWordList(
        new String(readAll(dictionaryStream), charset), affix.flagMode,
        affix.flagAliases);
    return new HunspellDictionary(entries, List.copyOf(affix.prefixes),
        List.copyOf(affix.suffixes));
  }

  /**
   * Looks up a word's flag sets.
   *
   * @param word The word exactly as listed.
   * @return The flag sets of all matching entries, or {@code null} when absent.
   */
  List<int[]> lookup(String word) {
    return entries.get(word);
  }

  /** @return The prefix rules. */
  List<Affix> prefixes() {
    return prefixes;
  }

  /** @return The suffix rules. */
  List<Affix> suffixes() {
    return suffixes;
  }

  private static final List<Affix> NO_AFFIXES = List.of();

  /**
   * The suffix rules whose affix material ends in the given character, which are the
   * only material-bearing rules that can be undone from a word ending in it.
   *
   * @param last The word's last character.
   * @return The bucket, possibly empty. Never {@code null}.
   */
  List<Affix> suffixesEndingWith(char last) {
    return suffixesByLast.getOrDefault(last, NO_AFFIXES);
  }

  /** @return The strip-only suffix rules, applicable to any word. Never {@code null}. */
  List<Affix> suffixesWithoutMaterial() {
    return suffixesWithoutMaterial;
  }

  /**
   * The prefix rules whose affix material starts with the given character, which are
   * the only material-bearing rules that can be undone from a word starting with it.
   *
   * @param first The word's first character.
   * @return The bucket, possibly empty. Never {@code null}.
   */
  List<Affix> prefixesStartingWith(char first) {
    return prefixesByFirst.getOrDefault(first, NO_AFFIXES);
  }

  /** @return The strip-only prefix rules, applicable to any word. Never {@code null}. */
  List<Affix> prefixesWithoutMaterial() {
    return prefixesWithoutMaterial;
  }

  /**
   * Checks whether any of a word's flag sets carries a flag.
   *
   * @param flagSets The flag sets from {@link #lookup(String)}.
   * @param flag The flag to look for.
   * @return {@code true} if some flag set contains the flag.
   */
  static boolean hasFlag(List<int[]> flagSets, int flag) {
    for (final int[] flags : flagSets) {
      for (final int candidate : flags) {
        if (candidate == flag) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Reads a stream fully into memory. The stream is not closed.
   *
   * @param in The stream to drain.
   * @return All bytes the stream produced. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   */
  private static byte[] readAll(InputStream in) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /**
   * Finds the {@code SET} declaration by scanning the raw affix bytes as ASCII, which
   * is safe because the declaration itself is ASCII in every supported encoding. Both
   * files are then decoded with the declared charset.
   *
   * @param affixBytes The raw affix file content.
   * @return The declared charset, or UTF-8 when no declaration is present.
   * @throws IOException Thrown if the declared encoding name is not supported.
   */
  private static Charset declaredCharset(byte[] affixBytes) throws IOException {
    final String ascii = new String(affixBytes, StandardCharsets.US_ASCII);
    for (final String line : splitLines(ascii)) {
      final String trimmed = trim(line);
      if (trimmed.startsWith("SET ") || trimmed.startsWith("SET\t")) {
        final String name = trim(trimmed.substring(4));
        try {
          return Charset.forName(name);
        } catch (RuntimeException e) {
          throw new IOException("unsupported SET encoding: " + name, e);
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  /** The flag encodings a dictionary may declare with the {@code FLAG} directive. */
  private enum FlagMode {
    /**
     * The default: each single character is one flag. Also what {@code FLAG UTF-8}
     * declares, which asks for single-character flags in a file the {@code SET}
     * declaration already had decoded.
     */
    CHAR,
    /** Declared as {@code FLAG long}: each pair of characters is one flag. */
    LONG,
    /** Declared as {@code FLAG num}: comma-separated decimal numbers are flags. */
    NUM
  }

  /** The parsed affix file content. */
  private static final class AffixFile {
    private final List<Affix> prefixes = new ArrayList<>();
    private final List<Affix> suffixes = new ArrayList<>();
    private final List<int[]> flagAliases = new ArrayList<>();
    private boolean aliasHeaderSeen;
    private FlagMode flagMode = FlagMode.CHAR;
  }

  /**
   * Parses the affix file: the {@code FLAG} declaration, the {@code AF} flag alias
   * table, and the {@code PFX} and {@code SFX} blocks. Directives outside the
   * supported set (compounding, conversion tables, suggestion options, ...) are
   * skipped, so their rules never fire and unsupported analyses are missed rather
   * than invented.
   *
   * @param content The decoded affix file content.
   * @return The parsed rules and flag mode. Never {@code null}.
   * @throws IOException Thrown if a supported directive is malformed.
   */
  private static AffixFile parseAffix(String content) throws IOException {
    final AffixFile result = new AffixFile();
    final String[] lines = splitLines(content);
    int i = 0;
    while (i < lines.length) {
      final String[] fields = split(lines[i]);
      if (fields.length == 0 || fields[0].startsWith("#")) {
        i++;
        continue;
      }
      switch (fields[0]) {
        case "FLAG":
          if (fields.length < 2) {
            throw new IOException("FLAG line without a mode at line " + (i + 1));
          }
          result.flagMode = switch (fields[1]) {
            case "long" -> FlagMode.LONG;
            case "num" -> FlagMode.NUM;
            case "UTF-8" -> FlagMode.CHAR;
            default -> throw new IOException(
                "unsupported FLAG mode '" + fields[1] + "' at line " + (i + 1));
          };
          i++;
          break;
        case "AF":
          // the first AF line declares the alias count; every further AF line is one
          // alias, a flag run whose 1-based position numeric dictionary flags refer to
          if (fields.length >= 2) {
            if (!result.aliasHeaderSeen) {
              result.aliasHeaderSeen = true;
            } else {
              result.flagAliases.add(parseFlags(fields[1], result.flagMode, i + 1));
            }
          }
          i++;
          break;
        case "PFX":
        case "SFX":
          i = parseAffixBlock(lines, i, fields, result);
          break;
        default:
          i++;
          break;
      }
    }
    return result;
  }

  /**
   * Parses one {@code PFX} or {@code SFX} block: the header line naming the flag, the
   * cross-product marker, and the rule count, followed by exactly that many rule
   * lines.
   *
   * @param lines All lines of the affix file.
   * @param index The line index of the block header.
   * @param header The already-split header fields.
   * @param result The parse target the rules are added to.
   * @return The index of the first line after the block.
   * @throws IOException Thrown if the header or a rule line is malformed.
   */
  private static int parseAffixBlock(String[] lines, int index, String[] header,
      AffixFile result) throws IOException {
    if (header.length < 4) {
      throw new IOException("malformed affix header at line " + (index + 1));
    }
    final boolean suffix = "SFX".equals(header[0]);
    final int flag = parseFlag(header[1], result.flagMode, index + 1);
    final boolean crossProduct = "Y".equals(header[2]);
    final int count;
    try {
      count = Integer.parseInt(header[3]);
    } catch (NumberFormatException e) {
      throw new IOException("malformed affix rule count at line " + (index + 1), e);
    }
    int line = index + 1;
    for (int rule = 0; rule < count; rule++, line++) {
      if (line >= lines.length) {
        throw new IOException("affix block truncated at line " + (line + 1));
      }
      final String[] fields = split(lines[line]);
      if (fields.length < 5 || !fields[0].equals(header[0])) {
        throw new IOException("malformed affix rule at line " + (line + 1));
      }
      final String strip = "0".equals(fields[2]) ? "" : fields[2];
      String affixText = fields[3];
      int[] continuation = new int[0];
      final int slash = affixText.indexOf('/');
      if (slash >= 0) {
        continuation = parseFlags(affixText.substring(slash + 1), result.flagMode, line + 1);
        affixText = affixText.substring(0, slash);
      }
      if ("0".equals(affixText)) {
        affixText = "";
      }
      final Affix affix = new Affix(flag, crossProduct, strip, affixText,
          AffixCondition.parse(fields[4], suffix, line + 1), continuation);
      if (suffix) {
        result.suffixes.add(affix);
      } else {
        result.prefixes.add(affix);
      }
    }
    return line;
  }

  /**
   * Parses the word list: an optional leading entry count, then one entry per line
   * consisting of the word, an optional {@code /flags} run, and optional trailing
   * morphological fields, which are ignored. The morphological fields are cut off
   * first, because the flag separator is only meaningful in what precedes them; a word
   * may itself contain spaces. A slash escaped as {@code \/} belongs to the word itself
   * and is unescaped in the stored key.
   *
   * @param content The decoded word-list content.
   * @param flagMode The flag encoding declared by the affix file.
   * @param flagAliases The affix file's {@code AF} alias table, possibly empty. When
   *                    it is not empty, a purely numeric flag field is a 1-based
   *                    reference into it rather than a flag run of its own.
   * @return The words mapped to the flag sets of their entries. Never {@code null}.
   * @throws IOException Thrown if a flag run is malformed or an alias reference is
   *         out of range.
   */
  private static Map<String, List<int[]>> parseWordList(String content,
      FlagMode flagMode, List<int[]> flagAliases) throws IOException {
    final String[] lines = splitLines(content);
    final Map<String, List<int[]>> entries = new HashMap<>();
    int start = 0;
    if (lines.length > 0 && isCount(trim(lines[0]))) {
      start = 1;
    }
    for (int i = start; i < lines.length; i++) {
      final String line = trim(lines[i]);
      if (line.isEmpty()) {
        continue;
      }
      final int morphology = morphologyIndex(line);
      final String entry = morphology < 0 ? line : trim(line.substring(0, morphology));
      String word = entry;
      int[] flags = new int[0];
      final int slash = unescapedSlash(entry);
      if (slash >= 0) {
        word = entry.substring(0, slash);
        String flagRun = entry.substring(slash + 1);
        // The flag run ends at the first space or tabulator, the separators the
        // word-list format defines; whatever follows is a morphological field even
        // when it carries no two-letter tag, which hunspell tolerates and so do we.
        for (int c = 0; c < flagRun.length(); c++) {
          if (isFieldSeparator(flagRun.charAt(c))) {
            flagRun = flagRun.substring(0, c);
            break;
          }
        }
        if (!flagAliases.isEmpty() && isCount(flagRun)) {
          final int alias = Integer.parseInt(flagRun);
          if (alias < 1 || alias > flagAliases.size()) {
            throw new IOException("flag alias " + alias + " at line " + (i + 1)
                + " is outside the AF table of " + flagAliases.size() + " aliases");
          }
          flags = flagAliases.get(alias - 1);
        } else {
          flags = parseFlags(flagRun, flagMode, i + 1);
        }
      }
      entries.computeIfAbsent(word.replace("\\/", "/"), key -> new ArrayList<>(1))
          .add(flags);
    }
    return entries;
  }

  /**
   * Checks whether a line consists purely of decimal digits, which identifies the
   * optional entry-count header of a word list.
   *
   * @param line The trimmed line to inspect.
   * @return {@code true} if the line is a non-empty digit run.
   */
  private static boolean isCount(String line) {
    if (line.isEmpty()) {
      return false;
    }
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) < '0' || line.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }

  /**
   * Finds the first {@code /} that is not escaped as {@code \/}, which separates the
   * word from its flag run in a word-list entry.
   *
   * @param line The word-list line to scan.
   * @return The index of the separator, or {@code -1} when the entry has no flags.
   */
  private static int unescapedSlash(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == '/' && (i == 0 || line.charAt(i - 1) != '\\')) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Finds where the trailing morphological fields of a word-list entry begin, which
   * terminates the word and its flag run. A morphological field is either introduced by
   * a tabulator, the older separator, or written as a two-letter tag followed by
   * {@code :} and preceded by a separator, such as {@code po:verb}. A separator that
   * is not followed by such a tag belongs to the word, because a word-list entry may
   * name several words. The separators are the space and the tabulator, exactly the
   * two characters the reference implementation's {@code hashmgr.cxx} splits on; they
   * are format delimiters of the word-list grammar, not a whitespace judgment, so
   * wider whitespace such as a no-break space stays part of the word by design.
   *
   * @param line The trimmed word-list line to scan.
   * @return The index at which the morphological fields begin, or {@code -1} if the
   *         entry carries none.
   */
  private static int morphologyIndex(String line) {
    int cut = -1;
    for (int i = 4; i < line.length(); i++) {
      if (line.charAt(i) == ':' && isFieldSeparator(line.charAt(i - 3))) {
        int fieldStart = i - 3;
        while (fieldStart > 0 && isFieldSeparator(line.charAt(fieldStart - 1))) {
          fieldStart--;
        }
        // a tag with no word in front of it is not a morphological field
        cut = fieldStart == 0 ? -1 : fieldStart;
        break;
      }
    }
    final int tab = line.indexOf('\t');
    if (tab >= 0 && (cut < 0 || tab < cut)) {
      cut = tab;
    }
    return cut;
  }

  /**
   * Checks one character against the word-list format's field separators, space and
   * tabulator, the exact set the reference implementation splits morphological fields
   * on.
   *
   * @param c The character to test.
   * @return {@code true} if {@code c} separates fields in the word-list format.
   */
  private static boolean isFieldSeparator(char c) {
    return c == ' ' || c == '\t';
  }

  /**
   * Removes leading and trailing whitespace, using the whitespace definition the rest
   * of the parser scans with.
   *
   * @param text The text to trim.
   * @return The text without leading or trailing whitespace. Never {@code null}.
   */
  private static String trim(String text) {
    int start = 0;
    int end = text.length();
    while (start < end && StringUtil.isWhitespace(text.charAt(start))) {
      start++;
    }
    while (end > start && StringUtil.isWhitespace(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(start, end);
  }

  /**
   * Parses a flag run according to the declared flag mode: single characters in
   * {@code char} mode, character pairs packed into one {@code int} in {@code long}
   * mode, and comma-separated decimal numbers in {@code num} mode.
   *
   * @param text The flag run without its leading {@code /}. An empty run carries no
   *             flags in every mode.
   * @param mode The declared flag encoding.
   * @param lineNumber The source line, for error messages.
   * @return The parsed flags. Never {@code null}.
   * @throws IOException Thrown if the run does not fit the declared encoding.
   */
  private static int[] parseFlags(String text, FlagMode mode, int lineNumber)
      throws IOException {
    if (text.isEmpty()) {
      return new int[0];
    }
    switch (mode) {
      case NUM: {
        final String[] parts = splitOn(text, ',');
        final int[] flags = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
          try {
            flags[i] = Integer.parseInt(trim(parts[i]));
          } catch (NumberFormatException e) {
            throw new IOException("malformed numeric flag at line " + lineNumber, e);
          }
        }
        return flags;
      }
      case LONG: {
        if (text.length() % 2 != 0) {
          throw new IOException("odd long-flag run at line " + lineNumber);
        }
        final int[] flags = new int[text.length() / 2];
        for (int i = 0; i < flags.length; i++) {
          flags[i] = (text.charAt(2 * i) << 16) | text.charAt(2 * i + 1);
        }
        return flags;
      }
      default: {
        // One flag per code point: published dictionaries, the Spanish one of the
        // LibreOffice collection among them, name affix rules with supplementary
        // characters under FLAG UTF-8, and reading per UTF-16 unit would split such
        // a flag into two and reject the rule header as carrying two flags. A
        // variation selector after a flag character selects its presentation, the
        // emoji telephone against the text telephone, and is no flag of its own; the
        // same collection writes such selectors, so they are dropped from flag
        // identity.
        final int[] buffer = new int[text.codePointCount(0, text.length())];
        int f = 0;
        for (int i = 0; i < text.length(); ) {
          final int codePoint = text.codePointAt(i);
          i += Character.charCount(codePoint);
          if (codePoint >= 0xFE00 && codePoint <= 0xFE0F) {
            continue;
          }
          buffer[f++] = codePoint;
        }
        return f == buffer.length ? buffer : Arrays.copyOf(buffer, f);
      }
    }
  }

  /**
   * Parses a field that must contain exactly one flag, such as the flag name in an
   * affix block header.
   *
   * @param text The flag field.
   * @param mode The declared flag encoding.
   * @param lineNumber The source line, for error messages.
   * @return The single parsed flag.
   * @throws IOException Thrown if the field holds no flag or more than one.
   */
  private static int parseFlag(String text, FlagMode mode, int lineNumber)
      throws IOException {
    final int[] flags = parseFlags(text, mode, lineNumber);
    if (flags.length != 1) {
      throw new IOException("expected exactly one flag at line " + lineNumber);
    }
    return flags[0];
  }

  /** Splits text into lines with a single character scan, tolerating CRLF endings. */
  private static String[] splitLines(String content) {
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
    return lines.toArray(new String[0]);
  }

  /** Splits text on a separator character with a single character scan. */
  private static String[] splitOn(String text, char separator) {
    final List<String> parts = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == separator) {
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    return parts.toArray(new String[0]);
  }

  /** Splits a line on whitespace with a single character scan. */
  private static String[] split(String line) {
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
}
