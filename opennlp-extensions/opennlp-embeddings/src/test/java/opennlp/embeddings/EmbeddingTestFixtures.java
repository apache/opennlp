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
import java.util.Base64;
import java.util.List;

import opennlp.embeddings.StaticEmbeddingModel.Casing;
import opennlp.embeddings.StaticEmbeddingModel.Normalization;
import opennlp.subword.sentencepiece.SentencePieceTokenizer;

/**
 * Fixtures shared by tests in this module: deterministic WordPiece tables and JSON string
 * quoting for {@code tokenizer.json} fixtures.
 */
final class EmbeddingTestFixtures {

  /** A deterministic ONNX graph mapping token ids to three-dimensional hidden states. */
  private static final String TINY_TEACHER_ONNX =
      "CAg66gIKJwoJaW5wdXRfaWRzEglpZHNfZmxvYXQiBENhc3QqCQoCdG8YAaABAgokCglpZHNf"
          + "ZmxvYXQKBGF4ZXMSBmlkc18zZCIJVW5zcXVlZXplCiYKBmlkc18zZAoBdxIRbGFzdF9oaWRk"
          + "ZW5fc3RhdGUiBk1hdE11bBIMdGlueS12ZWN0b3JzKhQIARAHQgRheGVzSggCAAAAAAAAACoX"
          + "CAEIAxABQgF3SgwAAAA/AACAvwAAAEBaJgoJaW5wdXRfaWRzEhkKFwgHEhMKBxIFYmF0Y2gK"
          + "CBIGdG9rZW5zWisKDmF0dGVudGlvbl9tYXNrEhkKFwgHEhMKBxIFYmF0Y2gKCBIGdG9rZW5z"
          + "WisKDnRva2VuX3R5cGVfaWRzEhkKFwgHEhMKBxIFYmF0Y2gKCBIGdG9rZW5zYjIKEWxhc3Rf"
          + "aGlkZGVuX3N0YXRlEh0KGwgBEhcKBxIFYmF0Y2gKCBIGdG9rZW5zCgIIA0IECgAQDQ==";

  /** A tiny graph whose only input is {@code input_ids}. */
  private static final String INPUT_IDS_ONLY_ONNX =
      "CAg6lwIKJwoJaW5wdXRfaWRzEglpZHNfZmxvYXQiBENhc3QqCQoCdG8YAaABAgokCglpZHNf"
          + "ZmxvYXQKBGF4ZXMSBmlkc18zZCIJVW5zcXVlZXplCiYKBmlkc18zZAoBdxIRbGFzdF9oaWRk"
          + "ZW5fc3RhdGUiBk1hdE11bBITdGlueS1pbnB1dC1jb250cmFjdCoUCAEQB0IEYXhlc0oIAgAA"
          + "AAAAAAAqFwgBCAMQAUIBd0oMAAAAPwAAgL8AAABAWiYKCWlucHV0X2lkcxIZChcIBxITCgcS"
          + "BWJhdGNoCggSBnRva2Vuc2IyChFsYXN0X2hpZGRlbl9zdGF0ZRIdChsIARIXCgcSBWJhdGNo"
          + "CggSBnRva2VucwoCCANCBAoAEA0=";

  /** A tiny graph with an input the encoder cannot supply. */
  private static final String UNSUPPORTED_INPUT_ONNX =
      "CAg6wgIKJwoJaW5wdXRfaWRzEglpZHNfZmxvYXQiBENhc3QqCQoCdG8YAaABAgokCglpZHNf"
          + "ZmxvYXQKBGF4ZXMSBmlkc18zZCIJVW5zcXVlZXplCiYKBmlkc18zZAoBdxIRbGFzdF9oaWRk"
          + "ZW5fc3RhdGUiBk1hdE11bBITdGlueS1pbnB1dC1jb250cmFjdCoUCAEQB0IEYXhlc0oIAgAA"
          + "AAAAAAAqFwgBCAMQAUIBd0oMAAAAPwAAgL8AAABAWiYKCWlucHV0X2lkcxIZChcIBxITCgcS"
          + "BWJhdGNoCggSBnRva2Vuc1opCgxwb3NpdGlvbl9pZHMSGQoXCAcSEwoHEgViYXRjaAoIEgZ0"
          + "b2tlbnNiMgoRbGFzdF9oaWRkZW5fc3RhdGUSHQobCAESFwoHEgViYXRjaAoIEgZ0b2tlbnMK"
          + "AggDQgQKABAN";

