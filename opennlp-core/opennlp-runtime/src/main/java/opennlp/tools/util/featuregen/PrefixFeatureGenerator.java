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
 * A feature generator implementation that generates prefix-based features.
 *
 * @see AdaptiveFeatureGenerator
 */
public class PrefixFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String PREFIX = "pre=";

  /**
   * The default upper boundary prefix length value is {@code 4}.
   */
  static final int DEFAULT_MAX_LENGTH = 4;
  
  private final int prefixLength;

  /**
   * Intializes a default {@link PrefixFeatureGenerator}.
   * The prefixLength length is set to the value of {@link #DEFAULT_MAX_LENGTH}.
   */
  public PrefixFeatureGenerator() {
    prefixLength = DEFAULT_MAX_LENGTH;
  }

  /**
   * Intializes a {@link PrefixFeatureGenerator} with the specified {@code prefixLength}.
   *
   * @param prefixLength The upper boundary prefix length to use.
   */
  public PrefixFeatureGenerator(int prefixLength) {
    this.prefixLength = prefixLength;
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {
    String[] prefs = getPrefixes(tokens[index]);
    for (String pref : prefs) {
      features.add(PREFIX + pref);
    }
  }
  
  private String[] getPrefixes(String lex) {
      
    int prefixes = StrictMath.min(prefixLength, lex.length());
    
    String[] prefs = new String[prefixes];
    for (int li = 0; li < prefixes; li++) {
      prefs[li] = lex.substring(0, StrictMath.min(li + 1, lex.length()));
    }
    return prefs;
  }
}
