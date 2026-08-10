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
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cookbook paths documented in {@code embeddings.xml}, mirroring its listings: load a
 * model directory with {@link StaticEmbeddingModel#load(Path)}, embed a text, and call
 * {@code similarity}, {@code mostSimilar}, and {@code analogy}; and load through the explicit
 * WordPiece and SentencePiece overloads.
 */
public class StaticEmbeddingUsageExampleTest {

  @Test
  void testEmbedSimilarityNeighborsAndAnalogy(@TempDir Path dir) throws IOException {
    EmbeddingTestFixtures.writeAnalogyDirectory(dir);

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);

    final float[] vector = model.embed("king");
    assertEquals(2, vector.length);

    assertEquals(1.0, model.similarity("king", "king"), 1e-5);

    final List<Neighbor> neighbors = model.mostSimilar("king", 5);
    assertTrue(!neighbors.isEmpty());
    assertEquals("king", neighbors.get(0).token());

    final List<Neighbor> analogy = model.analogy("man", "king", "woman", 1);
    assertEquals(1, analogy.size());
    assertEquals("queen", analogy.get(0).token());
  }

  @Test
  void testExplicitOverloads(@TempDir Path wordPieceDir, @TempDir Path sentencePieceDir)
      throws IOException {
    EmbeddingTestFixtures.writeAnalogyDirectory(wordPieceDir);
    EmbeddingTestFixtures.writeSentencePieceDirectory(sentencePieceDir);

    // The manual's explicit WordPiece overload: the data files plus the two switches the
    // model's configuration publishes.
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(
        wordPieceDir.resolve("vocab.txt"), wordPieceDir.resolve("model.safetensors"),
        StaticEmbeddingModel.Casing.UNCASED,
        StaticEmbeddingModel.Normalization.L2);
    assertEquals(2, model.dimension());
    assertUnitLength(model.embed("king"));

    // The manual's explicit SentencePiece overload: no casing switch, because the trained
    // .model file carries the model's own text normalizer.
    final StaticEmbeddingModel multilingual = StaticEmbeddingModel.loadSentencePiece(
        sentencePieceDir.resolve("sentencepiece.bpe.model"),
        sentencePieceDir.resolve("tokenizer.json"),
        sentencePieceDir.resolve("model.safetensors"),
        StaticEmbeddingModel.Normalization.L2);
    assertEquals(EmbeddingTestFixtures.SENTENCEPIECE_DIMENSION, multilingual.dimension());
    assertUnitLength(multilingual.embed("a"));
  }

  /**
   * Asserts that a vector has unit L2 length, the visible effect of choosing
   * {@code Normalization.L2} in the explicit overloads.
   *
   * @param vector The vector to measure.
   */
  private static void assertUnitLength(float[] vector) {
    double normSquared = 0;
    for (final float v : vector) {
      normSquared += (double) v * v;
    }
    assertEquals(1.0, Math.sqrt(normSquared), 1e-5);
  }
}
