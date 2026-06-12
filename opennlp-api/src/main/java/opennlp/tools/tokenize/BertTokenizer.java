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
 * models must not use it. Accent stripping is coupled to lower casing, as in
 * the reference implementation's default ({@code strip_accents} follows
 * {@code do_lower_case} unless overridden).
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
    Objects.requireNonNull(classificationToken, "classificationToken must not be null");
    Objects.requireNonNull(separatorToken, "separatorToken must not be null");
    Objects.requireNonNull(unknownToken, "unknownToken must not be null");
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
    return BertNormalization.isolatePunctuation(normalized);
  }

  /**
   * Removes invalid and control characters and normalizes all whitespace
   * characters to plain spaces.
   */
  private static String cleanText(String text) {
    final StringBuilder cleaned = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (codePoint == 0 || codePoint == 0xFFFD || BertNormalization.isControl(codePoint)) {
        return;
      }
      if (BertNormalization.isWhitespace(codePoint)) {
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
      if (BertNormalization.isCjk(codePoint)) {
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

}