  /** A tiny graph that returns {@link Float#MAX_VALUE} at every token position. */
  private static final String MAX_FLOAT_ONNX =
      "CAg6igIKJwoJaW5wdXRfaWRzEglpZHNfZmxvYXQiBENhc3QqCQoCdG8YAaABAgokCglpZHNf"
          + "ZmxvYXQKBGF4ZXMSBmlkc18zZCIJVW5zcXVlZXplCiYKBmlkc18zZAoBdxIRbGFzdF9oaWRk"
          + "ZW5fc3RhdGUiBk1hdE11bBIOdGlueS1tYXgtZmxvYXQqFAgBEAdCBGF4ZXNKCAIAAAAAAAAA"
          + "Kg8IAQgBEAFCAXdKBP//f39aJgoJaW5wdXRfaWRzEhkKFwgHEhMKBxIFYmF0Y2gKCBIGdG9r"
          + "ZW5zYjIKEWxhc3RfaGlkZGVuX3N0YXRlEh0KGwgBEhcKBxIFYmF0Y2gKCBIGdG9rZW5zCgII"
          + "AUIECgAQDQ==";

  /** A graph whose {@code input_ids} has the unsupported INT32 element type. */
  private static final String INT32_INPUT_ONNX =
      "CAgSDm9wZW5ubHAtcmV2aWV3OsMBCiYKCWlucHV0X2lkcxIIYXNfZmxvYXQiBENhc3Qq"
          + "CQoCdG8YAaABAgovCghhc19mbG9hdAoFYXhlczISEWxhc3RfaGlkZGVuX3N0YXRlIglV"
          + "bnNxdWVlemUSC2ludDMyLWlucHV0Kg4IARAHOgECQgVheGVzMlomCglpbnB1dF9pZHMS"
          + "GQoXCAYSEwoHEgViYXRjaAoIEgZ0b2tlbnNiIwoRbGFzdF9oaWRkZW5fc3RhdGUSDgoM"
          + "CAESCAoACgAKAggBQgQKABAN";

  /** A graph whose {@code input_ids} is rank one instead of batch by position. */
  private static final String RANK_ONE_INPUT_ONNX =
      "CAgSDm9wZW5ubHAtcmV2aWV3Or0BCiYKCWlucHV0X2lkcxIIYXNfZmxvYXQiBENhc3Qq"
          + "CQoCdG8YAaABAgowCghhc19mbG9hdAoGYXhlczEyEhFsYXN0X2hpZGRlbl9zdGF0ZSIJ"
          + "VW5zcXVlZXplEgtyYW5rMS1pbnB1dCoQCAIQBzoCAQJCBmF4ZXMxMlodCglpbnB1dF9p"
          + "ZHMSEAoOCAcSCgoIEgZ0b2tlbnNiIwoRbGFzdF9oaWRkZW5fc3RhdGUSDgoMCAESCAoA"
          + "CgAKAggBQgQKABAN";

  /** A graph whose optional {@code attention_mask} has the unsupported INT32 type. */
  private static final String INT32_ATTENTION_MASK_ONNX =
      "CAgSDm9wZW5ubHAtcmV2aWV3Ou8BCiYKCWlucHV0X2lkcxIIYXNfZmxvYXQiBENhc3Qq"
          + "CQoCdG8YAaABAgovCghhc19mbG9hdAoFYXhlczISEWxhc3RfaGlkZGVuX3N0YXRlIglV"
          + "bnNxdWVlemUSCmludDMyLW1hc2sqDggBEAc6AQJCBWF4ZXMyWiYKCWlucHV0X2lkcxIZ"
          + "ChcIBxITCgcSBWJhdGNoCggSBnRva2Vuc1orCg5hdHRlbnRpb25fbWFzaxIZChcIBhIT"
          + "CgcSBWJhdGNoCggSBnRva2Vuc2IjChFsYXN0X2hpZGRlbl9zdGF0ZRIOCgwIARIICgAK"
          + "AAoCCAFCBAoAEA0=";

