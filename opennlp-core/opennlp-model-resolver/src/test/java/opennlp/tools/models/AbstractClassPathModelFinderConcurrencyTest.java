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

package opennlp.tools.models;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link AbstractClassPathModelFinder#findModels(boolean)} can be called from several
 * threads.
 */
class AbstractClassPathModelFinderConcurrencyTest {

  private static final int THREADS = 8;

  private static final long TIMEOUT_SECONDS = 10;

  private static final List<URI> MODELS = List.of(
      URI.create("jar:file:/repo/opennlp-models-sentdetect-en.jar!/en-sent.bin"),
      URI.create("jar:file:/repo/opennlp-models-tokenizer-en.jar!/en-token.bin"),
      URI.create("jar:file:/repo/opennlp-models-pos-en.jar!/en-pos.bin"));

  private static final URI OTHER_MODEL =
      URI.create("jar:file:/repo/opennlp-models-langdetect.jar!/langdetect.bin");

  /**
   * Concurrent first calls scan once, and every caller gets the complete set, although all of
   * them call while the first scan is still running.
   */
  @Test
  void testConcurrentFirstCallsScanOnceAndReturnAllModels() throws InterruptedException {
    final CountDownLatch release = new CountDownLatch(1);
    final ScanCountingFinder finder = new ScanCountingFinder(MODELS, release);
    final List<Set<ClassPathModelEntry>> results = new ArrayList<>();
    final List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      final Thread thread = new Thread(() -> {
        final Set<ClassPathModelEntry> found = finder.findModels(false);
        synchronized (results) {
          results.add(found);
        }
      });
      threads.add(thread);
      thread.start();
    }

    awaitAllWaiting(threads);
    release.countDown();
    for (Thread thread : threads) {
      thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    }

    assertEquals(1, finder.scans());
    assertEquals(THREADS, results.size());
    for (Set<ClassPathModelEntry> found : results) {
      assertEquals(entries(MODELS), found);
    }
  }

  /** The returned set is not the cache itself, so callers cannot change it. */
  @Test
  void testReturnedModelsAreUnmodifiable() {
    final ScanCountingFinder finder = new ScanCountingFinder(MODELS, released());
    final Set<ClassPathModelEntry> found = finder.findModels(false);
    final ClassPathModelEntry other = new ClassPathModelEntry(OTHER_MODEL, Optional.empty());
    assertThrows(UnsupportedOperationException.class, () -> found.add(other));
  }

  /** A reload scans again and replaces the cache; a set returned earlier keeps its content. */
  @Test
  void testReloadReplacesTheCacheAndKeepsEarlierResults() {
    final ScanCountingFinder finder = new ScanCountingFinder(MODELS, released());
    final Set<ClassPathModelEntry> first = finder.findModels(false);
    assertSame(first, finder.findModels(false));

    finder.setModels(List.of(OTHER_MODEL));
    final Set<ClassPathModelEntry> reloaded = finder.findModels(true);

    assertEquals(2, finder.scans());
    assertEquals(entries(MODELS), first);
    assertEquals(entries(List.of(OTHER_MODEL)), reloaded);
    assertSame(reloaded, finder.findModels(false));
  }

  /**
   * Waits until every thread waits, either for the scan to be released or for the scan lock.
   *
   * @param threads The threads that call the finder.
   * @throws InterruptedException Thrown if the test thread is interrupted.
   */
  private static void awaitAllWaiting(List<Thread> threads) throws InterruptedException {
    final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    for (Thread thread : threads) {
      while (!isWaiting(thread.getState())) {
        assertTrue(System.nanoTime() < deadline, thread.getName() + " did not reach the finder");
        Thread.sleep(1);
      }
    }
  }

  /**
   * {@return whether a thread in {@code state} waits for a latch or a lock}
   *
   * @param state The state of a thread.
   */
  private static boolean isWaiting(Thread.State state) {
    return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING
        || state == Thread.State.BLOCKED;
  }

  /** {@return a latch that is already released} */
  private static CountDownLatch released() {
    return new CountDownLatch(0);
  }

  /**
   * {@return the entries the finder creates for {@code models}, which have no properties}
   *
   * @param models The model {@link URI URIs}.
   */
  private static Set<ClassPathModelEntry> entries(List<URI> models) {
    final Set<ClassPathModelEntry> entries = new HashSet<>();
    for (URI model : models) {
      entries.add(new ClassPathModelEntry(model, Optional.empty()));
    }
    return entries;
  }

  /**
   * A finder that counts its scans and holds each scan until a latch is released.
   */
  private static final class ScanCountingFinder extends AbstractClassPathModelFinder {

    private final AtomicInteger scans = new AtomicInteger();
    private final CountDownLatch release;
    private volatile List<URI> models;

    /**
     * @param models The model {@link URI URIs} a scan finds.
     * @param release The latch every scan waits for.
     */
    ScanCountingFinder(List<URI> models, CountDownLatch release) {
      this.models = models;
      this.release = release;
    }

    /**
     * @param models The model {@link URI URIs} the next scan finds.
     */
    void setModels(List<URI> models) {
      this.models = models;
    }

    /** {@return the number of scans so far} */
    int scans() {
      return scans.get();
    }

    /** {@inheritDoc} */
    @Override
    protected Object getContext() {
      return null;
    }

    /** {@inheritDoc} */
    @Override
    protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
      if (!"*.bin".equals(wildcardPattern)) {
        return List.of();
      }
      scans.incrementAndGet();
      try {
        if (!release.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          throw new IllegalStateException("the scan was not released");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      return models;
    }
  }
}
