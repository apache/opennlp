/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.tokenize;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import opennlp.tools.util.Span;

/**
 * A {@link Tokenizer} implementation of the full BERT tokenization pipeline:
 * basic tokenization (text normalization) followed by wordpiece tokenization.
 * <p>
 * The basic tokenization stage reproduces the reference BERT
 * {@code BasicTokenizer}:
 * <ol>
 *  <li>Removal of control characters and normalization of all whitespace
 *      to single spaces.</li>
 *  <li>Whitespace isolation of CJK ideographs.</li>
 *  <li>For <i>uncased</i> models: lower casing and accent stripping
 *      (Unicode NFD decomposition with removal of combining marks).</li>
 *  <li>Isolation of every punctuation character as its own token.</li>
 * </ol>
 * The normalized text is then split into subwords by a
 * {@link WordpieceTokenizer} sharing the same vocabulary and special tokens.
 * <p>
 * This pipeline is required for correct results with BERT-style models:
 * feeding raw text directly to {@link WordpieceTokenizer} maps every token
 * that does not literally appear in the vocabulary - for uncased models that
 * includes every capitalized word - to the unknown token.
 * <p>
 * Whether to use the lower casing variant is a property of the model: uncased
 * models (for example {@code bert-base-uncased} and the
 * {@code sentence-transformers} models derived from it) require it, cased
 * models must not use it.
 * <p>
 * For reference see:
 * <ul>
 *  <li><a href="https://github.com/google-research/bert">
 *    https://github.com/google-research/bert</a> ({@code tokenization.py})</li>
 * </ul>
 *
 * @see WordpieceTokenizer
 */
public class BertTokenizer implements Tokenizer {

  /**
   * Maximum characters per word before the word is replaced with the unknown
   * token, matching the reference BERT implementation.
   */
  private static final int MAX_WORD_CHARACTERS = 100;

  private final WordpieceTokenizer wordpieceTokenizer;
  private final boolean lowerCase;

  /**
   * Initializes a {@link BertTokenizer} for an <i>uncased</i> BERT model,
   * with lower casing and accent stripping enabled.
   *
   * @param vocabulary The wordpiece vocabulary. Must not be {@code null}.
   */
  public BertTokenizer(Set<String> vocabulary) {
    this(vocabulary, true);
  }

  /**
   * Initializes a {@link BertTokenizer} with BERT special tokens.
   *
   * @param vocabulary The wordpiece vocabulary. Must not be {@code null}.
   * @param lowerCase  {@code true} for uncased models (lower casing and accent
   *                   stripping), {@code false} for cased models.
   */
  public BertTokenizer(Set<String> vocabulary, boolean lowerCase) {
    this(vocabulary, lowerCase, WordpieceTokenizer.BERT_CLS_TOKEN,
        WordpieceTokenizer.BERT_SEP_TOKEN, WordpieceTokenizer.BERT_UNK_TOKEN);
  }

  /**
   * Initializes a {@link BertTokenizer} with custom special tokens, for models
   * like RoBERTa that do not use the BERT defaults.
   *
   * @param vocabulary          The wordpiece vocabulary. Must not be {@code null}.
   * @param lowerCase           {@code true} for uncased models (lower casing and
   *                            accent stripping), {@code false} for cased models.
   * @param classificationToken The CLS token.
   * @param separatorToken      The SEP token.
   * @param unknownToken        The UNK token.
   */
  public BertTokenizer(Set<String> vocabulary, boolean lowerCase,
      String classificationToken, String separatorToken, String unknownToken) {
    Objects.requireNonNull(vocabulary, "vocabulary must not be null");
    this.wordpieceTokenizer = new WordpieceTokenizer(vocabulary,
        classificationToken, separatorToken, unknownToken, MAX_WORD_CHARACTERS);
    this.lowerCase = lowerCase;
  }

  /**
   * Tokenizes the given text into wordpieces, surrounded by the classification
   * and separator tokens.
   *
   * @param text The text to tokenize. Must not be {@code null}.
   *
   * @return The wordpiece tokens.
   */
  @Override
  public String[] tokenize(String text) {
    return wordpieceTokenizer.tokenize(normalize(text));
  }

  /**
   * Not supported: wordpiece tokens (subwords, {@code ##} continuations and
   * special tokens) have no faithful character spans in the original text.
   *
   * @throws UnsupportedOperationException Always.
   */
  @Override
  public Span[] tokenizePos(String text) {
    throw new UnsupportedOperationException(
        "Wordpiece tokens cannot be mapped to character spans of the original text");
  }

  /**
   * Applies the BERT basic tokenization (normalization) stage.
   *
   * @param text The text to normalize. Must not be {@code null}.
   *
   * @return The normalized text, ready for wordpiece tokenization.
   */
  String normalize(String text) {
    Objects.requireNonNull(text, "text must not be null");
    String normalized = cleanText(text);
    normalized = isolateCjkCharacters(normalized);
    if (lowerCase) {
      normalized = stripAccents(normalized.toLowerCase(Locale.ROOT));
    }
    return isolatePunctuation(normalized);
  }

  /**
   * Removes invalid and control characters and normalizes all whitespace
   * characters to plain spaces.
   */
  private static String cleanText(String text) {
    final StringBuilder cleaned = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (codePoint == 0 || codePoint == 0xFFFD || isControl(codePoint)) {
        return;
      }
      if (isWhitespace(codePoint)) {
        cleaned.append(' ');
      } else {
        cleaned.appendCodePoint(codePoint);
      }
    });
    return cleaned.toString();
  }

  /**
   * Surrounds every CJK ideograph with spaces, so each ideograph becomes its
   * own token, matching the reference BERT treatment of Chinese text.
   */
  private static String isolateCjkCharacters(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (isCjk(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  /**
   * Removes accents by Unicode NFD decomposition followed by removal of
   * combining marks ({@code Mn}).
   */
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

  /**
   * Surrounds every punctuation character with spaces, so each punctuation
   * character becomes its own token. Shared with {@link WordpieceTokenizer}.
   */
  static String isolatePunctuation(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (isPunctuation(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  /**
   * A control character in the BERT sense: {@code Cc} or {@code Cf}, except
   * the characters treated as whitespace by {@link #isWhitespace(int)}.
   */
  private static boolean isControl(int codePoint) {
    if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return false;
    }
    final int type = Character.getType(codePoint);
    return type == Character.CONTROL || type == Character.FORMAT;
  }

  /**
   * A whitespace character in the BERT sense: space, tab, newline, carriage
   * return, or Unicode space separators ({@code Zs}).
   */
  private static boolean isWhitespace(int codePoint) {
    if (codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return true;
    }
    return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
  }

  /**
   * A punctuation character in the BERT sense: any non-alphanumeric ASCII
   * character that is not whitespace, or any Unicode punctuation category.
   */
  private static boolean isPunctuation(int codePoint) {
    if ((codePoint >= 33 && codePoint <= 47) || (codePoint >= 58 && codePoint <= 64)
        || (codePoint >= 91 && codePoint <= 96) || (codePoint >= 123 && codePoint <= 126)) {
      return true;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
           Character.START_PUNCTUATION, Character.END_PUNCTUATION,
           Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
           Character.OTHER_PUNCTUATION -> true;
      default -> false;
    };
  }

  /**
   * A CJK ideograph as defined by the reference BERT implementation: the CJK
   * Unified Ideographs blocks and their extensions. This intentionally does
   * not cover Japanese kana or Korean hangul, matching the reference.
   */
  private static boolean isCjk(int codePoint) {
    return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
        || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
        || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
        || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
        || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
        || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
        || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
        || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
  }

}
