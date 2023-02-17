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

import opennlp.tools.util.Cache;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

/**
 * A configurable {@link POSContextGenerator context generator} for a {@link POSTagger}.
 * This implementation makes use of {@link AdaptiveFeatureGenerator}.
 *
 * @see POSTagger
 * @see POSTaggerME
 * @see DefaultPOSContextGenerator
 */
public class ConfigurablePOSContextGenerator implements POSContextGenerator {

  private Cache<String, String[]> contextsCache;
  private Object wordsKey;

  private final AdaptiveFeatureGenerator featureGenerator;

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
   * Initializes a {@link ConfigurablePOSContextGenerator} instance.
   *
   * @param cacheSize The size of the {@link Cache} to set.
   *                  Must be greater than {@code 0} to have an effect.
   * @param featureGenerator The {@link AdaptiveFeatureGenerator} to be used.
   */
  public ConfigurablePOSContextGenerator(int cacheSize, AdaptiveFeatureGenerator featureGenerator) {
    this.featureGenerator = Objects.requireNonNull(featureGenerator, "featureGenerator must not be null");

    if (cacheSize > 0) {
      contextsCache = new Cache<>(cacheSize);
    }
  }

  /**
   * Returns the context for making a postag decision at the specified token {@code index}
   * given the specified {@code tokens} and previous {@code tags}.
   *
   * @param index The index of the token for which the context is provided.
   * @param tokens The tokens representing a sentence.
   * @param tags The tags assigned to the previous words in the sentence.
   * @param additionalContext The context for additional information.
   *
   * @return The context for making a postag decision at the specified token {@code index}
   *     given the specified {@code tokens} and previous {@code tags}.
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

    String cacheKey = index + tagprev + tagprevprev;
    if (contextsCache != null) {
      if (wordsKey == tokens) {
        String[] cachedContexts = contextsCache.get(cacheKey);
        if (cachedContexts != null) {
          return cachedContexts;
        }
      }
      else {
        contextsCache.clear();
        wordsKey = tokens;
      }
    }

    List<String> e = new ArrayList<>();

    featureGenerator.createFeatures(e, tokens, index, tags);

    String[] contexts = e.toArray(new String[0]);
    if (contextsCache != null) {
      contextsCache.put(cacheKey, contexts);
    }
    return contexts;
  }
}
