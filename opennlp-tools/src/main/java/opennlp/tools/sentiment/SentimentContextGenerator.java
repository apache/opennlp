/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.sentiment;

import opennlp.tools.util.BeamSearchContextGenerator;
import opennlp.tools.util.featuregen.AdaptiveFeatureGenerator;

/**
 * Class for using a Context Generator for Sentiment Analysis.
 */
public class SentimentContextGenerator
    implements BeamSearchContextGenerator<String> {

  private AdaptiveFeatureGenerator[] featureGenerators;

  public SentimentContextGenerator() {
    this(new AdaptiveFeatureGenerator[0]);
  }

  public SentimentContextGenerator(
      AdaptiveFeatureGenerator[] featureGenerators) {
    this.featureGenerators = featureGenerators;
  }

  /**
   * Returns the context
   *
   * @param text
   *          the given text to be returned as context
   * @return the text (the context)
   */
  public String[] getContext(String text[]) {
    return text;
  }

  /**
   * Returns the context
   *
   * @param index
   *          the index of the context
   * @param sequence
   *          String sequence given
   * @param priorDecisions
   *          decisions given earlier
   * @param additionalContext
   *          any additional context
   * @return the context
   */
  @Override
  public String[] getContext(int index, String[] sequence,
      String[] priorDecisions, Object[] additionalContext) {
    return new String[] {};
  }

  public void updateAdaptiveData(String[] tokens, String[] outcomes) {

    if (tokens != null && outcomes != null
        && tokens.length != outcomes.length) {
      throw new IllegalArgumentException(
          "The tokens and outcome arrays MUST have the same size!");
    }

    for (AdaptiveFeatureGenerator featureGenerator : featureGenerators) {
      featureGenerator.updateAdaptiveData(tokens, outcomes);
    }
  }

}
