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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.InvalidFormatException;

/**
 * The row table of a static embedding matrix: piece string to row index and back. Row {@code id}
 * of the matrix holds the vector of the piece at position {@code id} in this vocabulary.
 *
 * <p>Two layouts produce it: a BERT-style {@code vocab.txt} (one token per line, the line number
 * is the row), and a {@code tokenizer.json} with a Unigram model (the {@code model.vocab} list
 * order is the row order, with {@code added_tokens} overlaid).</p>
 *
 * <p>Immutable and safe for concurrent reads after construction.</p>
 */
@ThreadSafe
final class EmbeddingVocabulary {

  private final Map<String, Integer> idByToken;
  private final List<String> tokenById;
  private final Set<Integer> specialRows;

  /** Holds the parsed piece-to-row and row-to-piece views; built by the {@code from*} factories. */
  private EmbeddingVocabulary(Map<String, Integer> idByToken, List<String> tokenById,
                              Set<Integer> specialRows) {
    this.idByToken = idByToken;
    this.tokenById = tokenById;
    this.specialRows = specialRows;
  }

  /**
   * Reads a {@code vocab.txt} file: one token per line, the line number (0-based) is the row.
   *
   * @param file The vocabulary file. Must not be {@code null} and must exist.
   * @return The parsed vocabulary.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing.
   * @throws InvalidFormatException Thrown if the file contains a duplicate token.
   * @throws IOException Thrown if reading the file fails.
   */
  static EmbeddingVocabulary fromVocabTxt(Path file) throws IOException {
    requireRegularFile(file);
    return fromLines(Files.readAllLines(file), file.toString());
  }

  /**
   * Reads the Unigram vocabulary of a {@code tokenizer.json} file: the {@code model.vocab} list
   * order is the row order, with {@code added_tokens} overlaid.
   *
   * @param file The {@code tokenizer.json} file. Must not be {@code null} and must exist.
   * @return The parsed vocabulary.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing.
   * @throws InvalidFormatException Thrown if the file is not a well-formed Unigram
   *     {@code tokenizer.json} or a piece appears more than once.
   * @throws IOException Thrown if reading the file fails.
   */
  static EmbeddingVocabulary fromTokenizerJson(Path file) throws IOException {
    requireRegularFile(file);
    final TokenizerJsonVocab.Result result = TokenizerJsonVocab.read(file);
    return fromLines(result.rows(), file.toString(), result.specialRows());
  }

  /**
   * Requires {@code file} to be an existing regular file.
   *
   * @param file The file to check.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}, missing, or not a
   *     regular file.
   */
  private static void requireRegularFile(Path file) {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
    }
  }

  /**
   * Builds a vocabulary from in-memory lines, the token order.
   *
   * @param lines      The tokens, one per element; the index is the token's row.
   * @param sourceName The source's name, for error messages.
   * @return The parsed vocabulary.
   * @throws InvalidFormatException Thrown if a token appears more than once.
   */
  static EmbeddingVocabulary fromLines(List<String> lines, String sourceName)
      throws InvalidFormatException {
    return fromLines(lines, sourceName, Set.of());
  }

  /** Builds a vocabulary and records rows declared as special tokens. */
  private static EmbeddingVocabulary fromLines(List<String> lines, String sourceName,
                                               Set<Integer> specialRows)
      throws InvalidFormatException {
    final Map<String, Integer> idByToken = new LinkedHashMap<>(lines.size() * 2);
    for (int id = 0; id < lines.size(); id++) {
      final String token = lines.get(id);
      if (idByToken.putIfAbsent(token, id) != null) {
        throw new InvalidFormatException(
            "Vocabulary " + sourceName + " declares token '" + token
                + "' more than once, at rows " + idByToken.get(token) + " and " + id);
      }
    }
    return new EmbeddingVocabulary(Collections.unmodifiableMap(idByToken), List.copyOf(lines),
        Set.copyOf(specialRows));
  }

  /** {@return every token in this vocabulary, without order} */
  Set<String> tokens() {
    return idByToken.keySet();
  }

  /** {@return every token in row order, suitable for an id-is-index tokenizer constructor} */
  List<String> orderedTokens() {
    return tokenById;
  }

  /**
   * Looks up a token's row id.
   *
   * @param token The token to look up. Must not be {@code null}.
   * @return The token's id, or {@code -1} when the token is not in this vocabulary.
   * @throws IllegalArgumentException Thrown if {@code token} is {@code null}.
   */
  int id(String token) {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    final Integer id = idByToken.get(token);
    return id == null ? -1 : id;
  }

  /** {@return the number of tokens in this vocabulary} */
  int size() {
    return idByToken.size();
  }

  /** {@return the rows declared as special tokens by the vocabulary source} */
  Set<Integer> specialRows() {
    return specialRows;
  }

  /**
   * Looks up the token at a row id.
   *
   * @param id The row id. Must be within {@code [0, size())}.
   * @return The token at that id.
   * @throws IllegalArgumentException Thrown if {@code id} is outside {@code [0, size())}.
   */
  String token(int id) {
    if (id < 0 || id >= tokenById.size()) {
      throw new IllegalArgumentException(
          "Id " + id + " is outside [0, " + tokenById.size() + ")");
    }
    return tokenById.get(id);
  }
}
