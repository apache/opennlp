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
package opennlp.tools.tokenize;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.StringUtil;

/**
 * A {@link SubwordTokenizer} implementing the BERT tokenization stages: basic tokenization
 * (control removal, whitespace normalization, CJK isolation, optional lower casing with accent
 * stripping, punctuation isolation) followed by greedy longest-match wordpiece segmentation.
 *
 * <p>Each result includes a vocabulary id and range in the <i>original</i> text. The range
 * refers to the input before normalization. Classification and separator entries use empty
 * ranges at the text boundaries, so {@link #encode(CharSequence)} includes both control
 * entries.</p>
 *
 * <p>Ids follow the line-number convention of BERT {@code vocab.txt} files. List constructors use
 * the list index, while the map constructor uses the supplied ids. The classification, separator,
 * and unknown tokens must all be present in the
 * vocabulary, because each emitted piece must have an id. Vocabulary entries starting with
 * {@code ##} are continuation pieces and can match only after the first piece of a word.</p>
 *
 * <p>A word exceeding the configured maximum number of normalized Unicode code points becomes
 * the unknown piece. The default is 100, matching the BERT reference implementation.</p>
 *
 * @see WordpieceTokenizer
 * @see <a href="https://github.com/google-research/bert/blob/master/tokenization.py">
 *     BERT tokenization reference</a>
 * @since 3.0.0
 */
@ThreadSafe
public final class WordpieceEncoder implements SubwordTokenizer {

  // The wordpiece vocabulary convention: a piece with this prefix continues the current word,
  // so it can only match after the word's first piece.
  private static final String CONTINUATION_PREFIX = "##";

  private static final int GREEK_CAPITAL_SIGMA = 0x03A3;
  private static final int GREEK_SMALL_FINAL_SIGMA = 0x03C2;

  private final Map<String, Integer> ids;
  private final VocabularyTrie initialPieces;
  private final VocabularyTrie continuationPieces;
  private final boolean lowerCase;
  private final String classificationToken;
  private final String separatorToken;
  private final String unknownToken;
  private final int maxWordCodePoints;
  private final int classificationId;
  private final int separatorId;
  private final int unknownId;

  /**
   * Instantiates an encoder for an <i>uncased</i> BERT model with the BERT special tokens.
   *
   * @param vocabulary The ordered vocabulary; the list index becomes the id. Must not be {@code null}
   *                   or contain {@code null}, empty, or duplicate entries.
   * @throws IllegalArgumentException Thrown if the vocabulary is {@code null}, contains a
   *     {@code null}, empty, or duplicate entry, or is missing a BERT special token.
   */
  public WordpieceEncoder(List<String> vocabulary) {
    this(vocabulary, true);
  }

  /**
   * Instantiates an encoder with the BERT special tokens.
   *
   * @param vocabulary The ordered vocabulary; the list index becomes the id. Must not be {@code null}
   *                   or contain {@code null}, empty, or duplicate entries.
   * @param lowerCase  {@code true} for uncased models (lower casing and accent stripping),
   *                   {@code false} for cased models.
   * @throws IllegalArgumentException Thrown if the vocabulary is {@code null}, contains a
   *     {@code null}, empty, or duplicate entry, or is missing a BERT special token.
   */
  public WordpieceEncoder(List<String> vocabulary, boolean lowerCase) {
    this(vocabulary, lowerCase, BertNormalization.DEFAULT_MAX_WORD_CODE_POINTS);
  }

  /**
   * Instantiates an encoder with the BERT special tokens and a custom word-length limit.
   *
   * @param vocabulary The ordered vocabulary; the list index becomes the id. Must not be
   *                   {@code null} or contain {@code null}, empty, or duplicate entries.
   * @param lowerCase {@code true} for uncased models, {@code false} for cased models.
   * @param maxWordCodePoints The non-negative maximum number of normalized Unicode code points
   *                          in one word.
   * @throws IllegalArgumentException Thrown if an argument is invalid or a BERT special token is
   *     missing.
   */
  public WordpieceEncoder(List<String> vocabulary, boolean lowerCase, int maxWordCodePoints) {
    this(vocabulary, lowerCase, WordpieceTokenizer.BERT_CLS_TOKEN,
        WordpieceTokenizer.BERT_SEP_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN, maxWordCodePoints);
  }

