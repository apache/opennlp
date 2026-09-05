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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.Span;

/**
 * A {@link Tokenizer} implementation which performs tokenization
 * using word pieces.
 * <p>
 * Adapted under MIT license from
 * <a href="https://github.com/robrua/easy-bert">https://github.com/robrua/easy-bert</a>.
 * <p>
 * Note that this tokenizer performs <i>only</i> the wordpiece (subword) stage
 * of BERT tokenization. It does not normalize the input text: no lower casing,
 * no accent stripping, no control character removal. Text that does not match
 * the vocabulary's casing - for uncased models that includes every capitalized
 * word - is mapped to the unknown token. Use {@link WordpieceEncoder} for the
 * BERT normalization and wordpiece segmentation stages.
 * <p>
 * Runs of punctuation are split into individual tokens. A word that cannot be
 * fully represented by vocabulary pieces becomes one unknown token.
 * {@link #tokenizePos(String)} is not supported.
 * <p>
 * For reference see:
 * <ul>
 *  <li>
 *  <a href="https://www.tensorflow.org/text/guide/subwords_tokenizer#applying_wordpiece">
 *    https://www.tensorflow.org/text/guide/subwords_tokenizer#applying_wordpiece</a>
 *  </li>
 *  <li>
 *  <a href="https://cran.r-project.org/web/packages/wordpiece/vignettes/basic_usage.html">
 *    https://cran.r-project.org/web/packages/wordpiece/vignettes/basic_usage.html</a>
 *  </li>
 * </ul>
 *
 * @see WordpieceEncoder
 */
public class WordpieceTokenizer implements Tokenizer {

  private static final String CONTINUATION_PREFIX = "##";

  /** BERT classification token: {@code [CLS]}. */
  public static final String BERT_CLS_TOKEN = "[CLS]";
  /** BERT separator token: {@code [SEP]}. */
  public static final String BERT_SEP_TOKEN = "[SEP]";
  /** BERT unknown token: {@code [UNK]}. */
  public static final String BERT_UNK_TOKEN = "[UNK]";

  /** RoBERTa classification token: {@code <s>}. */
  public static final String ROBERTA_CLS_TOKEN = "<s>";
  /** RoBERTa separator token. */
  public static final String ROBERTA_SEP_TOKEN = "</s>";
  /** RoBERTa unknown token. */
  public static final String ROBERTA_UNK_TOKEN = "<unk>";

  private final Set<String> vocabulary;
  private final String classificationToken;
  private final String separatorToken;
  private final String unknownToken;
  private final int maxTokenLength;

  /**
   * Initializes a {@link WordpieceTokenizer} with a {@code vocabulary} and a default
   * maximum token length of 100 Unicode code points.
   *
   * @param vocabulary A set of tokens considered the vocabulary; must not be {@code null}
   *     or contain {@code null} or empty entries.
   * @throws IllegalArgumentException Thrown if {@code vocabulary} is invalid.
   */
  public WordpieceTokenizer(Set<String> vocabulary) {
    this(vocabulary, BERT_CLS_TOKEN, BERT_SEP_TOKEN, BERT_UNK_TOKEN,
        BertNormalization.DEFAULT_MAX_WORD_CODE_POINTS);
  }

  /**
   * Initializes a {@link WordpieceTokenizer} with a {@code vocabulary} and a custom
   * {@code maxTokenLength}.
   *
   * @param vocabulary A set of tokens considered the vocabulary; must not be {@code null}
   *     or contain {@code null} or empty entries.
   * @param maxTokenLength The non-negative maximum number of Unicode code points in one token.
   * @throws IllegalArgumentException Thrown if {@code vocabulary} is invalid or
   *     {@code maxTokenLength} is negative.
   */
  public WordpieceTokenizer(Set<String> vocabulary, int maxTokenLength) {
    this(vocabulary, BERT_CLS_TOKEN, BERT_SEP_TOKEN, BERT_UNK_TOKEN, maxTokenLength);
  }

  /**
   * Initializes a {@link WordpieceTokenizer} with a
   * {@code vocabulary} and custom special tokens.
   * This allows support for models like RoBERTa that
   * use different special tokens instead of the BERT
   * defaults.
   *
   * @param vocabulary          The vocabulary; must not be {@code null} or contain {@code null}
   *                            or empty entries.
   * @param classificationToken The CLS token; must not be {@code null} or empty.
   * @param separatorToken      The SEP token; must not be {@code null} or empty.
   * @param unknownToken        The UNK token; must not be {@code null} or empty.
   * @throws IllegalArgumentException Thrown if an argument is invalid.
   */
  public WordpieceTokenizer(
      final Set<String> vocabulary,
      final String classificationToken,
      final String separatorToken,
      final String unknownToken) {
    this(vocabulary, classificationToken, separatorToken, unknownToken,
        BertNormalization.DEFAULT_MAX_WORD_CODE_POINTS);
  }

