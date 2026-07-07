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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A concurrency smoke test for the {@code @ThreadSafe} claim on {@link StaticEmbeddingModel}:
 * one shared instance, many threads, every concurrent result compared against the
 * single-threaded reference computed up front. All operations are deterministic, so any
 * deviation under concurrency is a thread-safety defect by definition. Mirrors the
 * {@code LexiconConcurrencyTest} pattern from the opennlp-wordnet module.
 */
class StaticEmbeddingModelConcurrencyTest {

  private static final int THREADS = 8;
  private static final int ITERATIONS_PER_THREAD = 200;

  private static StaticEmbeddingModel loadFixture(Path dir) throws IOException {
    final Path vocab = dir.resolve("vocab.txt");
    Files.write(vocab,
        List.of("[CLS]", "[SEP]", "[UNK]", "king", "queen", "man", "woman", "apple"));
    final float[][] rows = {
        {0f, 0f}, {0f, 0f}, {0f, 0f},
        {3f, 3f}, {2f, 4f}, {2f, 1f}, {1f, 2f}, {-3f, -1f},
    };
    final ByteBuffer buffer = ByteBuffer.allocate(rows.length * 2 * 4)
        .order(ByteOrder.LITTLE_ENDIAN);
    for (final float[] row : rows) {
      for (final float value : row) {
        buffer.putFloat(value);
      }
    }
    final byte[] data = buffer.array();
    final String header = "{\"embeddings\":{\"dtype\":\"F32\",\"shape\":[" + rows.length
        + ",2],\"data_offsets\":[0," + data.length + "]}}";
    final byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(headerBytes.length).array());
    out.write(headerBytes);
    out.write(data);
    final Path tensors = dir.resolve("model.safetensors");
    Files.write(tensors, out.toByteArray());
    return StaticEmbeddingModel.load(vocab, tensors, true, true);
  }

  @Test
  void testConcurrentUseMatchesSingleThreadedReference(@TempDir Path dir) throws Exception {
    final StaticEmbeddingModel model = loadFixture(dir);
    final float[] referenceEmbedding = model.embed("The King and Queen");
    final double referenceSimilarity = model.similarity("king", "queen");
    final List<Neighbor> referenceNeighbors = model.mostSimilar("king", 3);
    final List<Neighbor> referenceAnalogy = model.analogy("man", "king", "woman", 2);

    final Queue<String> problems = new ConcurrentLinkedQueue<>();
    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      for (int t = 0; t < THREADS; t++) {
        executor.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
              if (!Arrays.equals(referenceEmbedding, model.embed("The King and Queen"))) {
                problems.add("embed deviated from the single-threaded reference");
              }
              if (referenceSimilarity != model.similarity("king", "queen")) {
                problems.add("similarity deviated from the single-threaded reference");
              }
              if (!referenceNeighbors.equals(model.mostSimilar("king", 3))) {
                problems.add("mostSimilar deviated from the single-threaded reference");
              }
              if (!referenceAnalogy.equals(model.analogy("man", "king", "woman", 2))) {
                problems.add("analogy deviated from the single-threaded reference");
              }
            }
          }
          catch (Exception e) {
            problems.add("Unexpected exception: " + e);
          }
        });
      }
      start.countDown();
      executor.shutdown();
      assertTrue(executor.awaitTermination(2, TimeUnit.MINUTES),
          "Concurrent workers did not finish in time");
    }
    finally {
      executor.shutdownNow();
    }
    assertTrue(problems.isEmpty(), () -> "Thread-safety violations: " + problems);
  }
}
