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

import java.util.Iterator;
import java.util.List;

import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.StringList;

/**
 * The {@link CharacterNgramFeatureGenerator} uses character ngrams to
 * generate features about each token.
 * The minimum and maximum length can be specified.
 */
public class CharacterNgramFeatureGenerator extends FeatureGeneratorAdapter {

  private final int minLength;
  private final int maxLength;

  public CharacterNgramFeatureGenerator(int minLength, int maxLength) {
    this.minLength = minLength;
    this.maxLength = maxLength;
  }

  /**
   * Initializes the current instance with min 2 length and max 5 length of ngrams.
   */
  public CharacterNgramFeatureGenerator() {
    this(2, 5);
  }

  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {

    NGramModel model = new NGramModel();
    model.add(tokens[index], minLength, maxLength);

    for (Iterator<StringList> it = model.iterator(); it.hasNext();) {

      StringList tokenList = it.next();

      if (tokenList.size() > 0) {
        features.add("ng=" + tokenList.getToken(0).toLowerCase());
      }
    }
  }
}
