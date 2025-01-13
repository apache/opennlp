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
 * Generates {@link BrownCluster} features for current token and token class.
 *
 * @see AdaptiveFeatureGenerator
 * @see BrownCluster
 */
public class BrownTokenClassFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String PREFIX = "c,browncluster=";

  private final BrownCluster brownLexicon;

  /**
   * Instantiates a {@link BrownTokenClassFeatureGenerator} via a specified
   * {@link BrownCluster}.
   *
   * @param dict The token {@link BrownCluster dictionary} to use.
   */
  public BrownTokenClassFeatureGenerator(BrownCluster dict) {
    this.brownLexicon = dict;
  }

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    String wordShape = FeatureGeneratorUtil.tokenFeature(tokens[index]);
    List<String> wordClasses = BrownTokenClasses.getWordClasses(tokens[index], brownLexicon);

    for (String wordClass : wordClasses) {
      features.add(PREFIX + wordShape + "," + wordClass);
    }
  }

}

