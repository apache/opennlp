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
import java.util.Map;

import opennlp.tools.util.featuregen.StringPattern;

/**
 * Generates a feature for each word in a document.
 */
public class BagOfWordsFeatureGenerator implements FeatureGenerator {

  private boolean useOnlyAllLetterTokens = false;

  public BagOfWordsFeatureGenerator() {
  }

  BagOfWordsFeatureGenerator(boolean useOnlyAllLetterTokens) {
    this.useOnlyAllLetterTokens = useOnlyAllLetterTokens;
  }

  @Override
  public Collection<String> extractFeatures(String[] text, Map<String, Object> extraInformation) {

    Collection<String> bagOfWords = new ArrayList<>(text.length);

    for (String word : text) {
      if (useOnlyAllLetterTokens) {
        StringPattern pattern = StringPattern.recognize(word);

        if (pattern.isAllLetter())
          bagOfWords.add("bow=" + word);
      }
      else {
        bagOfWords.add("bow=" + word);
      }
    }

    return bagOfWords;
  }
}
