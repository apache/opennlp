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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A model directory with a term table: term rows pool as single units where they match, the
 * subword path is untouched everywhere else, terms appear as similarity-search neighbors, and a
 * malformed or mismatched terms file is rejected during loading.
 */
class StaticEmbeddingModelTermTest {

  private static final List<String> VOCABULARY =
      List.of("[CLS]", "[SEP]", "[UNK]", "habeas", "corpus", "writ", "law");

  /**
   * The matrix rows: the three special tokens are zero, the content tokens have distinct
   * directions, and the two term rows (habeas corpus, replevin) are distinct again.
   */
  private static final float[][] ROWS = {
      {0f, 0f},    // [CLS]
      {0f, 0f},    // [SEP]
      {0f, 0f},    // [UNK]
      {1f, 0f},    // habeas
      {0f, 1f},    // corpus
      {2f, 0f},    // writ
      {4f, 0f},    // law
      {10f, 10f},  // term: habeas corpus
      {5f, -5f},   // term: replevin
  };

  /**
   * Writes a loadable WordPiece directory, optionally with the two term rows and their
   * {@code terms.txt}, and loads it.
   *
   * @param dir       The directory to write into.
   * @param withTerms Whether to include the term rows and the terms file.
   * @return The loaded model.
   * @throws IOException Thrown if writing or loading fails.
   */
  private static StaticEmbeddingModel model(Path dir, boolean withTerms) throws IOException {
    Files.write(dir.resolve("vocab.txt"), VOCABULARY);
    final int rows = withTerms ? ROWS.length : VOCABULARY.size();
    final float[][] matrix = new float[rows][];
    System.arraycopy(ROWS, 0, matrix, 0, rows);
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");
    Files.writeString(dir.resolve("tokenizer_config.json"), "{\"do_lower_case\":true}");
    if (withTerms) {
      Files.write(dir.resolve("terms.txt"), List.of("habeas corpus", "replevin"));
    }
    return StaticEmbeddingModel.load(dir);
  }

  @Test
  void testLoadsTheTermTable(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = model(dir, true);
    assertEquals(VOCABULARY.size(), model.vocabularySize());
    assertEquals(2, model.termCount());
  }

  @Test
  void testAMatchedTermPoolsItsSingleRow(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = model(dir, true);
    assertArrayEquals(new float[] {10f, 10f}, model.embed("habeas corpus"));
    // Case folding and punctuation between the words do not break the match.
    assertArrayEquals(new float[] {10f, 10f}, model.embed("Habeas-Corpus!"));
    // A single-word term matches ahead of its (absent) subword pieces.
    assertArrayEquals(new float[] {5f, -5f}, model.embed("replevin"));
  }

  @Test
  void testTermAndPieceRowsPoolTogetherInTextOrder(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = model(dir, true);
    // writ -> its piece row; "of" -> [UNK], skipped; "habeas corpus" -> the term row;
    // law -> its piece row. Mean of (2,0), (10,10), (4,0).
    assertArrayEquals(new float[] {16f / 3, 10f / 3}, model.embed("writ of habeas corpus law"));
  }

  @Test
  void testTextWithoutAMatchEmbedsExactlyLikeATermlessModel(@TempDir Path dir,
                                                            @TempDir Path termless)
      throws IOException {
    final StaticEmbeddingModel withTerms = model(dir, true);
    final StaticEmbeddingModel without = model(termless, false);
    // "habeas law" has both words in the vocabulary but matches no term: the two words are not
    // adjacent words of any stored phrase.
    assertArrayEquals(without.embed("habeas law"), withTerms.embed("habeas law"));
    assertArrayEquals(without.embed("the writ, of law."), withTerms.embed("the writ, of law."));
  }

  @Test
  void testATermlessModelZeroesWhatOnlyATermRowCouldEmbed(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel without = model(dir, false);
    // Without the term table, "replevin" is out of vocabulary entirely.
    assertArrayEquals(new float[] {0f, 0f}, without.embed("replevin"));
  }

  @Test
  void testTermsAreSimilarityNeighbors(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model = model(dir, true);
    final List<Neighbor> neighbors = model.mostSimilar("replevin", 1);
    assertEquals(1, neighbors.size());
    assertEquals("replevin", neighbors.get(0).token());
    assertEquals("habeas corpus", model.mostSimilar("habeas corpus", 1).get(0).token());
  }

  @Test
  void testRejectsARowCountMismatchWithTerms(@TempDir Path dir) throws IOException {
    Files.write(dir.resolve("vocab.txt"), VOCABULARY);
    final float[][] matrix = new float[VOCABULARY.size()][];
    System.arraycopy(ROWS, 0, matrix, 0, VOCABULARY.size());
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", matrix));
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");
    Files.writeString(dir.resolve("tokenizer_config.json"), "{\"do_lower_case\":true}");
    Files.write(dir.resolve("terms.txt"), List.of("habeas corpus", "replevin"));

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("plus 2 terms"), e.getMessage());
  }

  @Test
  void testRejectsAMalformedTermsFile(@TempDir Path dir) throws IOException {
    Files.write(dir.resolve("vocab.txt"), VOCABULARY);
    SafetensorsTestFiles.write(dir.resolve("model.safetensors"),
        SafetensorsTestFiles.matrix("embeddings", ROWS));
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");
    Files.writeString(dir.resolve("tokenizer_config.json"), "{\"do_lower_case\":true}");
    // Upper case is not the normalized form the matcher folds to.
    Files.write(dir.resolve("terms.txt"), List.of("HABEAS CORPUS", "replevin"));

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("normalized form"), e.getMessage());
  }

  @Test
  void testASentencePieceDirectoryLoadsItsTermTable(@TempDir Path dir) throws IOException {
    EmbeddingTestFixtures.writeSentencePieceDirectory(dir, List.of("lawbook"));
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false}");

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);
    assertEquals(1, model.termCount());
    // The fixture's cell formula is row + d * 0.25, and the term owns the row after the
    // vocabulary rows.
    final float[] expected = new float[EmbeddingTestFixtures.SENTENCEPIECE_DIMENSION];
    for (int d = 0; d < expected.length; d++) {
      expected[d] = model.vocabularySize() + d * 0.25f;
    }
    assertArrayEquals(expected, model.embed("Lawbook"));
  }
}
