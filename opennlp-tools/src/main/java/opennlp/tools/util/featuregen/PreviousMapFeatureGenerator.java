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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This {@link AdaptiveFeatureGenerator} generates features indicating the
 * outcome associated with a previously occuring word.
 */
public class PreviousMapFeatureGenerator implements AdaptiveFeatureGenerator {

  private Map<String, String> previousMap = new HashMap<>();

  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {
    features.add("pd=" + previousMap.get(tokens[index]));
  }

  /**
   * Generates previous decision features for the token based on contents of the previous map.
   */
  public void updateAdaptiveData(String[] tokens, String[] outcomes) {

    for (int i = 0; i < tokens.length; i++) {
      previousMap.put(tokens[i], outcomes[i]);
    }
  }

  /**
   * Clears the previous map.
   */
  public void clearAdaptiveData() {
    previousMap.clear();
  }
}
