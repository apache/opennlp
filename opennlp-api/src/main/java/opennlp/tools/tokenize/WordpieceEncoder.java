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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A {@link SubwordTokenizer} running the full BERT tokenization pipeline of the reference
 * implementation: basic tokenization (control removal, whitespace normalization, CJK
 * isolation, optional lower casing with accent stripping, punctuation isolation) followed by
 * greedy longest-match wordpiece segmentation.
 *
 * <p>Every piece carries its vocabulary id and the span of the <i>original</i> text it came
 * from, surviving the normalization steps that change, insert, and remove characters. The
 * classification and separator pieces frame every encoding, carrying empty spans at the
 * text's boundaries, so {@link #encode(CharSequence)} is never empty.</p>
 *
 * <p>Ids follow the line-number convention of BERT {@code vocab.txt} files: with the list
 * constructors a piece's id is its index, and with the map constructor the ids are given
 * explicitly. The classification, separator, and unknown tokens must all be present in the
 * vocabulary, because every emitted piece must have an id.</p>
 *
 * <p>Instances are immutable and safe for concurrent use by multiple threads.</p>
 *
 * @see WordpieceTokenizer
 */
public final class WordpieceEncoder implements SubwordTokenizer {

  // The reference implementation's limit: longer words become the unknown piece.
  private static final int MAX_WORD_CHARACTERS = 100;

  private final Set<String> vocabulary;
  private final Map<String, Integer> ids;
  private final boolean lowerCase;
  private final String classificationToken;
  private final String separatorToken;
  private final String unknownToken;
  private final int classificationId;
  private final int separatorId;
  private final int unknownId;

  /**
   * Instantiates an encoder for an <i>uncased</i> BERT model with the BERT special tokens.
   *
   * @param vocabulary The ordered vocabulary; a piece's id is its index. Must not be null,
   *                   must not contain nulls or duplicates.
   */
  public WordpieceEncoder(List<String> vocabulary) {
    this(vocabulary, true);
  }

  /**
   * Instantiates an encoder with the BERT special tokens.
   *
   * @param vocabulary The ordered vocabulary; a piece's id is its index. Must not be null,
   *                   must not contain nulls or duplicates.
   * @param lowerCase  True for uncased models (lower casing and accent stripping), false for
   *                   cased models.
   */
  public WordpieceEncoder(List<String> vocabulary, boolean lowerCase) {
    this(vocabulary, lowerCase, WordpieceTokenizer.BERT_CLS_TOKEN,
        WordpieceTokenizer.BERT_SEP_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN);
  }

  /**
   * Instantiates an encoder with custom special tokens, for models that do not use the BERT
   * defaults.
   *
   * @param vocabulary          The ordered vocabulary; a piece's id is its index. Must not be
   *                            null, must not contain nulls or duplicates.
   * @param lowerCase           True for uncased models (lower casing and accent stripping),
   *                            false for cased models.
   * @param classificationToken The CLS token; must be in the vocabulary.
   * @param separatorToken      The SEP token; must be in the vocabulary.
   * @param unknownToken        The UNK token; must be in the vocabulary.
   * @throws IllegalArgumentException Thrown if any argument is null, the vocabulary contains
   *     a null or duplicate entry, or a special token is missing from the vocabulary.
   */
  public WordpieceEncoder(List<String> vocabulary, boolean lowerCase,
                          String classificationToken, String separatorToken,
                          String unknownToken) {
    this(byPiece(vocabulary), lowerCase, classificationToken, separatorToken, unknownToken);
  }