  /**
   * Initializes a {@link WordpieceTokenizer} with a {@code vocabulary},
   * custom special tokens and a custom {@code maxTokenLength}.
   *
   * @param vocabulary          The vocabulary; must not be {@code null} or contain {@code null}
   *                            or empty entries.
   * @param classificationToken The CLS token; must not be {@code null} or empty.
   * @param separatorToken      The SEP token; must not be {@code null} or empty.
   * @param unknownToken        The UNK token; must not be {@code null} or empty.
   * @param maxTokenLength      The non-negative maximum number of Unicode code points in one token.
   * @throws IllegalArgumentException Thrown if an argument is invalid.
   */
  public WordpieceTokenizer(
      final Set<String> vocabulary,
      final String classificationToken,
      final String separatorToken,
      final String unknownToken,
      final int maxTokenLength) {
    this.vocabulary = copyVocabulary(vocabulary);
    this.classificationToken = requireToken(classificationToken, "classificationToken");
    this.separatorToken = requireToken(separatorToken, "separatorToken");
    this.unknownToken = requireToken(unknownToken, "unknownToken");
    this.maxTokenLength = requireNonNegative(maxTokenLength);
  }

  /** Validates and copies a vocabulary. */
  private Set<String> copyVocabulary(Set<String> vocabulary) {
    if (vocabulary == null) {
      throw new IllegalArgumentException("vocabulary must not be null");
    }
    final Set<String> copy = new HashSet<>(vocabulary.size());
    for (final String piece : vocabulary) {
      if (piece == null) {
        throw new IllegalArgumentException("vocabulary must not contain null");
      }
      if (piece.isEmpty()) {
        throw new IllegalArgumentException("vocabulary must not contain an empty piece");
      }
      copy.add(piece);
    }
    return Set.copyOf(copy);
  }

  /** Validates a special token. */
  private String requireToken(String token, String name) {
    if (token == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    if (token.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be empty");
    }
    return token;
  }

  /** Validates the maximum token length. */
  private int requireNonNegative(final int maxTokenLength) {
    if (maxTokenLength < 0) {
      throw new IllegalArgumentException(
          "maxTokenLength must be non-negative: " + maxTokenLength);
    }
    return maxTokenLength;
  }

  /**
   * Not supported: wordpiece tokens (subwords, {@code ##} continuations and
   * special tokens) have no faithful character spans in the original text.
   *
   * @throws UnsupportedOperationException Always.
   */
  @Override
  public Span[] tokenizePos(final String text) {
    throw new UnsupportedOperationException(
        "Wordpiece tokens cannot be mapped to character spans of the original text");
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  @Override
  public String[] tokenize(final String text) {

    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }

    final List<String> tokens = new LinkedList<>();
    tokens.add(classificationToken);

    // Isolate each punctuation character as its own token, as the reference
    // BERT tokenization does. Runs of punctuation become individual tokens.
    final String spacedPunctuation = BertNormalization.isolatePunctuation(text);

    // Split based on whitespace.
    final String[] split = WhitespaceTokenizer.INSTANCE.tokenize(spacedPunctuation);

    // For each resulting word, if the word is found in the WordPiece vocabulary, keep it as-is.
    // If not, starting from the beginning, pull off the biggest piece that is in the vocabulary,
    // and prefix "##" to the remaining piece. Repeat until the entire word is represented by
    // pieces from the vocabulary. If the word cannot be fully represented, the whole word
    // becomes a single unknown token, as in the reference BERT implementation.
    for (final String token : split) {

      final char[] characters = token.toCharArray();

      if (Character.codePointCount(characters, 0, characters.length) <= maxTokenLength) {

        // The pieces of this word. Only added to the result if the whole word matches.
        final List<String> wordPieces = new LinkedList<>();

        // To start, the substring is the whole token.
        int start = 0;
        int end;
        boolean found = true;

        // Look at the token from the start.
        while (start < characters.length) {

          end = characters.length;
          found = false;

          // Look at the token from the end until the end is equal to the start.
          while (start < end) {

            // The substring is the part of the token we are looking at now.
            String substring = String.valueOf(characters, start, end - start);

            // This is a substring so prefix it with ##.
            if (start > 0) {
              substring = CONTINUATION_PREFIX + substring;
            }

            // See if the substring is in the vocabulary.
            if (vocabulary.contains(substring)) {

              // It is in the vocabulary so add it to the pieces of this word.
              wordPieces.add(substring);

              // Next time we can pick up where we left off.
              start = end;
              found = true;

              break;

            }

            // Subtract 1 from the end to find the next longest piece in the vocabulary.
            end -= Character.charCount(Character.codePointBefore(characters, end));

          }

          // A part of the word is not representable by vocabulary pieces, so the
          // whole word is replaced with the unknown token.
          if (!found) {
            break;
          }

          // Start the next characters where we just left off.
          start = end;

        }

        if (found) {
          tokens.addAll(wordPieces);
        } else {
          tokens.add(unknownToken);
        }

      } else {

        // If the token's length is greater than the max length just add unknown token instead.
        tokens.add(unknownToken);

      }

    }

    tokens.add(separatorToken);

    return tokens.toArray(new String[0]);

  }

  /**
   * @return The maximum token length.
   */
  public int getMaxTokenLength() {
    return maxTokenLength;
  }

}
