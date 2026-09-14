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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.BenchmarkListEntry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests benchmark units, model reloading and fixture cleanup. */
class StaticEmbeddingModelBenchmarkTest {

  private static final String QUERY = "fox";

  @TempDir
  Path modelDirectory;

  /**
   * Checks that throughput and allocation are reported per text, not per batch.
   *
   * @throws NoSuchMethodException If the benchmark method cannot be found.
   */
  @Test
  void testEmbedOperationCount() throws NoSuchMethodException {
    final OperationsPerInvocation operations = StaticEmbeddingModelBenchmark.class
        .getMethod("embed", StaticEmbeddingModelBenchmark.ModelState.class, Blackhole.class)
        .getAnnotation(OperationsPerInvocation.class);
    assertNotNull(operations, "The batch requires an explicit operation count");
    assertEquals(StaticEmbeddingModelBenchmark.SENTENCES.size(), operations.value());
  }

  /**
   * Checks that generated JMH metadata reflects the current benchmark source.
   *
   * @throws IOException If the generated metadata cannot be read.
   */
  @Test
  void testGeneratedMetadata() throws IOException {
    try (InputStream metadata = StaticEmbeddingModelBenchmark.class
        .getResourceAsStream("/META-INF/BenchmarkList")) {
      assertNotNull(metadata, "JMH annotation processing must generate benchmark metadata");
      final List<BenchmarkListEntry> entries = BenchmarkList.readBenchmarkList(metadata).stream()
          .filter(entry -> entry.getUserClassQName()
              .equals(StaticEmbeddingModelBenchmark.class.getName()))
          .toList();
      final String benchmarkName = StaticEmbeddingModelBenchmark.class.getName();
      assertEquals(List.of(benchmarkName + ".embed", benchmarkName + ".load",
              benchmarkName + ".mostSimilarTop10"),
          entries.stream().map(BenchmarkListEntry::getUsername).sorted().toList());
      for (final BenchmarkListEntry entry : entries) {
        assertEquals(StaticEmbeddingModelBenchmark.class.getAnnotation(Threads.class).value(),
            entry.getThreads().get());
        assertArrayEquals(new String[] {"synthetic"}, entry.getParams().get().get("modelDir"));
        if (entry.getUsername().endsWith(".embed")) {
          assertEquals(StaticEmbeddingModelBenchmark.SENTENCES.size(),
              entry.getOperationsPerInvocation().get());
        }
        if (entry.getUsername().endsWith(".load")) {
          assertEquals(TimeUnit.MILLISECONDS, entry.getTimeUnit().get());
          assertEquals(Mode.AverageTime, entry.getMode());
        } else {
          assertEquals(TimeUnit.SECONDS, entry.getTimeUnit().get());
          assertEquals(Mode.Throughput, entry.getMode());
        }
      }
    }
  }

  /**
   * Loads the generated table through the public directory API and removes the fixture.
   *
   * @throws IOException If fixture creation or loading fails.
   */
  @Test
  void testSyntheticModel() throws IOException {
    final var files = new StaticEmbeddingModelBenchmark.ModelFiles();
    files.modelDir = "synthetic";
    try {
      files.prepare();
      final var state = new StaticEmbeddingModelBenchmark.ModelState();
      state.load(files);
      final StaticEmbeddingModel model = state.model;
      assertEquals(29_528, model.vocabularySize());
      assertEquals(256, model.dimension());
      assertEquals(10, model.mostSimilar(QUERY, 10).size());
      final StaticEmbeddingModel reloaded = new StaticEmbeddingModelBenchmark().load(files);
      assertNotSame(model, reloaded);
      for (final String text : StaticEmbeddingModelBenchmark.SENTENCES) {
        final float[] vector = model.embed(text);
        assertArrayEquals(vector, reloaded.embed(text));
        double norm = 0;
        for (final float value : vector) {
          norm += (double) value * value;
        }
        assertEquals(1, norm, 1e-6, text);
      }
    } finally {
      files.cleanup();
    }
    assertFalse(Files.exists(files.directory));
    files.cleanup();
  }

  /**
   * Checks fresh file loading without changing or deleting a supplied directory.
   *
   * @throws IOException If fixture creation or loading fails.
   */
  @Test
  void testUserModelReloadAndCleanup() throws IOException {
    Files.write(modelDirectory.resolve(ModelFileNames.VOCABULARY), List.of("[UNK]", QUERY));
    Files.writeString(modelDirectory.resolve(ModelFileNames.CONFIG), "{\"normalize\":false}");
    Files.writeString(modelDirectory.resolve(ModelFileNames.TOKENIZER_CONFIG), "{\"do_lower_case\":true}");
    writeMatrix(new float[] {1, 2});
    final var files = new StaticEmbeddingModelBenchmark.ModelFiles();
    files.modelDir = modelDirectory.toString();
    files.prepare();
    final var benchmark = new StaticEmbeddingModelBenchmark();
    final StaticEmbeddingModel original = benchmark.load(files);
    assertArrayEquals(new float[] {1, 2}, original.embed(QUERY));
    writeMatrix(new float[] {3, 4});
    final byte[] tensorBytes = Files.readAllBytes(modelDirectory.resolve(ModelFileNames.SAFETENSORS));
    final StaticEmbeddingModel updated = benchmark.load(files);
    assertNotSame(original, updated);
    assertArrayEquals(new float[] {3, 4}, updated.embed(QUERY));
    assertArrayEquals(new float[] {1, 2}, original.embed(QUERY));
    files.cleanup();
    assertTrue(Files.isDirectory(modelDirectory));
    assertEquals(List.of("[UNK]", QUERY),
        Files.readAllLines(modelDirectory.resolve(ModelFileNames.VOCABULARY)));
    assertEquals("{\"normalize\":false}", Files.readString(modelDirectory.resolve(ModelFileNames.CONFIG)));
    assertEquals("{\"do_lower_case\":true}",
        Files.readString(modelDirectory.resolve(ModelFileNames.TOKENIZER_CONFIG)));
    assertArrayEquals(tensorBytes, Files.readAllBytes(modelDirectory.resolve(ModelFileNames.SAFETENSORS)));
  }

  /**
   * Checks that a missing model directory fails without creating input files.
   *
   * @throws IOException If setup or cleanup fails.
   */
  @Test
  void testMissingUserModel() throws IOException {
    final Path missing = modelDirectory.resolve("missing-model");
    final var files = new StaticEmbeddingModelBenchmark.ModelFiles();
    files.modelDir = missing.toString();
    files.prepare();
    assertThrows(IllegalArgumentException.class, () -> new StaticEmbeddingModelBenchmark().load(files));
    files.cleanup();
    assertFalse(Files.exists(missing));
  }

  /**
   * Writes a small embedding table for the supplied-directory test.
   *
   * @param vector The vector for the query token.
   * @throws IOException If the file cannot be written.
   */
  private void writeMatrix(float[] vector) throws IOException {
    SafetensorsTestFiles.write(modelDirectory.resolve(ModelFileNames.SAFETENSORS),
        SafetensorsTestFiles.matrix("embeddings", new float[][] {new float[2], vector}));
  }
}
