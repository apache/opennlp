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
 * Generates features for different for the class of the token.
 */
public class TokenClassFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String TOKEN_CLASS_PREFIX = "wc";
  private static final String TOKEN_AND_CLASS_PREFIX = "w&c";

  private boolean generateWordAndClassFeature;

  public TokenClassFeatureGenerator() {
    this(false);
  }

  public TokenClassFeatureGenerator(boolean generateWordAndClassFeature) {
    this.generateWordAndClassFeature = generateWordAndClassFeature;
  }

  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {
    String wordClass = FeatureGeneratorUtil.tokenFeature(tokens[index]);
    features.add(TOKEN_CLASS_PREFIX + "=" + wordClass);

    if (generateWordAndClassFeature) {
      features.add(TOKEN_AND_CLASS_PREFIX + "=" + StringUtil.toLowerCase(tokens[index]) +
          "," + wordClass);
    }
  }
}
