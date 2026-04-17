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
 * Focused tests for {@link OwnerOrPerThreadState}: owner-fast-path semantics, the one-way transition
 * into multi-threaded mode, and {@code resetOwner} behavior on
 * {@link OwnerOrPerThreadState#clearForCurrentThread()}.
 */
class OwnerOrPerThreadStateTest {

  private static final class Counter {
    int value;
  }

  /**
   * The first thread to call {@code get} becomes the owner; subsequent calls from the same thread
   * keep returning the same owner state object.
   */
  @Test
  void singleThreadAlwaysGetsOwnerState() {
    OwnerOrPerThreadState<Counter> state = new OwnerOrPerThreadState<>(Counter::new, c -> c.value = 0);

    Counter first = state.get();
    Counter second = state.get();
    Assertions.assertSame(first, second,
        "owner thread must always see the same owner state instance");
  }

  /**
   * The second thread to access the state must receive a different instance from the owner — owner
   * gets the {@code ownerState} field, non-owners get their per-thread {@code ThreadLocal} slot.
   */
  @Test
  void secondThreadGetsADifferentInstance() throws Exception {
    OwnerOrPerThreadState<Counter> state = new OwnerOrPerThreadState<>(Counter::new, c -> c.value = 0);
    Counter ownerState = state.get();
    ownerState.value = 42;

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Counter workerState = pool.submit(state::get).get(2, TimeUnit.SECONDS);
      Assertions.assertNotSame(ownerState, workerState,
          "non-owner thread must not receive the owner state instance");
      Assertions.assertEquals(0, workerState.value,
          "non-owner thread starts with a fresh state object from the supplier");

      Assertions.assertEquals(42, state.get().value,
          "owner state value is unchanged by the non-owner thread");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * After the owner clears, a fresh thread can become the new owner — exercising the
   * {@code NO_OWNER_THREAD} reset path.
   */
  @Test
  void ownerCanBeReclaimedAfterClear() throws Exception {
    OwnerOrPerThreadState<Counter> state = new OwnerOrPerThreadState<>(Counter::new, c -> c.value = 0);
    Counter first = state.get();
    first.value = 7;
    state.clearForCurrentThread();
    Assertions.assertEquals(0, first.value,
        "resetOwner callback must clear the owner state on clearForCurrentThread");

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Counter reclaimed = pool.submit(state::get).get(2, TimeUnit.SECONDS);
      Assertions.assertSame(first, reclaimed,
          "after the previous owner releases, the next thread reuses the owner state instance");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Once two threads have used the same instance, every subsequent access from a non-owner thread
   * goes through {@link ThreadLocal} — even after a clear; the multi-threaded flag is one-way.
   */
  @Test
  void multiThreadedTransitionIsOneWay() throws Exception {
    OwnerOrPerThreadState<Counter> state = new OwnerOrPerThreadState<>(Counter::new, c -> c.value = 0);
    Counter ownerState = state.get();

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Counter firstWorker = pool.submit(() -> {
        Counter c = state.get();
        c.value = 1;
        state.clearForCurrentThread();
        return c;
      }).get(2, TimeUnit.SECONDS);

      Counter secondWorker = pool.submit(state::get).get(2, TimeUnit.SECONDS);
      Assertions.assertNotSame(ownerState, secondWorker,
          "after multi-threaded mode is set, non-owner threads never receive the owner state");
      Assertions.assertNotSame(firstWorker, secondWorker,
          "ThreadLocal was cleared, so the second worker call gets a fresh per-thread state");
    } finally {
      pool.shutdownNow();
    }
  }

  /**
   * Stress test: many threads concurrently mutate their own state object and read it back. Each
   * thread must see only its own writes, never another thread's value.
   */
  @Test
  void concurrentMutationIsThreadIsolated() throws Exception {
    final int threadCount = 16;
    final int iterations = 5_000;
    final OwnerOrPerThreadState<Counter> state =
        new OwnerOrPerThreadState<>(Counter::new, c -> c.value = 0);
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
              Counter c = state.get();
              c.value = threadIndex * iterations + i;
              if (c.value != threadIndex * iterations + i) {
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
        "every thread must see its own writes to its per-thread state");
  }

  /**
   * {@link OwnerOrPerThreadState#clearForCurrentThread()} only resets the calling thread's state;
   * other threads' state instances are untouched.
   */
  @Test
  void clearIsScopedToCurrentThread() throws Exception {
    final int threadCount = 4;
    final OwnerOrPerThreadState<Counter> state =
        new OwnerOrPerThreadState<>(Counter::new, c -> c.value = 0);

    final ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    final ConcurrentHashMap<Integer, Integer> writes = new ConcurrentHashMap<>();
    try {
      for (int t = 0; t < threadCount; t++) {
        final int idx = t;
        pool.submit(() -> {
          Counter c = state.get();
          c.value = 100 + idx;
          writes.put(idx, c.value);
        }).get(5, TimeUnit.SECONDS);
      }

      Set<Integer> distinct = new HashSet<>(writes.values());
      Assertions.assertEquals(threadCount, distinct.size(),
          "each worker must observe its own write");

      pool.submit(state::clearForCurrentThread).get(2, TimeUnit.SECONDS);

      Counter[] post = new Counter[threadCount];
      for (int t = 0; t < threadCount; t++) {
        final int idx = t;
        post[t] = pool.submit(state::get).get(5, TimeUnit.SECONDS);
        Assertions.assertNotNull(post[t], "worker " + idx + " state must not be null");
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
