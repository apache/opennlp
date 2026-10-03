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

package opennlp.morfologik.tagdict;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import morfologik.stemming.Dictionary;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.morfologik.AbstractMorfologikTest;
import opennlp.tools.postag.TagDictionary;

/**
 * Concurrency smoke test for {@link MorfologikTagDictionary}: one shared instance is
 * looked up from many threads. {@code POSTaggerME} is {@code @ThreadSafe} and may share
 * a Morfologik-backed tag dictionary across beam workers, so a shared lookup must not
 * corrupt results or throw.
 */
public class MorfologikTagDictionaryConcurrencyTest extends AbstractMorfologikTest {

  private static final int THREADS = 8;
  private static final int ITERATIONS_PER_THREAD = 20_000;

  @Test
  void testConcurrentGetTagsMatchesSingleThreadedReference() throws Exception {
    final Path output = createMorfologikDictionary();
    output.toFile().deleteOnExit();
    final TagDictionary dictionary =
        new MorfologikTagDictionary(Dictionary.read(output), false);

    final String[] referenceCasa = dictionary.getTags("casa");
    final String[] referenceCarro = dictionary.getTags("carro");
    Assertions.assertNotNull(referenceCasa);
    Assertions.assertNotNull(referenceCarro);

    final Queue<String> problems = new ConcurrentLinkedQueue<>();
    final CountDownLatch start = new CountDownLatch(1);
    final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      for (int t = 0; t < THREADS; t++) {
        executor.submit(() -> {
          try {
            start.await();
            for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
              final String[] casa = dictionary.getTags("casa");
              final String[] carro = dictionary.getTags("carro");
              if (!Arrays.equals(referenceCasa, casa)) {
                problems.add("casa tags drifted under contention");
              }
              if (!Arrays.equals(referenceCarro, carro)) {
                problems.add("carro tags drifted under contention");
              }
            }
          } catch (Exception e) {
            problems.add("Unexpected exception: " + e);
          }
        });
      }
      start.countDown();
      executor.shutdown();
      Assertions.assertTrue(executor.awaitTermination(2, TimeUnit.MINUTES),
          "Concurrent workers did not finish in time");
    } finally {
      executor.shutdownNow();
    }
    Assertions.assertTrue(problems.isEmpty(), () -> "Thread-safety violations: " + problems);
  }
}
