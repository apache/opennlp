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

import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;
import opennlp.tools.util.featuregen.BigramNameFeatureGenerator;
import opennlp.tools.util.featuregen.CachedFeatureGenerator;
import opennlp.tools.util.featuregen.OutcomePriorFeatureGenerator;
import opennlp.tools.util.featuregen.PreviousMapFeatureGenerator;
import opennlp.tools.util.featuregen.SentenceFeatureGenerator;
import opennlp.tools.util.featuregen.TokenClassFeatureGenerator;
import opennlp.tools.util.featuregen.TokenFeatureGenerator;
import opennlp.tools.util.featuregen.WindowFeatureGenerator;

/**
 * If a token contains an auxiliary information, e.g. POS tag, in the training data,
 * you can use this class via -factory command line option.
 *
 * <strong>EXPERIMENTAL</strong>.
 * This class has been added as part of a work in progress and might change without notice.
 */
public class AuxiliaryInfoTokenNameFinderFactory extends TokenNameFinderFactory {

  @Override
  public NameContextGenerator createContextGenerator() {

    AdaptiveFeatureGenerator featureGenerator = createFeatureGenerators();

    if (featureGenerator == null) {
      featureGenerator = new CachedFeatureGenerator(
          new WindowFeatureGenerator(new TokenFeatureGenerator(), 2, 2),
          new WindowFeatureGenerator(new TokenClassFeatureGenerator(true), 2, 2),
          new OutcomePriorFeatureGenerator(),
          new PreviousMapFeatureGenerator(),
          new BigramNameFeatureGenerator(),
          new SentenceFeatureGenerator(true, false));
    }

    return new AuxiliaryInfoNameContextGenerator(featureGenerator);
  }
}
