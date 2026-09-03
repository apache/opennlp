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

import java.util.ArrayList;
import java.util.Set;

import opennlp.tools.util.Span;

/**
 * A {@link Tokenizer} implementation of the full BERT tokenization pipeline:
 * basic tokenization (text normalization) followed by wordpiece tokenization,
 * with the classification and separator tokens framing every result.
 *
 * @deprecated Use {@link WordpieceEncoder} instead:
 *     {@link WordpieceEncoder#encodeToPieces(CharSequence)} returns the same
 *     {@code String[]} as {@link #tokenize(String)}, while
 *     {@link WordpieceEncoder#encode(CharSequence)} also returns ids and source spans.
 *
 * @see WordpieceEncoder
 */
@Deprecated(since = "3.0.0", forRemoval = true)
public class BertTokenizer implements Tokenizer {

  private final WordpieceEncoder encoder;

  /**
   * Initializes a {@link BertTokenizer} for an <i>uncased</i> BERT model,
   * with lower casing and accent stripping enabled.
   *
   * @param vocabulary The wordpiece vocabulary. Must not be {@code null}.
   *
   * @throws IllegalArgumentException Thrown if the vocabulary is {@code null},
   *     contains {@code null}, or is missing a BERT special token.
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
   *
   * @throws IllegalArgumentException Thrown if the vocabulary is {@code null},
   *     contains {@code null}, or is missing a BERT special token.
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
   * @param classificationToken The CLS token; must be in the vocabulary.
   * @param separatorToken      The SEP token; must be in the vocabulary.
   * @param unknownToken        The UNK token; must be in the vocabulary.
   *
   * @throws IllegalArgumentException Thrown if any argument is {@code null},
   *     the vocabulary contains {@code null}, or a special token is missing
   *     from the vocabulary.
   */
  public BertTokenizer(Set<String> vocabulary, boolean lowerCase,
      String classificationToken, String separatorToken, String unknownToken) {
    if (vocabulary == null) {
      throw new IllegalArgumentException("vocabulary must not be null");
    }
    // Set iteration order does not affect the piece strings returned by tokenize().
    this.encoder = new WordpieceEncoder(new ArrayList<>(vocabulary), lowerCase,
        classificationToken, separatorToken, unknownToken);
  }

  /** {@inheritDoc} */
  @Override
  public String[] tokenize(String text) {
    return encoder.encodeToPieces(text);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Wordpiece tokens (subwords, {@code ##} continuations and special tokens) do not have
   * character ranges that satisfy the {@link Tokenizer} input-span contract.
   * Use {@link WordpieceEncoder#encode(CharSequence)} for pieces with
   * original-text spans.
   *
   * @throws UnsupportedOperationException Always.
   */
  @Override
  public Span[] tokenizePos(String text) {
    throw new UnsupportedOperationException(
        "Wordpiece tokens cannot be mapped to character spans of the original text");
  }

}
