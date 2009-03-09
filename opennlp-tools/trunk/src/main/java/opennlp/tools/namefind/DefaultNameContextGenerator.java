/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.namefind;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;
import opennlp.tools.util.featuregen.OutcomePriorFeatureGenerator;
import opennlp.tools.util.featuregen.PreviousMapFeatureGenerator;
import opennlp.tools.util.featuregen.TokenClassFeatureGenerator;
import opennlp.tools.util.featuregen.TokenFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * Class for determining contextual features for a tag/chunk style
 * named-entity recognizer.
 *
 * @version $Revision: 1.3 $, $Date: 2009-03-09 03:10:51 $
 */
public class DefaultNameContextGenerator implements NameContextGenerator {

  private AdaptiveFeatureGenerator featureGenerators[];

  private static AdaptiveFeatureGenerator windowFeatures = new CachedFeatureGenerator(
      new AdaptiveFeatureGenerator[]{
      new WindowFeatureGenerator(new TokenFeatureGenerator(), 2, 2),
      new WindowFeatureGenerator(new TokenClassFeatureGenerator(true), 2, 2),
      new OutcomePriorFeatureGenerator()
      });

  /**
   * Creates a name context generator.
   */
  public DefaultNameContextGenerator() {
    this((AdaptiveFeatureGenerator[]) null);
  }

  /**
   * Creates a name context generator with the specified cache size.
   */
  public DefaultNameContextGenerator(AdaptiveFeatureGenerator... featureGenerators) {

    if (featureGenerators != null) {
      this.featureGenerators = featureGenerators;
    }
    else {
      // use defaults

      this.featureGenerators = new AdaptiveFeatureGenerator[]{
          windowFeatures,
          new PreviousMapFeatureGenerator()};
    }
  }

  public void addFeatureGenerator(AdaptiveFeatureGenerator generator) {
      AdaptiveFeatureGenerator generators[] = featureGenerators;

      featureGenerators = new AdaptiveFeatureGenerator[featureGenerators.length + 1];

      System.arraycopy(generators, 0, featureGenerators, 0, generators.length);

      featureGenerators[featureGenerators.length - 1] = generator;
  }

  public void updateAdaptiveData(String[] tokens, String[] outcomes) {

    if (tokens != null && outcomes != null && tokens.length != outcomes.length) {
        throw new IllegalArgumentException(
            "The tokens and outcome arrays MUST have the same size!");
      }

    for (int i = 0; i < featureGenerators.length; i++) {
      featureGenerators[i].updateAdaptiveData(tokens, outcomes);
    }
  }

  public void clearAdaptiveData() {
    for (int i = 0; i < featureGenerators.length; i++) {
      featureGenerators[i].clearAdaptiveData();
    }
  }

  /**
   * Return the context for finding names at the specified index.
   * @param index The index of the token in the specified toks array for which the context should be constructed.
   * @param toks The tokens of the sentence.  The <code>toString</code> methods of these objects should return the token text.
   * @param preds The previous decisions made in the tagging of this sequence.  Only indices less than i will be examined.
   * @param additionalContext Addition features which may be based on a context outside of the sentence.
   * @return the context for finding names at the specified index.
   */
  public String[] getContext(int index, String[] tokens, String[] preds, Object[] additionalContext) {
    List<String> features = new ArrayList<String>();

    for (int i = 0; i < featureGenerators.length; i++) {
      featureGenerators[i].createFeatures(features, tokens, index, preds);
    }

    if (index == 0) {
      features.add("fwis"); //first word in sentence
    }

    //previous outcome features
    String po = NameFinderME.OTHER;
    String ppo = NameFinderME.OTHER;

    if (index > 1){
      ppo = preds[index-2];
    }

    if (index > 0) {
      po = preds[index-1];
    }
    features.add("po=" + po);
    features.add("pow=" + po + "," + tokens[index]);
    features.add("powf=" + po + "," + FeatureGeneratorUtil.tokenFeature(tokens[index]));
    features.add("ppo=" + ppo);

    return features.toArray(new String[features.size()]);
  }
}
