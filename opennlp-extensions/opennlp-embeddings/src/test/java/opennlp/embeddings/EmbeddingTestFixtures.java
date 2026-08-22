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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel.Casing;
import opennlp.embeddings.StaticEmbeddingModel.Normalization;
import opennlp.subword.sentencepiece.SentencePieceTokenizer;

/**
 * Fixtures shared by more than one test in this module: the small WordPiece table the geometry
 * tests load, and JSON string quoting for the hand-built {@code tokenizer.json} fixtures.
 */
public final class EmbeddingTestFixtures {

  /** The analogy table's tokens; the list index is the matrix row. */
  static final List<String> ANALOGY_VOCABULARY =
      List.of("[CLS]", "[SEP]", "[UNK]", "king", "queen", "man", "woman", "apple");

  /**
   * The analogy table's rows, chosen so the classic word2vec analogy is exact:
   * {@code king - man + woman = [3,3] - [2,1] + [1,2] = [2,4] = queen}. The directions genuinely
   * differ, so pairwise cosine similarities are not trivially 1.0.
   */
  static final float[][] ANALOGY_ROWS = {
      {0f, 0f},   // [CLS]
      {0f, 0f},   // [SEP]
      {0f, 0f},   // [UNK]
      {3f, 3f},   // king
      {2f, 4f},   // queen
      {2f, 1f},   // man
      {1f, 2f},   // woman
      {-3f, -1f}, // apple: unrelated, opposite-ish direction
  };

  /** Not instantiable. */
  private EmbeddingTestFixtures() {
  }

  /**
   * Writes {@link #ANALOGY_VOCABULARY} and {@link #ANALOGY_ROWS} into a directory and loads them
   * through the explicit WordPiece overload.
   *
   * @param dir           The directory to write the fixture files into.
   * @param normalization Whether the loaded model L2-normalizes its pooled vectors.
   * @return The loaded model.
   * @throws IOException Thrown if writing or reading a fixture file fails.
   */
  static StaticEmbeddingModel loadAnalogyModel(Path dir, Normalization normalization)
      throws IOException {
    writeVocabularyAndMatrix(dir);
    return StaticEmbeddingModel.load(dir.resolve("vocab.txt"), dir.resolve("model.safetensors"),
        Casing.UNCASED, normalization);
  }

  /**
   * Writes {@link #ANALOGY_VOCABULARY} and {@link #ANALOGY_ROWS} into a directory as a complete
   * WordPiece model directory (with its two JSON configuration files), so a test can load it
   * with {@code StaticEmbeddingModel.load(Path)} the way the manual's usage listing shows.
   *
   * @param dir The directory to write the model files into.
   * @throws IOException Thrown if writing a fixture file fails.
   */
  public static void writeAnalogyDirectory(Path dir) throws IOException {
    writeVocabularyAndMatrix(dir);
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");
    Files.writeString(dir.resolve("tokenizer_config.json"), "{\"do_lower_case\":true}");
  }

  /**
   * Writes the analogy table's {@code vocab.txt} and {@code model.safetensors} into a directory.
   *
   * @param dir The directory to write the fixture files into.
   * @throws IOException Thrown if writing a fixture file fails.
   */
  private static void writeVocabularyAndMatrix(Path dir) throws IOException {
    Files.write(dir.resolve("vocab.txt"), ANALOGY_VOCABULARY);
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", ANALOGY_ROWS));
  }

  /** The classpath resource of the tiny trained SentencePiece model shared by the tests. */
  static final String TINY_UNIGRAM_RESOURCE = "/opennlp/embeddings/tiny-unigram.model";

  /** The row width of the matrix {@link #writeSentencePieceDirectory(Path)} writes. */
  static final int SENTENCEPIECE_DIMENSION = 4;

  /**
   * Writes a minimal loadable SentencePiece model into a directory: the trained
   * {@code tiny-unigram.model} fixture copied as {@code sentencepiece.bpe.model}, a Unigram
   * {@code tokenizer.json} whose vocabulary is the unknown piece followed by every poolable
   * tokenizer piece, and a deterministic embedding matrix with one row per listed piece. A test
   * can then load it through the explicit
   * {@code StaticEmbeddingModel.loadSentencePiece(Path, Path, Path, Normalization)} overload
   * the way the manual's listing shows.
   *
   * @param dir The directory to write the model files into.
   * @throws IOException Thrown if reading the fixture resource or writing a file fails.
   */
  static void writeSentencePieceDirectory(Path dir) throws IOException {
    writeSentencePieceDirectory(dir, List.of());
  }

  /**
   * Writes the SentencePiece model directory of {@link #writeSentencePieceDirectory(Path)} with
   * additional term rows: the terms land in {@code terms.txt} and the matrix grows one row per
   * term, keeping the deterministic {@code row + d * 0.25} cell formula, so a test can predict a
   * term row's vector from the model's vocabulary size.
   *
   * @param dir   The directory to write the model files into.
   * @param terms The terms in row order; empty for none.
   * @throws IOException Thrown if reading the fixture resource or writing a file fails.
   */
  static void writeSentencePieceDirectory(Path dir, List<String> terms) throws IOException {
    final byte[] modelBytes;
    try (InputStream in =
             EmbeddingTestFixtures.class.getResourceAsStream(TINY_UNIGRAM_RESOURCE)) {
      modelBytes = in.readAllBytes();
    }
    Files.write(dir.resolve("sentencepiece.bpe.model"), modelBytes);
    final SentencePieceTokenizer tokenizer =
        SentencePieceTokenizer.load(new ByteArrayInputStream(modelBytes));
    final List<String> rows = new ArrayList<>();
    rows.add("<unk>");
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      if (!tokenizer.isControl(id) && !tokenizer.isUnknown(id)) {
        rows.add(tokenizer.idToPiece(id));
      }
    }
    final StringBuilder json =
        new StringBuilder("{\"model\":{\"type\":\"Unigram\",\"unk_id\":0,\"vocab\":[");
    for (int i = 0; i < rows.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('[').append(jsonString(rows.get(i))).append(",-1.5]");
    }
    Files.writeString(dir.resolve("tokenizer.json"), json.append("]}}").toString());
    if (!terms.isEmpty()) {
      Files.write(dir.resolve("terms.txt"), terms);
    }
    final float[][] matrix = new float[rows.size() + terms.size()][SENTENCEPIECE_DIMENSION];
    for (int row = 0; row < matrix.length; row++) {
      for (int d = 0; d < SENTENCEPIECE_DIMENSION; d++) {
        matrix[row][d] = row + d * 0.25f;
      }
    }
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));
  }

  /**
   * {@return {@code value} as a JSON string literal, quoted and escaped}
   *
   * @param value The string to quote.
   */
  static String jsonString(String value) {
    final StringBuilder quoted = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
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
}
