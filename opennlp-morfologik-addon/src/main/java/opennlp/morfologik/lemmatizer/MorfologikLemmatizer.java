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

package opennlp.morfologik.lemmatizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.IStemmer;
import morfologik.stemming.WordData;

import opennlp.tools.lemmatizer.Lemmatizer;

public class MorfologikLemmatizer implements Lemmatizer {

  private IStemmer dictLookup;

  public MorfologikLemmatizer(Path dictionaryPath) throws IllegalArgumentException,
      IOException {
    dictLookup = new DictionaryLookup(Dictionary.read(dictionaryPath));
  }

  private List<String> lemmatize(String word, String postag) {
    List<WordData> dictMap = dictLookup.lookup(word.toLowerCase());
    Set<String> lemmas = new HashSet<>();
    for (WordData wordData : dictMap) {
      if (Objects.equals(postag, asString(wordData.getTag()))) {
        lemmas.add(asString(wordData.getStem()));
      }
    }
    return Collections.unmodifiableList(new ArrayList<>(lemmas));
  }

  private String asString(CharSequence tag) {
    if (tag == null) {
      return null;
    }
    return tag.toString();
  }

  @Override
  public String[] lemmatize(String[] toks, String[] tags) {
    String[] lemmas = new String[toks.length];
    for (int i = 0; i < toks.length; i++) {
      List<String> l = lemmatize(toks[i], tags[i]);
      if (l.size() > 0) {
        lemmas[i] = l.get(0);
      } else {
        lemmas[i] = null;
      }
    }
    return lemmas;
  }


  /**
   * Generates a lemma tags for the word and postag returning the result in list of possible lemmas.
   *
   * @param toks an array of the tokens
   * @param tags an array of the pos tags
   * @return an list of possible lemmas for each token in the sequence.
   */
  public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
    List<List<String>> lemmas = new ArrayList<>();
    for (int i = 0; i < toks.size(); i++) {
      lemmas.add(lemmatize(toks.get(i), tags.get(i)));
    }
    return lemmas;
  }
}
