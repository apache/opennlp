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

import opennlp.embeddings.StaticEmbeddingModel.Normalization;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A concurrency smoke test for the {@code @ThreadSafe} claim on {@link StaticEmbeddingModel}:
 * one shared instance, many threads, every concurrent result compared against the
 * single-threaded reference computed up front. Every operation is deterministic, so any
 * deviation under contention is a thread-safety defect.
 */
class StaticEmbeddingModelConcurrencyTest {

  private static final int THREADS = 8;
  private static final int ITERATIONS_PER_THREAD = 200;

  @Test
  void testConcurrentUseMatchesSingleThreadedReference(@TempDir Path dir) throws Exception {
    final StaticEmbeddingModel model =
        EmbeddingTestFixtures.loadAnalogyModel(dir, Normalization.L2);
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
