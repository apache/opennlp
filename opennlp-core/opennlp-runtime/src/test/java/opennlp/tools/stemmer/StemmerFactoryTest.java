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
import java.util.concurrent.atomic.AtomicInteger;

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
  @Test
  void factoryPassesRepeatThrough() {
    // "internationalization" stems differently at repeat 1 and 2, witnessing the pass-through.
    final Stemmer once = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH, 1).newStemmer();
    final Stemmer twice = new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH, 2).newStemmer();
    Assertions.assertEquals("internation", once.stem("internationalization").toString());
    Assertions.assertEquals("intern", twice.stem("internationalization").toString());
    Assertions.assertEquals(
        new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH, 2).stem("internationalization").toString(),
        twice.stem("internationalization").toString());
  }

  @Test
  void factoryRejectsNullAlgorithmWithIllegalArgument() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SnowballStemmerFactory(null, 1));
  }

  @Test
  void snowballConstructorValidatesLikeTheFactory() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SnowballStemmer(null, 1));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH, 0));
  }

  @Test
  void sharingStemmerForwardsStemAllToMultiOutputDelegate() {
    // A multi-output delegate must keep its full result list through the wrapper.
    final StemmerFactory multiOutput = () -> new Stemmer() {
      @Override
      public CharSequence stem(CharSequence word) {
        return word.toString() + "-a";
      }

      @Override
      public List<CharSequence> stemAll(CharSequence word) {
        return List.of(word.toString() + "-a", word.toString() + "-b");
      }
    };
    final SharingStemmer sharing = new SharingStemmer(multiOutput);
    Assertions.assertEquals(2, sharing.stemAll("run").size());
    Assertions.assertEquals("run-b", sharing.stemAll("run").get(1).toString());
  }

  @Test
  void clearThreadLocalStateEmptiesTheOwnerCache() {
    // On the owning thread the state object is retained and reset: the cache must be empty
    // afterwards, observable as a fresh delegate call for a previously cached word.
    final List<String> delegateCalls = new ArrayList<>();
    final StemmerFactory counting = () -> word -> {
      delegateCalls.add(word.toString());
      return word.toString() + "-s";
    };
    final CachingStemmer caching = new CachingStemmer(counting);
    caching.stem("dog");
    caching.stem("dog");
    Assertions.assertEquals(1, delegateCalls.size(), "second lookup is served from the cache");
    caching.clearThreadLocalState();
    Assertions.assertEquals("dog-s", caching.stem("dog").toString());
    Assertions.assertEquals(2, delegateCalls.size(), "the clear emptied the cache");
  }

  @Test
  void clearThreadLocalStateReleasesAWorkerThreadsDelegate() throws Exception {
    // On a non-owner thread the per-thread delegate is removed and rebuilt on next use.
    final List<Integer> built = new ArrayList<>();
    final StemmerFactory counting = () -> {
      synchronized (built) {
        built.add(built.size());
      }
      return word -> word.toString() + "-s";
    };
    final SharingStemmer sharing = new SharingStemmer(counting);
    sharing.stem("owner");
    final int builtForOwner = built.size();
    final Thread worker = new Thread(() -> {
      sharing.stem("dog");
      sharing.stem("cat");
      final int beforeClear = built.size();
      sharing.clearThreadLocalState();
      sharing.stem("fish");
      Assertions.assertEquals(beforeClear + 1, built.size(),
          "a fresh delegate is built after the clear");
    });
    worker.start();
    worker.join(30_000);
    Assertions.assertTrue(built.size() > builtForOwner, "the worker built its own delegate");

    final SnowballStemmer snowball = new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH);
    Assertions.assertEquals("run", snowball.stem("running").toString());
    snowball.clearThreadLocalState();
    Assertions.assertEquals("run", snowball.stem("running").toString());
  }

  @Test
  void nullWordThrowsIllegalArgumentAcrossTheStemmerApi() {
    final SnowballStemmerFactory factory =
        new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SnowballStemmer(SnowballStemmer.ALGORITHM.ENGLISH).stem(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> factory.newStemmer().stem(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new CachingStemmer(factory).stem(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new SharingStemmer(factory).stem(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new PorterStemmer().stem((CharSequence) null));
  }

  @Test
  void hugeCapacityConstructsAndStems() {
    // Pins the overflow-safe table sizing: capacities near Integer.MAX_VALUE are documented
    // as legal ("must be positive") and must not wrap the LinkedHashMap initial capacity.
    final CachingStemmer cached = new CachingStemmer(
        new SnowballStemmerFactory(SnowballStemmer.ALGORITHM.ENGLISH), Integer.MAX_VALUE);
    Assertions.assertEquals("run", cached.stem("running").toString());
  }

  @Test
  void clearCacheForcesRestemmingOnTheCallingThread() {
    final AtomicInteger delegateCalls = new AtomicInteger();
    final StemmerFactory counting = () -> word -> {
      delegateCalls.incrementAndGet();
      return word.toString();
    };
    final CachingStemmer cached = new CachingStemmer(counting);

    cached.stem("running");
    cached.stem("running");
    Assertions.assertEquals(1, delegateCalls.get(), "the repeat is served from the cache");

    cached.clearCache();
    cached.stem("running");
    Assertions.assertEquals(2, delegateCalls.get(),
        "after clearCache() the word goes through the delegate again");
  }
}
