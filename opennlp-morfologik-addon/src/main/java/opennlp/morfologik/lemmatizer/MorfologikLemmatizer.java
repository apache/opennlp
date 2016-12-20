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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.IStemmer;
import morfologik.stemming.WordData;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;

public class MorfologikLemmatizer implements DictionaryLemmatizer {

  private IStemmer dictLookup;
  public final Set<String> constantTags = new HashSet<>(Arrays.asList("NNP", "NP00000"));

  public MorfologikLemmatizer(Path dictionaryPath) throws IllegalArgumentException,
      IOException {
    dictLookup = new DictionaryLookup(Dictionary.read(dictionaryPath));
  }

  private Map<List<String>, String> getLemmaTagsDict(String word) {
    List<WordData> wdList = dictLookup.lookup(word);
    Map<List<String>, String> dictMap = new HashMap<>();
    for (WordData wd : wdList) {
      List<String> wordLemmaTags = new ArrayList<>();
      wordLemmaTags.add(word);
      wordLemmaTags.add(wd.getTag().toString());
      dictMap.put(wordLemmaTags, wd.getStem().toString());
    }
    return dictMap;
  }

  private List<String> getDictKeys(String word, String postag) {
    List<String> keys = new ArrayList<>();
    if (constantTags.contains(postag)) {
      keys.addAll(Arrays.asList(word, postag));
    } else {
      keys.addAll(Arrays.asList(word.toLowerCase(), postag));
    }
    return keys;
  }

  private Map<List<String>, String> getDictMap(String word, String postag) {
    Map<List<String>, String> dictMap;

    if (constantTags.contains(postag)) {
      dictMap = this.getLemmaTagsDict(word);
    } else {
      dictMap = this.getLemmaTagsDict(word.toLowerCase());
    }
    return dictMap;
  }

  public String lemmatize(String word, String postag) {
    String lemma;
    List<String> keys = this.getDictKeys(word, postag);
    Map<List<String>, String> dictMap = this.getDictMap(word, postag);
    // lookup lemma as value of the map
    String keyValue = dictMap.get(keys);
    if (keyValue != null) {
      lemma = keyValue;
    } else if (constantTags.contains(postag)) {
      lemma = word;
    } else if (Objects.equals(word.toUpperCase(), word)) {
      lemma = word;
    } else {
      lemma = word.toLowerCase();
    }
    return lemma;
  }
}
