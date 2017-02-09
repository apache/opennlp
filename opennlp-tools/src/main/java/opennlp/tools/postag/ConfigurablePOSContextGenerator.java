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
 * A context generator for the POS Tagger.
 */
public class ConfigurablePOSContextGenerator implements POSContextGenerator {

  private Cache<String, String[]> contextsCache;
  private Object wordsKey;

  private final AdaptiveFeatureGenerator featureGenerator;

  /**
   * Initializes the current instance.
   *
   * @param cacheSize
   */
  public ConfigurablePOSContextGenerator(int cacheSize, AdaptiveFeatureGenerator featureGenerator) {
    this.featureGenerator = Objects.requireNonNull(featureGenerator, "featureGenerator must not be null");

    if (cacheSize > 0) {
      contextsCache = new Cache<>(cacheSize);
    }
  }

  /**
   * Initializes the current instance.
   *
   */
  public ConfigurablePOSContextGenerator(AdaptiveFeatureGenerator featureGenerator) {
    this(0, featureGenerator);
  }

  /**
   * Returns the context for making a pos tag decision at the specified token index
   * given the specified tokens and previous tags.
   * @param index The index of the token for which the context is provided.
   * @param tokens The tokens in the sentence.
   * @param tags The tags assigned to the previous words in the sentence.
   * @return The context for making a pos tag decision at the specified token index
   *     given the specified tokens and previous tags.
   */
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

    String[] contexts = e.toArray(new String[e.size()]);
    if (contextsCache != null) {
      contextsCache.put(cacheKey, contexts);
    }
    return contexts;
  }
}
