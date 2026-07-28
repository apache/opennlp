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
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel.Casing;
import opennlp.embeddings.StaticEmbeddingModel.Normalization;

/**
 * Fixtures shared by more than one test in this module: the small WordPiece table the geometry
 * tests load, and JSON string quoting for the hand-built {@code tokenizer.json} fixtures.
 */
final class EmbeddingTestFixtures {

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
    final Path vocabulary = dir.resolve("vocab.txt");
    Files.write(vocabulary, ANALOGY_VOCABULARY);
    final Path safetensors = dir.resolve("model.safetensors");
    SafetensorsTestFiles.write(safetensors,
        SafetensorsTestFiles.matrix("embeddings", ANALOGY_ROWS));
    return StaticEmbeddingModel.load(vocabulary, safetensors, Casing.UNCASED, normalization);
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
