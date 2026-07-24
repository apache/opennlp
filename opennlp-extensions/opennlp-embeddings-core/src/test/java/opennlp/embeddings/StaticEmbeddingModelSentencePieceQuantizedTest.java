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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quantized SentencePiece loading path: a SentencePiece directory whose matrix has been
 * quantized and whose safetensors removed loads through {@code loadSentencePieceQuantized},
 * resolves rows by piece string across the tokenizer id offset, and embeds like the float
 * model up to the quantization error. Both matrix files present is rejected.
 */
class StaticEmbeddingModelSentencePieceQuantizedTest {

  private static final int DIMENSION = 16;
  private static final long SEED = 7L;

  private static SentencePieceModelFixture fixture;

  @BeforeAll
  static void loadFixture() throws IOException {
    fixture = new SentencePieceModelFixture();
  }

  @Test
  void testQuantizedSentencePieceEmbedsLikeTheFloatModel(@TempDir Path directory)
      throws IOException {
    fixture.write(directory, DIMENSION, true, SEED);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    ModelQuantizer.quantize(directory, 4, SEED);
    Files.delete(directory.resolve(ModelFileNames.SAFETENSORS));
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    assertEquals(floatModel.dimension(), quantizedModel.dimension());
    assertEquals(floatModel.vocabularySize(), quantizedModel.vocabularySize());
    for (final String text : new String[] {"a", "the model", "hello there world"}) {
      final double cosine = cosine(floatModel.embed(text), quantizedModel.embed(text));
      if (Double.isNaN(cosine)) {
        continue;
      }
      assertTrue(cosine > 0.97,
          "quantized SentencePiece embedding of '" + text + "' drifted to cosine " + cosine);
    }
  }

  @Test
  void testQuantizedSentencePieceRanksLikeTheFloatModel(@TempDir Path directory)
      throws IOException {
    fixture.write(directory, DIMENSION, true, SEED);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    ModelQuantizer.quantize(directory, 4, SEED);
    Files.delete(directory.resolve(ModelFileNames.SAFETENSORS));
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    // A piece is its own nearest neighbor under both storage forms.
    final String piece = fixture.rowPieces().get(3);
    assertEquals(floatModel.mostSimilar(piece, 1).get(0).token(),
        quantizedModel.mostSimilar(piece, 1).get(0).token());
  }

  @Test
  void testBothMatrixFilesPresentIsRejected(@TempDir Path directory) throws IOException {
    fixture.write(directory, DIMENSION, true, SEED);
    ModelQuantizer.quantize(directory, 4, SEED);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(directory));
    assertTrue(e.getMessage().contains("has both"), e.getMessage());
  }

  /**
   * {@return the cosine between two vectors, or {@code Double.NaN} when either has no
   * direction}
   *
   * @param a The first vector.
   * @param b The second vector, of the same length.
   */
  private static double cosine(float[] a, float[] b) {
    return ModelQuantizer.cosine(a, 0, a.length, b);
  }
}
