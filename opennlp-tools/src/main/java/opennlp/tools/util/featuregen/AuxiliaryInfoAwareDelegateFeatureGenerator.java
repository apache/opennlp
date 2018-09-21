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

import opennlp.tools.namefind.AuxiliaryInfoUtil;

/**
 * If a token contains an auxiliary information, e.g. POS tag, in the training data,
 * you can use this feature generator in order to let the feature generator choose
 * word part or auxiliary information part.<br>
 *
 * ex) token := word '/' POStag
 *
 * <strong>EXPERIMENTAL</strong>.
 * This class has been added as part of a work in progress and might change without notice.
 */
public class AuxiliaryInfoAwareDelegateFeatureGenerator implements AdaptiveFeatureGenerator {

  private String[] cachedWordAndAuxes;
  private String[] cached;

  private final AdaptiveFeatureGenerator generator;
  private final boolean useAux;

  public AuxiliaryInfoAwareDelegateFeatureGenerator(AdaptiveFeatureGenerator generator, boolean useAux) {
    this.generator = generator;
    this.useAux = useAux;
  }

  @Override
  public void createFeatures(List<String> features, String[] wordAndAuxes, int index,
                             String[] previousOutcomes) {
    if (useAux) {
      if (!Arrays.equals(cachedWordAndAuxes, wordAndAuxes)) {
        cachedWordAndAuxes = wordAndAuxes;
        cached = AuxiliaryInfoUtil.getAuxParts(wordAndAuxes);
      }
    }
    else {
      if (!Arrays.equals(cachedWordAndAuxes, wordAndAuxes)) {
        cachedWordAndAuxes = wordAndAuxes;
        cached = AuxiliaryInfoUtil.getWordParts(wordAndAuxes);
      }
    }
    generator.createFeatures(features, cached, index, previousOutcomes);
  }
}
