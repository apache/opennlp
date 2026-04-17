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

package opennlp.tools.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for {@link LastResultOwnerOrThreadLocal}: owner-fast-path semantics, the one-way
 * transition into multi-threaded mode, and {@link LastResultOwnerOrThreadLocal#clearForCurrentThread()}
 * behavior for both owner and non-owner threads.
 */
class LastResultOwnerOrThreadLocalTest {

  /**
   * The first thread to call {@code set} becomes the owner; subsequent calls from the same thread keep
   * reading and writing the owner slot.
   */
  @Test
  void singleThreadOwnerFastPath() {
    LastResultOwnerOrThreadLocal<String> slot = new LastResultOwnerOrThreadLocal<>();

    Assertions.assertNull(slot.get(), "fresh instance should report no value");

    slot.set("a");
    Assertions.assertEquals("a", slot.get());

    slot.set("b");
    Assertions.assertEquals("b", slot.get(), "owner thread should overwrite its own value");
  }

  /**
   * A second thread must not see the first thread's value via the owner slot. Once it touches the
   * instance, the slot transitions into multi-threaded mode and the second thread starts with an
   * empty {@link ThreadLocal}.
   */
  @Test
  void secondThreadGetsItsOwnSlot() throws Exception {
    LastResultOwnerOrThreadLocal<String> slot = new LastResultOwnerOrThreadLocal<>();
    slot.set("owner-value");

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      String secondThreadInitial = pool.submit(slot::get).get(2, TimeUnit.SECONDS);
      Assertions.assertNull(secondThreadInitial,
          "non-owner thread must not see the owner's value via the owner slot");

      pool.submit(() -> slot.set("non-owner-value")).get(2, TimeUnit.SECONDS);
      String secondThreadAfterSet = pool.submit(slot::get).get(2, TimeUnit.SECONDS);
      Assertions.assertEquals("non-owner-value", secondThreadAfterSet,
          "non-owner thread reads back its own ThreadLocal value");

      Assertions.assertEquals("owner-value", slot.get(),
          "owner thread continues to read the owner slot after the mode transition");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * After {@link LastResultOwnerOrThreadLocal#clearForCurrentThread()} on the owner, a fresh thread can
   * become the new owner — exercising the {@code NO_OWNER_THREAD} reset path.
   */
  @Test
  void ownerCanBeReclaimedAfterClear() throws Exception {
    LastResultOwnerOrThreadLocal<String> slot = new LastResultOwnerOrThreadLocal<>();
    slot.set("first");
    slot.clearForCurrentThread();
    Assertions.assertNull(slot.get(), "owner reads null after clearing its own slot");

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      pool.submit(() -> slot.set("second")).get(2, TimeUnit.SECONDS);
      String reclaimed = pool.submit(slot::get).get(2, TimeUnit.SECONDS);
      Assertions.assertEquals("second", reclaimed,
          "a fresh thread reclaims the owner slot after the previous owner cleared");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Once the slot has been observed by two threads, it stays in multi-threaded mode permanently — even
   * after every non-owner clears its {@link ThreadLocal} entry.
   */
  @Test
  void multiThreadedTransitionIsOneWay() throws Exception {
    LastResultOwnerOrThreadLocal<String> slot = new LastResultOwnerOrThreadLocal<>();
    slot.set("owner");

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      pool.submit(() -> {
        slot.set("non-owner");
        slot.clearForCurrentThread();
      }).get(2, TimeUnit.SECONDS);

      String secondVisit = pool.submit(slot::get).get(2, TimeUnit.SECONDS);
      Assertions.assertNull(secondVisit,
          "after a clear, the non-owner thread starts fresh; multi-threaded mode persists");

      Assertions.assertEquals("owner", slot.get(),
          "owner slot survives non-owner activity");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Stress test: many threads concurrently {@code set} a unique value and immediately {@code get} it
   * back. Each thread must read its own write — never another thread's value, never null.
   */
  @Test
  void concurrentSetGetIsThreadSafe() throws Exception {
    final int threadCount = 16;
    final int iterations = 2_000;
    final LastResultOwnerOrThreadLocal<Integer> slot = new LastResultOwnerOrThreadLocal<>();
    final CyclicBarrier startBarrier = new CyclicBarrier(threadCount);
    final AtomicInteger errors = new AtomicInteger();
    final ExecutorService pool = Executors.newFixedThreadPool(threadCount);

    try {
      Future<?>[] futures = new Future[threadCount];
      for (int t = 0; t < threadCount; t++) {
        final int threadIndex = t;
        futures[t] = pool.submit(() -> {
          try {
            startBarrier.await(5, TimeUnit.SECONDS);
            for (int i = 0; i < iterations; i++) {
              int value = threadIndex * iterations + i;
              slot.set(value);
              Integer read = slot.get();
              if (read == null || read != value) {
                errors.incrementAndGet();
              }
            }
          } catch (Exception e) {
            errors.incrementAndGet();
          }
        });
      }
      for (Future<?> f : futures) {
        f.get(15, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    Assertions.assertEquals(0, errors.get(),
        "every thread must read back the value it just wrote");
  }

  /**
   * After {@link LastResultOwnerOrThreadLocal#clearForCurrentThread()}, the cleared thread reports
   * {@code null} — both for the owner and for non-owner threads.
   */
  @Test
  void clearIsScopedToCurrentThread() throws Exception {
    final int threadCount = 4;
    final LastResultOwnerOrThreadLocal<String> slot = new LastResultOwnerOrThreadLocal<>();
    slot.set("main");

    final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    final ConcurrentHashMap<Integer, String> seen = new ConcurrentHashMap<>();
    try {
      for (int t = 0; t < threadCount; t++) {
        final int idx = t;
        pool.submit(() -> {
          slot.set("worker-" + idx);
          seen.put(idx, slot.get());
        }).get(5, TimeUnit.SECONDS);
      }

      Set<String> values = new HashSet<>(seen.values());
      Assertions.assertEquals(threadCount, values.size(),
          "each worker must see its own value");

      pool.submit(slot::clearForCurrentThread).get(2, TimeUnit.SECONDS);
      Assertions.assertEquals("main", slot.get(),
          "clearing on a worker thread must not affect the owner");
    } finally {
      pool.shutdownNow();
    }
  }
}
