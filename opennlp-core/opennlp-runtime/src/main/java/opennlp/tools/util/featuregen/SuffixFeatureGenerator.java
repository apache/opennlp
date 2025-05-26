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

/**
 * A feature generator implementation that generates suffix-based features.
 *
 * @see AdaptiveFeatureGenerator
 */
public class SuffixFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String PREFIX = "suf=";

  /**
   * The default upper boundary suffix length value is {@code 4}.
   */
  static final int DEFAULT_MAX_LENGTH = 4;
    
  private final int suffixLength;

  /**
   * Intializes a {@link SuffixFeatureGenerator}.
   * The suffix length is set to the value of {@link #DEFAULT_MAX_LENGTH}.
   */
  public SuffixFeatureGenerator() {
    suffixLength = DEFAULT_MAX_LENGTH;
  }

  /**
   * Intializes a {@link SuffixFeatureGenerator} with the specified {@code suffixLength}.
   *
   * @param suffixLength The upper boundary suffix length to use.
   */
  public SuffixFeatureGenerator(int suffixLength) {
    this.suffixLength = suffixLength;
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {
    String[] suffs = getSuffixes(tokens[index]);
    for (String suff : suffs) {
      features.add(PREFIX + suff);
    }
  }
  
  private String[] getSuffixes(String lex) {
      
    int suffixes = StrictMath.min(suffixLength, lex.length());
      
    String[] suffs = new String[suffixes];
    for (int li = 0; li < suffixes; li++) {
      suffs[li] = lex.substring(StrictMath.max(lex.length() - li - 1, 0));
    }
    return suffs;
  }
  
}
