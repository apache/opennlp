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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A BERT-style {@code vocab.txt} vocabulary: one token per line, the line number (0-based) is
 * the token's id. This is the same file format {@code bert-base-uncased} and the BGE family of
 * models ship (the tokenizer {@code minishlab/potion-base-8M} was distilled from), and it is the
 * row index into a static-embedding table's weight matrix: row {@code id} is that token's
 * vector.
 *
 * <p>Immutable and safe for concurrent reads after construction.</p>
 */
final class WordPieceVocabulary {

  private final Map<String, Integer> idByToken;
  private final List<String> tokenById;

  private WordPieceVocabulary(Map<String, Integer> idByToken, List<String> tokenById) {
    this.idByToken = idByToken;
    this.tokenById = tokenById;
  }

  /**
   * Reads a {@code vocab.txt} file.
   *
   * @param file The vocabulary file. Must not be {@code null} and must exist.
   * @return The parsed vocabulary.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null}, missing, or
   *     contains a duplicate token.
   * @throws UncheckedIOException Thrown if reading the file fails.
   */
  static WordPieceVocabulary read(Path file) {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
    }
    final List<String> lines;
    try {
      lines = Files.readAllLines(file);
    }
    catch (IOException e) {
      throw new UncheckedIOException("Unable to read vocabulary file " + file, e);
    }
    return fromLines(lines, file.toString());
  }

  // Package-private so tests can build a vocabulary from in-memory lines without a temp file.
  static WordPieceVocabulary fromLines(List<String> lines, String sourceName) {
    final Map<String, Integer> idByToken = new LinkedHashMap<>(lines.size() * 2);
    for (int id = 0; id < lines.size(); id++) {
      final String token = lines.get(id);
      if (idByToken.putIfAbsent(token, id) != null) {
        throw new IllegalArgumentException(
            "Vocabulary " + sourceName + " declares token '" + token
                + "' more than once, at lines " + idByToken.get(token) + " and " + id);
      }
    }
    return new WordPieceVocabulary(Collections.unmodifiableMap(idByToken), List.copyOf(lines));
  }

  /** {@return every token in this vocabulary, suitable for a WordpieceTokenizer} */
  Set<String> tokens() {
    return idByToken.keySet();
  }

  /**
   * Looks up a token's row id.
   *
   * @param token The token to look up. Must not be {@code null}.
   * @return The token's id, or empty when the token is not in this vocabulary.
   */
  OptionalInt id(String token) {
    if (token == null) {
      throw new IllegalArgumentException("Token must not be null");
    }
    final Integer id = idByToken.get(token);
    return id == null ? OptionalInt.empty() : OptionalInt.of(id);
  }

  /** {@return the number of tokens in this vocabulary} */
  int size() {
    return idByToken.size();
  }

  /**
   * Looks up the token at a row id.
   *
   * @param id The row id. Must be within {@code [0, size())}.
   * @return The token at that id.
   */
  String token(int id) {
    return tokenById.get(id);
  }
}
