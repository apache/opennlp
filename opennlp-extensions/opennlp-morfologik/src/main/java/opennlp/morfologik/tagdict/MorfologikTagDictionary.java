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

package opennlp.morfologik.tagdict;

import java.util.ArrayList;
import java.util.List;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.IStemmer;
import morfologik.stemming.WordData;

import opennlp.tools.postag.TagDictionary;

/**
 * A {@link TagDictionary} implementation based on Morfologik binary
 * dictionaries
 */
public class MorfologikTagDictionary implements TagDictionary {

  private final IStemmer dictLookup;
  private final boolean isCaseSensitive;

  /**
   * Initializes a case sensitive {@link MorfologikTagDictionary}
   *
   * @param dict A Morfologik FSA {@link Dictionary}.
   * @throws IllegalArgumentException Thrown if FSA's root node cannot be acquired
   *                                  (dictionary is empty).
   */
  public MorfologikTagDictionary(Dictionary dict) throws IllegalArgumentException {
    this(dict, true);
  }

  /**
   * Initializes a {@link MorfologikTagDictionary}
   *
   * @param dict A Morfologik FSA {@link Dictionary}.
   * @param caseSensitive If {@code true} it performs case-sensitive lookup
   * @throws IllegalArgumentException Thrown if FSA's root node cannot be acquired
   *                                  (dictionary is empty).
   */
  public MorfologikTagDictionary(Dictionary dict, boolean caseSensitive)
      throws IllegalArgumentException {
    this.dictLookup = new DictionaryLookup(dict);
    this.isCaseSensitive = caseSensitive;
  }

  @Override
  public String[] getTags(String word) {
    if (!isCaseSensitive) {
      word = word.toLowerCase();
    }

    List<WordData> data = dictLookup.lookup(word);
    if (data != null && data.size() > 0) {
      List<String> tags = new ArrayList<>(data.size());
      for (WordData aData : data) {
        tags.add(aData.getTag().toString());
      }
      if (tags.size() > 0)
        return tags.toArray(new String[0]);
      return null;
    }
    return null;
  }

  @Override
  public boolean isCaseSensitive() {
    return isCaseSensitive;
  }
}
