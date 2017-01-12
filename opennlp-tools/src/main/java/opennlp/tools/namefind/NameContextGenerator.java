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

import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

/**
 * Interface for generating the context for an name finder by specifying a set of geature generators.
 *
 */
public interface NameContextGenerator extends BeamSearchContextGenerator<String> {

  /**
   * Adds a feature generator to this set of feature generators.
   * @param generator The feature generator to add.
   */
  void addFeatureGenerator(AdaptiveFeatureGenerator generator);

  /**
   * Informs all the feature generators for a name finder that the specified tokens have
   * been classified with the coorisponds set of specified outcomes.
   * @param tokens The tokens of the sentence or other text unit which has been processed.
   * @param outcomes The outcomes associated with the specified tokens.
   */
  void updateAdaptiveData(String[] tokens, String[] outcomes);

  /**
   * Informs all the feature generators for a name finder that the context of the adaptive
   * data (typically a document) is no longer valid.
   */
  void clearAdaptiveData();

}
