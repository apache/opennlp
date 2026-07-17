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
package opennlp.wordnet;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the immutable-after-load contract: one loaded lexicon serves many threads issuing
 * concurrent lookups, and every thread observes exactly the single-threaded results.
 */
public class LexiconConcurrencyTest {

  private static final int THREADS = 8;
  private static final int ITERATIONS = 500;

  @Test
  void testConcurrentLookupsSeeConsistentResults() throws InterruptedException {
    final LexicalKnowledgeBase lexicon = WndbReaderTest.fixture();
    final CountDownLatch start = new CountDownLatch(1);
    final CountDownLatch done = new CountDownLatch(THREADS);
    final Queue<String> problems = new ConcurrentLinkedQueue<>();
    for (int t = 0; t < THREADS; t++) {
      final Thread thread = new Thread(() -> {
        try {
          start.await();
          for (int i = 0; i < ITERATIONS; i++) {
            verifyOnce(lexicon, problems);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          problems.add("Interrupted: " + e);
        } catch (RuntimeException e) {
          problems.add("Unexpected exception: " + e);
        } finally {
          done.countDown();
        }
      });
      thread.setDaemon(true);
      thread.start();
    }
    start.countDown();
    assertTrue(done.await(60, TimeUnit.SECONDS), "Worker threads must finish in time");
    assertEquals(List.of(), List.copyOf(problems));
  }

  private static void verifyOnce(LexicalKnowledgeBase lexicon, Queue<String> problems) {
    if (!"wndb-00001075-n".equals(lexicon.lookup("dog", WordNetPOS.NOUN).get(0).id())) {
      problems.add("Wrong dog lookup");
    }
    if (lexicon.lookup("run", WordNetPOS.NOUN).size() != 2) {
      problems.add("Wrong run sense count");
    }
    if (!List.of("wndb-00001160-n")
        .equals(lexicon.related("wndb-00001075-n", WordNetRelation.HYPERNYM))) {
      problems.add("Wrong dog hypernym");
    }
    if (lexicon.contains("zebra", WordNetPOS.NOUN)) {
      problems.add("Phantom zebra");
    }
    if (!lexicon.contains("walk", WordNetPOS.VERB)) {
      problems.add("Missing walk verb");
    }
  }
}
