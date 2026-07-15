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
 * suffix; {@code FLAG} modes {@code char} (default), {@code long}, and {@code num};
 * the {@code SET} encoding declaration. Compounding, continuation classes, and
 * conversion tables are not interpreted in this version; rules using them simply do
 * not fire, so unsupported analyses are missed rather than invented.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @see HunspellStemmer
 * @see HunspellStemmerFactory
 * @since 3.0.0
 */
public final class HunspellDictionary {

  /** One parsed affix rule; {@code affix} is the surface material added to the stem. */
  record Affix(int flag, boolean crossProduct, String strip, String affix,
      AffixCondition condition) {
  }

  private final Map<String, List<int[]>> entries;
  private final List<Affix> prefixes;
  private final List<Affix> suffixes;

  private HunspellDictionary(Map<String, List<int[]>> entries, List<Affix> prefixes,
      List<Affix> suffixes) {
    this.entries = entries;
    this.prefixes = prefixes;
    this.suffixes = suffixes;
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
    final Map<String, List<int[]>> entries =
        parseWordList(new String(readAll(dictionaryStream), charset), affix.flagMode);
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

  private static byte[] readAll(InputStream in) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) >= 0) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /** Finds the {@code SET} declaration by scanning the raw bytes as ASCII. */
  private static Charset declaredCharset(byte[] affixBytes) throws IOException {
    final String ascii = new String(affixBytes, StandardCharsets.US_ASCII);
    for (final String line : splitLines(ascii)) {
      final String trimmed = line.trim();
      if (trimmed.startsWith("SET ") || trimmed.startsWith("SET\t")) {
        final String name = trimmed.substring(4).trim();
        try {
          return Charset.forName(name);
        } catch (RuntimeException e) {
          throw new IOException("unsupported SET encoding: " + name, e);
        }
      }
    }
    return StandardCharsets.UTF_8;
  }

  /** The flag encodings a dictionary may declare. */
  private enum FlagMode {
    CHAR, LONG, NUM
  }

  /** The parsed affix file content. */
  private static final class AffixFile {
    private final List<Affix> prefixes = new ArrayList<>();
    private final List<Affix> suffixes = new ArrayList<>();
    private FlagMode flagMode = FlagMode.CHAR;
  }

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
            default -> throw new IOException(
                "unsupported FLAG mode '" + fields[1] + "' at line " + (i + 1));
          };
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

  /** Parses one PFX or SFX header and its rule lines; returns the next line index. */
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
      final int continuation = affixText.indexOf('/');
      if (continuation >= 0) {
        affixText = affixText.substring(0, continuation);
      }
      if ("0".equals(affixText)) {
        affixText = "";
      }
      final Affix affix = new Affix(flag, crossProduct, strip, affixText,
          AffixCondition.parse(fields[4], suffix, line + 1));
      if (suffix) {
        result.suffixes.add(affix);
      } else {
        result.prefixes.add(affix);
      }
    }
    return line;
  }

  private static Map<String, List<int[]>> parseWordList(String content,
      FlagMode flagMode) throws IOException {
    final String[] lines = splitLines(content);
    final Map<String, List<int[]>> entries = new HashMap<>();
    int start = 0;
    if (lines.length > 0 && isCount(lines[0].trim())) {
      start = 1;
    }
    for (int i = start; i < lines.length; i++) {
      final String line = lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }
      String word = line;
      int[] flags = new int[0];
      final int slash = unescapedSlash(line);
      if (slash >= 0) {
        word = line.substring(0, slash);
        String flagText = line.substring(slash + 1);
        final int fieldEnd = whitespaceIndex(flagText);
        if (fieldEnd >= 0) {
          flagText = flagText.substring(0, fieldEnd);
        }
        flags = parseFlags(flagText, flagMode, i + 1);
      } else {
        final int fieldEnd = whitespaceIndex(word);
        if (fieldEnd >= 0) {
          word = word.substring(0, fieldEnd);
        }
      }
      entries.computeIfAbsent(word.replace("\\/", "/"), key -> new ArrayList<>(1))
          .add(flags);
    }
    return entries;
  }

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

  /** Finds the first {@code /} that is not escaped as {@code \/}. */
  private static int unescapedSlash(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == '/' && (i == 0 || line.charAt(i - 1) != '\\')) {
        return i;
      }
    }
    return -1;
  }

  private static int whitespaceIndex(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (StringUtil.isWhitespace(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  private static int[] parseFlags(String text, FlagMode mode, int lineNumber)
      throws IOException {
    switch (mode) {
      case NUM: {
        final String[] parts = splitOn(text, ',');
        final int[] flags = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
          try {
            flags[i] = Integer.parseInt(parts[i].trim());
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
        final int[] flags = new int[text.length()];
        for (int i = 0; i < flags.length; i++) {
          flags[i] = text.charAt(i);
        }
        return flags;
      }
    }
  }

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