  /** A graph that declares a decoy rank-three float output before {@code last_hidden_state}. */
  private static final String MULTIPLE_OUTPUTS_ONNX =
      "CAgSDm9wZW5ubHAtcmV2aWV3OpYCCiYKCWlucHV0X2lkcxIIYXNfZmxvYXQiBENhc3Qq"
          + "CQoCdG8YAaABAgovCghhc19mbG9hdAoFYXhlczISEWxhc3RfaGlkZGVuX3N0YXRlIglV"
          + "bnNxdWVlemUKJAoRbGFzdF9oaWRkZW5fc3RhdGUKA3RlbhIFZGVjb3kiA011bBIQbXVs"
          + "dGlwbGUtb3V0cHV0cyoOCAEQBzoBAkIFYXhlczIqDRABIgQAACBBQgN0ZW5aJgoJaW5w"
          + "dXRfaWRzEhkKFwgHEhMKBxIFYmF0Y2gKCBIGdG9rZW5zYhcKBWRlY295Eg4KDAgBEggKAAoA"
          + "CgIIAWIjChFsYXN0X2hpZGRlbl9zdGF0ZRIOCgwIARIICgAKAAoCCAFCBAoAEA0=";

  /** A graph whose {@code input_ids} has the unsupported FLOAT element type. */
  private static final String FLOAT_INPUT_ONNX =
      "CAgSDm9wZW5ubHAtcmV2aWV3OpwBCjAKCWlucHV0X2lkcwoFYXhlczISEWxhc3RfaGlk"
          + "ZGVuX3N0YXRlIglVbnNxdWVlemUSC2Zsb2F0LWlucHV0Kg4IARAHOgECQgVheGVzMlom"
          + "CglpbnB1dF9pZHMSGQoXCAESEwoHEgViYXRjaAoIEgZ0b2tlbnNiIwoRbGFzdF9oaWRk"
          + "ZW5fc3RhdGUSDgoMCAESCAoACgAKAggBQgQKABAN";

  /** A graph whose fixed output does not follow the input batch and sequence dimensions. */
  private static final String FIXED_OUTPUT_ONNX =
      "CAgSDm9wZW5ubHAtcmV2aWV3OqEBCkASEWxhc3RfaGlkZGVuX3N0YXRlIghDb25zdGFu"
          + "dCohCgV2YWx1ZSoVCAEIAQgBEAEiBAAA4EBCBWZpeGVkoAEEEgxmaXhlZC1vdXRwdXRa"
          + "JgoJaW5wdXRfaWRzEhkKFwgHEhMKBxIFYmF0Y2gKCBIGdG9rZW5zYicKEWxhc3RfaGlk"
          + "ZGVuX3N0YXRlEhIKEAgBEgwKAggBCgIIAQoCCAFCBAoAEA0=";

  /** The analogy table's tokens; the list index is the matrix row. */
  static final List<String> ANALOGY_VOCABULARY =
      List.of("[CLS]", "[SEP]", "[UNK]", "king", "queen", "man", "woman", "apple");

  /**
   * The analogy table's rows, chosen so the classic word2vec analogy is exact:
   * {@code king - man + woman = [3,3] - [2,1] + [1,2] = [2,4] = queen}. The directions differ,
   * so pairwise cosine similarities are not all 1.0.
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

  /** Tokens used by the semantic-search example; the list index is the matrix row. */
  private static final List<String> SEARCH_VOCABULARY = List.of(
      "[CLS]", "[SEP]", "[UNK]",
      "home", "espresso", "machine", "how", "do", "i", "brew", "at",
      "the", "history", "of", "tea", "in", "east", "asia",
      "best", "grinders", "for", "pour", "over", "coffee");

