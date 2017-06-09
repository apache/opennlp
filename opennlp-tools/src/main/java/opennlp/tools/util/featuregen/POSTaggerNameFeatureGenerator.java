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

import java.util.Arrays;
import java.util.List;

import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;

/**
 * Adds the token POS Tag as feature. Requires a POS Tag model.
 */
public class POSTaggerNameFeatureGenerator implements AdaptiveFeatureGenerator {

  private POSTagger posTagger;

  private String[] cachedTokens;
  private String[] cachedTags;

  /**
   * Initializes a new instance.
   *
   * @param aPosTagger a POSTagger implementation.
   */
  public POSTaggerNameFeatureGenerator(POSTagger aPosTagger) {
    this.posTagger = aPosTagger;
  }

  /**
   * Initializes a new instance.
   *
   * @param aPosModel a POSTagger model.
   */
  public POSTaggerNameFeatureGenerator(POSModel aPosModel) {

    this.posTagger = new POSTaggerME(aPosModel);
  }


  public void createFeatures(List<String> feats, String[] toks, int index, String[] preds) {
    if (!Arrays.equals(this.cachedTokens, toks)) {
      this.cachedTokens = toks;
      this.cachedTags = this.posTagger.tag(toks);
    }

    feats.add("pos=" + this.cachedTags[index]);
  }


}
