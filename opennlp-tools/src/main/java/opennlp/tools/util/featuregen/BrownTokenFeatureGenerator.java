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

import opennlp.tools.util.model.ArtifactSerializer;

/**
 * Generates Brown cluster features for current token.
 */
public class BrownTokenFeatureGenerator implements AdaptiveFeatureGenerator {

  private final String dictName;
  private final BrownCluster brownLexicon;

  BrownTokenFeatureGenerator(String dictName) {
    this(null, dictName);
  }

  public BrownTokenFeatureGenerator(BrownCluster dict) {
    this(dict, BrownCluster.BrownClusterSerializer.class.getSimpleName());
  }

  public BrownTokenFeatureGenerator(BrownCluster dict, String dictName) {
    this.brownLexicon = dict;
    this.dictName = dictName;
  }

  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {

    List<String> wordClasses = BrownTokenClasses.getWordClasses(tokens[index], brownLexicon);

    for (int i = 0; i < wordClasses.size(); i++) {
      features.add("browncluster" + "=" + wordClasses.get(i));
    }
  }

  public ArtifactSerializer<?> getArtifactSerializer() {
    return new BrownCluster.BrownClusterSerializer();
  }

  public String getArtifactSerializerName() {
    return dictName;
  }
}