  /**
   * Instantiates an encoder with custom special tokens.
   *
   * @param vocabulary          The ordered vocabulary; the list index becomes the id. Must not be
   *                            {@code null} or contain {@code null}, empty, or duplicate entries.
   * @param lowerCase           {@code true} for uncased models (lower casing and accent stripping),
   *                            {@code false} for cased models.
   * @param classificationToken The CLS token; must not be {@code null} or empty and must be in the
   *                            vocabulary.
   * @param separatorToken      The SEP token; must not be {@code null} or empty and must be in the
   *                            vocabulary.
   * @param unknownToken        The UNK token; must not be {@code null} or empty and must be in the
   *                            vocabulary.
   * @throws IllegalArgumentException Thrown if any argument is {@code null}, the vocabulary
   *     contains a {@code null}, empty, or duplicate entry, or a special token is empty or missing.
   */
  public WordpieceEncoder(List<String> vocabulary, boolean lowerCase,
                          String classificationToken, String separatorToken,
                          String unknownToken) {
    this(vocabulary, lowerCase, classificationToken, separatorToken, unknownToken,
        BertNormalization.DEFAULT_MAX_WORD_CODE_POINTS);
  }

  /**
   * Instantiates an encoder with custom special tokens and a custom word-length limit.
   *
   * @param vocabulary The ordered vocabulary; the list index becomes the id. Must not be
   *                   {@code null} or contain {@code null}, empty, or duplicate entries.
   * @param lowerCase {@code true} for uncased models, {@code false} for cased models.
   * @param classificationToken The CLS token; must be present in the vocabulary.
   * @param separatorToken The SEP token; must be present in the vocabulary.
   * @param unknownToken The UNK token; must be present in the vocabulary.
   * @param maxWordCodePoints The non-negative maximum number of normalized Unicode code points
   *                          in one word.
   * @throws IllegalArgumentException Thrown if an argument is invalid or a special token is
   *     missing.
   */
  public WordpieceEncoder(List<String> vocabulary, boolean lowerCase,
                          String classificationToken, String separatorToken,
                          String unknownToken, int maxWordCodePoints) {
    this(byPiece(vocabulary), lowerCase, classificationToken, separatorToken, unknownToken,
        maxWordCodePoints);
  }

  /**
   * Instantiates an encoder from an explicit piece-to-id mapping for vocabularies with
   * noncontiguous ids.
   *
   * @param vocabularyIds       The piece-to-id mapping. Must not be {@code null} or contain
   *                            {@code null} or empty keys, {@code null} values, or negative ids.
   * @param lowerCase           {@code true} for uncased models (lower casing and accent stripping),
   *                            {@code false} for cased models.
   * @param classificationToken The CLS token; must not be {@code null} or empty and must be in the
   *                            vocabulary.
   * @param separatorToken      The SEP token; must not be {@code null} or empty and must be in the
   *                            vocabulary.
   * @param unknownToken        The UNK token; must not be {@code null} or empty and must be in the
   *                            vocabulary.
   * @throws IllegalArgumentException Thrown if any argument is {@code null}, the mapping contains
   *     a {@code null} or empty key, {@code null} value, or negative id, or a special token is empty
   *     or missing.
   */
  public WordpieceEncoder(Map<String, Integer> vocabularyIds, boolean lowerCase,
                          String classificationToken, String separatorToken,
                          String unknownToken) {
    this(vocabularyIds, lowerCase, classificationToken, separatorToken, unknownToken,
        BertNormalization.DEFAULT_MAX_WORD_CODE_POINTS);
  }