  /**
   * Instantiates an encoder from an explicit piece-to-id mapping, for vocabularies whose ids
   * are not contiguous line numbers.
   *
   * @param vocabularyIds       The piece-to-id mapping. Must not be null, must not contain
   *                            null keys or values.
   * @param lowerCase           True for uncased models (lower casing and accent stripping),
   *                            false for cased models.
   * @param classificationToken The CLS token; must be in the vocabulary.
   * @param separatorToken      The SEP token; must be in the vocabulary.
   * @param unknownToken        The UNK token; must be in the vocabulary.
   * @throws IllegalArgumentException Thrown if any argument is null, the mapping contains a
   *     null key or value, or a special token is missing from the vocabulary.
   */
  public WordpieceEncoder(Map<String, Integer> vocabularyIds, boolean lowerCase,
                          String classificationToken, String separatorToken,
                          String unknownToken) {
    if (vocabularyIds == null || classificationToken == null || separatorToken == null
        || unknownToken == null) {
      throw new IllegalArgumentException("The vocabulary and special tokens must not be null.");
    }
    final Map<String, Integer> byPiece = new HashMap<>(vocabularyIds.size() * 2);
    for (final Map.Entry<String, Integer> entry : vocabularyIds.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException(
            "The vocabulary must not contain null pieces or ids: " + entry);
      }
      byPiece.put(entry.getKey(), entry.getValue());
    }
    this.vocabulary = new HashSet<>(byPiece.keySet());
    this.ids = byPiece;
    this.lowerCase = lowerCase;
    this.classificationToken = classificationToken;
    this.separatorToken = separatorToken;
    this.unknownToken = unknownToken;
    this.classificationId = requiredId(byPiece, classificationToken);
    this.separatorId = requiredId(byPiece, separatorToken);
    this.unknownId = requiredId(byPiece, unknownToken);
  }

  private static Map<String, Integer> byPiece(List<String> vocabulary) {
    if (vocabulary == null) {
      throw new IllegalArgumentException("The vocabulary must not be null.");
    }
    final Map<String, Integer> byPiece = new HashMap<>(vocabulary.size() * 2);
    for (int id = 0; id < vocabulary.size(); id++) {
      final String piece = vocabulary.get(id);
      if (piece == null) {
        throw new IllegalArgumentException("The vocabulary contains null at index " + id + ".");
      }
      if (byPiece.putIfAbsent(piece, id) != null) {
        throw new IllegalArgumentException("The vocabulary contains '" + piece
            + "' more than once; ids would be ambiguous.");
      }
    }
    return byPiece;
  }

  private static int requiredId(Map<String, Integer> ids, String specialToken) {
    final Integer id = ids.get(specialToken);
    if (id == null) {
      throw new IllegalArgumentException("The special token '" + specialToken
          + "' is not in the vocabulary; every emitted piece must have an id.");
    }
    return id;
  }

  @Override
  public List<SubwordPiece> encode(CharSequence text) {
    if (text == null) {
      throw new IllegalArgumentException("The text must not be null.");
    }
    final String original = text.toString();

    // The normalized text, one original-text range per char, built through the reference
    // pipeline's transformations in the reference order.
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

  /**
   * Greedily longest-match segments one whitespace-delimited word; the pieces are emitted only if
   * the whole word is representable, otherwise the word becomes a single unknown piece.
   *
   * @param mapped The normalized text with per-character original-text ranges.
   * @param from   The inclusive start of the word in {@code mapped}.
   * @param to     The exclusive end of the word in {@code mapped}.
   * @param pieces The output list to append to.
   */
  private void encodeWord(MappedText mapped, int from, int to, List<SubwordPiece> pieces) {
    final int wordStart = mapped.starts[from];
    final int wordEnd = mapped.ends[to - 1];
    if (to - from > MAX_WORD_CHARACTERS) {
      pieces.add(new SubwordPiece(unknownToken, unknownId, wordStart, wordEnd));
      return;
    }
    final List<SubwordPiece> wordPieces = new ArrayList<>();
    int start = from;
    boolean found = true;
    while (start < to) {
      int end = to;
      found = false;
      while (start < end) {
        String substring = new String(mapped.chars, start, end - start);
        if (start > from) {
          substring = "##" + substring;
        }
        if (vocabulary.contains(substring)) {
          wordPieces.add(new SubwordPiece(substring, ids.get(substring),
              mapped.starts[start], mapped.ends[end - 1]));
          start = end;
          found = true;
          break;
        }
        end--;
      }
      if (!found) {
        break;
      }
    }
    if (found) {
      pieces.addAll(wordPieces);
    } else {
      pieces.add(new SubwordPiece(unknownToken, unknownId, wordStart, wordEnd));
    }
  }

  /**
   * The normalized text with, for every char, the original-text range it came from. Characters
   * inserted by the pipeline (isolation spaces) carry an empty range at the insertion point.
   */
  private static final class MappedText {
    private char[] chars;
    private int[] starts;
    private int[] ends;
    private int length;

    private MappedText(int capacity) {
      chars = new char[capacity];
      starts = new int[capacity];
      ends = new int[capacity];
    }

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

    private void add(String s, int originalStart, int originalEnd) {
      for (int i = 0; i < s.length(); i++) {
        add(s.charAt(i), originalStart, originalEnd);
      }
    }
  }

  /**
   * Cleans the text (control and whitespace normalization) and isolates CJK code points in one
   * pass, recording the original-text range of every output character.
   *
   * @param original The original input text.
   * @return The cleaned, CJK-isolated text with per-character ranges.
   */
  private static MappedText cleanAndIsolateCjk(String original) {
    final MappedText out = new MappedText(original.length() + 16);
    int i = 0;
    while (i < original.length()) {
      final int codePoint = original.codePointAt(i);
      final int width = Character.charCount(codePoint);
      if (codePoint == 0 || codePoint == 0xFFFD || BertNormalization.isControl(codePoint)) {
        i += width;
        continue;
      }
      if (BertNormalization.isWhitespace(codePoint)) {
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
   * Isolates punctuation, surrounding each punctuation code point with spaces, preserving the
   * original-text range of every character.
   *
   * @param in The input text with per-character ranges.
   * @return The punctuation-isolated text with per-character ranges.
   */
  private static MappedText isolatePunctuation(MappedText in) {
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

  /**
   * Lower cases and strips accents, preserving the original-text range of every character. When a
   * contextual case mapping prevents a per-character range from being recovered, the whole
   * whitespace run falls back to its full range, which widens spans but never misplaces them.
   *
   * @param in The input text with per-character ranges.
   * @return The lower-cased, accent-stripped text with per-character ranges.
   */
  private static MappedText lowerCaseAndStripAccents(MappedText in) {
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

  private static void transformRun(MappedText in, int from, int to, MappedText out) {
    final String run = new String(in.chars, from, to - from);
    final String content = stripAccents(run.toLowerCase(Locale.ROOT));

    // Rerun per code point to learn how many output chars each input code point produces.
    final StringBuilder rerun = new StringBuilder(content.length());
    final int[] produced = new int[to - from];
    int i = from;
    while (i < to) {
      final int codePoint = codePointAt(in, i);
      final int width = Character.charCount(codePoint);
      final String transformed = stripAccents(
          new String(Character.toChars(codePoint)).toLowerCase(Locale.ROOT));
      rerun.append(transformed);
      produced[i - from] = transformed.length();
      i += width;
    }

    if (rerun.toString().equals(content)) {
      int at = from;
      int emitted = 0;
      while (at < to) {
        final int width = Character.charCount(codePointAt(in, at));
        for (int c = 0; c < produced[at - from]; c++) {
          out.add(content.charAt(emitted++), in.starts[at], in.ends[at + width - 1]);
        }
        at += width;
      }
    } else {
      // Contextual case mapping changed the content; the run's chars share the run's range.
      out.add(content, in.starts[from], in.ends[to - 1]);
    }
  }

  private static String stripAccents(String text) {
    final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    final StringBuilder stripped = new StringBuilder(decomposed.length());
    decomposed.codePoints().forEach(codePoint -> {
      if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
        stripped.appendCodePoint(codePoint);
      }
    });
    return stripped.toString();
  }

  private static int codePointAt(MappedText text, int index) {
    final char c = text.chars[index];
    if (Character.isHighSurrogate(c) && index + 1 < text.length
        && Character.isLowSurrogate(text.chars[index + 1])) {
      return Character.toCodePoint(c, text.chars[index + 1]);
    }
    return c;
  }
}
