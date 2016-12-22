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
 * Generates a feature which contains the token itself.
 */
public class TokenFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String WORD_PREFIX = "w";
  private boolean lowercase;

  public TokenFeatureGenerator(boolean lowercase) {
    this.lowercase = lowercase;
  }

  public TokenFeatureGenerator() {
    this(true);
  }

  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {
    if (lowercase) {
      features.add(WORD_PREFIX + "=" + StringUtil.toLowerCase(tokens[index]));
    }
    else {
      features.add(WORD_PREFIX + "=" + tokens[index]);
    }
  }
}
