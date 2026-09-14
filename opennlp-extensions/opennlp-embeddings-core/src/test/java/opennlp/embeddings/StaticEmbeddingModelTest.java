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

import opennlp.embeddings.StaticEmbeddingModel.Casing;
import opennlp.embeddings.StaticEmbeddingModel.Normalization;
import opennlp.tools.embeddings.TextEmbedder;
import opennlp.tools.util.InvalidFormatException;

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

  private static Path writeSafetensorsF16(Path dir) throws IOException {
    final Path file = dir.resolve("model.safetensors");
    SafetensorsTestFiles.write(file, "F16", SafetensorsTestFiles.matrix("embeddings", ROWS));
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
  void testLoadsAnF16EmbeddingMatrix(@TempDir Path dir) throws IOException {
    // model2vec writes float16 by default, so the loader must accept it and widen to float.
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensorsF16(dir),
            Casing.UNCASED, Normalization.NONE);

    assertEquals(DIMENSION, model.dimension());
    // (hello + world) / 2 = [3.5, 35, 350]; the row values are all exact in IEEE half.
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, model.embed("hello world"), 1e-2f);
  }

  @Test
  void testLoadsAModelWhoseVocabularyDroppedTheFrameTokens(@TempDir Path dir) throws IOException {
    // Model2Vec mean-pools content pieces and never frames, so it removes [CLS]/[SEP] from the
    // distilled table, keeping only [PAD]/[UNK]. Such a table must still load; the loader caches
    // the frame onto the unknown row and pooling skips it. The content rows below carry the same
    // values as the framed fixture, so the embedding must match it piece for piece.
    final List<String> tokens = List.of("[PAD]", "[UNK]", "hello", "world", "cat");
    final float[][] rows = {
        {9f, 9f, 9f},       // [PAD], never pooled
        {8f, 8f, 8f},       // [UNK], never pooled
        {3f, 30f, 300f},    // hello, same as the framed fixture's row
        {4f, 40f, 400f},    // world, same as the framed fixture's row
        {5f, 50f, 500f},    // cat, same as the framed fixture's row
    };
    final Path vocab = dir.resolve("vocab.txt");
    Files.write(vocab, tokens);
    final Path tensors = dir.resolve("model.safetensors");
    SafetensorsTestFiles.write(tensors, SafetensorsTestFiles.matrix("embeddings", rows));

    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(vocab, tensors, Casing.UNCASED, Normalization.NONE);

    // (hello + world) / 2, identical to testEmbedMeanPoolsWithoutWeights: the cached frame and
    // any [UNK] are skipped, so only the two content pieces pool.
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, model.embed("hello world"), 1e-5f);
    // "xyzzy" folds to [UNK] and is dropped, leaving just "cat".
    assertArrayEquals(new float[] {5f, 50f, 500f}, model.embed("cat xyzzy"), 1e-5f);
    // Text with no content pieces is a zero vector, not the frame or [UNK] vector.
    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed("xyzzy"), 1e-5f);
    // The unknown row must never surface as a neighbor.
    for (final Neighbor neighbor : model.mostSimilar("cat", 4)) {
      assertTrue(!"[UNK]".equals(neighbor.token()) && !"[PAD]".equals(neighbor.token()),
          "a special row leaked into neighbors: " + neighbor.token());
    }
  }

  @Test
  void testRejectsAWordPieceVocabularyWithoutUnknownToken(@TempDir Path dir) throws IOException {
    final List<String> tokens = List.of("[CLS]", "[SEP]", "hello", "world");
    final float[][] rows = {{0f, 0f, 0f}, {1f, 1f, 1f}, {2f, 2f, 2f}, {3f, 3f, 3f}};
    final Path vocab = dir.resolve("vocab.txt");
    Files.write(vocab, tokens);
    final Path tensors = dir.resolve("model.safetensors");
    SafetensorsTestFiles.write(tensors, SafetensorsTestFiles.matrix("embeddings", rows));

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(vocab, tensors, Casing.UNCASED, Normalization.NONE));
    assertTrue(e.getMessage().contains("[UNK]"), e.getMessage());
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
  void testMeanPoolingDoesNotOverflowFiniteRows(@TempDir Path dir) throws IOException {
    final Path vocabulary = dir.resolve("large-vocab.txt");
    Files.write(vocabulary, List.of("[CLS]", "[SEP]", "[UNK]", "large", "value"));
    final Path tensors = dir.resolve("large-model.safetensors");
    SafetensorsTestFiles.write(tensors, SafetensorsTestFiles.matrix("embeddings", new float[][] {
        {0f}, {0f}, {0f}, {Float.MAX_VALUE}, {Float.MAX_VALUE}
    }));
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(vocabulary, tensors,
        Casing.UNCASED, Normalization.NONE);

    final float[] result = model.embed("large value");

    assertArrayEquals(new float[] {Float.MAX_VALUE}, result);
  }

  @Test
  void testL2NormalizationHandlesFiniteVectorsWhoseNormExceedsFloatRange(@TempDir Path dir)
      throws IOException {
    final Path vocabulary = dir.resolve("large-vocab.txt");
    Files.write(vocabulary, List.of("[CLS]", "[SEP]", "[UNK]", "large"));
    final Path tensors = dir.resolve("large-model.safetensors");
    SafetensorsTestFiles.write(tensors, SafetensorsTestFiles.matrix("embeddings", new float[][] {
        {0f, 0f}, {0f, 0f}, {0f, 0f}, {Float.MAX_VALUE, Float.MAX_VALUE}
    }));
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(vocabulary, tensors,
        Casing.UNCASED, Normalization.L2);

    final float[] result = model.embed("large");

    final float normalizedCoordinate = (float) (1.0 / Math.sqrt(2.0));
    assertArrayEquals(new float[] {normalizedCoordinate, normalizedCoordinate}, result, 1e-6f);
  }

  @Test
  void testFinitePoolingWeightsDoNotProduceInfiniteCoordinates(@TempDir Path dir)
      throws IOException {
    final Path vocabulary = dir.resolve("weighted-vocab.txt");
    Files.write(vocabulary, List.of("[CLS]", "[SEP]", "[UNK]", "large"));
    final Path tensors = dir.resolve("weighted-model.safetensors");
    SafetensorsTestFiles.write(tensors,
        SafetensorsTestFiles.matrix("embeddings", new float[][] {
            {0f}, {0f}, {0f}, {Float.MAX_VALUE}
        }),
        SafetensorsTestFiles.vector("weights", new float[] {1f, 1f, 1f, Float.MAX_VALUE}));
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(vocabulary, tensors,
        Casing.UNCASED, Normalization.NONE);

    final float[] result = model.embed("large");

    assertArrayEquals(new float[] {Float.MAX_VALUE}, result);
  }

  @Test
  void testL2NormalizationPrecedesFloatNarrowing(@TempDir Path dir) throws IOException {
    final Path vocabulary = dir.resolve("weighted-vocab.txt");
    Files.write(vocabulary, List.of("[CLS]", "[SEP]", "[UNK]", "large"));
    final Path tensors = dir.resolve("weighted-model.safetensors");
    SafetensorsTestFiles.write(tensors,
        SafetensorsTestFiles.matrix("embeddings", new float[][] {
            {0f, 0f}, {0f, 0f}, {0f, 0f}, {Float.MAX_VALUE, 1f}
        }),
        SafetensorsTestFiles.vector("weights", new float[] {1f, 1f, 1f, Float.MAX_VALUE}));
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(vocabulary, tensors,
        Casing.UNCASED, Normalization.L2);

    final float[] result = model.embed("large");

    assertEquals(1f, result[0]);
    assertEquals((float) (1.0 / Float.MAX_VALUE), result[1], Float.MIN_VALUE);
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
  void testEmbedOfWhitespaceOnlyTextIsZeroVector(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    // Whitespace-only text produces no content pieces at all, unlike unknown text, which still
    // produces a (skipped) [UNK]; both must pool to the zero vector without dividing by zero.
    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed(" \t\n "), 1e-5f);
  }

  @Test
  void testEmbedSkipsSupplementaryPlaneTextAsUnknown(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        StaticEmbeddingModel.load(writeVocab(dir), writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE);

    // An emoji is a supplementary-plane character (a surrogate pair in Java) no vocabulary
    // piece covers; it must fold to [UNK] and be skipped, leaving just "cat" in the pool.
    assertArrayEquals(new float[] {5f, 50f, 500f}, model.embed("cat \uD83D\uDE00"), 1e-5f);
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

    // Malformed model content (files that disagree) is a checked InvalidFormatException, not
    // an IllegalArgumentException; the latter is reserved for caller argument errors.
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(shortVocab, writeSafetensors(dir, false),
            Casing.UNCASED, Normalization.NONE));
    assertTrue(e.getMessage().contains("rows"));
  }

  @Test
  void testLoadRejectsAZeroDimensionMatrix(@TempDir Path dir) throws IOException {
    final float[][] rows = new float[VOCAB_TOKENS.size()][0];
    final Path tensors = dir.resolve("zero-dimension.safetensors");
    SafetensorsTestFiles.write(tensors,
        SafetensorsTestFiles.matrix("embeddings", rows));

    final InvalidFormatException error = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(writeVocab(dir), tensors,
            Casing.UNCASED, Normalization.NONE));

    assertTrue(error.getMessage().contains("dimension"), error.getMessage());
  }

  @Test
  void testLoadRejectsANonFiniteMatrixValue(@TempDir Path dir) throws IOException {
    // The distiller replaces non-finite teacher values with zero before writing, so a NaN in a
    // loaded matrix marks a corrupt or foreign file. Loading must reject it because a NaN row
    // defeats both the zero-norm guard and every similarity comparison downstream.
    final float[][] rows = new float[ROWS.length][];
    for (int r = 0; r < ROWS.length; r++) {
      rows[r] = ROWS[r].clone();
    }
    rows[4][1] = Float.NaN;
    final Path vocab = writeVocab(dir);
    final Path nanTensors = dir.resolve("nan.safetensors");
    SafetensorsTestFiles.write(nanTensors, SafetensorsTestFiles.matrix("embeddings", rows));

    final InvalidFormatException nan = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(vocab, nanTensors, Casing.UNCASED, Normalization.NONE));
    assertTrue(nan.getMessage().contains("row 4"), nan.getMessage());

    // An infinity is just as corrupting and must be rejected the same way.
    rows[4][1] = Float.POSITIVE_INFINITY;
    final Path infiniteTensors = dir.resolve("infinite.safetensors");
    SafetensorsTestFiles.write(infiniteTensors,
        SafetensorsTestFiles.matrix("embeddings", rows));

    final InvalidFormatException infinite = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(vocab, infiniteTensors,
            Casing.UNCASED, Normalization.NONE));
    assertTrue(infinite.getMessage().contains("row 4"), infinite.getMessage());
  }

  @Test
  void testLoadRejectsANonFiniteWeight(@TempDir Path dir) throws IOException {
    final Path vocab = writeVocab(dir);
    final float[] weights = {1f, 1f, 1f, 1f, Float.NaN, 1f};
    final Path nanTensors = dir.resolve("nan-weight.safetensors");
    SafetensorsTestFiles.write(nanTensors,
        SafetensorsTestFiles.matrix("embeddings", ROWS),
        SafetensorsTestFiles.vector("weights", weights));

    final InvalidFormatException nan = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(vocab, nanTensors,
            Casing.UNCASED, Normalization.NONE));
    assertTrue(nan.getMessage().contains("weights"), nan.getMessage());
    assertTrue(nan.getMessage().contains("row 4"), nan.getMessage());

    weights[4] = Float.NEGATIVE_INFINITY;
    final Path infiniteTensors = dir.resolve("infinite-weight.safetensors");
    SafetensorsTestFiles.write(infiniteTensors,
        SafetensorsTestFiles.matrix("embeddings", ROWS),
        SafetensorsTestFiles.vector("weights", weights));

    final InvalidFormatException infinite = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(vocab, infiniteTensors,
            Casing.UNCASED, Normalization.NONE));
    assertTrue(infinite.getMessage().contains("weights"), infinite.getMessage());
    assertTrue(infinite.getMessage().contains("row 4"), infinite.getMessage());
  }

  @Test
  void testLoadRejectsWeightsSizeMismatch(@TempDir Path dir) throws IOException {
    // A weights tensor sized for a different (smaller) vocabulary than the embedding matrix.
    final Path file = dir.resolve("mismatched.safetensors");
    SafetensorsTestFiles.write(file,
        SafetensorsTestFiles.matrix("embeddings", ROWS),
        SafetensorsTestFiles.vector("weights", new float[] {1f}));

    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(writeVocab(dir), file, Casing.UNCASED, Normalization.NONE));
    assertTrue(e.getMessage().contains("weights"));
  }

  @Test
  void testLoadRejectsWeightsThatAreNotOneDimensional(@TempDir Path dir) throws IOException {
    final Path vocabulary = dir.resolve("scalar-weight-vocab.txt");
    Files.write(vocabulary, List.of("[UNK]"));
    final Path tensors = dir.resolve("scalar-weight.safetensors");
    SafetensorsTestFiles.write(tensors,
        SafetensorsTestFiles.matrix("embeddings", new float[][] {{1f}}),
        new SafetensorsTestFiles.Tensor("weights", new int[0], new float[] {1f}));

    final InvalidFormatException exception = assertThrows(InvalidFormatException.class,
        () -> StaticEmbeddingModel.load(vocabulary, tensors,
            Casing.UNCASED, Normalization.NONE));

    assertTrue(exception.getMessage().contains("weights"), exception.getMessage());
    assertTrue(exception.getMessage().contains("1-D"), exception.getMessage());
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
  void testDirectoryLoadReadsCasedFromTheTokenizerConfig(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "false");

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);

    // do_lower_case=false maps to Casing.CASED: lower-case text still matches the vocabulary...
    assertArrayEquals(new float[] {3.5f, 35f, 350f}, model.embed("hello world"), 1e-5f);
    // ...but upper-case text is preserved as-is, matches no cased vocabulary entry, folds to
    // the (skipped) [UNK], and pools to the zero vector instead of being lower-cased first.
    assertArrayEquals(new float[] {0f, 0f, 0f}, model.embed("HELLO WORLD"), 1e-5f);
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

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("config.json"));
    assertTrue(e.getMessage().contains("explicit load overloads"));
  }

  @Test
  void testDirectoryLoadRejectsAConfigWithoutNormalize(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");
    Files.writeString(dir.resolve("config.json"), "{\"model_type\":\"model2vec\"}");

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("normalize"));
  }

  @Test
  void testDirectoryLoadRejectsAConfigDeclaringNonMeanPooling(@TempDir Path dir)
      throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");
    // Only mean pooling is implemented, so a config declaring another operation is invalid.
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false,\"pooling\":\"max\"}");

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("max"), e.getMessage());
    assertTrue(e.getMessage().contains("mean"), e.getMessage());
  }

  @Test
  void testDirectoryLoadAcceptsTheDeclaredMeanPooling(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");
    // The pooling the distiller writes; declaring it explicitly must load like omitting it.
    Files.writeString(dir.resolve("config.json"),
        "{\"model_type\":\"model2vec\",\"normalize\":false,\"pooling\":\"mean\"}");

    assertArrayEquals(new float[] {3.5f, 35f, 350f},
        StaticEmbeddingModel.load(dir).embed("hello world"), 1e-5f);
  }

  @Test
  void testDirectoryLoadRejectsContradictoryStripAccents(@TempDir Path dir) throws IOException {
    writeVocab(dir);
    writeSafetensors(dir, false);
    writeConfigs(dir, "false", "true");
    Files.writeString(dir.resolve("tokenizer_config.json"),
        "{\"do_lower_case\":true,\"strip_accents\":false}");

    final InvalidFormatException e =
        assertThrows(InvalidFormatException.class, () -> StaticEmbeddingModel.load(dir));
    assertTrue(e.getMessage().contains("strip_accents"));
  }

  @Test
  void testTextEmbedderInterfaceMatchesDirectUseAndBatches(@TempDir Path dir) throws IOException {
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
