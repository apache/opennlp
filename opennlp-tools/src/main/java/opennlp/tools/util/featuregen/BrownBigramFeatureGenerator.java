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
 */
public class BrownBigramFeatureGenerator implements AdaptiveFeatureGenerator {

  private BrownCluster brownLexicon;

  public BrownBigramFeatureGenerator(BrownCluster dict) {
    this.brownLexicon = dict;
  }

  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    List<String> wordClasses = BrownTokenClasses.getWordClasses(tokens[index], brownLexicon);
    if (index > 0) {
      List<String> prevWordClasses = BrownTokenClasses.getWordClasses(tokens[index - 1], brownLexicon);
      for (int i = 0; i < wordClasses.size() && i < prevWordClasses.size(); i++)
      features.add("p" + "browncluster" + "," + "browncluster" + "="
          + prevWordClasses.get(i) + "," + wordClasses.get(i));
    }

    if (index + 1 < tokens.length) {
      List<String> nextWordClasses = BrownTokenClasses.getWordClasses(tokens[index + 1], brownLexicon);
      for (int i = 0; i < wordClasses.size() && i < nextWordClasses.size(); i++) {
        features.add("browncluster" + "," + "n" + "browncluster" + "="
            + wordClasses.get(i) + "," + nextWordClasses.get(i));
      }
    }
  }

}

