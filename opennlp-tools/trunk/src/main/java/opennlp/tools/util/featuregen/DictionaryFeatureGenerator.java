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

import opennlp.tools.namefind.DictionaryNameFinder;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

/**
 * Generates features if the tokens are contained in the dictionary.
 */
public class DictionaryFeatureGenerator extends FeatureGeneratorAdapter {

  private TokenNameFinder mFinder;

  private String mCurrentSentence[];

  private Span mCurrentNames[];

  /**
   * Initializes the current instance. Pass in an instance of
   * the {@link DictionaryNameFinder}.
   *
   * @param dictionary
   */
  public DictionaryFeatureGenerator(TokenNameFinder finder) {
    mFinder = finder;
  }

  public void createFeatures(List<String> features, String[] tokens, int index, String[] preds) {
    // cache results sentence
    if (mCurrentSentence != tokens) {
      mCurrentSentence = tokens;
      mCurrentNames = mFinder.find(tokens);
    }

    // iterate over names and check if a span is contained
    for (int i = 0; i < mCurrentNames.length; i++) {
      if (mCurrentNames[i].contains(index)) {
        // found a span for the current token
        features.add("w=dic");
        features.add("w=dic=" + tokens[index]);

        // TODO: consider generation start and continuation features

        break;
      }
    }
  }
}
