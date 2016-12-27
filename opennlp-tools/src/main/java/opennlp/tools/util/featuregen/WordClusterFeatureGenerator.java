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

public class WordClusterFeatureGenerator implements AdaptiveFeatureGenerator {

  private WordClusterDictionary tokenDictionary;
  private String resourceName;
  private boolean lowerCaseDictionary;

  public WordClusterFeatureGenerator(WordClusterDictionary dict, String dictResourceKey, boolean lowerCaseDictionary) {
      tokenDictionary = dict;
      resourceName = dictResourceKey;
      this.lowerCaseDictionary = lowerCaseDictionary;
  }

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