/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmerFactory;

class StemmerFactoryTest {

  @Test
  void snowballFactoryMatchesDirectStemmer() {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    Stemmer direct = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    Assertions.assertEquals(direct.stem("running"), factory.newStemmer().stem("running"));
    Assertions.assertEquals(direct.stem("running"), factory.stem("running"));
  }

  @Test
  void porterFactoryMatchesDirectStemmer() {
    StemmerFactory factory = new PorterStemmerFactory();
    Stemmer direct = new PorterStemmer();
    Assertions.assertEquals(direct.stem("running"), factory.newStemmer().stem("running"));
  }

  @Test
  void stemAllDefaultReturnsSingleForm() {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    List<CharSequence> forms = factory.newStemmer().stemAll("running");
    Assertions.assertEquals(1, forms.size());
    Assertions.assertEquals("run", forms.getFirst());
  }

  @Test
  void factoryRejectsInvalidRepeat() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH, 0));
  }

  @Test
  void sharingStemmerMatchesSingleThreadReference() throws Exception {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    Stemmer reference = factory.newStemmer();
    SharingStemmer shared = new SharingStemmer(factory);

    Assertions.assertEquals(reference.stem("running"), shared.stem("running"));
    Assertions.assertEquals(reference.stem("this"), shared.stem("this"));
  }

  @Test
  void sharingStemmerIsSafeUnderConcurrentUse() throws Exception {
    StemmerFactory factory = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    SharingStemmer shared = new SharingStemmer(factory);
    CharSequence expectedRunning = factory.newStemmer().stem("running");
    CharSequence expectedDeclining = factory.newStemmer().stem("declining");

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      Future<?>[] tasks = new Future<?>[64];
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 200; n++) {
            Assertions.assertEquals(expectedRunning, shared.stem("running"));
            Assertions.assertEquals(expectedDeclining, shared.stem("declining"));
          }
        });
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void snowballStemmerIsSafeUnderConcurrentUse() throws Exception {
    Stemmer shared = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    CharSequence expectedRunning = shared.stem("running");
    CharSequence expectedDeclining = shared.stem("declining");

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      Future<?>[] tasks = new Future<?>[64];
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 200; n++) {
            Assertions.assertEquals(expectedRunning, shared.stem("running"));
            Assertions.assertEquals(expectedDeclining, shared.stem("declining"));
          }
        });
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void sharingStemmerRejectsNullFactory() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new SharingStemmer(null));
  }

  @ParameterizedTest
  @EnumSource(SnowballStemmer.ALGORITHM.class)
  void everyAlgorithmIsSafeUnderConcurrentUse(SnowballStemmer.ALGORITHM algorithm)
      throws Exception {
    // Words across scripts; stemming any input is deterministic, so a mismatch under
    // concurrency signals corrupted shared state, regardless of the input language.
    List<String> words = List.of("running", "declining", "ab'yle", "prévoyant",
        "επιστροφή", "яблоками", "استفتياكما", "skrøbeligheder");
    Stemmer reference = new SnowballStemmer(algorithm);
    List<String> expected = new ArrayList<>(words.size());
    for (String word : words) {
      expected.add(reference.stem(word).toString());
    }

    Stemmer shared = new SnowballStemmer(algorithm);
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      Future<?>[] tasks = new Future<?>[16];
      for (int i = 0; i < tasks.length; i++) {
        final int offset = i;
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 50; n++) {
            for (int w = 0; w < words.size(); w++) {
              int idx = (w + offset) % words.size();
              Assertions.assertEquals(expected.get(idx), shared.stem(words.get(idx)).toString());
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
  void ownerThreadCanStemConcurrentlyWithOtherThreads() throws Exception {
    // The calling thread claims the owner fast path first; other threads must transition the
    // stemmer to per-thread state while the owner keeps stemming.
    Stemmer shared = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    CharSequence expectedRunning = shared.stem("running");
    CharSequence expectedDeclining = shared.stem("declining");

    CountDownLatch started = new CountDownLatch(4);
    try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
      Future<?>[] tasks = new Future<?>[4];
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = pool.submit(() -> {
          started.countDown();
          for (int n = 0; n < 500; n++) {
            Assertions.assertEquals(expectedRunning, shared.stem("running"));
            Assertions.assertEquals(expectedDeclining, shared.stem("declining"));
          }
        });
      }
      Assertions.assertTrue(started.await(10, TimeUnit.SECONDS));
      // Owner thread stems concurrently with the pool threads.
      for (int n = 0; n < 500; n++) {
        Assertions.assertEquals(expectedRunning, shared.stem("running"));
        Assertions.assertEquals(expectedDeclining, shared.stem("declining"));
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void snowballStemmerIsSafeOnVirtualThreads() throws Exception {
    // Every task runs on a fresh virtual thread, exercising per-thread state creation on each
    // access rather than reuse from a fixed pool.
    Stemmer shared = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    CharSequence expected = shared.stem("running");

    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<?>> tasks = new ArrayList<>(256);
      for (int i = 0; i < 256; i++) {
        tasks.add(pool.submit(() -> {
          for (int n = 0; n < 20; n++) {
            Assertions.assertEquals(expected, shared.stem("running"));
          }
        }));
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void sharingPorterStemmerIsSafeUnderConcurrentUse() throws Exception {
    // PorterStemmer is the remaining stateful engine; SharingStemmer must isolate it per thread.
    Stemmer reference = new PorterStemmer();
    CharSequence expectedRunning = reference.stem("running");
    CharSequence expectedConspiracies = reference.stem("conspiracies");
    Stemmer shared = new SharingStemmer(new PorterStemmerFactory());

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      Future<?>[] tasks = new Future<?>[64];
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = pool.submit(() -> {
          for (int n = 0; n < 200; n++) {
            Assertions.assertEquals(expectedRunning, shared.stem("running"));
            Assertions.assertEquals(expectedConspiracies, shared.stem("conspiracies"));
          }
        });
      }
      for (Future<?> task : tasks) {
        task.get(30, TimeUnit.SECONDS);
      }
    }
  }
}
