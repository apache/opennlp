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

package opennlp.tools.namefind;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorUtil;

/**
 * If a token contains an auxiliary information, e.g. POS tag, this class can be used
 * to extract word part in {@link #getContext(int, String[], String[], Object[])} method.
 *
 * <strong>EXPERIMENTAL</strong>.
 * This class has been added as part of a work in progress and might change without notice.
 */
public class AuxiliaryInfoNameContextGenerator extends DefaultNameContextGenerator {

  public AuxiliaryInfoNameContextGenerator(AdaptiveFeatureGenerator... featureGenerators) {
    super(featureGenerators);
  }

  /**
   * Return the context for finding names at the specified index.
   * @param index The index of the token in the specified toks array for which the
   *              context should be constructed.
   * @param tokens The tokens of the sentence.  The <code>toString</code> methods
   *               of these objects should return the token text.
   * @param preds The previous decisions made in the tagging of this sequence.
   *              Only indices less than i will be examined.
   * @param additionalContext Addition features which may be based on a context outside of the sentence.
   *
   * @return the context for finding names at the specified index.
   */
  @Override
  public String[] getContext(int index, String[] tokens, String[] preds, Object[] additionalContext) {
    List<String> features = new ArrayList<>();

    for (AdaptiveFeatureGenerator featureGenerator : featureGenerators) {
      featureGenerator.createFeatures(features, tokens, index, preds);
    }

    //previous outcome features
    String po = NameFinderME.OTHER;
    String ppo = NameFinderME.OTHER;

    // TODO: These should be moved out here in its own feature generator!
    if (preds != null) {
      if (index > 1) {
        ppo = preds[index - 2];
      }

      if (index > 0) {
        po = preds[index - 1];
      }
      features.add("po=" + po);
      String word = AuxiliaryInfoUtil.getWordPart(tokens[index]);
      features.add("pow=" + po + "," + word);
      features.add("powf=" + po + "," + FeatureGeneratorUtil.tokenFeature(word));
      features.add("ppo=" + ppo);
    }

    return features.toArray(new String[features.size()]);
  }
}
