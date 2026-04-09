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

package opennlp.tools.postag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.Cache;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

/**
 * A configurable {@link POSContextGenerator context generator} for a {@link POSTagger}.
 * This implementation makes use of {@link AdaptiveFeatureGenerator}.
 * <p>
 * The per-sentence context cache is maintained per-thread via {@link ThreadLocal},
 * making this class safe for concurrent use.
 * <p>
 * <b>Note:</b> In container environments with classloader isolation (e.g. Jakarta EE),
 * {@link ThreadLocal} state may pin the classloader. Ensure instances do not outlive
 * the application's lifecycle, or call {@link ThreadLocal#remove()} on pooled threads.
 *
 * @see POSTagger
 * @see POSTaggerME
 * @see DefaultPOSContextGenerator
 */
@ThreadSafe
public class ConfigurablePOSContextGenerator implements POSContextGenerator {

  private final AdaptiveFeatureGenerator featureGenerator;
  private final int cacheSize;

  private final ThreadLocal<CacheState> threadState;

  private static final class CacheState {
    private Object wordsKey;
    private final Cache<String, String[]> cache;

    CacheState(int size) {
      this.cache = new Cache<>(size);
    }
  }

  /**
   * Initializes a {@link ConfigurablePOSContextGenerator} instance.
   * A cache size of {@code 0} will be used as default.
   *
   * @param featureGenerator The {@link AdaptiveFeatureGenerator} to be used.
   */
  public ConfigurablePOSContextGenerator(AdaptiveFeatureGenerator featureGenerator) {
    this(0, featureGenerator);
  }

  /**
   * Initializes a {@link ConfigurablePOSContextGenerator} instance with an optional
   * per-thread context cache.
   *
   * @param cacheSize The size of the per-thread context cache.
   *                  Use {@code 0} to disable caching.
   * @param featureGenerator The {@link AdaptiveFeatureGenerator} to be used.
   */
  public ConfigurablePOSContextGenerator(int cacheSize, AdaptiveFeatureGenerator featureGenerator) {
    this.featureGenerator = Objects.requireNonNull(featureGenerator,
        "featureGenerator must not be null");
    this.cacheSize = cacheSize;
    this.threadState = cacheSize > 0
        ? ThreadLocal.withInitial(() -> new CacheState(cacheSize))
        : null;
  }

  private String[] createContextFeatures(int index, String[] tokens, String[] tags) {
    List<String> feats = new ArrayList<>();
    featureGenerator.createFeatures(feats, tokens, index, tags);
    return feats.toArray(new String[0]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getContext(int index, String[] tokens, String[] tags,
      Object[] additionalContext) {

    String tagprev = null;
    String tagprevprev = null;

    if (index - 1 >= 0) {
      tagprev =  tags[index - 1];

      if (index - 2 >= 0) {
        tagprevprev = tags[index - 2];
      }
    }

    if (threadState != null) {
      CacheState state = threadState.get();
      String cacheKey = index + tagprev + tagprevprev;
      if (state.wordsKey == tokens) {
        String[] cachedContexts = state.cache.get(cacheKey);
        if (cachedContexts != null) {
          return cachedContexts;
        }
      } else {
        state.cache.clear();
        state.wordsKey = tokens;
      }

      String[] contexts = createContextFeatures(index, tokens, tags);
      state.cache.put(cacheKey, contexts);
      return contexts;
    } else {
      return createContextFeatures(index, tokens, tags);
    }
  }
}
