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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quantized model directory contract: after {@link ModelQuantizer} runs, the directory
 * loads from {@code model.quantized} (with or without the safetensors still present), embeds
 * and ranks like the float model up to the quantization error, and carries per-token pooling
 * weights through the quantized file.
 */
class StaticEmbeddingModelQuantizedTest {

  private static final int DIMENSION = 32;
  private static final String[] WORDS = {
      "hello", "world", "apple", "banana", "cherry", "river", "mountain", "guitar",
      "piano", "silver", "copper", "window"
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
          SafetensorsTestFiles.vector("weights", weights));
    } else {
      SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
          SafetensorsTestFiles.matrix("embeddings", rows));
    }
    Files.writeString(directory.resolve(ModelFileNames.CONFIG), "{\"normalize\": true}");
    Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_CONFIG),
        "{\"do_lower_case\": true}");
  }

  @Test
  void testQuantizedDirectoryEmbedsLikeTheFloatModel(@TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, false);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    final ModelQuantizer.Result result = ModelQuantizer.quantize(directory, 4, 7L);
    assertEquals(WORDS.length + 3, result.rowCount());
    assertTrue(result.meanCosine() > 0.98,
        "4-bit reconstruction reported mean cosine " + result.meanCosine());
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    assertEquals(floatModel.dimension(), quantizedModel.dimension());
    for (final String text : new String[] {"hello world", "apple banana cherry",
        "a guitar by the river", "no vocabulary hit here matches nothing"}) {
      final double cosine = cosine(floatModel.embed(text), quantizedModel.embed(text));
      if (Double.isNaN(cosine)) {
        // Both pooled to the zero vector: an out-of-vocabulary text, identical behavior.
        continue;
      }
      assertTrue(cosine > 0.98,
          "quantized embedding of '" + text + "' drifted to cosine " + cosine);
    }
  }

  @Test
  void testQuantizedDirectoryLoadsWithoutTheSafetensors(@TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, false);
    ModelQuantizer.quantize(directory, 4, 7L);
    Files.delete(directory.resolve(ModelFileNames.SAFETENSORS));
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(directory);
    assertEquals(DIMENSION, model.dimension());
    assertEquals(WORDS.length + 3, model.vocabularySize());
    // The model's own row is its nearest neighbor, so ranking works end to end.
    assertEquals("hello", model.mostSimilar("hello", 1).get(0).token());
  }

  @Test
  void testPoolingWeightsRideThroughTheQuantizedFile(@TempDir Path directory)
      throws IOException {
    writeModelDirectory(directory, true);
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);
    final ModelQuantizer.Result result = ModelQuantizer.quantize(directory, 4, 7L);
    assertTrue(result.hasWeights(), "the weights tensor must be carried over");
    Files.delete(directory.resolve(ModelFileNames.SAFETENSORS));
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
    ModelQuantizer.quantize(directory, 4, 7L);
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);
    for (final String word : new String[] {"hello", "river", "copper"}) {
      assertEquals(floatModel.mostSimilar(word, 1).get(0).token(),
          quantizedModel.mostSimilar(word, 1).get(0).token(),
          "top neighbor of '" + word + "' must survive quantization");
    }
  }

  @Test
  void testQuantizedFileSmallerAndVerified(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    final ModelQuantizer.Result result = ModelQuantizer.quantize(directory, 2, 7L);
    assertTrue(result.quantizedBytes() < result.safetensorsBytes(),
        result.quantizedBytes() + " must be smaller than " + result.safetensorsBytes());
    assertEquals(result.rowCount(), result.sampledRows(),
        "a small table is verified row by row");
    assertTrue(result.meanCosine() > 0.9,
        "2-bit reconstruction reported mean cosine " + result.meanCosine());
  }

  @Test
  void testRowCountMismatchFailsLoud(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    ModelQuantizer.quantize(directory, 4, 7L);
    final Path vocabularyFile = directory.resolve("vocab.txt");
    final List<String> extended = new ArrayList<>(Files.readAllLines(vocabularyFile));
    extended.add("straggler");
    Files.write(vocabularyFile, extended);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> StaticEmbeddingModel.load(directory));
    assertTrue(e.getMessage().contains("do not belong to the same model"), e.getMessage());
  }

  @Test
  void testQuantizerRequiresTheSafetensors(@TempDir Path directory) throws IOException {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelQuantizer.quantize(directory, 4, 7L));
    assertTrue(e.getMessage().contains(ModelFileNames.SAFETENSORS), e.getMessage());
  }

  /**
   * {@return the cosine between two vectors, or {@code Double.NaN} when either has no
   * direction}
   *
   * @param a The first vector.
   * @param b The second vector, of the same length.
   */
  private static double cosine(float[] a, float[] b) {
    double dot = 0;
    double normASquared = 0;
    double normBSquared = 0;
    for (int d = 0; d < a.length; d++) {
      dot += (double) a[d] * b[d];
      normASquared += (double) a[d] * a[d];
      normBSquared += (double) b[d] * b[d];
    }
    final double denominator = Math.sqrt(normASquared) * Math.sqrt(normBSquared);
    return denominator == 0 ? Double.NaN : dot / denominator;
  }
}
