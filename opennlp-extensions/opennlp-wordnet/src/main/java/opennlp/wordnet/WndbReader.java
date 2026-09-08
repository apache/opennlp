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
package opennlp.wordnet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * Reads a Princeton WordNet database directory in the
 * <a href="https://wordnet.princeton.edu/documentation/wndb5wn">WNDB format</a>
 * ({@code index.noun}, {@code data.noun}, and the corresponding pairs for verbs, adjectives, and
 * adverbs) into a {@link LexicalKnowledgeBase}.
 *
 * <p>All eight index and data files must be present. License preamble lines (which begin with a
 * space in the released files) are skipped. {@code index.sense} is not read, and the
 * {@code *.exc} exception lists are the {@link MorphyLemmatizer} companion input, read
 * separately.</p>
 *
 * <p>Synset ids use {@code wndb-}<i>offset</i>{@code -}<i>pos</i>, formed from the data file's
 * 8-digit byte offset and part-of-speech letter, for example {@code wndb-00001740-n}; the id is
 * opaque to consumers. Adjective satellite lines normalize to {@link WordNetPOS#ADJECTIVE}, the
 * syntactic markers the adjective files append ({@code (p)}, {@code (a)}, {@code (ip)}) are
 * stripped, and underscores in lemmas become spaces. Sense order per lemma follows the index
 * file's offset order.</p>
 *
 * <p>Malformed content causes an {@link InvalidFormatException} naming the file and line; I/O
 * failures propagate as {@link IOException}. The returned lexicon is immutable and safe for
 * concurrent lookups.</p>
 *
 * @since 3.0.0
 */
public final class WndbReader {

  /** The WNDB pointer symbols this reader accepts, mapped to the contract relations. */
  private static final Map<String, WordNetRelation> POINTER_SYMBOLS = pointerSymbols();

  /** Pointer symbols used by index files, where instance and domain subtypes are collapsed. */
  private static final Set<String> INDEX_POINTER_SYMBOLS = Set.of(
      "!", "@", "~", "#m", "#s", "#p", "%m", "%s", "%p", "=", "+", "*", ">", "^",
      "$", "&", "<", "\\", ";", "-");

  /** The prefix used for every synset id returned by this reader. */
  private static final String SYNSET_ID_PREFIX = "wndb-";

  /** The failure detail for a synset offset field that is not exactly 8 digits. */
  private static final String MALFORMED_OFFSET = "Synset offset must be 8 digits, got: ";

  /** Not instantiable. */
  private WndbReader() {
  }

  /**
   * Builds a synset id in this reader's scheme: the {@code wndb-} prefix, the 8-digit data-file
   * byte offset, a hyphen, and the part-of-speech letter, for example {@code wndb-00001740-n}.
   *
   * @param offset  The 8-digit synset offset field.
   * @param posChar The WNDB part-of-speech letter.
   * @return The synset id.
   */
  private static String synsetId(String offset, char posChar) {
    return SYNSET_ID_PREFIX + offset + '-' + posChar;
  }

  /**
   * Reads a WNDB database directory.
   *
   * @param directory The directory containing the eight index and data files. Must not be
   *                  {@code null} and must exist.
   * @return The loaded lexicon.
   * @throws IllegalArgumentException Thrown if {@code directory} is {@code null} or not a
   *     directory.
   * @throws InvalidFormatException Thrown if a database file is missing or any file is
   *     malformed; the message names the file and line.
   * @throws IOException Thrown if reading a file fails.
   */
  public static LexicalKnowledgeBase read(Path directory) throws IOException {
    if (directory == null) {
      throw new IllegalArgumentException("directory must not be null");
    }
    if (!Files.isDirectory(directory)) {
      throw new IllegalArgumentException(
          "directory does not exist or is not a directory: " + directory);
    }
    final Map<String, RawSynset> rawSynsets = new LinkedHashMap<>();
    for (final FilePos filePos : FilePos.values()) {
      parseDataFile(directory, filePos, rawSynsets);
    }
    final Map<String, Synset> synsetsById = resolve(rawSynsets);
    final Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senseOrder = new LinkedHashMap<>();
    for (final FilePos filePos : FilePos.values()) {
      parseIndexFile(directory, filePos, rawSynsets, senseOrder);
    }
    return new InMemoryWordNetLexicon(synsetsById, senseOrder);
  }

  /** The four part-of-speech file pairs of a WNDB directory. */
  private enum FilePos {
    NOUN("noun", 'n', WordNetPOS.NOUN),
    VERB("verb", 'v', WordNetPOS.VERB),
    ADJECTIVE("adj", 'a', WordNetPOS.ADJECTIVE),
    ADVERB("adv", 'r', WordNetPOS.ADVERB);

    private final String suffix;
    private final char posChar;
    private final WordNetPOS pos;

    /**
     * Binds a part of speech to its file suffix and WNDB letter.
     *
     * @param suffix  The file suffix, for example {@code noun}.
     * @param posChar The WNDB part-of-speech letter.
     * @param pos     The mapped part of speech.
     */
    FilePos(String suffix, char posChar, WordNetPOS pos) {
      this.suffix = suffix;
      this.posChar = posChar;
      this.pos = pos;
    }
  }

  /**
   * Parses one {@code data.*} file, collecting its synsets by id.
   *
   * @param directory  The database directory.
   * @param filePos    The part-of-speech file pair.
   * @param rawSynsets The accumulating synset table.
   * @throws IOException Thrown if the file is missing, malformed, or unreadable.
   */
  private static void parseDataFile(Path directory, FilePos filePos,
                                    Map<String, RawSynset> rawSynsets) throws IOException {
    final String fileName = "data." + filePos.suffix;
    final byte[] bytes = readAll(directory.resolve(fileName));
    int lineStart = 0;
    int lineNumber = 0;
    while (lineStart < bytes.length) {
      lineNumber++;
      int lineEnd = lineStart;
      while (lineEnd < bytes.length && bytes[lineEnd] != '\n') {
        lineEnd++;
      }
      // ISO-8859-1 decodes bytes one-to-one, keeping offsets exact for any released file.
      final String line =
          new String(bytes, lineStart, lineEnd - lineStart, StandardCharsets.ISO_8859_1);
      if (!line.isEmpty() && line.charAt(0) != ' ') {
        parseDataLine(line, lineStart, fileName, lineNumber, filePos, rawSynsets);
      }
      lineStart = lineEnd + 1;
    }
  }

  /**
   * Parses one data-file synset line into a raw synset.
   *
   * @param line       The decoded line, without its trailing newline.
   * @param byteOffset The line's byte offset, matched against the line's own offset field.
   * @param fileName   The data file name, for error reporting.
   * @param lineNumber The 1-based line number.
   * @param filePos    The part-of-speech file pair.
   * @param rawSynsets The accumulating synset table.
   * @throws InvalidFormatException Thrown if the line is malformed or its offset field disagrees
   *     with its byte position.
   */
  private static void parseDataLine(String line, int byteOffset, String fileName, int lineNumber,
                                    FilePos filePos, Map<String, RawSynset> rawSynsets)
      throws InvalidFormatException {
    final Tokenizer tokens = new Tokenizer(line, fileName, lineNumber);
    final String offsetField = tokens.next("synset_offset");
    if (parseOffset(offsetField, tokens) != byteOffset) {
      throw malformed(fileName, lineNumber, "Synset offset field " + offsetField
          + " disagrees with the actual byte position " + byteOffset);
    }
    final int lexicographerFile = tokens.nextFixedInt(
        "lex_filenum", 10, 2);
    if (lexicographerFile > 44) {
      throw malformed(fileName, lineNumber,
          "lex_filenum must be between 00 and 44, got: " + lexicographerFile);
    }
    final String ssType = tokens.next("ss_type (synset type)");
    final boolean validType = switch (filePos) {
      case ADJECTIVE -> "a".equals(ssType) || "s".equals(ssType);
      default -> ssType.length() == 1 && ssType.charAt(0) == filePos.posChar;
    };
    if (!validType) {
      throw malformed(fileName, lineNumber,
          "Synset type " + ssType + " does not belong in " + fileName);
    }
    final int wordCount = tokens.nextFixedCount(
        "w_cnt", 16, 2, "Word count");
    if (wordCount < 1) {
      throw malformed(fileName, lineNumber, "Word count must be at least 1, got: " + wordCount);
    }
    final List<String> lemmas = new ArrayList<>(wordCount);
    for (int i = 0; i < wordCount; i++) {
      final String lemma = cleanLemma(tokens.next("word"), fileName, lineNumber, filePos);
      tokens.nextFixedInt("lex_id", 16, 1);
      if (!lemmas.contains(lemma)) {
        lemmas.add(lemma);
      }
    }
    final int pointerCount = tokens.nextFixedCount(
        "p_cnt", 10, 3, "Pointer count");
    final List<RawPointer> pointers = new ArrayList<>(pointerCount);
    for (int i = 0; i < pointerCount; i++) {
      final String symbol = tokens.next("pointer_symbol");
      final WordNetRelation relation = POINTER_SYMBOLS.get(symbol);
      if (relation == null) {
        throw malformed(fileName, lineNumber, "Undeclared pointer symbol: " + symbol);
      }
      final String targetOffset = tokens.next("pointer synset_offset");
      parseOffset(targetOffset, tokens);
      final char targetPos = posChar(tokens.next("pointer pos"), tokens);
      final int sourceTarget = tokens.nextFixedInt("pointer source/target", 16, 4);
      final int sourceWord = sourceTarget >>> 8;
      final int targetWord = sourceTarget & 0xff;
      if (sourceWord > wordCount) {
        throw malformed(fileName, lineNumber, "Pointer source word " + sourceWord
            + " exceeds word count " + wordCount);
      }
      if ((sourceWord == 0) != (targetWord == 0)) {
        throw malformed(fileName, lineNumber,
            "Pointer source and target words must both be zero or nonzero");
      }
      pointers.add(new RawPointer(relation, synsetId(targetOffset, targetPos),
          targetWord, lineNumber));
    }
    if (filePos == FilePos.VERB) {
      final int frameCount = tokens.nextFixedCount(
          "f_cnt", 10, 2, "Verb frame count");
      for (int i = 0; i < frameCount; i++) {
        final String marker = tokens.next("verb frame marker");
        if (!"+".equals(marker)) {
          throw malformed(fileName, lineNumber,
              "Expected + before a verb frame, got: " + marker);
        }
        final int frameNumber = tokens.nextFixedInt("f_num", 10, 2);
        if (frameNumber < 1 || frameNumber > 35) {
          throw malformed(fileName, lineNumber,
              "f_num must be between 01 and 35, got: " + frameNumber);
        }
        final int wordNumber = tokens.nextFixedInt("w_num", 16, 2);
        if (wordNumber > wordCount) {
          throw malformed(fileName, lineNumber, "Verb frame word " + wordNumber
              + " exceeds word count " + wordCount);
        }
      }
    }
    final String gloss = tokens.gloss();
    final String id = synsetId(offsetField, filePos.posChar);
    rawSynsets.put(id, new RawSynset(id, filePos.pos, lemmas, gloss, pointers,
        fileName, lineNumber));
  }

  /**
   * Resolves raw synsets into contract synsets, validating every pointer target.
   *
   * @param rawSynsets The parsed synsets keyed by id.
   * @return The contract synsets keyed by id.
   * @throws InvalidFormatException Thrown if a pointer targets a nonexistent synset.
   */
  private static Map<String, Synset> resolve(Map<String, RawSynset> rawSynsets)
      throws InvalidFormatException {
    final Map<String, Synset> synsetsById =
        LinkedHashMap.newLinkedHashMap(rawSynsets.size());
    for (final RawSynset raw : rawSynsets.values()) {
      final Map<WordNetRelation, LinkedHashSet<String>> typed = new LinkedHashMap<>();
      for (final RawPointer pointer : raw.pointers) {
        final RawSynset target = rawSynsets.get(pointer.targetId);
        if (target == null) {
          throw malformed(raw.fileName, pointer.lineNumber, "Synset " + raw.id + " has a "
              + pointer.relation + " pointer to nonexistent synset " + pointer.targetId);
        }
        if (pointer.targetWord > target.lemmas.size()) {
          throw malformed(raw.fileName, pointer.lineNumber, "Pointer target word "
              + pointer.targetWord + " exceeds target word count " + target.lemmas.size());
        }
        // Share the synset table's id instance so only one copy of each id is retained.
        typed.computeIfAbsent(pointer.relation, unused -> new LinkedHashSet<>())
            .add(target.id);
      }
      final Map<WordNetRelation, List<String>> relations =
          LinkedHashMap.newLinkedHashMap(typed.size());
      for (final Map.Entry<WordNetRelation, LinkedHashSet<String>> entry : typed.entrySet()) {
        relations.put(entry.getKey(), List.copyOf(entry.getValue()));
      }
      synsetsById.put(raw.id, new Synset(raw.id, raw.pos, raw.lemmas, raw.gloss, relations));
    }
    return synsetsById;
  }

  /**
   * Parses one {@code index.*} file, building the sense order per folded lemma key.
   *
   * @param directory  The database directory.
   * @param filePos    The part-of-speech file pair.
   * @param rawSynsets The resolved synset table, for offset validation.
   * @param senses     The accumulating sense-order map.
   * @throws IOException Thrown if the file is missing, malformed, or unreadable.
   */
  private static void parseIndexFile(Path directory, FilePos filePos,
                                     Map<String, RawSynset> rawSynsets,
                                     Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senses)
      throws IOException {
    final String fileName = "index." + filePos.suffix;
    final byte[] bytes = readAll(directory.resolve(fileName));
    final String content = new String(bytes, StandardCharsets.ISO_8859_1);
    int lineNumber = 0;
    int lineStart = 0;
    while (lineStart < content.length()) {
      lineNumber++;
      int lineEnd = content.indexOf('\n', lineStart);
      if (lineEnd < 0) {
        lineEnd = content.length();
      }
      final String line = content.substring(lineStart, lineEnd);
      if (!line.isEmpty() && line.charAt(0) != ' ') {
        parseIndexLine(line, fileName, lineNumber, filePos, rawSynsets, senses);
      }
      lineStart = lineEnd + 1;
    }
  }

  /**
   * Parses one index-file line into a lemma's sense order.
   *
   * @param line       The line to parse.
   * @param fileName   The index file name, for error reporting.
   * @param lineNumber The 1-based line number.
   * @param filePos    The part-of-speech file pair.
   * @param rawSynsets The resolved synset table, for offset validation.
   * @param senses     The accumulating sense-order map.
   * @throws InvalidFormatException Thrown if the line is malformed or references an unknown
   *     offset.
   */
  private static void parseIndexLine(String line, String fileName, int lineNumber,
                                     FilePos filePos, Map<String, RawSynset> rawSynsets,
                                     Map<InMemoryWordNetLexicon.LemmaKey, List<String>> senses)
      throws InvalidFormatException {
    final Tokenizer tokens = new Tokenizer(line, fileName, lineNumber);
    final String lemma = tokens.next("lemma");
    final String pos = tokens.next("pos");
    if (pos.length() != 1 || pos.charAt(0) != filePos.posChar) {
      throw malformed(fileName, lineNumber, "Index pos " + pos + " does not belong in "
          + fileName);
    }
    final int synsetCount = tokens.nextNonNegativeInt("synset count", "Synset count");
    if (synsetCount < 1) {
      throw malformed(fileName, lineNumber,
          "Synset count must be at least 1, got: " + synsetCount);
    }
    final int pointerTypeCount = tokens.nextNonNegativeInt("pointer count", "Pointer count");
    final LinkedHashSet<String> pointerTypes = new LinkedHashSet<>();
    for (int i = 0; i < pointerTypeCount; i++) {
      // The summary symbols are informational; the data file's pointers are authoritative.
      final String symbol = tokens.next("ptr_symbol (pointer symbol)");
      if (!INDEX_POINTER_SYMBOLS.contains(symbol)) {
        throw malformed(fileName, lineNumber, "Undeclared pointer symbol: " + symbol);
      }
      if (!pointerTypes.add(symbol)) {
        throw malformed(fileName, lineNumber, "Duplicate pointer symbol: " + symbol);
      }
    }
    final int senseCount = tokens.nextNonNegativeInt("sense count", "Sense count");
    if (senseCount != synsetCount) {
      throw malformed(fileName, lineNumber, "Sense count " + senseCount
          + " does not match synset count " + synsetCount);
    }
    final int taggedSenseCount = tokens.nextNonNegativeInt(
        "tagged-sense count", "Tagged-sense count");
    if (taggedSenseCount > senseCount) {
      throw malformed(fileName, lineNumber, "Tagged-sense count " + taggedSenseCount
          + " exceeds sense count " + senseCount);
    }
    if (synsetCount > tokens.remainingFixedWidthFields(8)) {
      throw malformed(fileName, lineNumber, "Synset count " + synsetCount
          + " exceeds the available offset fields");
    }
    final List<String> order = new ArrayList<>(synsetCount);
    for (int i = 0; i < synsetCount; i++) {
      final String offset = tokens.next("synset_offset");
      parseOffset(offset, tokens);
      final String synsetId = synsetId(offset, filePos.posChar);
      if (!rawSynsets.containsKey(synsetId)) {
        throw malformed(fileName, lineNumber, "Lemma " + lemma + " references offset " + offset
            + " with no data." + filePos.suffix + " line");
      }
      if (order.contains(synsetId)) {
        throw malformed(fileName, lineNumber, "Duplicate synset offset " + offset);
      }
      order.add(synsetId);
    }
    tokens.requireEnd("synset offsets");
    final InMemoryWordNetLexicon.LemmaKey key =
        InMemoryWordNetLexicon.LemmaKey.of(lemma, filePos.pos);
    final List<String> existing = senses.get(key);
    if (existing == null) {
      senses.put(key, order);
    } else {
      // Two index lemmas can fold to one key; keep first-listed order and append the rest.
      for (final String synsetId : order) {
        if (!existing.contains(synsetId)) {
          existing.add(synsetId);
        }
      }
    }
  }

  /**
   * Strips the adjective syntactic markers ({@code (p)}, {@code (a)}, {@code (ip)}) and turns
   * underscores into spaces.
   *
   * @param word       The raw word field.
   * @param fileName   The data file name, for error reporting.
   * @param lineNumber The 1-based line number.
   * @param filePos    The part-of-speech file being parsed.
   * @return The cleaned lemma.
   * @throws InvalidFormatException Thrown if the word is empty, contains an unknown marker, or
   *     uses an adjective marker outside {@code data.adj}.
   */
  private static String cleanLemma(String word, String fileName, int lineNumber, FilePos filePos)
      throws InvalidFormatException {
    String cleaned = word;
    if (cleaned.endsWith(")")) {
      final int open = cleaned.lastIndexOf('(');
      final String marker = open < 0 ? "" : cleaned.substring(open);
      if (!"(p)".equals(marker) && !"(a)".equals(marker) && !"(ip)".equals(marker)) {
        throw malformed(fileName, lineNumber, "Unknown syntactic marker on word: " + word);
      }
      if (filePos != FilePos.ADJECTIVE) {
        throw malformed(fileName, lineNumber,
            "Syntactic marker " + marker + " is only valid in data.adj");
      }
      cleaned = cleaned.substring(0, open);
    }
    if (cleaned.isEmpty()) {
      throw malformed(fileName, lineNumber, "Empty word field");
    }
    return cleaned.replace('_', ' ');
  }

  /**
   * Parses an 8-digit synset offset.
   *
   * @param offset The offset field.
   * @param tokens The tokenizer, for error reporting.
   * @return The offset as an integer.
   * @throws InvalidFormatException Thrown if the field is not 8 digits.
   */
  private static int parseOffset(String offset, Tokenizer tokens) throws InvalidFormatException {
    if (offset.length() != 8) {
      throw tokens.malformedToken(MALFORMED_OFFSET + offset);
    }
    int value = 0;
    for (int i = 0; i < 8; i++) {
      final char c = offset.charAt(i);
      if (c < '0' || c > '9') {
        throw tokens.malformedToken(MALFORMED_OFFSET + offset);
      }
      value = value * 10 + (c - '0');
    }
    return value;
  }

  /**
   * Parses a pointer's one-letter part-of-speech code.
   *
   * @param pos    The code field.
   * @param tokens The tokenizer, for error reporting.
   * @return One of {@code n}, {@code v}, {@code a}, {@code r}.
   * @throws InvalidFormatException Thrown if the code is not one of those letters.
   */
  private static char posChar(String pos, Tokenizer tokens) throws InvalidFormatException {
    if (pos.length() == 1) {
      final char c = pos.charAt(0);
      if (c == 'n' || c == 'v' || c == 'a' || c == 'r') {
        return c;
      }
    }
    throw tokens.malformedToken("Pointer pos must be one of n, v, a, r, got: " + pos);
  }

  /**
   * Reads a required database file in full.
   *
   * @param file The file path.
   * @return The file bytes.
   * @throws InvalidFormatException Thrown if the file is missing.
   * @throws IOException Thrown if reading fails.
   */
  private static byte[] readAll(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new InvalidFormatException("Missing WNDB database file: " + file);
    }
    return Files.readAllBytes(file);
  }

  /**
   * Builds a malformed-file exception naming the file and line.
   *
   * @param fileName   The file name.
   * @param lineNumber The 1-based line number.
   * @param message    The failure detail.
   * @return The exception to throw.
   */
  private static InvalidFormatException malformed(String fileName, int lineNumber,
                                                  String message) {
    return new InvalidFormatException(malformedMessage(fileName, lineNumber, message));
  }

  /**
   * Builds the malformed-file message naming the file and line.
   *
   * @param fileName   The file name.
   * @param lineNumber The 1-based line number.
   * @param message    The failure detail.
   * @return The message text.
   */
  private static String malformedMessage(String fileName, int lineNumber, String message) {
    return "Malformed WNDB file " + fileName + " at line " + lineNumber + ": " + message;
  }

  /** A cursor over one line's space-separated fields. */
  private static final class Tokenizer {

    private final String line;
    private final String fileName;
    private final int lineNumber;
    private int position;

    /**
     * Creates a tokenizer over one line.
     *
     * @param line       The line to tokenize.
     * @param fileName   The file name, for error reporting.
     * @param lineNumber The 1-based line number.
     */
    Tokenizer(String line, String fileName, int lineNumber) {
      this.line = line;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }

    /**
     * Reads the next space-separated field.
     *
     * @param field The field name, for error reporting.
     * @return The field value.
     * @throws InvalidFormatException Thrown if the line is truncated before the field.
     */
    String next(String field) throws InvalidFormatException {
      while (position < line.length() && line.charAt(position) == ' ') {
        position++;
      }
      if (position >= line.length()) {
        throw malformed(fileName, lineNumber, "Truncated line, missing field: " + field);
      }
      final int start = position;
      while (position < line.length() && line.charAt(position) != ' ') {
        position++;
      }
      return line.substring(start, position);
    }

    /**
     * Reads a non-negative decimal field.
     *
     * @param field The field name used when parsing fails.
     * @param label The field label used for a negative value.
     * @return The parsed value.
     * @throws InvalidFormatException Thrown if the field is missing, invalid, or negative.
     */
    int nextNonNegativeInt(String field, String label) throws InvalidFormatException {
      final String token = next(field);
      if (token.charAt(0) == '-') {
        throw malformed(fileName, lineNumber,
            label + " must not be negative, got: " + token);
      }
      int value = 0;
      for (int i = 0; i < token.length(); i++) {
        final char c = token.charAt(i);
        final int digit = c - '0';
        if (digit < 0 || digit > 9 || value > (Integer.MAX_VALUE - digit) / 10) {
          throw malformed(fileName, lineNumber,
              field + " is not an unsigned decimal integer: " + token);
        }
        value = value * 10 + digit;
      }
      return value;
    }

    /**
     * Reads a fixed-width ASCII integer.
     *
     * @param field The field name used when parsing fails.
     * @param radix The numeric radix, either 10 or 16.
     * @param width The required field width.
     * @return The parsed value.
     * @throws InvalidFormatException Thrown if the field has the wrong width or contains a digit
     *     outside the radix.
     */
    int nextFixedInt(String field, int radix, int width) throws InvalidFormatException {
      final String token = next(field);
      return parseFixedInt(field, radix, width, token);
    }

    /** Parses a fixed-width ASCII integer already read from the line. */
    private int parseFixedInt(String field, int radix, int width, String token)
        throws InvalidFormatException {
      if (token.length() != width) {
        throw malformed(fileName, lineNumber, fixedIntegerMessage(field, radix, width, token));
      }
      int value = 0;
      for (int i = 0; i < token.length(); i++) {
        final char c = token.charAt(i);
        final int digit;
        if (c >= '0' && c <= '9') {
          digit = c - '0';
        } else if (c >= 'a' && c <= 'f') {
          digit = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
          digit = c - 'A' + 10;
        } else {
          throw malformed(fileName, lineNumber,
              fixedIntegerMessage(field, radix, width, token));
        }
        if (digit >= radix) {
          throw malformed(fileName, lineNumber,
              fixedIntegerMessage(field, radix, width, token));
        }
        value = value * radix + digit;
      }
      return value;
    }

    /**
     * Reads a non-negative fixed-width count.
     *
     * @param field The field name used when parsing fails.
     * @param radix The numeric radix.
     * @param width The required field width.
     * @param label The field label used for a negative value.
     * @return The parsed count.
     * @throws InvalidFormatException Thrown if the field is negative or malformed.
     */
    int nextFixedCount(String field, int radix, int width, String label)
        throws InvalidFormatException {
      final String token = next(field);
      if (!token.isEmpty() && token.charAt(0) == '-') {
        throw malformed(fileName, lineNumber, label + " must not be negative, got: " + token);
      }
      return parseFixedInt(field, radix, width, token);
    }

    /** Returns the standard fixed-width integer error detail. */
    private String fixedIntegerMessage(String field, int radix, int width, String token) {
      return field + " must be a " + width + "-digit base-" + radix
          + " integer, got: " + token;
    }

    /** Returns the maximum number of space-prefixed fields of {@code width} still available. */
    int remainingFixedWidthFields(int width) {
      return (line.length() - position) / (width + 1);
    }

    /**
     * Rejects fields after a complete index entry.
     *
     * @param parsedFields The fields already consumed.
     * @throws InvalidFormatException Thrown if another field remains.
     */
    void requireEnd(String parsedFields) throws InvalidFormatException {
      while (position < line.length() && line.charAt(position) == ' ') {
        position++;
      }
      if (position < line.length()) {
        throw malformed(fileName, lineNumber,
            "Unexpected field after " + parsedFields + ": " + next("field"));
      }
    }

    /**
     * Reads the gloss: the remainder after the pipe separator, trimmed of surrounding spaces.
     *
     * @return The gloss text.
     * @throws InvalidFormatException Thrown if the pipe separator is missing.
     */
    String gloss() throws InvalidFormatException {
      final String separator = next("gloss separator");
      if (!"|".equals(separator)) {
        throw malformed(fileName, lineNumber, "Expected the | gloss separator, got: " + separator);
      }
      int start = position;
      while (start < line.length() && line.charAt(start) == ' ') {
        start++;
      }
      int end = line.length();
      while (end > start && line.charAt(end - 1) == ' ') {
        end--;
      }
      return line.substring(start, end);
    }

    /**
     * Builds a malformed-file exception at this tokenizer's line.
     *
     * @param message The failure detail.
     * @return The exception to throw.
     */
    InvalidFormatException malformedToken(String message) {
      return malformed(fileName, lineNumber, message);
    }
  }

  /** A parsed pointer line, kept until the target synset is known. */
  private record RawPointer(WordNetRelation relation, String targetId, int targetWord,
                            int lineNumber) {
  }

  /** A parsed data-file synset, kept until its pointer targets can be resolved. */
  private static final class RawSynset {
    private final String id;
    private final WordNetPOS pos;
    private final List<String> lemmas;
    private final String gloss;
    private final List<RawPointer> pointers;
    private final String fileName;
    private final int lineNumber;

    /**
     * Creates a raw synset gathered while parsing a data file.
     *
     * @param id         The synset id.
     * @param pos        The part of speech.
     * @param lemmas     The member lemmas.
     * @param gloss      The gloss text.
     * @param pointers   The raw pointers to resolve.
     * @param fileName   The source file name.
     * @param lineNumber The source line number.
     */
    RawSynset(String id, WordNetPOS pos, List<String> lemmas, String gloss,
              List<RawPointer> pointers, String fileName, int lineNumber) {
      this.id = id;
      this.pos = pos;
      this.lemmas = lemmas;
      this.gloss = gloss;
      this.pointers = pointers;
      this.fileName = fileName;
      this.lineNumber = lineNumber;
    }
  }

  /**
   * Builds the WNDB pointer-symbol to {@link WordNetRelation} table.
   *
   * @return The immutable symbol table.
   */
  private static Map<String, WordNetRelation> pointerSymbols() {
    final Map<String, WordNetRelation> symbols = new HashMap<>();
    symbols.put("!", WordNetRelation.ANTONYM);
    symbols.put("@", WordNetRelation.HYPERNYM);
    symbols.put("@i", WordNetRelation.INSTANCE_HYPERNYM);
    symbols.put("~", WordNetRelation.HYPONYM);
    symbols.put("~i", WordNetRelation.INSTANCE_HYPONYM);
    symbols.put("#m", WordNetRelation.MEMBER_HOLONYM);
    symbols.put("#s", WordNetRelation.SUBSTANCE_HOLONYM);
    symbols.put("#p", WordNetRelation.PART_HOLONYM);
    symbols.put("%m", WordNetRelation.MEMBER_MERONYM);
    symbols.put("%s", WordNetRelation.SUBSTANCE_MERONYM);
    symbols.put("%p", WordNetRelation.PART_MERONYM);
    symbols.put("=", WordNetRelation.ATTRIBUTE);
    symbols.put("+", WordNetRelation.DERIVATIONALLY_RELATED);
    symbols.put("*", WordNetRelation.ENTAILMENT);
    symbols.put(">", WordNetRelation.CAUSE);
    symbols.put("^", WordNetRelation.ALSO_SEE);
    symbols.put("$", WordNetRelation.VERB_GROUP);
    symbols.put("&", WordNetRelation.SIMILAR_TO);
    symbols.put("<", WordNetRelation.PARTICIPLE);
    symbols.put("\\", WordNetRelation.PERTAINYM);
    symbols.put(";c", WordNetRelation.DOMAIN_TOPIC);
    symbols.put("-c", WordNetRelation.MEMBER_OF_DOMAIN_TOPIC);
    symbols.put(";r", WordNetRelation.DOMAIN_REGION);
    symbols.put("-r", WordNetRelation.MEMBER_OF_DOMAIN_REGION);
    symbols.put(";u", WordNetRelation.DOMAIN_USAGE);
    symbols.put("-u", WordNetRelation.MEMBER_OF_DOMAIN_USAGE);
    return Map.copyOf(symbols);
  }
}
