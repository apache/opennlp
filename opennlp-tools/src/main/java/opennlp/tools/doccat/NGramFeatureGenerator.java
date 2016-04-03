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

package opennlp.tools.doccat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * n-gram {@link FeatureGenerator}
 */
public class NGramFeatureGenerator implements FeatureGenerator {

  //default values for bigrams
  private int minGram = 2;
  private int maxGram = 2;

  public NGramFeatureGenerator() {
  }

  public void NGramFeatureGenerator(int minGram, int maxGram) {
    this.minGram = minGram;
    this.maxGram = maxGram;
  }

  public Collection<String> extractFeatures(String[] text, Map<String, Object> extraInfo) {

    List<String> features = new ArrayList<String>();

    for (int i = 0; i <= text.length - minGram; i++) {
      String feature = "ng=";
      for (int y = 0; y < maxGram && i + y < text.length; y++) {
        feature = feature + ":" + text[i + y];
        int gramCount = y + 1;
        if (maxGram >= gramCount && gramCount >= minGram) {
          features.add(feature);
        }
      }
    }

    return features;
  }
}
