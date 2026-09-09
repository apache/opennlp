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

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests quantized WordPiece model loading, embedding, search, and file selection.
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
      "xyzzy plugh"
  };

  /**
   * Writes a small WordPiece model directory.
   *
   * @param directory   The directory to write into.
   * @param withWeights Whether to bundle a per-token {@code weights} tensor.
   * @throws IOException Thrown if writing fails.
   */
  private void writeModelDirectory(Path directory, boolean withWeights)
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
   * @return The quantization result.
   * @throws IOException Thrown if quantizing or deleting fails.
   */
  private ModelQuantizer.Result deployQuantized(Path directory, int bits)
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
    // Pooling several rows accumulates independent quantization noise, so the pooled cosine can
    // be lower than a single-row reconstruction. The threshold decreases with the bit width and
    // detects errors in the rotation, grid, or scale.
    final double threshold = switch (bits) {
      case 2 -> 0.88;
      case 3 -> 0.95;
      default -> 0.98;
    };
    int compared = 0;
    for (final String text : SENTENCES) {
      final double cosine = cosine(floatModel.embed(text), quantizedModel.embed(text));
      if (Double.isNaN(cosine)) {
        // Both models produced a zero vector for out-of-vocabulary text.
        continue;
      }
      compared++;
      assertTrue(cosine >= threshold,
          bits + "-bit embedding of '" + text + "' has cosine " + cosine);
    }
    assertEquals(SENTENCES.length - 1, compared,
        "only the out-of-vocabulary sentence should produce a zero vector");
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
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(directory));
    assertTrue(e.getMessage().contains("has both"), e.getMessage());
    assertTrue(e.getMessage().contains(ModelFileNames.QUANTIZED), e.getMessage());
    assertTrue(e.getMessage().contains(ModelFileNames.SAFETENSORS), e.getMessage());
  }

  @Test
  void testPoolingWeightsRideThroughTheQuantizedFile(@TempDir Path directory)
      throws IOException {
    final Path weightedDirectory = Files.createDirectory(directory.resolve("weighted"));
    final Path unweightedDirectory = Files.createDirectory(directory.resolve("unweighted"));
    final List<String> vocabulary = List.of("[UNK]", "[CLS]", "[SEP]", "hello", "world");
    final float[][] rows = {
        {0f, 0f}, {0f, 0f}, {0f, 0f}, {1f, 0f}, {0f, 1f}
    };
    for (final Path modelDirectory : List.of(weightedDirectory, unweightedDirectory)) {
      Files.write(modelDirectory.resolve(ModelFileNames.VOCABULARY), vocabulary);
      Files.writeString(modelDirectory.resolve(ModelFileNames.CONFIG), "{\"normalize\": true}");
      Files.writeString(modelDirectory.resolve(ModelFileNames.TOKENIZER_CONFIG),
          "{\"do_lower_case\": true}");
    }
    SafetensorsTestFiles.write(weightedDirectory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", rows),
        SafetensorsTestFiles.vector(StaticEmbeddingModel.WEIGHTS_TENSOR_NAME,
            new float[] {1f, 1f, 1f, 10f, 1f}));
    SafetensorsTestFiles.write(unweightedDirectory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", rows));
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(weightedDirectory);
    final StaticEmbeddingModel unweightedModel = StaticEmbeddingModel.load(unweightedDirectory);
    final float[] expected = floatModel.embed("hello world");
    assertTrue(cosine(expected, unweightedModel.embed("hello world")) < 0.9,
        "the fixture must distinguish weighted from unweighted pooling");

    final ModelQuantizer.Result result = deployQuantized(weightedDirectory, 4);
    assertTrue(result.hasWeights(), "the weights tensor must be included");
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(weightedDirectory);
    final double cosine = cosine(expected, quantizedModel.embed("hello world"));
    assertTrue(cosine > 0.98, "weighted quantized embedding has cosine " + cosine);
  }

  @Test
  void testQuantizedDirectoryIncludesTermRows(@TempDir Path directory) throws IOException {
    Files.write(directory.resolve(ModelFileNames.VOCABULARY),
        List.of("[CLS]", "[SEP]", "[UNK]", "habeas", "corpus"));
    Files.write(directory.resolve(ModelFileNames.TERMS), List.of("habeas corpus"));
    SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", new float[][] {
            {0f, 0f}, {0f, 0f}, {0f, 0f}, {1f, 0f}, {0f, 1f}, {10f, 10f}
        }));
    Files.writeString(directory.resolve(ModelFileNames.CONFIG), "{\"normalize\": false}");
    Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_CONFIG),
        "{\"do_lower_case\": true}");
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);

    deployQuantized(directory, 4);
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);

    assertEquals(1, quantizedModel.termCount());
    assertTrue(cosine(floatModel.embed("habeas corpus"),
        quantizedModel.embed("habeas corpus")) > 0.98);
    assertEquals("habeas corpus",
        quantizedModel.mostSimilar("habeas corpus", 1).get(0).token());
  }

  @Test
  void testQuantizedModel2VecUnigramDirectoryLoadsWithoutSafetensors(@TempDir Path directory)
      throws IOException {
    Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_JSON),
        "{\"normalizer\":{\"type\":\"Sequence\",\"normalizers\":["
            + "{\"type\":\"Precompiled\",\"precompiled_charsmap\":\"\"},"
            + "{\"type\":\"Replace\",\"pattern\":{\"String\":\".\"},"
            + "\"content\":\" . \"},"
            + "{\"type\":\"Replace\",\"pattern\":{\"Regex\":\"\\\\s+\"},"
            + "\"content\":\" \"},"
            + "{\"type\":\"Strip\",\"strip_left\":true,\"strip_right\":true}]},"
            + "\"pre_tokenizer\":{\"type\":\"Metaspace\",\"replacement\":\"▁\","
            + "\"prepend_scheme\":\"always\",\"split\":false},"
            + "\"model\":{\"type\":\"Unigram\",\"unk_id\":1,"
            + "\"byte_fallback\":false,\"vocab\":["
            + "[\"[PAD]\",-10.0],[\"[UNK]\",-10.0],[\"▁hello\",-1.0],"
            + "[\"▁world\",-1.0],[\"▁\",-2.0],[\".\",-1.0]]}}");
    Files.writeString(directory.resolve(ModelFileNames.CONFIG), "{\"normalize\":false}");
    SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", new float[][] {
            {0f}, {0f}, {2f}, {4f}, {8f}, {16f}
        }));
    final StaticEmbeddingModel floatModel = StaticEmbeddingModel.load(directory);

    deployQuantized(directory, 4);
    final StaticEmbeddingModel quantizedModel = StaticEmbeddingModel.load(directory);

    assertEquals(6, quantizedModel.vocabularySize());
    assertTrue(cosine(floatModel.embed("hello world."),
        quantizedModel.embed("hello world.")) > 0.98);
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
          "top neighbor of '" + word + "' must remain first after quantization");
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
    assertFalse(List.of("hello", "world", "apple").contains(neighbors.get(0).token()),
        "the analogy result must exclude every query term");
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
  void testVerificationSampleDoesNotExceedItsCap(@TempDir Path directory) throws IOException {
    final float[][] rows = new float[1025][1];
    for (int row = 0; row < rows.length; row++) {
      rows[row][0] = row + 1f;
    }
    SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", rows));

    final ModelQuantizer.Result result = ModelQuantizer.quantize(directory, 4, SEED);

    assertEquals(1024, result.sampledRows());
  }

  @Test
  void testVerificationCosineStaysWithinItsMathematicalRange() {
    final float[] row = {6.0943845e19f, 2.0969745e19f};

    assertEquals(1.0, ModelQuantizer.cosine(row, 0, row.length, row));
  }

  @Test
  void testRowCountMismatchIsRejected(@TempDir Path directory) throws IOException {
    writeModelDirectory(directory, false);
    deployQuantized(directory, 4);
    final Path vocabularyFile = directory.resolve("vocab.txt");
    final List<String> extended = new ArrayList<>(Files.readAllLines(vocabularyFile));
    extended.add("straggler");
    Files.write(vocabularyFile, extended);
    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(directory));
    assertTrue(e.getMessage().contains("do not belong to the same model"), e.getMessage());
  }

  @Test
  void testQuantizerRequiresTheSafetensors(@TempDir Path directory) {
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
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
  void testQuantizerRejectsBadBitWidthBeforeReadingTheMatrix(@TempDir Path directory)
      throws IOException {
    Files.write(directory.resolve(ModelFileNames.SAFETENSORS), new byte[] {1, 2, 3, 4});

    final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> ModelQuantizer.quantize(directory, 1, SEED));

    assertTrue(error.getMessage().contains("Bits"), error.getMessage());
  }

  @Test
  void testQuantizerRejectsMatrixShapedPoolingWeights(@TempDir Path directory)
      throws IOException {
    SafetensorsTestFiles.write(directory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", new float[][] {{1f, 2f}, {3f, 4f}}),
        SafetensorsTestFiles.matrix(StaticEmbeddingModel.WEIGHTS_TENSOR_NAME,
            new float[][] {{1f}, {1f}}));

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> ModelQuantizer.quantize(directory, 4, SEED));

    assertTrue(error.getMessage().contains("1-D"), error.getMessage());
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
          "the same table, bits, and seed must produce equal embeddings");
    }
  }

  /**
   * {@return the cosine between two vectors, or {@code Double.NaN} when either has no
   * direction}
   *
   * @param a The first vector.
   * @param b The second vector, of the same length.
   */
  private double cosine(float[] a, float[] b) {
    return ModelQuantizer.cosine(a, 0, a.length, b);
  }
}
