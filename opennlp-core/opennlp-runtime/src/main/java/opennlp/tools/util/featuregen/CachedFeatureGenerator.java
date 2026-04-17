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


package opennlp.tools.util.featuregen;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.Cache;

/**
 * Caches features of the aggregated {@link AdaptiveFeatureGenerator generators}.
 * <p>
 * The cache is maintained per-thread via {@link ThreadLocal}, making this class safe for
 * concurrent use from multiple threads. Each thread gets its own independent cache that is
 * cleared when a new sentence (token array) is encountered.
 * <p>
 * <b>Cache key is reference identity, not content.</b> The "is this still the same sentence?" check
 * uses {@code tokens == state.prevTokens}; passing a freshly allocated {@code String[]} with the same
 * contents is treated as a new sentence and triggers a cache miss + clear. Reuse the same {@code tokens}
 * array across calls when you need the cache to hit.
 * <p>
 * <b>Note:</b> In container environments with classloader isolation (e.g. Jakarta EE),
 * {@link ThreadLocal} state may pin the classloader. Ensure instances do not outlive
 * the application's lifecycle, or call {@link ThreadLocal#remove()} on pooled threads.
 *
 * @see Cache
 */
@ThreadSafe
public class CachedFeatureGenerator implements AdaptiveFeatureGenerator {

  /**
   * System property to disable the feature cache globally.
   * Set to {@code "true"} to bypass caching (useful for benchmarking).
   */
  public static final String DISABLE_CACHE_PROPERTY = "opennlp.featuregen.cache.disabled";

  private final AdaptiveFeatureGenerator generator;
  private final boolean cacheEnabled;

  private final ThreadLocal<CacheState> threadState =
      ThreadLocal.withInitial(() -> new CacheState(new Cache<>(100)));

  private static final class CacheState {
    private String[] prevTokens;
    private final Cache<Integer, List<String>> cache;

    CacheState(Cache<Integer, List<String>> cache) {
      this.cache = cache;
    }
  }

  /**
   * @deprecated Use {@link #CachedFeatureGenerator(AdaptiveFeatureGenerator)} instead.
   */
  @Deprecated
  public CachedFeatureGenerator(AdaptiveFeatureGenerator... generators) {
    this.generator = new AggregatedFeatureGenerator(generators);
    this.cacheEnabled = !Boolean.getBoolean(DISABLE_CACHE_PROPERTY);
  }

  public CachedFeatureGenerator(AdaptiveFeatureGenerator generator) {
    this.generator = generator;
    this.cacheEnabled = !Boolean.getBoolean(DISABLE_CACHE_PROPERTY);
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    if (!cacheEnabled) {
      generator.createFeatures(features, tokens, index, previousOutcomes);
    } else {
      CacheState state = threadState.get();
      List<String> cacheFeatures;

      if (tokens == state.prevTokens) {
        cacheFeatures = state.cache.get(index);

        if (cacheFeatures != null) {
          features.addAll(cacheFeatures);
          return;
        }

      } else {
        state.cache.clear();
        state.prevTokens = tokens;
      }

      cacheFeatures = new ArrayList<>();

      generator.createFeatures(cacheFeatures, tokens, index, previousOutcomes);

      state.cache.put(index, cacheFeatures);
      features.addAll(cacheFeatures);
    }
  }

  @Override
  public void updateAdaptiveData(String[] tokens, String[] outcomes) {
    generator.updateAdaptiveData(tokens, outcomes);
  }

  @Override
  public void clearAdaptiveData() {
    generator.clearAdaptiveData();
  }

  /**
   * @return Retrieves the number of cache hits for the current thread.
   * @deprecated Cache statistics are no longer tracked.
   */
  @Deprecated(since = "3.0.0")
  public long getNumberOfCacheHits() {
    throw new UnsupportedOperationException("Cache statistics are no longer tracked.");
  }

  /**
   * @return Retrieves the number of cache misses for the current thread.
   * @deprecated Cache statistics are no longer tracked.
   */
  @Deprecated(since = "3.0.0")
  public long getNumberOfCacheMisses() {
    throw new UnsupportedOperationException("Cache statistics are no longer tracked.");
  }

  public AdaptiveFeatureGenerator getCachedFeatureGenerator() {
    return generator;
  }
}
