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
 * The {@link AdditionalContextFeatureGenerator} generates the context from the passed
 * in additional context.
 */
public class AdditionalContextFeatureGenerator implements AdaptiveFeatureGenerator {

  private static final String PREFIX = "ne=";

  private String[][] additionalContext;

  @Override
  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {

    if (additionalContext != null && additionalContext.length != 0) {
      String[] context = additionalContext[index];

      for (String s : context) {
        features.add(PREFIX + s);
      }
    }
  }

  public void setCurrentContext(String[][] context) {
    additionalContext = context;
  }
}
