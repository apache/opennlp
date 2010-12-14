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


package opennlp.tools.util.featuregen;

import java.util.List;

/**
 * An interface for generating features for name entity identification and for
 * updating document level contexts.  
 */
public interface AdaptiveFeatureGenerator {
 
  /**
   * Adds the appropriate features for the token at the specified index with the 
   * specified array of previous outcomes to the specified list of features.
   * 
   * @param features The list of features to be added to.
   * @param tokens The tokens of the sentence or other text unit being processed.
   * @param index The index of the token which is currently being processed.
   * @param previousOutcomes The outcomes for the tokens prior to the specified index.
   */
  void createFeatures(List<String> features, String[] tokens, int index, String[] previousOutcomes);
  
  /**
   * Informs the feature generator that the specified tokens have been classified with the
   * corresponding set of specified outcomes.
   * 
   * @param tokens The tokens of the sentence or other text unit which has been processed.
   * @param outcomes The outcomes associated with the specified tokens.
   */
   void updateAdaptiveData(String[] tokens, String[] outcomes);
  
  /**
   * Informs the feature generator that the context of the adaptive data (typically a document)
   * is no longer valid.
   */
   void clearAdaptiveData();
}