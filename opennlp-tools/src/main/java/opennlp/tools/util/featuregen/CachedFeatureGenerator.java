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
import java.util.Arrays;
import java.util.List;

import opennlp.tools.util.Cache;

/**
 * Caches features of the aggregated {@link AdaptiveFeatureGenerator generators}.
 */
public class CachedFeatureGenerator implements AdaptiveFeatureGenerator {

  private final AdaptiveFeatureGenerator generator;

  private final Cache<Integer, Cache<Integer, List<String>>> contextCaches;
  private final int cacheSize;

  private long numberOfCacheHits;
  private long numberOfCacheMisses;

  public CachedFeatureGenerator(AdaptiveFeatureGenerator generator, int cacheSize) {
    this.generator = generator;
    this.contextCaches = new Cache<>(cacheSize);
    this.cacheSize = cacheSize;
  }

  public CachedFeatureGenerator(AdaptiveFeatureGenerator generator) {
    this(generator, 100);
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
                             String[] previousOutcomes) {

    final int tokenHash = Arrays.hashCode(tokens);

    final Cache<Integer, List<String>> contextCache = contextCaches.computeIfAbsent(tokenHash,
        k -> new Cache<>(cacheSize));
    List<String> cacheFeatures = contextCache.get(index);

    if (cacheFeatures != null) {
      numberOfCacheHits++;
      features.addAll(cacheFeatures);
    } else {
      numberOfCacheMisses++;
      cacheFeatures = new ArrayList<>();
      generator.createFeatures(cacheFeatures, tokens, index, previousOutcomes);
      contextCache.put(index, cacheFeatures);
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
   * @return Retrieves the number of times a cache hit occurred.
   */
  public long getNumberOfCacheHits() {
    return numberOfCacheHits;
  }

  /**
   * @return Retrieves the number of times a cache miss occurred.
   */
  public long getNumberOfCacheMisses() {
    return numberOfCacheMisses;
  }

  public void clearCache() {
    this.contextCaches.clear();
    this.numberOfCacheMisses = 0;
    this.numberOfCacheHits = 0;
  }

  @Override
  public String toString() {
    return super.toString() + ": hits=" + numberOfCacheHits
        + " misses=" + numberOfCacheMisses + " hit%" + (numberOfCacheHits > 0 ?
        (double) numberOfCacheHits / (numberOfCacheMisses + numberOfCacheHits) : 0);
  }

  public AdaptiveFeatureGenerator getCachedFeatureGenerator() {
    return generator;
  }
}
