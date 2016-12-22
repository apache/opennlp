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
 * Adds trigram features based on tokens and token classes.
 *
 */
public class TrigramNameFeatureGenerator implements AdaptiveFeatureGenerator {

  public void createFeatures(List<String> features, String[] tokens, int index,
      String[] previousOutcomes) {
    String wc = FeatureGeneratorUtil.tokenFeature(tokens[index]);
    // trigram features
    if (index > 1) {
      features.add("ppw,pw,w=" + tokens[index - 2] + "," + tokens[index - 1] + "," + tokens[index]);
      String pwc = FeatureGeneratorUtil.tokenFeature(tokens[index - 1]);
      String ppwc = FeatureGeneratorUtil.tokenFeature(tokens[index - 2]);
      features.add("ppwc,pwc,wc=" + ppwc + "," + pwc + "," + wc);
    }
    if (index + 2 < tokens.length) {
      features.add("w,nw,nnw=" + tokens[index] + "," + tokens[index + 1] + "," + tokens[index + 2]);
      String nwc = FeatureGeneratorUtil.tokenFeature(tokens[index + 1]);
      String nnwc = FeatureGeneratorUtil.tokenFeature(tokens[index + 2]);
      features.add("wc,nwc,nnwc=" + wc + "," + nwc + "," + nnwc);
    }
  }
}
