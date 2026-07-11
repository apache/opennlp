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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.StaticEmbeddingModel.Casing;
import opennlp.embeddings.StaticEmbeddingModel.Normalization;
import opennlp.tools.embeddings.TextEmbedder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticEmbeddingModelTest {

  // Fixture vocabulary: [CLS]=0, [SEP]=1, [UNK]=2, hello=3, world=4, cat=5.
  private static final List<String> VOCAB_TOKENS =
      List.of("[CLS]", "[SEP]", "[UNK]", "hello", "world", "cat");
  private static final int DIMENSION = 3;

  // Row i is [i, i*10, i*100], so hand-computed expected pooled vectors are easy to verify.
  private static final float[][] ROWS = {
      {0f, 0f, 0f},       // [CLS]
      {1f, 10f, 100f},    // [SEP]
      {2f, 20f, 200f},    // [UNK]
      {3f, 30f, 300f},    // hello
      {4f, 40f, 400f},    // world
      {5f, 50f, 500f},    // cat
  };

  private static Path writeVocab(Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    Files.write(file, VOCAB_TOKENS);
    return file;
  }

  private static Path writeSafetensors(Path dir, boolean withWeights) throws IOException {
    final Path file = dir.resolve("model.safetensors");
    if (withWeights) {
      // Weight per row: [1, 1, 1, 2, 1, 1] so "hello" (row 3) counts double in the sum but not
      // in the pooling denominator, which is the exact behavior being pinned.
      SafetensorsTestFiles.write(file,
          SafetensorsTestFiles.matrix("embeddings", ROWS),
          SafetensorsTestFiles.vector("weights", new float[] {1f, 1f, 1f, 2f, 1f, 1f}));
    } else {
      SafetensorsTestFiles.write(file, SafetensorsTestFiles.matrix("embeddings", ROWS));
    }
    return file;
  }

  @Test
  void testEmbedMeanPoolsWithoutWeights(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    final float[] result = model.embed("hello world");

    // (hello + world) / 2 = ([3,30,300] + [4,40,400]) / 2 = [3.5, 35, 350]
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, result, 1e-5f);
  }

  @Test
  void testEmbedAppliesPerTokenWeightsButDividesByTokenCount(@TempDir Path dir)
      throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, true),
            Casing.UNCASED, Normalization.NONE);

    final float[] result = model.embed("hello world");

    // hello has weight 2: (2*[3,30,300] + 1*[4,40,400]) / 2 (denominator is token COUNT, not
    // the sum of weights) = ([6,60,600] + [4,40,400]) / 2 = [5, 50, 500]
    assertArrayEquals(new float[] {5f, 50f, 500f}, result, 1e-5f);
  }

  @Test
  void testEmbedNormalizesToUnitLength(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.L2);

    final float[] result = model.embed("cat");

    double normSquared = 0;
    for (final float v : result) {
      normSquared += (double) v * v;
    }
    assertEquals(1.0, Math.sqrt(normSquared), 1e-5);
    // Direction preserved: cat's raw vector is [5, 50, 500], i.e. a positive multiple of
    // [1, 10, 100]; the normalized result must be that same direction.
    assertTrue(result[1] / result[0] > 9.9f && result[1] / result[0] < 10.1f);
  }

  @Test
  void testEmbedSkipsUnknownTokens(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    // "xyzzy" cannot be represented by any vocabulary piece, so it becomes [UNK] and must be
    // excluded from both the sum and the pooling denominator, leaving just "cat".
    final float[] result = model.embed("cat xyzzy");

    assertArrayEquals(new float[] {5f, 50f, 500f}, result, 1e-5f);
  }

  @Test
  void testEmbedOfTextWithNoInVocabularyTokensIsZeroVector(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed("xyzzy"), 1e-5f);
  }

  @Test
  void testEmbedOfEmptyTextIsZeroVectorNotAnError(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.L2);

    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed(""), 1e-5f);
  }

  @Test
  void testDimensionAndVocabularySizeAccessors(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    assertEquals(DIMENSION, model.dimension());
    assertEquals(VOCAB_TOKENS.size(), model.vocabularySize());
  }

  @Test
  void testEmbedRejectsNullText(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    assertThrows(IllegalArgumentException.class, () -> model.embed(null));
  }

  @Test
  void testLoadRejectsNullArguments(@TempDir Path dir) throws IOException {
    final Path vocab = writeVocab(dir);
    final Path tensors = writeSafetensors(dir, false);

    assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(null, tensors, Casing.UNCASED, Normalization.NONE));
    assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(vocab, null, Casing.UNCASED, Normalization.NONE));
  }

  @Test
  void testLoadRejectsVocabularySizeMismatch(@TempDir Path dir) throws IOException {
    final Path shortVocab = dir.resolve("short-vocab.txt");
    Files.write(shortVocab, List.of("[CLS]", "[SEP]", "[UNK]"));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(shortVocab, writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE));
    assertTrue(e.getMessage().contains("rows"));
  }

  @Test
  void testLoadRejectsWeightsSizeMismatch(@TempDir Path dir) throws IOException {
    // A weights tensor sized for a different (smaller) vocabulary than the embedding matrix.
    final ByteArrayOutputStream data = new ByteArrayOutputStream();
    final ByteBuffer embeddingBuffer =
        ByteBuffer.allocate(ROWS.length * DIMENSION * 4).order(ByteOrder.LITTLE_ENDIAN);
    for (final float[] row : ROWS) {
      for (final float value : row) {
        embeddingBuffer.putFloat(value);
      }
    }
    final byte[] embeddingBytes = embeddingBuffer.array();
    data.write(embeddingBytes);
    final byte[] weightBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        .putFloat(1f).array();
    data.write(weightBytes);
    final String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":[" + ROWS.length + ","
        + DIMENSION + "],\"data_offsets\":[0," + embeddingBytes.length + "]},"
        + "\"weights\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":["
        + embeddingBytes.length + "," + (embeddingBytes.length + weightBytes.length) + "]}}";
    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data.toByteArray());
    final Path file = dir.resolve("mismatched.safetensors");
    Files.write(file, out.toByteArray());

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(writeVocab(dir), file, Casing.UNCASED, Normalization.NONE));
    assertTrue(e.getMessage().contains("weights"));
  }

  // Writes the two JSON configuration files of a published model directory alongside the
  // vocab/safetensors fixtures, with the shapes real releases use (extra fields, floats,
  // nested objects, an explicit strip_accents null).
  private static void writeConfigs(Path dir, String normalize, String doLowerCase)
      throws IOException {
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"architectures\":[\"StaticModel\"],"
            + "\"apply_pca\":256,\"normalize\":" + normalize + ",\"hidden_dim\":3}");
    Files.writeString(dir.resolve("tokenizer_config.json"),
        "{\"added_tokens_decoder\":{\"0\":{\"content\":\"[PAD]\",\"special\":true}},"
            + "\"do_lower_case\":" + doLowerCase + ",\"strip_accents\":null,"
            + "\"tokenizer_class\":\"BertTokenizer\"}");
  }

  @Test
  void testLoadsFromAModelDirectory(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);

    // Same fixture and switches as testEmbedMeanPoolsWithoutWeights, resolved from the configs
    // this time; the upper-cased input additionally proves do_lower_case was picked up.
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, model.embed("HELLO WORLD"), 1e-5f);
  }

  @Test
  void testDirectoryLoadReadsNormalizeFromTheConfig(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "true", "true");

    final float[] result = StaticEmbeddingModel.load(dir).embed("cat");

    double normSquared = 0;
    for (final float v : result) {
      normSquared += (double) v * v;
    }
    assertEquals(1.0, Math.sqrt(normSquared), 1e-5);
  }

  @Test
  void testDirectoryLoadRejectsNullAndNonDirectory(@TempDir Path dir) {
    assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(null));
    assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(dir.resolve("absent")));
  }

  @Test
  void testDirectoryLoadNamesTheMissingFile(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    // no config.json, no tokenizer_config.json

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("config.json"));
    assertTrue(e.getMessage().contains("load(vocabularyFile, safetensorsFile"));
  }

  @Test
  void testDirectoryLoadRejectsAConfigWithoutNormalize(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");
    Files.writeString(dir.resolve("config.json"), "{\"model_type\":\"model2vec\"}");

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("normalize"));
  }

  @Test
  void testDirectoryLoadRejectsContradictoryStripAccents(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");
    Files.writeString(dir.resolve("tokenizer_config.json"),
        "{\"do_lower_case\":true,\"strip_accents\":false}");

    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("strip_accents"));
  }

  @Test
  void testTextEmbedderSeamMatchesDirectUseAndBatches(@TempDir Path dir) throws IOException {
    final TextEmbedder embedder =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    // The CharSequence entry point produces the same vector as the String one, including for a
    // CharSequence that is not a String.
    assertArrayEquals(new float[] {3.5f, 35f, 350f},
        embedder.embed(new StringBuilder("hello world")), 1e-5f);
    assertEquals(DIMENSION, embedder.dimension());

    // The interface's default batch method returns one vector per input, in input order.
    final float[][] vectors = embedder.embedAll(List.of("hello world", "cat"));
    assertEquals(2, vectors.length);
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, vectors[0], 1e-5f);
    assertArrayEquals(new float[] {5f, 50f, 500f}, vectors[1], 1e-5f);

    assertThrows(IllegalArgumentException.class, () -> embedder.embed(null));
    assertThrows(IllegalArgumentException.class, () -> embedder.embedAll(null));
  }
}
