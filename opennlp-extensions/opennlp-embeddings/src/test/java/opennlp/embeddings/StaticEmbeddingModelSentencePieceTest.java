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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.StaticEmbeddingModel.Normalization;
import opennlp.subword.sentencepiece.SentencePieceTokenizer;
import opennlp.tools.tokenize.SubwordPiece;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SentencePiece loading path, exercised against a real trained tiny model (a copy of the
 * opennlp-subword test fixture). The matrix vocabulary is written the way a distillation ships
 * it: control pieces dropped, rows ordered differently from the tokenizer's ids, extra special
 * rows in front, and an extra token appended through {@code added_tokens}; every lookup must
 * therefore go by piece string, never by tokenizer id.
 */
class StaticEmbeddingModelSentencePieceTest {

  private static final String MODEL_RESOURCE = "/opennlp/embeddings/tiny-unigram.model";
  private static final int DIMENSION = 4;

  private static byte[] modelBytes;
  private static SentencePieceTokenizer tokenizer;
  // The matrix rows: <pad>, <unk>, then every poolable tokenizer piece, then <mask>.
  private static List<String> rows;

  @BeforeAll
  static void loadFixture() throws IOException {
    try (InputStream in =
             StaticEmbeddingModelSentencePieceTest.class.getResourceAsStream(MODEL_RESOURCE)) {
      modelBytes = in.readAllBytes();
    }
    tokenizer = SentencePieceTokenizer.load(
        StaticEmbeddingModelSentencePieceTest.class.getResourceAsStream(MODEL_RESOURCE));
    rows = new ArrayList<>();
    rows.add("<pad>");
    rows.add("<unk>");
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      if (!tokenizer.isControl(id) && !tokenizer.isUnknown(id)) {
        rows.add(tokenizer.idToPiece(id));
      }
    }
  }

  /**
   * {@return the value at {@code (row, d)} of the deterministic test matrix}
   *
   * @param row The matrix row.
   * @param d   The dimension index.
   */
  private static float cell(int row, int d) {
    return row + d * 0.25f;
  }

  /**
   * Writes the three SentencePiece-layout files (and optionally a {@code config.json}) into a
   * directory: the copied {@code .model}, a synthesized Unigram {@code tokenizer.json} whose
   * vocabulary is {@link #rows} with one token appended via {@code added_tokens}, and a
   * deterministic embedding matrix with one extra row for it.
   *
   * @param dir       The directory to write into.
   * @param normalize The {@code config.json} normalize value, or {@code null} to omit the file.
   * @return The directory.
   * @throws IOException Thrown if writing fails.
   */
  private static Path writeModelDirectory(Path dir, Boolean normalize) throws IOException {
    Files.write(dir.resolve("sentencepiece.bpe.model"), modelBytes);
    Files.writeString(dir.resolve("tokenizer.json"), tokenizerJson(rows));
    final float[][] matrix = new float[rows.size() + 1][DIMENSION];
    for (int row = 0; row < matrix.length; row++) {
      for (int d = 0; d < DIMENSION; d++) {
        matrix[row][d] = cell(row, d);
      }
    }
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));
    if (normalize != null) {
      Files.writeString(dir.resolve("config.json"),
          "{\"model_type\":\"model2vec\",\"normalize\":" + normalize + "}");
    }
    return dir;
  }

  /**
   * {@return a Unigram {@code tokenizer.json} whose vocabulary is the given pieces plus an
   * appended added token}
   *
   * <p>The appended token is not named {@code <mask>} because the fixture model itself defines
   * {@code <mask>} as a user-defined piece, which already owns a row.</p>
   *
   * @param pieces The {@code model.vocab} pieces in row order.
   */
  private static String tokenizerJson(List<String> pieces) {
    final StringBuilder json = new StringBuilder("{\"version\":\"1.0\",\"added_tokens\":[");
    json.append("{\"id\":0,\"content\":\"<pad>\",\"special\":true},");
    json.append("{\"id\":").append(pieces.size()).append(",\"content\":\"<extra_special>\","
        + "\"special\":true}],");
    json.append("\"normalizer\":{\"type\":\"Precompiled\"},\"model\":{\"type\":\"Unigram\","
        + "\"unk_id\":1,\"vocab\":[");
    for (int i = 0; i < pieces.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('[').append(quote(pieces.get(i))).append(",-").append(i % 7).append(".5]");
    }
    return json.append("]}}").toString();
  }

  /** {@return {@code s} as a JSON string literal} */
  private static String quote(String s) {
    final StringBuilder quoted = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        default -> {
          if (c < 0x20) {
            quoted.append(String.format("\\u%04x", (int) c));
          } else {
            quoted.append(c);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }

  @Test
  void testEmbedGathersRowsByPieceStringAcrossTheIdOffset(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = loadFromDirectory(writeModelDirectory(dir, null));

    // "a" segments to the single piece U+2581 + "a"; the embedding must be exactly that piece's
    // matrix row, found by string in the reordered vocabulary, not by the tokenizer's id.
    final List<SubwordPiece> pieces = tokenizer.encode("a");
    assertEquals(1, pieces.size());
    final int row = rows.indexOf(pieces.get(0).piece());
    assertTrue(row >= 2, "the fixture row must sit above the injected specials");
    final float[] expected = new float[DIMENSION];
    for (int d = 0; d < DIMENSION; d++) {
      expected[d] = cell(row, d);
    }
    assertArrayEquals(expected, model.embed("a"), 1e-5f);
  }

  @Test
  void testEmbedMeanPoolsAllMappedPieces(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = loadFromDirectory(writeModelDirectory(dir, null));

    // Expected: the mean over every non-control, non-unknown piece's row, resolved by string.
    final List<SubwordPiece> pieces = tokenizer.encode("Hello world");
    final float[] expected = new float[DIMENSION];
    int pooled = 0;
    for (final SubwordPiece piece : pieces) {
      if (tokenizer.isControl(piece.id()) || tokenizer.isUnknown(piece.id())) {
        continue;
      }
      final int row = rows.indexOf(piece.piece());
      assertTrue(row >= 0, "fixture piece '" + piece.piece() + "' must have a row");
      for (int d = 0; d < DIMENSION; d++) {
        expected[d] += cell(row, d);
      }
      pooled++;
    }
    assertTrue(pooled > 1, "the fixture text must pool more than one piece");
    for (int d = 0; d < DIMENSION; d++) {
      expected[d] /= pooled;
    }
    assertArrayEquals(expected, model.embed("Hello world"), 1e-4f);
  }

  @Test
  void testUnknownPiecesAreSkippedInPooling(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = loadFromDirectory(writeModelDirectory(dir, null));

    // The euro sign is outside the tiny training corpus, so it segments to the dummy-prefix
    // piece plus an unknown piece carrying the surface text. The unknown piece's string is not
    // a vocabulary entry, so pooling must skip it by its id, leaving only the mapped pieces.
    final List<SubwordPiece> pieces = tokenizer.encode("\u20AC");
    final float[] expected = new float[DIMENSION];
    int pooled = 0;
    int unknown = 0;
    for (final SubwordPiece piece : pieces) {
      if (tokenizer.isUnknown(piece.id())) {
        unknown++;
        continue;
      }
      if (tokenizer.isControl(piece.id())) {
        continue;
      }
      final int row = rows.indexOf(piece.piece());
      for (int d = 0; d < DIMENSION; d++) {
        expected[d] += cell(row, d);
      }
      pooled++;
    }
    assertTrue(unknown > 0, "fixture assumption: the euro sign must produce an unknown piece");
    for (int d = 0; d < DIMENSION; d++) {
      expected[d] /= Math.max(pooled, 1);
    }
    assertArrayEquals(expected, model.embed("\u20AC"), 1e-5f);
  }

  @Test
  void testDirectoryLoadDetectsTheSentencePieceLayout(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeModelDirectory(dir, true));

    assertEquals(DIMENSION, model.dimension());
    assertEquals(rows.size() + 1, model.vocabularySize());
    // normalize=true from config.json: the pooled vector must have unit length.
    final float[] vector = model.embed("a");
    double normSquared = 0;
    for (final float v : vector) {
      normSquared += (double) v * v;
    }
    assertEquals(1.0, Math.sqrt(normSquared), 1e-5);
  }

  @Test
  void testMostSimilarNeverReturnsSpecialRows(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = loadFromDirectory(writeModelDirectory(dir, null));

    for (final Neighbor neighbor : model.mostSimilar("a", 5)) {
      assertFalse(List.of("<pad>", "<unk>", "<mask>").contains(neighbor.token()),
          "special row leaked into neighbors: " + neighbor.token());
    }
  }

  @Test
  void testLoadRejectsAVocabularyMissingAPoolablePiece(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir, null);
    // Remove one poolable piece from the matrix vocabulary; the matrix shrinks with it, so only
    // the coverage check can catch the mismatch.
    final List<String> truncated = new ArrayList<>(rows);
    truncated.remove(truncated.size() - 1);
    Files.writeString(dir.resolve("tokenizer.json"), tokenizerJson(truncated));
    final float[][] matrix = new float[truncated.size() + 1][DIMENSION];
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> loadFromDirectory(dir));
    assertTrue(e.getMessage().contains("do not belong"), e.getMessage());
  }

  @Test
  void testLoadRejectsARowCountMismatch(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir, null);
    final float[][] matrix = new float[rows.size()][DIMENSION];
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> loadFromDirectory(dir));
    assertTrue(e.getMessage().contains("rows"), e.getMessage());
  }

  @Test
  void testDirectoryLoadNamesTheMissingSentencePieceModel(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir, true);
    Files.delete(dir.resolve("sentencepiece.bpe.model"));

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("copy the .model"), e.getMessage());
  }

  @Test
  void testLoadSentencePieceRejectsNullArguments(@TempDir Path dir) throws IOException {
    writeModelDirectory(dir, null);
    final Path model = dir.resolve("sentencepiece.bpe.model");
    final Path json = dir.resolve("tokenizer.json");
    final Path tensors = dir.resolve("model.safetensors");

    assertThrows(IllegalArgumentException.class, () ->
        StaticEmbeddingModel.loadSentencePiece(null, json, tensors, Normalization.NONE));
    assertThrows(IllegalArgumentException.class, () ->
        StaticEmbeddingModel.loadSentencePiece(model, null, tensors, Normalization.NONE));
    assertThrows(IllegalArgumentException.class, () ->
        StaticEmbeddingModel.loadSentencePiece(model, json, null, Normalization.NONE));
    assertThrows(IllegalArgumentException.class, () ->
        StaticEmbeddingModel.loadSentencePiece(model, json, tensors, null));
  }

  /**
   * Loads through the explicit SentencePiece overload from a directory written by
   * {@link #writeModelDirectory(Path, Boolean)}.
   *
   * @param dir The model directory.
   * @return The loaded model.
   * @throws IOException Thrown if reading fails.
   */
  private static StaticEmbeddingModel loadFromDirectory(Path dir) throws IOException {
    return StaticEmbeddingModel.loadSentencePiece(dir.resolve("sentencepiece.bpe.model"),
        dir.resolve("tokenizer.json"), dir.resolve("model.safetensors"), Normalization.NONE);
  }
}
