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

import java.util.List;

import opennlp.tools.util.StringUtil;

/**
 * An {@link AdaptiveFeatureGenerator} implementation of a word cluster feature generator.
 * It is based on a pre-defined {@link WordClusterDictionary}.
 *
 * @see AdaptiveFeatureGenerator
 * @see WordClusterDictionary
 */
public class WordClusterFeatureGenerator implements AdaptiveFeatureGenerator {

  private final WordClusterDictionary tokenDictionary;
  private final String resourceName;
  private final boolean lowerCaseDictionary;

  /**
   * Instantiates a {@link WordClusterFeatureGenerator} via a specified
   * {@link WordClusterDictionary}.
   *
   * @param dict The token {@link WordClusterDictionary dictionary} to use.
   * @param dictResourceKey The prefix to use for detected features. Typically,
   *                        the value for this prefix should be {@code "dict"}.
   * @param lowerCaseDictionary {@code true} if tokens will be lower-cased during
   *                            dictionary lookup, {@code false} otherwise.
   */
  public WordClusterFeatureGenerator(WordClusterDictionary dict,
      String dictResourceKey, boolean lowerCaseDictionary) {
    tokenDictionary = dict;
    resourceName = dictResourceKey;
    this.lowerCaseDictionary = lowerCaseDictionary;
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    String clusterId;
    if (lowerCaseDictionary) {
      clusterId = tokenDictionary.lookupToken(StringUtil.toLowerCase(tokens[index]));
    } else {
      clusterId = tokenDictionary.lookupToken(tokens[index]);
    }
    if (clusterId != null) {
      features.add(resourceName + clusterId);
    }
  }
}
