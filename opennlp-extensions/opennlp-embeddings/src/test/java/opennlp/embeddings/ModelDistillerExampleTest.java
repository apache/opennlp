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
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Distills an original ONNX lookup table, loads the output and ranks short documents.
 * The table tests data flow, not the quality of a trained language model.
 */
class ModelDistillerExampleTest {

  /** Matrix order after removal of CLS and SEP. */
  private static final List<String> TOKENS =
      List.of("[PAD]", "[UNK]", "coffee", "espresso", "tea", "history");

  /** Longer input first to exercise term batching by sequence length. */
  private static final List<String> REQUESTED_TERMS =
      List.of("Coffee espresso tea", "coffee", "TEA HISTORY", "coffee ESPRESSO", " tea history ");

  /** Expected term order after normalization and duplicate removal. */
  private static final List<String> TERMS =
      List.of("coffee espresso tea", "tea history", "coffee espresso");

  /** The initial coordinates after pooling CLS, content and SEP in the lookup graph. */
  private static final double[][] POOLED = {
      {0, 0}, {0, 0}, {1, 0}, {2.0 / 3, 1.0 / 3}, {-1.0 / 3, 2.0 / 3}, {-1.0 / 3, -2.0 / 3},
      {4.0 / 5, 3.0 / 5}, {-0.5, 0}, {1.25, 0.25}
  };

  /** Floating-point tolerance for PCA projection and normalization. */
  private static final double TOLERANCE = 1e-5;

  /** A document and cosine similarity to the query. */
  private record Scored(String document, double score) {
  }

  /**
   * Tests ONNX inference, PCA, weighting, serialization and optional term batching.
   *
   * @param includeTerms Whether to distill additional phrases.
   * @param directory The test directory.
   * @throws IOException Thrown if a model file cannot be read or written.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testDistillReloadAndSearch(boolean includeTerms, @TempDir Path directory)
      throws IOException {
    final Path teacher = writeTeacher(directory.resolve("teacher"));
    final Path output = directory.resolve("static-model");
    final List<String> terms = includeTerms ? REQUESTED_TERMS : List.of();

    final ModelDistiller.Result result = ModelDistiller.distill(teacher, output, 2, terms, null);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(output);

    assertEquals("WordPiece", result.family());
    assertEquals(TOKENS.size(), result.vocabularySize());
    assertEquals(includeTerms ? TERMS.size() : 0, result.termCount());
    assertEquals(4, result.teacherDimension());
    assertEquals(2, result.dimension());
    assertEquals(1, result.explainedVarianceRatio(), TOLERANCE);
    assertEquals(result.dimension(), model.dimension());
    assertEquals(result.vocabularySize(), model.vocabularySize());
    assertEquals(result.termCount(), model.termCount());
    assertEquals(TOKENS, Files.readAllLines(output.resolve(ModelFileNames.VOCABULARY)));
    if (includeTerms) {
      assertEquals(TERMS, Files.readAllLines(output.resolve(ModelFileNames.TERMS)));
    } else {
      assertTrue(Files.notExists(output.resolve(ModelFileNames.TERMS)));
    }

    final double[][] expected = centeredRows(TOKENS.size() + result.termCount());
    checkStoredMatrix(output, expected);
    final List<String> texts = new ArrayList<>(TOKENS);
    if (includeTerms) {
      texts.addAll(TERMS);
    }
    for (int index = 2; index < texts.size(); index++) {
      final float[] vector = model.embed(texts.get(index));
      assertEquals(1, vector[0] * vector[0] + vector[1] * vector[1], TOLERANCE);
      assertEquals(cosine(expected[2], expected[index]),
          model.similarity("coffee", texts.get(index)), TOLERANCE, texts.get(index));
    }
    assertArrayEquals(model.embed("coffee"), model.embed("COFFEE"));
    assertArrayEquals(new float[2], model.embed("unlisted"));

    final List<Scored> results = new ArrayList<>();
    for (final String document : List.of("history", "tea", "espresso")) {
      results.add(new Scored(document, model.similarity("coffee", document)));
    }
    results.sort(Comparator.comparingDouble(Scored::score).reversed());
    assertEquals("espresso", results.get(0).document());
    assertTrue(results.get(0).score() > results.get(1).score());
    assertEquals("coffee", model.mostSimilar("coffee", 1).get(0).token());

    final Path repeatedOutput = directory.resolve("repeated-model");
    assertEquals(result, ModelDistiller.distill(teacher, repeatedOutput, 2, terms, null));
    for (final String file : List.of(ModelFileNames.SAFETENSORS, ModelFileNames.TOKENIZER_JSON,
        ModelFileNames.CONFIG, ModelFileNames.VOCABULARY, ModelFileNames.TOKENIZER_CONFIG)) {
      assertEquals(-1L, Files.mismatch(output.resolve(file), repeatedOutput.resolve(file)), file);
    }
    assertArrayEquals(model.embed("coffee espresso"),
        StaticEmbeddingModel.load(repeatedOutput).embed("coffee espresso"));
  }

  /**
   * Runs the Java distillation listing in the manual with an original test graph.
   *
   * @param directory The test directory.
   * @throws IOException Thrown if a model file cannot be read or written.
   */
  @Test
  void testManualDistillationExample(@TempDir Path directory) throws IOException {
    final Path teacher = writeTeacher(directory.resolve("teacher"));
    final Path output = directory.resolve("static-model");

    ModelDistiller.distill(teacher, output, 2, List.of("coffee espresso"), null);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(output);
    final float[] vector = model.embed("coffee espresso");
    final List<Neighbor> related = model.mostSimilar("coffee espresso", 3);

    assertEquals(2, vector.length);
    assertEquals("coffee espresso", related.get(0).token());
    assertEquals(3, related.size());
  }

