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

package opennlp.tools.stemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmerFactory;

class CachingStemmerTest {

  private static final SnowballStemmerFactory ENGLISH =
      new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);

  @Test
  void cachedResultsMatchUncachedResults() {
    Stemmer reference = ENGLISH.newStemmer();
    CachingStemmer cached = new CachingStemmer(ENGLISH);

    List<String> words = List.of("running", "declining", "conspiracies", "this",
        "running", "declining", "ab'yle", "running");
    for (String word : words) {
      Assertions.assertEquals(reference.stem(word).toString(), cached.stem(word).toString(),
          "mismatch for '" + word + "'");
    }
  }

  @Test
  void repeatedWordsHitTheCache() {
    AtomicInteger delegateCalls = new AtomicInteger();
    StemmerFactory countingFactory = () -> {
      Stemmer delegate = ENGLISH.newStemmer();
      return word -> {
        delegateCalls.incrementAndGet();
        return delegate.stem(word);
      };
    };

    CachingStemmer cached = new CachingStemmer(countingFactory);
    for (int i = 0; i < 100; i++) {
      Assertions.assertEquals("run", cached.stem("running").toString());
    }
    Assertions.assertEquals(1, delegateCalls.get());
  }

  @Test
  void evictionKeepsResultsCorrect() {
    AtomicInteger delegateCalls = new AtomicInteger();
    StemmerFactory countingFactory = () -> {
      Stemmer delegate = ENGLISH.newStemmer();
      return word -> {
        delegateCalls.incrementAndGet();
        return delegate.stem(word);
      };
    };

    // Capacity 2 with a 3-word cycle: every access evicts the word needed two steps later.
    CachingStemmer cached = new CachingStemmer(countingFactory, 2);
    Stemmer reference = ENGLISH.newStemmer();
    List<String> words = List.of("running", "declining", "conspiracies");
    for (int round = 0; round < 5; round++) {
      for (String word : words) {
        Assertions.assertEquals(reference.stem(word).toString(), cached.stem(word).toString());
      }
    }
    Assertions.assertTrue(delegateCalls.get() > 3,
        "expected evictions to force repeated delegate calls, got " + delegateCalls.get());
  }

  @Test
  void stemAllBypassesTheCache() {
    CachingStemmer cached = new CachingStemmer(ENGLISH);
    List<CharSequence> forms = cached.stemAll("running");
    Assertions.assertEquals(1, forms.size());
    Assertions.assertEquals("run", forms.getFirst().toString());
  }

  @Test
  void isSafeUnderConcurrentUse() throws Exception {
    Stemmer reference = ENGLISH.newStemmer();
    List<String> words = List.of("running", "declining", "conspiracies", "annotations",
        "photographers", "responsibilities", "this", "querying");
    List<String> expected = new ArrayList<>(words.size());
    for (String word : words) {
      expected.add(reference.stem(word).toString());
    }

    // Capacity below the vocabulary size, so threads continuously evict within their own cache.
    CachingStemmer cached = new CachingStemmer(ENGLISH, 4);
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      Future<?>[] tasks = new Future<?>[32];
      for (int i = 0; i < tasks.length; i++) {
        final int offset = i;
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 200; n++) {
            for (int w = 0; w < words.size(); w++) {
              int idx = (w + offset) % words.size();
              Assertions.assertEquals(expected.get(idx), cached.stem(words.get(idx)).toString());
            }
          }
        });
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void rejectsInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new CachingStemmer(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new CachingStemmer(ENGLISH, 0));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new CachingStemmer(ENGLISH, -1));
  }
}
