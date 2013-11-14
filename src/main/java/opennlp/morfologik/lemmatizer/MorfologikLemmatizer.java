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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.IStemmer;
import morfologik.stemming.WordData;

public class MorfologikLemmatizer implements DictionaryLemmatizer {

  private IStemmer dictLookup;
  public final Set<String> constantTags = new HashSet<String>(Arrays.asList(
      "NNP", "NP00000"));

  public MorfologikLemmatizer(URL dictURL) throws IllegalArgumentException,
      IOException {
    dictLookup = new DictionaryLookup(Dictionary.read(dictURL));
  }

  private HashMap<List<String>, String> getLemmaTagsDict(String word) {
    List<WordData> wdList = dictLookup.lookup(word);
    HashMap<List<String>, String> dictMap = new HashMap<List<String>, String>();
    for (WordData wd : wdList) {
      List<String> wordLemmaTags = new ArrayList<String>();
      wordLemmaTags.add(word);
      wordLemmaTags.add(wd.getTag().toString());
      dictMap.put(wordLemmaTags, wd.getStem().toString());
    }
    return dictMap;
  }

  private List<String> getDictKeys(String word, String postag) {
    List<String> keys = new ArrayList<String>();
    if (constantTags.contains(postag)) {
      keys.addAll(Arrays.asList(word, postag));
    } else {
      keys.addAll(Arrays.asList(word.toLowerCase(), postag));
    }
    return keys;
  }

  private HashMap<List<String>, String> getDictMap(String word, String postag) {
    HashMap<List<String>, String> dictMap = new HashMap<List<String>, String>();

    if (constantTags.contains(postag)) {
      dictMap = this.getLemmaTagsDict(word);
    } else {
      dictMap = this.getLemmaTagsDict(word.toLowerCase());
    }
    return dictMap;
  }

  public String lemmatize(String word, String postag) {
    String lemma = null;
    List<String> keys = this.getDictKeys(word, postag);
    HashMap<List<String>, String> dictMap = this.getDictMap(word, postag);
    // lookup lemma as value of the map
    String keyValue = dictMap.get(keys);
    if (keyValue != null) {
      lemma = keyValue;
    } else if (keyValue == null && constantTags.contains(postag)) {
      lemma = word;
    } else if (keyValue == null && word.toUpperCase() == word) {
      lemma = word;
    } else {
      lemma = word.toLowerCase();
    }
    return lemma;
  }
}
