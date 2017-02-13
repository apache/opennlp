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

public class SuffixFeatureGenerator implements AdaptiveFeatureGenerator {

  static final int DEFAULT_MAX_LENGTH = 4;
    
  private final int suffixLength;
  
  public SuffixFeatureGenerator() {
    suffixLength = DEFAULT_MAX_LENGTH;
  }
  
  public SuffixFeatureGenerator(int suffixLength) {
    this.suffixLength = suffixLength;
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {
    String[] suffs = getSuffixes(tokens[index]);
    for (String suff : suffs) {
      features.add("suf=" + suff);
    }
  }
  
  private String[] getSuffixes(String lex) {
      
    int suffixes = Math.min(suffixLength, lex.length());
      
    String[] suffs = new String[suffixes];
    for (int li = 0; li < suffixes; li++) {
      suffs[li] = lex.substring(Math.max(lex.length() - li - 1, 0));
    }
    return suffs;
  }
  
}
