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
import java.util.Random;

import opennlp.subword.sentencepiece.SentencePieceTokenizer;

/**
 * Builds a SentencePiece-layout embedding model around the bundled tiny Unigram model. Its
 * tokenizer ids and matrix rows differ, which tests row lookup by piece text.
 */
final class SentencePieceModelFixture {

  static final String MODEL_RESOURCE = "/opennlp/embeddings/tiny-unigram.model";
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

  private final byte[] modelBytes;
  private final SentencePieceTokenizer tokenizer;
  // The matrix rows: <pad>, <unk>, then every poolable tokenizer piece.
  private final List<String> rows;

  /**
   * Loads the bundled model resource and derives the row order.
   *
   * @throws IOException Thrown if the resource cannot be read.
   */
  SentencePieceModelFixture() throws IOException {
    try (InputStream in = modelResource()) {
      modelBytes = in.readAllBytes();
    }
    try (InputStream in = modelResource()) {
      tokenizer = SentencePieceTokenizer.load(in);
    }
    rows = new ArrayList<>();
    rows.add("<pad>");
    rows.add("<unk>");
    for (int id = 0; id < tokenizer.vocabularySize(); id++) {
      if (!tokenizer.isControl(id) && !tokenizer.isUnknown(id)) {
        rows.add(tokenizer.idToPiece(id));
      }
    }
  }

  /** {@return the loaded tokenizer} */
  SentencePieceTokenizer tokenizer() {
    return tokenizer;
  }

  /** {@return the matrix row pieces, in row order, with the appended added token last} */
  List<String> rowPieces() {
    final List<String> pieces = new ArrayList<>(rows);
    pieces.add("<extra_special>");
    return pieces;
  }

  /**
   * Writes the {@code .model}, a synthesized Unigram {@code tokenizer.json}, a deterministic
   * embedding matrix, and a {@code config.json} into a directory.
   *
   * @param directory The directory to write into.
   * @param dimension The embedding dimension.
   * @param normalize The {@code config.json} normalize value.
   * @param seed      The seed of the deterministic matrix values.
   * @throws IOException Thrown if writing fails.
   */
  void write(Path directory, int dimension, boolean normalize, long seed) throws IOException {
    Files.write(directory.resolve("sentencepiece.bpe.model"), modelBytes);
    Files.writeString(directory.resolve("tokenizer.json"), tokenizerJson(rows));
    final int rowCount = rows.size() + 1;
    final float[][] matrix = new float[rowCount][dimension];
    final Random random = new Random(seed);
    for (final float[] row : matrix) {
      final float rowScale = 0.5f + 2f * random.nextFloat();
      for (int d = 0; d < dimension; d++) {
        row[d] = rowScale * (float) random.nextGaussian();
      }
    }
    SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", matrix));
    Files.writeString(directory.resolve(ModelFileNames.CONFIG),
        "{\"model_type\":\"model2vec\",\"normalize\":" + normalize + "}");
  }

  /**
   * {@return a Unigram {@code tokenizer.json} whose vocabulary is the given pieces plus an
   * appended added token} The appended token is not {@code <mask>} because the fixture model
   * defines {@code <mask>} as a user-defined piece that already owns a row.
   *
   * @param pieces The {@code model.vocab} pieces in row order.
   */
  private String tokenizerJson(List<String> pieces) {
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

  /**
   * {@return {@code text} as a JSON string literal}
   *
   * @param text The text to quote.
   */
  private String quote(String text) {
    final StringBuilder quoted = new StringBuilder("\"");
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);
      switch (c) {
        case '"' -> quoted.append("\\\"");
        case '\\' -> quoted.append("\\\\");
        default -> {
          if (c < 0x20) {
            quoted.append("\\u")
                .append(HEX_DIGITS[(c >>> 12) & 0xF])
                .append(HEX_DIGITS[(c >>> 8) & 0xF])
                .append(HEX_DIGITS[(c >>> 4) & 0xF])
                .append(HEX_DIGITS[c & 0xF]);
          } else {
            quoted.append(c);
          }
        }
      }
    }
    return quoted.append('"').toString();
  }

  /**
   * Opens the bundled SentencePiece model.
   *
   * @return A new stream for the model resource.
   * @throws IOException Thrown if the resource is missing.
   */
  private InputStream modelResource() throws IOException {
    final InputStream in = SentencePieceModelFixture.class.getResourceAsStream(MODEL_RESOURCE);
    if (in == null) {
      throw new IOException("Missing test resource " + MODEL_RESOURCE);
    }
    return in;
  }
}
