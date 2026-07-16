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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the row order of a static embedding matrix out of a {@code tokenizer.json} file with a
 * Unigram model: the {@code model.vocab} list holds {@code [piece, score]} pairs whose index is
 * the piece's id, and the {@code added_tokens} list overlays extra pieces (appended when their id
 * is the next row, checked for agreement when it is an existing row). Only the vocabulary is
 * read; every other section, including the tokenizer's normalizer and segmentation state, is
 * skipped structurally. Like the package's other readers this is purpose-built on
 * {@link JsonCursor}, not a general JSON library, and fails loud on anything outside the known
 * shape.
 */
final class TokenizerJsonVocab {

  /** Not instantiable. */
  private TokenizerJsonVocab() {
  }

  /**
   * One entry of the {@code added_tokens} list.
   *
   * @param id      The token's id, the matrix row it claims.
   * @param content The token's string.
   */
  private record AddedToken(long id, String content) {
  }

  /**
   * Reads the pieces of a Unigram {@code tokenizer.json} in row order.
   *
   * @param file The {@code tokenizer.json} file. Must not be {@code null} and must exist.
   * @return The pieces; the index is the matrix row.
   * @throws IllegalArgumentException Thrown if {@code file} is {@code null} or missing, the
   *     file is not a well-formed {@code tokenizer.json}, its model is not Unigram, or an added
   *     token's id neither matches an existing row nor appends as the next one.
   * @throws IOException Thrown if reading the file fails.
   */
  static List<String> rows(Path file) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("File must not be null");
    }
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("File does not exist or is not a regular file: " + file);
    }
    final String json = Files.readString(file);
    final JsonCursor cursor = new JsonCursor(json, file.getFileName().toString());
    cursor.skipWhitespace();
    cursor.expect('{');
    cursor.skipWhitespace();

    List<String> vocab = null;
    String modelType = null;
    List<AddedToken> addedTokens = List.of();
    boolean modelSeen = false;
    boolean addedTokensSeen = false;

    if (cursor.peek() == '}') {
      cursor.consume();
    } else {
      while (true) {
        cursor.skipWhitespace();
        final String key = cursor.parseString();
        cursor.skipWhitespace();
        cursor.expect(':');
        cursor.skipWhitespace();
        switch (key) {
          case "model" -> {
            if (modelSeen) {
              throw cursor.malformed("Field 'model' appears more than once");
            }
            modelSeen = true;
            final ParsedModel model = parseModel(cursor);
            vocab = model.vocab;
            modelType = model.type;
          }
          case "added_tokens" -> {
            if (addedTokensSeen) {
              throw cursor.malformed("Field 'added_tokens' appears more than once");
            }
            addedTokensSeen = true;
            addedTokens = parseAddedTokens(cursor);
          }
          default -> cursor.skipValue();
        }
        cursor.skipWhitespace();
        final char next = cursor.consume();
        if (next == ',') {
          continue;
        }
        if (next == '}') {
          break;
        }
        throw cursor.malformed("Expected ',' or '}' after a field, got '" + next + "'");
      }
    }
    cursor.requireEnd("Trailing content after the top-level object");

    if (modelType != null && !"Unigram".equals(modelType)) {
      throw new IllegalArgumentException(file + " has a '" + modelType + "' tokenizer model; "
          + "only the Unigram list layout maps pieces to matrix rows here. For a WordPiece "
          + "model, load from its vocab.txt instead");
    }
    if (vocab == null) {
      throw new IllegalArgumentException(file + " has no model.vocab list; it does not name "
          + "the matrix rows");
    }
    return overlayAddedTokens(vocab, addedTokens, file);
  }

  /** The fields read out of the {@code model} object. */
  private record ParsedModel(String type, List<String> vocab) {
  }

  /**
   * Parses the {@code model} object, collecting its {@code type} and its {@code vocab} pieces
   * in list order.
   *
   * @param cursor The cursor, positioned at the object's opening brace.
   * @return The parsed type and vocabulary; either may be absent ({@code null}).
   */
  private static ParsedModel parseModel(JsonCursor cursor) {
    cursor.expect('{');
    cursor.skipWhitespace();
    String type = null;
    List<String> vocab = null;
    if (cursor.peek() == '}') {
      cursor.consume();
      return new ParsedModel(null, null);
    }
    while (true) {
      cursor.skipWhitespace();
      final String key = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (key) {
        case "type" -> {
          if (type != null) {
            throw cursor.malformed("Field 'model.type' appears more than once");
          }
          type = cursor.parseString();
        }
        case "vocab" -> {
          if (vocab != null) {
            throw cursor.malformed("Field 'model.vocab' appears more than once");
          }
          if (cursor.peek() == '{') {
            throw cursor.malformed("model.vocab is an object; only the Unigram list layout "
                + "([piece, score] pairs) maps pieces to matrix rows here");
          }
          vocab = parseVocabList(cursor);
        }
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        return new ParsedModel(type, vocab);
      }
      throw cursor.malformed("Expected ',' or '}' after a model field, got '" + next + "'");
    }
  }

  /**
   * Parses the Unigram {@code vocab} list of {@code [piece, score]} pairs.
   *
   * @param cursor The cursor, positioned at the list's opening bracket.
   * @return The pieces in list order.
   */
  private static List<String> parseVocabList(JsonCursor cursor) {
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<String> pieces = new ArrayList<>();
    if (cursor.peek() == ']') {
      cursor.consume();
      return pieces;
    }
    while (true) {
      cursor.skipWhitespace();
      cursor.expect('[');
      cursor.skipWhitespace();
      pieces.add(cursor.parseString());
      cursor.skipWhitespace();
      cursor.expect(',');
      cursor.skipWhitespace();
      cursor.skipValue();
      cursor.skipWhitespace();
      cursor.expect(']');
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        return pieces;
      }
      throw cursor.malformed("Expected ',' or ']' after a vocab entry, got '" + next + "'");
    }
  }

  /**
   * Parses the {@code added_tokens} list of objects, keeping each entry's {@code id} and
   * {@code content}.
   *
   * @param cursor The cursor, positioned at the list's opening bracket.
   * @return The added tokens in list order.
   */
  private static List<AddedToken> parseAddedTokens(JsonCursor cursor) {
    cursor.expect('[');
    cursor.skipWhitespace();
    final List<AddedToken> tokens = new ArrayList<>();
    if (cursor.peek() == ']') {
      cursor.consume();
      return tokens;
    }
    while (true) {
      cursor.skipWhitespace();
      tokens.add(parseAddedToken(cursor));
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == ']') {
        return tokens;
      }
      throw cursor.malformed("Expected ',' or ']' after an added token, got '" + next + "'");
    }
  }

  /**
   * Parses one {@code added_tokens} object, requiring its {@code id} and {@code content}.
   *
   * @param cursor The cursor, positioned at the object's opening brace.
   * @return The parsed entry.
   */
  private static AddedToken parseAddedToken(JsonCursor cursor) {
    cursor.expect('{');
    cursor.skipWhitespace();
    Long id = null;
    String content = null;
    if (cursor.peek() == '}') {
      throw cursor.malformed("An added token must carry 'id' and 'content'");
    }
    while (true) {
      cursor.skipWhitespace();
      final String key = cursor.parseString();
      cursor.skipWhitespace();
      cursor.expect(':');
      cursor.skipWhitespace();
      switch (key) {
        case "id" -> {
          if (id != null) {
            throw cursor.malformed("Field 'id' appears more than once in an added token");
          }
          id = cursor.parseLong();
        }
        case "content" -> {
          if (content != null) {
            throw cursor.malformed("Field 'content' appears more than once in an added token");
          }
          content = cursor.parseString();
        }
        default -> cursor.skipValue();
      }
      cursor.skipWhitespace();
      final char next = cursor.consume();
      if (next == ',') {
        continue;
      }
      if (next == '}') {
        break;
      }
      throw cursor.malformed("Expected ',' or '}' after an added token field, got '"
          + next + "'");
    }
    if (id == null || content == null) {
      throw cursor.malformed("An added token must carry 'id' and 'content'");
    }
    if (id < 0) {
      throw cursor.malformed("An added token's id must not be negative: " + id);
    }
    return new AddedToken(id, content);
  }

  /**
   * Overlays the added tokens onto the vocabulary in id order: an id equal to the current size
   * appends, an id below it must agree with the piece already there, and a gap fails loud.
   *
   * @param vocab       The {@code model.vocab} pieces in list order; extended in place.
   * @param addedTokens The added tokens to overlay.
   * @param file        The source file, for error messages.
   * @return The vocabulary with the added tokens applied.
   */
  private static List<String> overlayAddedTokens(List<String> vocab,
                                                 List<AddedToken> addedTokens, Path file) {
    final List<AddedToken> byId = new ArrayList<>(addedTokens);
    byId.sort(Comparator.comparingLong(AddedToken::id));
    for (final AddedToken token : byId) {
      if (token.id() == vocab.size()) {
        vocab.add(token.content());
      } else if (token.id() < vocab.size()) {
        final String existing = vocab.get((int) token.id());
        if (!existing.equals(token.content())) {
          throw new IllegalArgumentException(file + " declares added token '" + token.content()
              + "' at id " + token.id() + " but model.vocab holds '" + existing
              + "' there; the file contradicts itself");
        }
      } else {
        throw new IllegalArgumentException(file + " declares added token '" + token.content()
            + "' at id " + token.id() + " but the vocabulary only has " + vocab.size()
            + " rows; the id space has a gap");
      }
    }
    return vocab;
  }
}
