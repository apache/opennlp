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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quantized WordPiece model directory contract: after {@link ModelQuantizer} runs and the
 * safetensors is removed, the directory loads from {@code model.quantized}, embeds and ranks
 * like the float model up to the quantization error, and carries per-token pooling weights
 * through the quantized file. A directory holding both matrix files is rejected.
 */
class StaticEmbeddingModelQuantizedTest {

  private static final int DIMENSION = 32;
  private static final long SEED = 7L;
  private static final String[] WORDS = {
      "hello", "world", "apple", "banana", "cherry", "river", "mountain", "guitar",
      "piano", "silver", "copper", "window"
  };
  private static final String[] SENTENCES = {
      "hello world", "apple banana cherry", "a guitar by the river",
      "no vocabulary hit here matches nothing"
  };

  /**
   * Writes a small WordPiece model directory.
   *
   * @param directory   The directory to write into.
   * @param withWeights Whether to bundle a per-token {@code weights} tensor.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeModelDirectory(Path directory, boolean withWeights)
      throws IOException {
    final List<String> vocabulary = new ArrayList<>(List.of("[UNK]", "[CLS]", "[SEP]"));
    vocabulary.addAll(List.of(WORDS));
    Files.write(directory.resolve("vocab.txt"), vocabulary);
    final Random random = new Random(11);
    final float[][] rows = new float[vocabulary.size()][DIMENSION];
    for (final float[] row : rows) {
      final float rowScale = 0.5f + 2f * random.nextFloat();
      for (int d = 0; d < DIMENSION; d++) {
        row[d] = rowScale * (float) random.nextGaussian();
      }
    }
    if (withWeights) {
      final float[] weights = new float[vocabulary.size()];
      for (int row = 0; row < weights.length; row++) {
        weights[row] = 0.5f + 1.5f * random.nextFloat();
      }
      SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
          SafetensorsTestFiles.matrix("embeddings", rows),
          SafetensorsTestFiles.vector(StaticEmbeddingModel.WEIGHTS_TENSOR_NAME, weights));
    } else {
      SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
          SafetensorsTestFiles.matrix("embeddings", rows));
    }
    Files.writeString(directory.resolve(ModelFileNames.CONFIG), "{\"normalize\": true}");
    Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_CONFIG),
        "{\"do_lower_case\": true}");
  }

  /**
   * Quantizes the directory and removes the safetensors, leaving the quantized deployment the
   * loader accepts.
   *
   * @param directory The model directory to quantize in place.
   * @param bits      The bit width.
   * @return What the quantizer measured.
   * @throws IOException Thrown if quantizing or deleting fails.
   */
  private static ModelQuantizer.Result deployQuantized(Path directory, int bits)
      throws IOException {
    final ModelQuantizer.Result result = ModelQuantizer.quantize(directory, bits, SEED);
    Files.delete(directory.resolve(ModelFileNames.SAFETENSORS));
    return result;
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 4})
  void testQuantizedDirectoryEmbedsLikeTheFloatModel(int bits, @TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, false);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    deployQuantized(directory, bits);
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    assertEquals(floatModel.dimension(), quantizedModel.dimension());
    // Pooling several rows accumulates independent quantization noise, so the pooled cosine
    // sits a little below the single-row reconstruction; the floor loosens as bits shrink.
    // Every value is far above what a broken rotation, grid, or scale would produce.
    final double threshold = switch (bits) {
      case 2 -> 0.88;
      case 3 -> 0.95;
      default -> 0.98;
    };
    for (final String text : SENTENCES) {
      final double cosine = cosine(floatModel.embed(text), quantizedModel.embed(text));
      if (Double.isNaN(cosine)) {
        // Both pooled to the zero vector: an out-of-vocabulary text, identical behavior.
        continue;
      }
      assertTrue(cosine >= threshold,
          bits + "-bit embedding of '" + text + "' drifted to cosine " + cosine);
    }
  }

  @Test
  void testQuantizedDirectoryLoadsAfterTheSafetensorsIsRemoved(@TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, false);
    deployQuantized(directory, 4);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(directory);
    assertEquals(DIMENSION, model.dimension());
    assertEquals(WORDS.length + 3, model.vocabularySize());
    // The model's own row is its nearest neighbor, so ranking works end to end.
    assertEquals("hello", model.mostSimilar("hello", 1).get(0).token());
  }

  @Test
  void testBothMatrixFilesPresentIsRejected(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    // ModelQuantizer writes model.quantized next to model.safetensors and leaves both.
    ModelQuantizer.quantize(directory, 4, SEED);
    assertTrue(Files.isRegularFile(directory.resolve(ModelFileNames.SAFETENSORS)));
    assertTrue(Files.isRegularFile(directory.resolve(ModelFileNames.QUANTIZED)));
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(directory));
    assertTrue(e.getMessage().contains("has both"), e.getMessage());
    assertTrue(e.getMessage().contains(ModelFileNames.QUANTIZED), e.getMessage());
    assertTrue(e.getMessage().contains(ModelFileNames.SAFETENSORS), e.getMessage());
  }

  @Test
  void testPoolingWeightsRideThroughTheQuantizedFile(@TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, true);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    final ModelQuantizer.Result result = deployQuantized(directory, 4);
    assertTrue(result.hasWeights(), "the weights tensor must be carried over");
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    // Weighted pooling changes the pooled direction; matching the float model closely proves
    // the weights arrived, since dropping them would score a visibly different vector.
    final double cosine =
        cosine(floatModel.embed("hello world apple"), quantizedModel.embed("hello world apple"));
    assertTrue(cosine > 0.98, "weighted quantized embedding drifted to cosine " + cosine);
  }

  @Test
  void testMostSimilarAgreesWithTheFloatModel(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    deployQuantized(directory, 4);
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    for (final String word : new String[] {"hello", "river", "copper"}) {
      assertEquals(floatModel.mostSimilar(word, 1).get(0).token(),
          quantizedModel.mostSimilar(word, 1).get(0).token(),
          "top neighbor of '" + word + "' must survive quantization");
    }
  }

  @Test
  void testAnalogyRunsOverTheQuantizedTable(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    deployQuantized(directory, 4);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(directory);
    // The three query terms are excluded, so a fourth vocabulary word comes back; the point is
    // that the analogy path (query build, exclusion, scan) runs end to end over rotated space.
    final List<Neighbor> neighbors = model.analogy("hello", "world", "apple", 1);
    assertEquals(1, neighbors.size());
    // The analogy excludes its own terms, so none of them comes back.
    assertFalse(neighbors.get(0).token().equals("apple"));
  }

  @ParameterizedTest
  @CsvSource({"2, 0.9", "4, 0.98"})
  void testQuantizedFileSmallerAndVerified(int bits, double minCosine, @TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, false);
    final ModelQuantizer.Result result = ModelQuantizer.quantize(directory, bits, SEED);
    assertTrue(result.quantizedBytes() < result.safetensorsBytes(),
        result.quantizedBytes() + " must be smaller than " + result.safetensorsBytes());
    assertEquals(result.rowCount(), result.sampledRows(),
        "a small table is verified row by row");
    assertTrue(result.meanCosine() > minCosine,
        bits + "-bit reconstruction reported mean cosine " + result.meanCosine());
  }

  @Test
  void testRowCountMismatchFailsLoud(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    deployQuantized(directory, 4);
    final Path vocabularyFile = directory.resolve("vocab.txt");
    final List<String> extended = new ArrayList<>(Files.readAllLines(vocabularyFile));
    extended.add("straggler");
    Files.write(vocabularyFile, extended);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(directory));
    assertTrue(e.getMessage().contains("do not belong to the same model"), e.getMessage());
  }

  @Test
  void testQuantizerRequiresTheSafetensors(@TempDir Path directory) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelQuantizer.quantize(directory, 4, SEED));
    assertTrue(e.getMessage().contains(ModelFileNames.SAFETENSORS), e.getMessage());
  }

  @Test
  void testQuantizerRejectsBadBitWidths(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    assertThrows(IllegalArgumentException.class, () -> ModelQuantizer.quantize(directory, 1, SEED));
    assertThrows(IllegalArgumentException.class, () -> ModelQuantizer.quantize(directory, 5, SEED));
  }

  @Test
  void testQuantizedEmbeddingsAreDeterministic(@TempDir Path first, @TempDir Path second)
      throws IOException {
    writeModelDirectory(first, false);
    writeModelDirectory(second, false);
    deployQuantized(first, 4);
    deployQuantized(second, 4);
    final StaticEmbeddingModel modelA = StaticEmbeddingModel.load(first);
    final StaticEmbeddingModel modelB = StaticEmbeddingModel.load(second);
    for (final String text : SENTENCES) {
      assertArrayEquals(modelA.embed(text), modelB.embed(text), 0f,
          "the same table, bits, and seed must embed bit-identically");
    }
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
