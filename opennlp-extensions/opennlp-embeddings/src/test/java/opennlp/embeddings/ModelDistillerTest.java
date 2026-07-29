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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The distiller's argument checking and its Zipf weighting. The forward pass itself needs a real
 * ONNX teacher and is exercised by the distillation script, not here, so these tests pin the
 * checks that must reject a bad call before any teacher is downloaded or run.
 */
class ModelDistillerTest {

  /** Model2Vec's SIF coefficient, the value the distiller uses. */
  private static final double SIF = 1e-4;

  @Test
  void testZipfWeightsFollowTheModel2vecFormula() {
    // Two rows: the Zipf distribution is over 1/2 and 1/3, normalized by their sum 5/6.
    final float[] weights = ModelDistiller.zipfWeights(2, SIF);

    assertEquals(2, weights.length);
    assertEquals(SIF / (SIF + 0.6), weights[0], 1e-10);
    assertEquals(SIF / (SIF + 0.4), weights[1], 1e-10);
  }

  @Test
  void testZipfWeightsOfASingleRowUseTheWholeDistribution() {
    // One row takes all the probability mass, so p is 1 regardless of the harmonic sum.
    final float[] weights = ModelDistiller.zipfWeights(1, SIF);

    assertEquals(1, weights.length);
    assertEquals(SIF / (SIF + 1.0), weights[0], 1e-10);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3, 100, 1000})
  void testZipfWeightsDiscountEarlyRows(int rows) {
    final float[] weights = ModelDistiller.zipfWeights(rows, SIF);

    assertEquals(rows, weights.length);
    // Frequent (early) tokens are down-weighted relative to rare (late) ones, and every weight is
    // a proper fraction: sif / (sif + p) with p in (0, 1].
    for (int i = 0; i < weights.length; i++) {
      assertTrue(weights[i] > 0 && weights[i] < 1, "row " + i + " has weight " + weights[i]);
      if (i > 0) {
        assertTrue(weights[i] > weights[i - 1],
            "row " + i + " (" + weights[i] + ") must outweigh row " + (i - 1) + " ("
                + weights[i - 1] + ")");
      }
    }
  }

  @Test
  void testZipfWeightsMatchTheHarmonicNormalizationOfTheLastRow() {
    final int rows = 1000;
    final float[] weights = ModelDistiller.zipfWeights(rows, SIF);

    double harmonicSum = 0;
    for (int j = 2; j <= rows + 1; j++) {
      harmonicSum += 1.0 / j;
    }
    assertEquals(SIF / (SIF + 1.0 / (rows + 1) / harmonicSum), weights[rows - 1], 1e-5);
  }

  @Test
  void testRejectsANullTeacherDirectory(@TempDir Path dir) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelDistiller.distill((Path) null, dir, 256, null));
    assertEquals("TeacherDirectory must not be null", e.getMessage());
  }

  @Test
  void testRejectsATeacherDirectoryThatIsNotADirectory(@TempDir Path dir) throws IOException {
    final Path file = Files.writeString(dir.resolve("teacher"), "not a directory");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelDistiller.distill(file, dir.resolve("out"), 256, null));
    assertTrue(e.getMessage().contains("is not a directory"), e.getMessage());
  }

  @Test
  void testRejectsANullOutputDirectory(@TempDir Path dir) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelDistiller.distill(dir, null, 256, null));
    assertEquals("OutputDirectory must not be null", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void testRejectsANonPositivePcaDimension(int pcaDims, @TempDir Path dir) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelDistiller.distill(dir, dir.resolve("out"), pcaDims, null));
    assertEquals("PcaDims must be at least 1, got " + pcaDims, e.getMessage());
  }

  @Test
  void testRejectsATeacherDirectoryWithoutAnOnnxGraph(@TempDir Path dir) throws IOException {
    final Path teacher = Files.createDirectory(dir.resolve("teacher"));
    Files.writeString(teacher.resolve(ModelFileNames.TOKENIZER_JSON), "{}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelDistiller.distill(teacher, dir.resolve("out"), 256, null));
    assertTrue(e.getMessage().contains(ModelFileNames.ONNX_MODEL), e.getMessage());
    // Nothing may be written before the teacher is known to be usable.
    assertTrue(Files.notExists(dir.resolve("out")), "the output directory must not be created");
  }

  /**
   * A teacher whose whole vocabulary is dropped by the cleaning has no rows to encode. That has to
   * be caught before the ONNX graph is loaded, where it would otherwise surface as a zero-length
   * matrix. The graph file here is a placeholder that would fail to load if it were reached.
   */
  @Test
  void testRejectsATeacherWhoseVocabularyIsFullyCleanedAway(@TempDir Path dir) throws IOException {
    final Path teacher = Files.createDirectory(dir.resolve("teacher"));
    Files.createDirectory(teacher.resolve("onnx"));
    Files.writeString(teacher.resolve(ModelFileNames.ONNX_MODEL), "not a real graph");
    Files.writeString(teacher.resolve(ModelFileNames.TOKENIZER_JSON),
        "{\"model\":{\"type\":\"WordPiece\",\"unk_token\":\"[unused0]\","
            + "\"vocab\":{\"[unused0]\":0}}}");

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> ModelDistiller.distill(teacher, dir.resolve("out"), 256, null));

    assertTrue(e.getMessage().contains("nothing to distill"), e.getMessage());
    assertTrue(Files.notExists(dir.resolve("out")), "the output directory must not be created");
  }

  /**
   * A bad output argument must be rejected before the teacher reference is resolved, so that a
   * mistyped command against a hub id does not download gigabytes first. The teacher here is a
   * well-formed hub id that would otherwise be fetched.
   */
  @ParameterizedTest
  @ValueSource(strings = {"BAAI/bge-m3", "sentence-transformers/all-MiniLM-L6-v2"})
  void testRejectsABadOutputBeforeResolvingAHubTeacher(String teacher, @TempDir Path dir) {
    assertEquals("OutputDirectory must not be null",
        assertThrows(IllegalArgumentException.class,
            () -> ModelDistiller.distill(teacher, null, 256, null)).getMessage());
    assertEquals("PcaDims must be at least 1, got 0",
        assertThrows(IllegalArgumentException.class,
            () -> ModelDistiller.distill(teacher, dir.resolve("out"), 0, null)).getMessage());
  }
}