  /**
   * Writes the tokenizer configuration and original ONNX lookup graph.
   *
   * @param directory The destination directory.
   * @return The teacher directory.
   * @throws IOException Thrown if writing fails.
   */
  private Path writeTeacher(Path directory) throws IOException {
    EmbeddingTestFixtures.writeLookupTeacherOnnxModel(
        Files.createDirectories(directory.resolve("onnx")));
    Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_JSON), """
        {"version":"1.0",
         "normalizer":{"type":"BertNormalizer","lowercase":true},
         "added_tokens":[
           {"id":0,"content":"[PAD]","special":true},
           {"id":1,"content":"[UNK]","special":true},
           {"id":2,"content":"[CLS]","special":true},
           {"id":3,"content":"[SEP]","special":true}],
         "post_processor":{"type":"BertProcessing","cls":["[CLS]",2],"sep":["[SEP]",3]},
         "model":{"type":"WordPiece","unk_token":"[UNK]",
           "vocab":{"[PAD]":0,"[UNK]":1,"[CLS]":2,"[SEP]":3,
             "coffee":4,"espresso":5,"tea":6,"history":7}}}
        """);
    Files.writeString(directory.resolve(ModelFileNames.TOKENIZER_CONFIG),
        "{\"pad_token\":\"[PAD]\"}");
    return directory;
  }

  /**
   * Subtracts the mean from the analytically calculated teacher vectors.
   *
   * @param count The number of model entries.
   * @return The expected PCA inputs.
   */
  private double[][] centeredRows(int count) {
    final double[] mean = new double[2];
    for (int index = 0; index < count; index++) {
      for (int component = 0; component < mean.length; component++) {
        mean[component] += POOLED[index][component] / count;
      }
    }
    final double[][] result = new double[count][2];
    for (int index = 0; index < count; index++) {
      for (int component = 0; component < mean.length; component++) {
        result[index][component] = POOLED[index][component] - mean[component];
      }
    }
    return result;
  }

  /**
   * Checks PCA distances and Zipf scaling without depending on component signs.
   *
   * @param directory The saved model directory.
   * @param expected The centered teacher vectors before Zipf scaling.
   * @throws IOException Thrown if reading fails.
   */
  private void checkStoredMatrix(Path directory, double[][] expected) throws IOException {
    final SafetensorsFile file = SafetensorsFile.read(directory.resolve(ModelFileNames.SAFETENSORS));
    assertEquals(1, file.tensorNames().size());
    final String tensor = file.tensorNames().iterator().next();
    assertArrayEquals(new int[] {expected.length, 2}, file.tensorInfo(tensor).shape());
    final float[] matrix = file.readFloats(tensor);
    double harmonicSum = 0;
    for (int rank = 2; rank <= expected.length + 1; rank++) {
      harmonicSum += 1.0 / rank;
    }
    final double[][] scaled = new double[expected.length][2];
    for (int index = 0; index < expected.length; index++) {
      final double weight = 1e-4 / (1e-4 + 1.0 / (index + 2) / harmonicSum);
      scaled[index][0] = matrix[index * 2] / weight;
      scaled[index][1] = matrix[index * 2 + 1] / weight;
    }
    for (int left = 0; left < expected.length; left++) {
      for (int right = 0; right < expected.length; right++) {
        assertEquals(dot(expected[left], expected[right]), dot(scaled[left], scaled[right]),
            TOLERANCE, "matrix entries " + left + ", " + right);
      }
    }
  }

  /**
   * Calculates the dot product in the original test coordinate system.
   *
   * @param left The initial vector.
   * @param right The other vector.
   * @return The dot product.
   */
  private double dot(double[] left, double[] right) {
    return left[0] * right[0] + left[1] * right[1];
  }

  /**
   * Calculates cosine similarity for the expected test vectors.
   *
   * @param left The initial vector.
   * @param right The other vector.
   * @return Cosine similarity.
   */
  private double cosine(double[] left, double[] right) {
    return dot(left, right) / Math.sqrt(dot(left, left) * dot(right, right));
  }
}