  /**
   * Instantiates an encoder from a piece-to-id mapping with a custom word-length limit.
   *
   * @param vocabularyIds The piece-to-id mapping. Must not be {@code null} or contain invalid
   *                      entries.
   * @param lowerCase {@code true} for uncased models, {@code false} for cased models.
   * @param classificationToken The CLS token; must be present in the vocabulary.
   * @param separatorToken The SEP token; must be present in the vocabulary.
   * @param unknownToken The UNK token; must be present in the vocabulary.
   * @param maxWordCodePoints The non-negative maximum number of normalized Unicode code points
   *                          in one word.
   * @throws IllegalArgumentException Thrown if an argument is invalid or a special token is
   *     missing.
   */
  public WordpieceEncoder(Map<String, Integer> vocabularyIds, boolean lowerCase,
                          String classificationToken, String separatorToken,
                          String unknownToken, int maxWordCodePoints) {
    if (vocabularyIds == null) {
      throw new IllegalArgumentException("vocabularyIds must not be null");
    }
    if (classificationToken == null) {
      throw new IllegalArgumentException("classificationToken must not be null");
    }
    if (separatorToken == null) {
      throw new IllegalArgumentException("separatorToken must not be null");
    }
    if (unknownToken == null) {
      throw new IllegalArgumentException("unknownToken must not be null");
    }
    if (classificationToken.isEmpty()) {
      throw new IllegalArgumentException("classificationToken must not be empty");
    }
    if (separatorToken.isEmpty()) {
      throw new IllegalArgumentException("separatorToken must not be empty");
    }
    if (unknownToken.isEmpty()) {
      throw new IllegalArgumentException("unknownToken must not be empty");
    }
    if (maxWordCodePoints < 0) {
      throw new IllegalArgumentException("maxWordCodePoints must not be negative");
    }
    final Map<String, Integer> byPiece = HashMap.newHashMap(vocabularyIds.size());
    for (final Map.Entry<String, Integer> entry : vocabularyIds.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException("vocabularyIds must not contain null pieces or ids");
      }
      if (entry.getKey().isEmpty()) {
        throw new IllegalArgumentException("vocabularyIds must not contain an empty piece");
      }
      if (entry.getValue() < 0) {
        throw new IllegalArgumentException("vocabularyIds must not contain a negative id for piece '"
            + entry.getKey() + "'");
      }
      byPiece.put(entry.getKey(), entry.getValue());
    }
    this.ids = Map.copyOf(byPiece);
    this.initialPieces = new VocabularyTrie(this.ids, false);
    this.continuationPieces = new VocabularyTrie(this.ids, true);
    this.lowerCase = lowerCase;
    this.classificationToken = classificationToken;
    this.separatorToken = separatorToken;
    this.unknownToken = unknownToken;
    this.maxWordCodePoints = maxWordCodePoints;
    this.classificationId = requiredId(classificationToken);
    this.separatorId = requiredId(separatorToken);
    this.unknownId = requiredId(unknownToken);
  }

  /** Converts an ordered vocabulary to a piece-to-id mapping. */
  private static Map<String, Integer> byPiece(List<String> vocabulary) {
    if (vocabulary == null) {
      throw new IllegalArgumentException("vocabulary must not be null");
    }
    final Map<String, Integer> byPiece = HashMap.newHashMap(vocabulary.size());
    for (int id = 0; id < vocabulary.size(); id++) {
      final String piece = vocabulary.get(id);
      if (piece == null) {
        throw new IllegalArgumentException("vocabulary must not contain null at index " + id);
      }
      if (piece.isEmpty()) {
        throw new IllegalArgumentException(
            "vocabulary must not contain an empty piece at index " + id);
      }
      if (byPiece.putIfAbsent(piece, id) != null) {
        throw new IllegalArgumentException(
            "vocabulary must not contain duplicate piece '" + piece + "'");
      }
    }
    return byPiece;
  }

  /** Returns the id of a required special token. */
  private int requiredId(String specialToken) {
    final Integer id = ids.get(specialToken);
    if (id == null) {
      throw new IllegalArgumentException(
          "vocabulary must contain special token '" + specialToken + "'");
    }
    return id;
  }

  /** {@inheritDoc} */
  @Override
  public List<SubwordPiece> encode(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    final String original = text.toString();

    // Stores an original-text range for each normalized char.
    MappedText mapped = cleanAndIsolateCjk(original);
    if (lowerCase) {
      mapped = lowerCaseAndStripAccents(mapped);
    }
    mapped = isolatePunctuation(mapped);

    final List<SubwordPiece> pieces = new ArrayList<>();
    pieces.add(new SubwordPiece(classificationToken, classificationId, 0, 0));
    int from = 0;
    while (from < mapped.length) {
      if (mapped.chars[from] == ' ') {
        from++;
        continue;
      }
      int to = from;
      while (to < mapped.length && mapped.chars[to] != ' ') {
        to++;
      }
      encodeWord(mapped, from, to, pieces);
      from = to;
    }
    pieces.add(new SubwordPiece(separatorToken, separatorId,
        original.length(), original.length()));
    return pieces;
  }

  /** Appends the longest vocabulary segmentation of one normalized word. */
  private void encodeWord(MappedText mapped, int from, int to, List<SubwordPiece> pieces) {
    final int wordStart = mapped.starts[from];
    final int wordEnd = mapped.ends[to - 1];
    if (Character.codePointCount(mapped.chars, from, to - from) > maxWordCodePoints) {
      pieces.add(new SubwordPiece(unknownToken, unknownId, wordStart, wordEnd));
      return;
    }
    final List<SubwordPiece> wordPieces = new ArrayList<>();
    int start = from;
    boolean found = true;
    while (start < to) {
      final VocabularyMatch match = (start == from ? initialPieces : continuationPieces)
          .longestMatch(mapped.chars, start, to);
      if (match == null) {
        found = false;
        break;
      }
      wordPieces.add(new SubwordPiece(match.piece(), match.id(),
          mapped.starts[start], mapped.ends[match.end() - 1]));
      start = match.end();
    }
    if (found) {
      pieces.addAll(wordPieces);
    } else {
      pieces.add(new SubwordPiece(unknownToken, unknownId, wordStart, wordEnd));
    }
  }

  /** A vocabulary match and the exclusive end offset in normalized text. */
  private record VocabularyMatch(String piece, int id, int end) {
  }

  /** Finds vocabulary entries without creating strings for candidate lengths. */
  private static final class VocabularyTrie {

    private final TrieNode root = new TrieNode();

    private VocabularyTrie(Map<String, Integer> vocabulary, boolean continuation) {
      for (final Map.Entry<String, Integer> entry : vocabulary.entrySet()) {
        final String piece = entry.getKey();
        final boolean continuationPiece = piece.startsWith(CONTINUATION_PREFIX);
        if (continuationPiece != continuation) {
          continue;
        }
        final int offset = continuationPiece ? CONTINUATION_PREFIX.length() : 0;
        if (offset == piece.length()) {
          continue;
        }
        TrieNode node = root;
        for (int i = offset; i < piece.length(); i++) {
          node = node.children.computeIfAbsent(piece.charAt(i), key -> new TrieNode());
        }
        node.piece = piece;
        node.id = entry.getValue();
      }
    }

    /** Returns the longest entry that starts at {@code from}. */
    private VocabularyMatch longestMatch(char[] text, int from, int to) {
      TrieNode node = root;
      VocabularyMatch longest = null;
      int index = from;
      while (index < to) {
        final int codePoint = Character.codePointAt(text, index, to);
        final int width = Character.charCount(codePoint);
        for (int i = 0; i < width; i++) {
          node = node.children.get(text[index + i]);
          if (node == null) {
            return longest;
          }
        }
        index += width;
        if (node.piece != null) {
          longest = new VocabularyMatch(node.piece, node.id, index);
        }
      }
      return longest;
    }
  }

  /** One node of the vocabulary trie. */
  private static final class TrieNode {
    private final Map<Character, TrieNode> children = new HashMap<>();
    private String piece;
    private int id;
  }

  /**
   * The normalized text with the original-text range for each character. Characters inserted by
   * the pipeline (isolation spaces) use an empty range at the insertion point.
   */
  private static final class MappedText {
    private char[] chars;
    private int[] starts;
    private int[] ends;
    private int length;

    /** Creates an empty mapping with space for the expected number of UTF-16 code units. */
    private MappedText(int capacity) {
      chars = new char[capacity];
      starts = new int[capacity];
      ends = new int[capacity];
    }

    /** Adds one UTF-16 code unit with a source range. */
    private void add(char c, int originalStart, int originalEnd) {
      if (length == chars.length) {
        final int capacity = Math.max(16, length * 2);
        chars = Arrays.copyOf(chars, capacity);
        starts = Arrays.copyOf(starts, capacity);
        ends = Arrays.copyOf(ends, capacity);
      }
      chars[length] = c;
      starts[length] = originalStart;
      ends[length] = originalEnd;
      length++;
    }

    /** Adds a string with one source range shared by all code units. */
    private void add(String s, int originalStart, int originalEnd) {
      for (int i = 0; i < s.length(); i++) {
        add(s.charAt(i), originalStart, originalEnd);
      }
    }

    /** Adds one code point with a source range shared by all code units. */
    private void addCodePoint(int codePoint, int originalStart, int originalEnd) {
      if (Character.isBmpCodePoint(codePoint)) {
        add((char) codePoint, originalStart, originalEnd);
      } else {
        add(Character.highSurrogate(codePoint), originalStart, originalEnd);
        add(Character.lowSurrogate(codePoint), originalStart, originalEnd);
      }
    }

    /** Returns the mapped character content. */
    private String text() {
      return new String(chars, 0, length);
    }
  }

  /** Removes BERT control characters, normalizes whitespace, and isolates CJK ideographs. */
  private MappedText cleanAndIsolateCjk(String original) {
    final MappedText out = new MappedText(original.length() + 16);
    int i = 0;
    while (i < original.length()) {
      final int codePoint = original.codePointAt(i);
      final int width = Character.charCount(codePoint);
      if (codePoint == 0 || codePoint == 0xFFFD || BertNormalization.isControl(codePoint)) {
        i += width;
        continue;
      }
      if (BertNormalization.isWhitespace(codePoint)
          || BertNormalization.isLineOrParagraphSeparator(codePoint)) {
        out.add(' ', i, i + width);
      } else if (BertNormalization.isCjk(codePoint)) {
        out.add(' ', i, i);
        for (int c = 0; c < width; c++) {
          out.add(original.charAt(i + c), i, i + width);
        }
        out.add(' ', i + width, i + width);
      } else {
        for (int c = 0; c < width; c++) {
          out.add(original.charAt(i + c), i, i + width);
        }
      }
      i += width;
    }
    return out;
  }

  /**
   * Surrounds each BERT punctuation character with mapped spaces. Keep the character
   * classification in sync with {@link BertNormalization#isolatePunctuation(String)}.
   */
  private MappedText isolatePunctuation(MappedText in) {
    final MappedText out = new MappedText(in.length + 16);
    int i = 0;
    while (i < in.length) {
      final int codePoint = codePointAt(in, i);
      final int width = Character.charCount(codePoint);
      if (BertNormalization.isPunctuation(codePoint)) {
        out.add(' ', in.starts[i], in.starts[i]);
        for (int c = 0; c < width; c++) {
          out.add(in.chars[i + c], in.starts[i + c], in.ends[i + c]);
        }
        out.add(' ', in.ends[i + width - 1], in.ends[i + width - 1]);
      } else {
        for (int c = 0; c < width; c++) {
          out.add(in.chars[i + c], in.starts[i + c], in.ends[i + c]);
        }
      }
      i += width;
    }
    return out;
  }

  /** Applies BERT lower casing and accent removal while retaining source ranges. */
  private MappedText lowerCaseAndStripAccents(MappedText in) {
    final MappedText out = new MappedText(in.length + 16);
    int from = 0;
    while (from < in.length) {
      if (in.chars[from] == ' ') {
        out.add(' ', in.starts[from], in.ends[from]);
        from++;
        continue;
      }
      int to = from;
      while (to < in.length && in.chars[to] != ' ') {
        to++;
      }
      transformRun(in, from, to, out);
      from = to;
    }
    return out;
  }

  /** Normalizes one non-whitespace run and appends it to the output mapping. */
  private void transformRun(MappedText in, int from, int to, MappedText out) {
    final MappedText lower = lowerCaseRun(in, from, to);
    if (Normalizer.isNormalized(lower.text(), Normalizer.Form.NFD)) {
      appendWithoutAccents(lower, out);
      return;
    }

    final MappedText decomposedByCodePoint = decomposeByCodePoint(lower);
    final String decomposedRun = Normalizer.normalize(lower.text(), Normalizer.Form.NFD);
    if (decomposedRun.equals(decomposedByCodePoint.text())) {
      appendWithoutAccents(decomposedByCodePoint, out);
    } else {
      // Canonical reordering can cross character ranges, so each result covers the source run.
      appendWithoutAccents(decomposedRun, in.starts[from], in.ends[to - 1], out);
    }
  }

  /** Lowercases one run and retains a source range for each result code point. */
  private MappedText lowerCaseRun(MappedText in, int from, int to) {
    final String source = new String(in.chars, from, to - from);
    final String lower = StringUtil.toLowerCase(source);
    final MappedText mapped = new MappedText(lower.length());
    int sourceIndex = from;
    int lowerIndex = 0;
    while (sourceIndex < to) {
      final int sourceCodePoint = codePointAt(in, sourceIndex);
      final int sourceWidth = Character.charCount(sourceCodePoint);
      int lowerCodePoint = lower.codePointAt(lowerIndex);
      final int lowerWidth = Character.charCount(lowerCodePoint);
      if (sourceCodePoint == GREEK_CAPITAL_SIGMA
          && isFinalSigma(in, from, to, sourceIndex)) {
        lowerCodePoint = GREEK_SMALL_FINAL_SIGMA;
      }
      mapped.addCodePoint(lowerCodePoint, in.starts[sourceIndex],
          in.ends[sourceIndex + sourceWidth - 1]);
      sourceIndex += sourceWidth;
      lowerIndex += lowerWidth;
    }
    return mapped;
  }

  /** Tests whether the capital sigma at {@code index} is word-final. */
  private boolean isFinalSigma(MappedText in, int from, int to, int index) {
    int before = index;
    boolean followsCased = false;
    while (before > from) {
      final int codePoint = Character.codePointBefore(in.chars, before);
      before -= Character.charCount(codePoint);
      if (!isCaseIgnorable(codePoint)) {
        followsCased = isCased(codePoint);
        break;
      }
    }
    if (!followsCased) {
      return false;
    }

    int after = index + Character.charCount(GREEK_CAPITAL_SIGMA);
    while (after < to) {
      final int codePoint = codePointAt(in, after);
      if (!isCaseIgnorable(codePoint)) {
        return !isCased(codePoint);
      }
      after += Character.charCount(codePoint);
    }
    return true;
  }

  /** Tests whether a code point participates in Unicode casing. */
  private boolean isCased(int codePoint) {
    return Character.isUpperCase(codePoint)
        || Character.isLowerCase(codePoint)
        || Character.isTitleCase(codePoint);
  }

  /**
   * Tests the general categories and Unicode 15.0 word-break values used by the
   * {@code Case_Ignorable} property.
   *
   * @see <a href="https://www.unicode.org/Public/15.0.0/ucd/auxiliary/WordBreakProperty.txt">
   *     Unicode 15.0 word-break properties</a>
   */
  private boolean isCaseIgnorable(int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK,
           Character.FORMAT, Character.MODIFIER_LETTER,
           Character.MODIFIER_SYMBOL -> true;
      default -> switch (codePoint) {
        case 0x0027, 0x002E, 0x003A, 0x00B7, 0x0387, 0x055F, 0x05F4, 0x2018,
             0x2019, 0x2024, 0x2027, 0xFE13, 0xFE52, 0xFE55, 0xFF07, 0xFF0E,
             0xFF1A -> true;
        default -> false;
      };
    };
  }

  /** Canonically decomposes each code point while preserving the source range. */
  private MappedText decomposeByCodePoint(MappedText text) {
    final MappedText decomposed = new MappedText(text.length + 16);
    int index = 0;
    while (index < text.length) {
      final int codePoint = codePointAt(text, index);
      final int width = Character.charCount(codePoint);
      final String source = new String(text.chars, index, width);
      final String normalized = Normalizer.isNormalized(source, Normalizer.Form.NFD)
          ? source : Normalizer.normalize(source, Normalizer.Form.NFD);
      decomposed.add(normalized, text.starts[index], text.ends[index + width - 1]);
      index += width;
    }
    return decomposed;
  }

  /** Appends mapped characters excluding non-spacing marks. */
  private void appendWithoutAccents(MappedText text, MappedText out) {
    final int outputStart = out.length;
    int index = 0;
    while (index < text.length) {
      final int codePoint = codePointAt(text, index);
      final int width = Character.charCount(codePoint);
      if (Character.getType(codePoint) == Character.NON_SPACING_MARK) {
        if (out.length > outputStart) {
          out.ends[out.length - 1] = text.ends[index + width - 1];
        }
      } else {
        for (int i = 0; i < width; i++) {
          out.add(text.chars[index + i], text.starts[index + i], text.ends[index + i]);
        }
      }
      index += width;
    }
  }

  /** Appends characters excluding non-spacing marks with one source range. */
  private void appendWithoutAccents(String text, int start, int end, MappedText out) {
    int index = 0;
    while (index < text.length()) {
      final int codePoint = text.codePointAt(index);
      if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
        out.addCodePoint(codePoint, start, end);
      }
      index += Character.charCount(codePoint);
    }
  }

  /** Returns a code point from the mapped UTF-16 buffer. */
  private int codePointAt(MappedText text, int index) {
    final char c = text.chars[index];
    if (Character.isHighSurrogate(c) && index + 1 < text.length
        && Character.isLowSurrogate(text.chars[index + 1])) {
      return Character.toCodePoint(c, text.chars[index + 1]);
    }
    return c;
  }
}
