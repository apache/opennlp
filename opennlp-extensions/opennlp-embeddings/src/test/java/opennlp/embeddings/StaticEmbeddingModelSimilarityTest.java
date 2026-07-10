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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link StaticEmbeddingModel#similarity}, {@link StaticEmbeddingModel#mostSimilar},
 * and {@link StaticEmbeddingModel#analogy} against a small fixture whose vectors point in
 * genuinely different directions (unlike {@link StaticEmbeddingModelTest}'s collinear rows,
 * which are ideal for pooling-math assertions but would make every pairwise cosine similarity
 * trivially 1.0). The fixture is built so the classic word2vec analogy has an exact answer:
 * {@code king - man + woman == queen}.
 */
class StaticEmbeddingModelSimilarityTest {

  private static final List<String> VOCAB_TOKENS =
      List.of("[CLS]", "[SEP]", "[UNK]", "king", "queen", "man", "woman", "apple");
  private static final int DIMENSION = 2;

  // king - man + woman = [3,3] - [2,1] + [1,2] = [2,4] = queen, exactly.
  private static final float[][] ROWS = {
      {0f, 0f},   // [CLS]
      {0f, 0f},   // [SEP]
      {0f, 0f},   // [UNK]
      {3f, 3f},   // king
      {2f, 4f},   // queen
      {2f, 1f},   // man
      {1f, 2f},   // woman
      {-3f, -1f}, // apple: unrelated, opposite-ish direction
  };

  private static Path writeVocab(Path dir) throws IOException {
    final Path file = dir.resolve("vocab.txt");
    Files.write(file, VOCAB_TOKENS);
    return file;
  }

  private static Path writeSafetensors(Path dir) throws IOException {
    final Path file = dir.resolve("model.safetensors");
    SafetensorsTestFiles.write(file, SafetensorsTestFiles.matrix("embeddings", ROWS));
    return file;
  }

  private static StaticEmbeddingModel load(Path dir) throws IOException {
    return StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir),
            Casing.UNCASED, Normalization.NONE);
  }

  @Test
  void testSimilarityOfIdenticalTextIsOne(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertEquals(1.0, model.similarity("king", "king"), 1e-5);
  }

  @Test
  void testSimilarityIsSymmetric(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertEquals(model.similarity("king", "queen"), model.similarity("queen", "king"), 1e-9);
  }

  @Test
  void testSimilarityOfUnrelatedTermsIsLow(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertTrue(model.similarity("king", "apple") < model.similarity("king", "queen"));
  }

  @Test
  void testSimilarityOfOutOfVocabularyTextIsZero(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertEquals(0.0, model.similarity("xyzzy", "king"), 1e-9);
  }

  @Test
  void testMostSimilarFindsSelfAsTopMatch(@TempDir Path dir) throws IOException {
    // Documented, deliberate behavior: unlike gensim's convention of excluding the query word,
    // mostSimilar only excludes special tokens, so a single-word query's own vocabulary row is
    // (correctly) its own nearest neighbor.
    final StaticEmbeddingModel model = load(dir);

    final List<Neighbor> result = model.mostSimilar("king", 1);

    assertEquals(1, result.size());
    assertEquals("king", result.get(0).token());
    assertEquals(1.0, result.get(0).similarity(), 1e-5);
  }

  @Test
  void testMostSimilarExcludesSpecialTokensAndOrdersByDescendingSimilarity(@TempDir Path dir)
      throws IOException {
    final StaticEmbeddingModel model = load(dir);

    final List<Neighbor> result = model.mostSimilar("king", 5);

    assertEquals(5, result.size());
    for (final Neighbor neighbor : result) {
      assertFalse(List.of("[CLS]", "[SEP]", "[UNK]").contains(neighbor.token()));
    }
    // Descending order.
    for (int i = 1; i < result.size(); i++) {
      assertTrue(result.get(i - 1).similarity() >= result.get(i).similarity());
    }
    // apple is the clear outlier (opposite-ish direction) and must rank last.
    assertEquals("apple", result.get(result.size() - 1).token());
  }

  @Test
  void testMostSimilarOfZeroVectorQueryReturnsEmptyList(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertEquals(List.of(), model.mostSimilar("xyzzy", 3));
  }

  @Test
  void testAnalogyFindsTheExactTarget(@TempDir Path dir) throws IOException {
    // man is to king as woman is to ?  Expected: queen (king - man + woman == queen exactly).
    final StaticEmbeddingModel model = load(dir);

    final List<Neighbor> result = model.analogy("man", "king", "woman", 1);

    assertEquals(1, result.size());
    assertEquals("queen", result.get(0).token());
    assertEquals(1.0, result.get(0).similarity(), 1e-5);
  }

  @Test
  void testAnalogyExcludesItsOwnInputTerms(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    // Only "queen" and "apple" remain eligible once man/king/woman and the special tokens are
    // excluded, regardless of how close the raw analogy target vector is to the inputs.
    final List<Neighbor> result = model.analogy("man", "king", "woman", 4);

    assertEquals(2, result.size());
    assertFalse(result.stream().map(Neighbor::token)
        .anyMatch(token -> List.of("man", "king", "woman").contains(token)));
  }

  @Test
  void testAnalogyToleratesEqualTerms(@TempDir Path dir) throws IOException {
    // A duplicate term used to crash with IllegalArgumentException("duplicate element") from
    // Set.of before the exclusion moved to tokenized rows. b - a + c with a == b is just c's
    // vector, so with man and woman excluded the exactly collinear queen must win.
    final StaticEmbeddingModel model = load(dir);

    final List<Neighbor> result = model.analogy("man", "man", "woman", 2);

    assertEquals("queen", result.get(0).token());
    assertEquals(1.0, result.get(0).similarity(), 1e-5);
  }

  @Test
  void testAnalogyExclusionFoldsLikeEmbed(@TempDir Path dir) throws IOException {
    // On an uncased model, capitalized inputs must exclude their lower-cased vocabulary rows.
    // Before the fix the exclusion compared raw input strings, so "King" failed to exclude
    // "king" and the analogy handed an input term back as a result.
    final StaticEmbeddingModel model = load(dir);

    final List<Neighbor> result = model.analogy("Man", "King", "Woman", 4);

    assertEquals(2, result.size());
    assertEquals("queen", result.get(0).token());
    assertFalse(result.stream().map(Neighbor::token)
        .anyMatch(token -> List.of("man", "king", "woman").contains(token)));
  }

  @Test
  void testZeroVectorRowScoresZeroNotNaN(@TempDir Path dir) throws IOException {
    // A non-special all-zero row has no direction; it must score exactly 0.0, not the NaN a
    // naive 0/0 cosine would produce.
    final Path vocab = dir.resolve("zero-vocab.txt");
    Files.write(vocab, List.of("[CLS]", "[SEP]", "[UNK]", "a", "zero"));
    final float[][] rows = {{0f, 0f}, {0f, 0f}, {0f, 0f}, {1f, 0f}, {0f, 0f}};
    final ByteBuffer buffer = ByteBuffer.allocate(rows.length * 2 * 4)
        .order(ByteOrder.LITTLE_ENDIAN);
    for (final float[] row : rows) {
      for (final float value : row) {
        buffer.putFloat(value);
      }
    }
    final byte[] data = buffer.array();
    final String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":[" + rows.length
        + ",2],\"data_offsets\":[0," + data.length + "]}}";
    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data);
    final Path tensors = dir.resolve("zero-model.safetensors");
    Files.write(tensors, out.toByteArray());
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(vocab, tensors, Casing.UNCASED, Normalization.NONE);

    final List<Neighbor> result = model.mostSimilar("a", 5);

    assertEquals(2, result.size());
    assertEquals("a", result.get(0).token());
    assertEquals("zero", result.get(1).token());
    assertEquals(0.0, result.get(1).similarity());
    assertTrue(result.stream().allMatch(neighbor -> Double.isFinite(neighbor.similarity())));
  }

  @Test
  void testMostSimilarRejectsInvalidArguments(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertThrows(IllegalArgumentException.class, () -> model.mostSimilar(null, 1));
    assertThrows(IllegalArgumentException.class, () -> model.mostSimilar("king", 0));
    assertThrows(IllegalArgumentException.class, () -> model.mostSimilar("king", -1));
  }

  @Test
  void testAnalogyRejectsInvalidArguments(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertThrows(IllegalArgumentException.class, () -> model.analogy(null, "king", "woman", 1));
    assertThrows(IllegalArgumentException.class, () -> model.analogy("man", null, "woman", 1));
    assertThrows(IllegalArgumentException.class, () -> model.analogy("man", "king", null, 1));
    assertThrows(IllegalArgumentException.class, () -> model.analogy("man", "king", "woman", 0));
  }

  @Test
  void testSimilarityRejectsNullArguments(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = load(dir);

    assertThrows(IllegalArgumentException.class, () -> model.similarity(null, "king"));
    assertThrows(IllegalArgumentException.class, () -> model.similarity("king", null));
  }
}
