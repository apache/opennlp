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
 * Generates Brown cluster features for token bigrams.
 *
 * @see AdaptiveFeatureGenerator
 * @see BrownCluster
 */
public class BrownBigramFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String BROWNCLUSTER = "browncluster";
  private static final String FEATURE_NEXT_BROWNCLUSTER_BASE = BROWNCLUSTER + ",n" + BROWNCLUSTER + "=";
  private static final String FEATURE_PREV_BROWNCLUSTER_BASE = "p" + BROWNCLUSTER + "," + BROWNCLUSTER + "=";

  private final BrownCluster brownCluster;

  /**
   * Initializes a {@link BrownBigramFeatureGenerator} generator via a specified
   * {@link BrownCluster}.
   * 
   * @param brownCluster The token {@link BrownCluster dictionary} to use.
   */
  public BrownBigramFeatureGenerator(BrownCluster brownCluster) {
    this.brownCluster = brownCluster;
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    List<String> wc = BrownTokenClasses.getWordClasses(tokens[index], brownCluster);
    if (index > 0) {
      List<String> prevWC = BrownTokenClasses.getWordClasses(tokens[index - 1], brownCluster);
      for (int i = 0; i < wc.size() && i < prevWC.size(); i++) {
        features.add(FEATURE_PREV_BROWNCLUSTER_BASE + prevWC.get(i) + "," + wc.get(i));
      }
    }

    if (index + 1 < tokens.length) {
      List<String> nextWordClasses = BrownTokenClasses.getWordClasses(tokens[index + 1], brownCluster);
      for (int i = 0; i < wc.size() && i < nextWordClasses.size(); i++) {
        features.add(FEATURE_NEXT_BROWNCLUSTER_BASE + wc.get(i) + "," + nextWordClasses.get(i));
      }
    }
  }

}
