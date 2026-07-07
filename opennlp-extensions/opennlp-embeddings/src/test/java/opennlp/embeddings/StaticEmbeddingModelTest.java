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

    String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":[" + ROWS.length + ","
        + DIMENSION + "],\"data_offsets\":[0," + embeddingBytes.length + "]}";
    if (withWeights) {
      // Weight per row: [1, 1, 1, 2, 1, 1] so "hello" (row 3) counts double in the sum but not
      // in the pooling denominator, which is the exact behavior being pinned.
      final float[] weightValues = {1f, 1f, 1f, 2f, 1f, 1f};
      final ByteBuffer weightBuffer =
          ByteBuffer.allocate(weightValues.length * 4).order(ByteOrder.LITTLE_ENDIAN);
      for (final float value : weightValues) {
        weightBuffer.putFloat(value);
      }
      final byte[] weightBytes = weightBuffer.array();
      final int start = embeddingBytes.length;
      data.write(weightBytes);
      header += ",\"weights\":{\"dtype\":\"F32\",\"shape\":[" + weightValues.length
          + "],\"data_offsets\":[" + start + "," + (start + weightBytes.length) + "]}";
    }
    header += "}";

    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data.toByteArray());
    final Path file = dir.resolve("model.safetensors");
    Files.write(file, out.toByteArray());
    return file;
  }

  @Test
  void testEmbedMeanPoolsWithoutWeights(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, false);

    final float[] result = model.embed("hello world");

    // (hello + world) / 2 = ([3,30,300] + [4,40,400]) / 2 = [3.5, 35, 350]
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, result, 1e-5f);
  }

  @Test
  void testEmbedAppliesPerTokenWeightsButDividesByTokenCount(@TempDir Path dir)
      throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, true), true, false);

    final float[] result = model.embed("hello world");

    // hello has weight 2: (2*[3,30,300] + 1*[4,40,400]) / 2 (denominator is token COUNT, not
    // the sum of weights) = ([6,60,600] + [4,40,400]) / 2 = [5, 50, 500]
    assertArrayEquals(new float[] {5f, 50f, 500f}, result, 1e-5f);
  }

  @Test
  void testEmbedNormalizesToUnitLength(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, true);

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
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, false);

    // "xyzzy" cannot be represented by any vocabulary piece, so it becomes [UNK] and must be
    // excluded from both the sum and the pooling denominator, leaving just "cat".
    final float[] result = model.embed("cat xyzzy");

    assertArrayEquals(new float[] {5f, 50f, 500f}, result, 1e-5f);
  }

  @Test
  void testEmbedOfTextWithNoInVocabularyTokensIsZeroVector(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, false);

    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed("xyzzy"), 1e-5f);
  }

  @Test
  void testEmbedOfEmptyTextIsZeroVectorNotAnError(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, true);

    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed(""), 1e-5f);
  }

  @Test
  void testDimensionAndVocabularySizeAccessors(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, false);

    assertEquals(DIMENSION, model.dimension());
    assertEquals(VOCAB_TOKENS.size(), model.vocabularySize());
  }

  @Test
  void testEmbedRejectsNullText(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false), true, false);

    assertThrows(IllegalArgumentException.class, () -> model.embed(null));
  }

  @Test
  void testLoadRejectsNullArguments(@TempDir Path dir) throws IOException {
    final Path vocab = writeVocab(dir);
    final Path tensors = writeSafetensors(dir, false);

    assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(null, tensors, true, false));
    assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(vocab, null, true, false));
  }

  @Test
  void testLoadRejectsVocabularySizeMismatch(@TempDir Path dir) throws IOException {
    final Path shortVocab = dir.resolve("short-vocab.txt");
    Files.write(shortVocab, List.of("[CLS]", "[SEP]", "[UNK]"));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> StaticEmbeddingModel.load(shortVocab, writeSafetensors(dir, false), true, false));
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
        () -> StaticEmbeddingModel.load(writeVocab(dir), file, true, false));
    assertTrue(e.getMessage().contains("weights"));
  }
}
