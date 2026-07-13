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
import java.util.function.Supplier;

import opennlp.tools.commons.ThreadSafe;

/**
 * A {@link Stemmer} that memoizes word-to-stem mappings in a bounded per-thread LRU cache.
 *
 * <p>Each thread gets its own delegate stemmer (minted from the supplied {@link StemmerFactory})
 * and its own cache, so instances are safe to share regardless of whether the factory's stemmers
 * are, and memory is bounded to {@code capacity} entries per thread that stems. Caching pays off
 * on threads reused across many words, such as a fixed platform-thread pool; on a
 * virtual-thread-per-task executor every task starts with an empty cache, so repeats are served
 * only within one task. {@link #stemAll(CharSequence)} is forwarded to the delegate uncached.</p>
 *
 * <p>Long-running environments such as application containers should call
 * {@link #clearThreadLocalState()} when a pooled thread no longer uses this stemmer.</p>
 */
@ThreadSafe
public final class CachingStemmer extends DelegatingStemmer<CachingStemmer.ThreadState> {

  /**
   * Covers the high-frequency vocabulary of most corpora while keeping the per-thread footprint
   * small.
   */
  public static final int DEFAULT_CAPACITY = 1024;

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
    super(threadStateSupplier(factory, capacity), threadState -> threadState.cache.clear());
  }

  /**
   * Validates the constructor arguments eagerly and returns a supplier that mints one delegate and
   * its per-thread cache, so invalid arguments fail at construction rather than on first use.
   *
   * @param factory  The factory that mints one delegate per thread. Must not be {@code null}.
   * @param capacity The maximum number of word-to-stem entries kept per thread; must be positive.
   * @return a supplier of fresh per-thread state.
   * @throws IllegalArgumentException if {@code factory} is {@code null} or {@code capacity} is
   *     not positive.
   */
  private static Supplier<ThreadState> threadStateSupplier(StemmerFactory factory, int capacity) {
    requireFactory(factory);
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive, got " + capacity);
    }
    return () -> new ThreadState(factory.newStemmer(), capacity);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code word} is {@code null}.
   */
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
    // toString() copies the result out of any buffer the delegate reuses.
    final String stemmed = ts.delegate.stem(key).toString();
    ts.cache.put(key, stemmed);
    return stemmed;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException if {@code word} is {@code null}.
   */
  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    return state.get().delegate.stemAll(word);
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

  static final class ThreadState {

    private final Stemmer delegate;
    private final LinkedHashMap<String, String> cache;

    /**
     * Creates the per-thread delegate and its access-ordered LRU cache.
     *
     * @param delegate The stemmer minted for this thread.
     * @param capacity The maximum number of entries retained before eviction.
     */
    private ThreadState(Stemmer delegate, int capacity) {
      this.delegate = delegate;
      // Access-ordered for LRU eviction.
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
