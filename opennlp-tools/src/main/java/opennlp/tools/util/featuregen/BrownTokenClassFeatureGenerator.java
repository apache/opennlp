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
 * Generates Brown cluster features for current token and token class.
 */
public class BrownTokenClassFeatureGenerator implements AdaptiveFeatureGenerator {

  private BrownCluster brownLexicon;

  public BrownTokenClassFeatureGenerator(BrownCluster dict) {
    this.brownLexicon = dict;
  }

  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    String wordShape = FeatureGeneratorUtil.tokenFeature(tokens[index]);
    List<String> wordClasses = BrownTokenClasses.getWordClasses(tokens[index], brownLexicon);

    for (int i = 0; i < wordClasses.size(); i++) {
      features.add("c," + "browncluster" + "=" + wordShape + "," + wordClasses.get(i));
    }
  }

}

