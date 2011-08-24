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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Generates previous and next features for a given {@link AdaptiveFeatureGenerator}.
 * The window size can be specified.
 *
 * Features:
 * Current token is always included unchanged
 * Previous tokens are prefixed with p distance
 * Next tokens are prefix with n distance
 */
public class WindowFeatureGenerator implements AdaptiveFeatureGenerator {

  public static final String PREV_PREFIX = "p";
  public static final String NEXT_PREFIX = "n";

  private final AdaptiveFeatureGenerator generator;

  private final int prevWindowSize;
  private final int nextWindowSize;

  /**
   * Initializes the current instance with the given parameters.
   *
   * @param generator Feature generator to apply to the window.
   * @param prevWindowSize Size of the window to the left of the current token.
   * @param nextWindowSize Size of the window to the right of the current token.
   */
  public WindowFeatureGenerator(AdaptiveFeatureGenerator generator, int prevWindowSize,  int nextWindowSize) {
    this.generator = generator;
    this.prevWindowSize = prevWindowSize;
    this.nextWindowSize = nextWindowSize;
  }
  
  /**
   * Initializes the current instance with the given parameters.
   * 
   * @param prevWindowSize
   * @param nextWindowSize
   * @param generators
   */
  public WindowFeatureGenerator(int prevWindowSize, int nextWindowSize, AdaptiveFeatureGenerator... generators) {
    this(new AggregatedFeatureGenerator(generators), prevWindowSize, nextWindowSize);
  }
  
  /**
   * Initializes the current instance. The previous and next window size is 5.
   *
   * @param generator
   */
  public WindowFeatureGenerator(AdaptiveFeatureGenerator generator) {
    this(generator, 5, 5);
  }
  
  /**
   * Initializes the current isntance with the given parameters.
   * 
   * @param generators
   */
  public WindowFeatureGenerator(AdaptiveFeatureGenerator... generators) {
    this(new AggregatedFeatureGenerator(generators), 5, 5);
  }
  
  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {
    // current features
    generator.createFeatures(features, tokens, index, preds);

    // previous features
    for (int i = 1; i < prevWindowSize + 1; i++) {
      if (index - i >= 0) {

        List<String> prevFeatures = new ArrayList<String>();

        generator.createFeatures(prevFeatures, tokens, index - i, preds);

        for (Iterator<String> it = prevFeatures.iterator(); it.hasNext();) {
          features.add(PREV_PREFIX + i + it.next());
        }
      }
    }

    // next features
    for (int i = 1; i < nextWindowSize + 1; i++) {
      if (i + index < tokens.length) {

        List<String> nextFeatures = new ArrayList<String>();

        generator.createFeatures(nextFeatures, tokens, index + i, preds);

        for (Iterator<String> it = nextFeatures.iterator(); it.hasNext();) {
          features.add(NEXT_PREFIX + i + it.next());
        }
      }
    }
  }

  public void updateAdaptiveData(String[] tokens, String[] outcomes) {
    generator.updateAdaptiveData(tokens, outcomes);
  }

  public void clearAdaptiveData() {
      generator.clearAdaptiveData();
  }

  @Override
  public String toString() {
    return super.toString()+": Prev windwow size: " + prevWindowSize +", Next window size: " + nextWindowSize;
  }
}