  /**
   * Search rows with three directions: espresso brewing, tea history, and coffee equipment.
   * The query uses the first direction, so the example has a deterministic ranking.
   */
  private static final float[][] SEARCH_ROWS = {
      {0f, 0f}, {0f, 0f}, {0f, 0f},
      {1f, 0f}, {1f, 0f}, {1f, 0f}, {1f, 0f}, {1f, 0f}, {1f, 0f}, {1f, 0f}, {1f, 0f},
      {0f, 1f}, {0f, 1f}, {0f, 1f}, {0f, 1f}, {0f, 1f}, {0f, 1f}, {0f, 1f},
      {0.6f, 0.8f}, {0.6f, 0.8f}, {0.6f, 0.8f}, {0.6f, 0.8f}, {0.6f, 0.8f},
      {0.6f, 0.8f}
  };

  /** Not instantiable. */
  private EmbeddingTestFixtures() {
  }

  /**
   * Writes the deterministic test ONNX graph.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeTinyOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, TINY_TEACHER_ONNX);
  }

  /**
   * Writes a graph that declares only {@code input_ids}.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeInputIdsOnlyOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, INPUT_IDS_ONLY_ONNX);
  }

  /**
   * Writes a graph that also requires {@code position_ids}.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeUnsupportedInputOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, UNSUPPORTED_INPUT_ONNX);
  }

  /**
   * Writes a graph whose finite hidden states expose overflow in float accumulation.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeMaxFloatOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, MAX_FLOAT_ONNX);
  }

  /**
   * Writes a graph whose {@code input_ids} element type is INT32.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeInt32InputOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, INT32_INPUT_ONNX);
  }

  /**
   * Writes a graph whose {@code input_ids} is rank one.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeRankOneInputOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, RANK_ONE_INPUT_ONNX);
  }

  /**
   * Writes a graph whose {@code attention_mask} element type is INT32.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeInt32AttentionMaskOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, INT32_ATTENTION_MASK_ONNX);
  }

  /**
   * Writes a graph with two rank-three float outputs, including {@code last_hidden_state}.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeMultipleOutputsOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, MULTIPLE_OUTPUTS_ONNX);
  }

  /**
   * Writes a graph whose {@code input_ids} element type is FLOAT.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeFloatInputOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, FLOAT_INPUT_ONNX);
  }

  /**
   * Writes a graph whose output is always shaped {@code [1][1][1]}.
   *
   * @param directory The directory in which to create {@code model.onnx}.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  static Path writeFixedOutputOnnxModel(Path directory) throws IOException {
    return writeOnnxModel(directory, FIXED_OUTPUT_ONNX);
  }

  /**
   * Decodes an ONNX fixture into {@code model.onnx}.
   *
   * @param directory The destination directory.
   * @param encodedModel The base64-encoded graph.
   * @return The created graph file.
   * @throws IOException Thrown if the graph cannot be written.
   */
  private static Path writeOnnxModel(Path directory, String encodedModel) throws IOException {
    final Path file = directory.resolve("model.onnx");
    Files.write(file, Base64.getDecoder().decode(encodedModel));
    return file;
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
  static void writeAnalogyDirectory(Path dir) throws IOException {
    writeVocabularyAndMatrix(dir);
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");
    Files.writeString(dir.resolve("tokenizer_config.json"), "{\"do_lower_case\":true}");
  }

  /**
   * Writes the complete WordPiece model used by the semantic-search example.
   *
   * @param dir The directory to write the model files into.
   * @throws IOException Thrown if writing a fixture file fails.
   */
  static void writeSearchDirectory(Path dir) throws IOException {
    Files.write(dir.resolve("vocab.txt"), SEARCH_VOCABULARY);
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", SEARCH_ROWS));
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":true}");
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
