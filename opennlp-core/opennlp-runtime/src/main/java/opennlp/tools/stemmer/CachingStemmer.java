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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.OwnerOrPerThreadState;

/**
 * A {@link Stemmer} that memoizes word-to-stem mappings in a bounded per-thread LRU cache.
 *
 * <p>Natural language is Zipf-distributed: a small vocabulary accounts for most tokens, so the
 * same words are stemmed over and over. Wrapping a stemmer in a {@code CachingStemmer} turns
 * those repeats into a hash lookup instead of a full stemming pass.</p>
 *
 * <p>Each thread gets its own delegate stemmer (minted from the supplied {@link StemmerFactory})
 * and its own cache, following the {@link OwnerOrPerThreadState} pattern of the thread-safe
 * {@code *ME} components. There is no cross-thread sharing at all, so instances are safe to share
 * regardless of whether the factory's stemmers are. Memory is bounded by
 * {@code capacity * averageWordLength} characters per thread that stems.</p>
 *
 * <p>Because the cache is keyed to the thread, the memoization pays off on threads that are
 * reused across many words, such as a fixed platform-thread pool. On a virtual-thread-per-task
 * executor every task starts with an empty cache, so repeats are only served within one task.
 * As with the thread-safe {@code *ME} components, long-running environments such as application
 * containers should call {@link #clearThreadLocalState()} when a pooled thread no longer uses
 * this stemmer; otherwise the thread retains its delegate and cache until the instance is
 * unreachable, which can pin the defining classloader on redeploys.</p>
 *
 * <p>{@link #stemAll(CharSequence)} is forwarded to the delegate uncached, so multi-output
 * engines keep their full result list.</p>
 */
@ThreadSafe
public final class CachingStemmer implements Stemmer {

  /**
   * Covers the high-frequency vocabulary of most corpora while keeping the per-thread footprint
   * small.
   */
  public static final int DEFAULT_CAPACITY = 1024;

  private final OwnerOrPerThreadState<ThreadState> state;

  /**
   * Creates a caching stemmer with the {@linkplain #DEFAULT_CAPACITY default capacity}.
   *
   * @param factory The factory that mints one delegate per thread. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code factory} is {@code null}.
   */
  public CachingStemmer(StemmerFactory factory) {
    this(factory, DEFAULT_CAPACITY);
  }

  /**
   * Creates a caching stemmer.
   *
   * @param factory  The factory that mints one delegate per thread. Must not be {@code null}.
   * @param capacity The maximum number of word-to-stem entries kept per thread; must be positive.
   * @throws IllegalArgumentException if {@code factory} is {@code null} or {@code capacity} is
   *     not positive.
   */
  public CachingStemmer(StemmerFactory factory, int capacity) {
    if (factory == null) {
      throw new IllegalArgumentException("factory must not be null");
    }
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive, got " + capacity);
    }
    this.state = new OwnerOrPerThreadState<>(
        () -> new ThreadState(factory.newStemmer(), capacity),
        threadState -> threadState.cache.clear());
  }

  @Override
  public CharSequence stem(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    final ThreadState ts = state.get();
    final String key = word.toString();
    final String cached = ts.cache.get(key);
    if (cached != null) {
      return cached;
    }
    // toString() detaches the result from any buffer the delegate may reuse internally. The
    // result is deliberately NOT interned: the JVM-wide interner holds strong references
    // forever, which would defeat this class's bounded-memory guarantee on open vocabularies.
    final String stemmed = ts.delegate.stem(key).toString();
    ts.cache.put(key, stemmed);
    return stemmed;
  }

  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    return state.get().delegate.stemAll(word);
  }

  /**
   * Removes this thread's delegate and cache to prevent classloader leaks in container
   * environments. Call when the thread is returned to a pool or the stemmer is no longer needed,
   * mirroring {@code clearThreadLocalState()} on the thread-safe {@code *ME} components.
   */
  public void clearThreadLocalState() {
    state.clearForCurrentThread();
  }

  /**
   * Empties the calling thread's cache while keeping its delegate, forcing every subsequent
   * word through a fresh stemming pass. Only the calling thread's cache is affected. A thread
   * that has not stemmed yet has its state initialized by this call, so invoke it on the
   * thread whose cache should be emptied, not from an unrelated maintenance thread.
   */
  public void clearCache() {
    state.get().cache.clear();
  }

  private static final class ThreadState {

    private final Stemmer delegate;
    private final LinkedHashMap<String, String> cache;

    private ThreadState(Stemmer delegate, int capacity) {
      this.delegate = delegate;
      // Access-ordered so iteration order is least-recently-used first. The initial table size
      // accounts for the 0.75 load factor so a cache filled to capacity never rehashes; the clamp
      // keeps the eager allocation reasonable for very large capacities. Sized in double
      // arithmetic: the int expression overflows to a negative capacity near Integer.MAX_VALUE.
      this.cache = new LinkedHashMap<>((int) Math.min(capacity / 0.75d + 1.0d, 4096.0d),
          0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
          return size() > capacity;
        }
      };
    }
  }
}
