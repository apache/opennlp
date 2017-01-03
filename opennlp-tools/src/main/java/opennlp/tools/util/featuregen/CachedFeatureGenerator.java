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

import opennlp.tools.util.Cache;

/**
 * Caches features of the aggregated {@link AdaptiveFeatureGenerator}s.
 */
public class CachedFeatureGenerator implements AdaptiveFeatureGenerator {

  private final AdaptiveFeatureGenerator generator;

  private String[] prevTokens;

  private Cache<Integer, List<String>> contextsCache;

  private long numberOfCacheHits;
  private long numberOfCacheMisses;

  public CachedFeatureGenerator(AdaptiveFeatureGenerator... generators) {
    this.generator = new AggregatedFeatureGenerator(generators);
    contextsCache = new Cache<>(100);
  }

  @SuppressWarnings("unchecked")
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    List<String> cacheFeatures;

    if (tokens == prevTokens) {
      cacheFeatures = contextsCache.get(index);

      if (cacheFeatures != null) {
        numberOfCacheHits++;
        features.addAll(cacheFeatures);
        return;
      }

    } else {
      contextsCache.clear();
      prevTokens = tokens;
    }

    cacheFeatures = new ArrayList<>();

    numberOfCacheMisses++;

    generator.createFeatures(cacheFeatures, tokens, index, previousOutcomes);

    contextsCache.put(index, cacheFeatures);
    features.addAll(cacheFeatures);
  }

  public void updateAdaptiveData(String[] tokens, String[] outcomes) {
    generator.updateAdaptiveData(tokens, outcomes);
  }

  public void clearAdaptiveData() {
    generator.clearAdaptiveData();
  }

  /**
   * Retrieves the number of times a cache hit occurred.
   *
   * @return number of cache hits
   */
  public long getNumberOfCacheHits() {
    return numberOfCacheHits;
  }

  /**
   * Retrieves the number of times a cache miss occurred.
   *
   * @return number of cache misses
   */
  public long getNumberOfCacheMisses() {
    return numberOfCacheMisses;
  }

  @Override
  public String toString() {
    return super.toString() + ": hits=" + numberOfCacheHits
        + " misses=" + numberOfCacheMisses + " hit%" + (numberOfCacheHits > 0 ?
        (double) numberOfCacheHits / (numberOfCacheMisses + numberOfCacheHits) : 0);
  }
}
