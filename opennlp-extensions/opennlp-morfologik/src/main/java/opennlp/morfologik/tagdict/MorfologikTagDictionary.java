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
import java.util.Locale;

import morfologik.stemming.Dictionary;
import morfologik.stemming.DictionaryLookup;
import morfologik.stemming.WordData;

import opennlp.tools.postag.TagDictionary;

/**
 * A {@link TagDictionary} implementation based on Morfologik binary
 * dictionaries.
 * <p>
 * This implementation is thread-safe: the immutable {@link Dictionary} is shared
 * and a {@link DictionaryLookup} is created per {@link #getTags(String)} call, as
 * {@link DictionaryLookup} instances are stateful and must not be used concurrently.
 */
public class MorfologikTagDictionary implements TagDictionary {

  private final Dictionary dictionary;
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
    // Validates eagerly that a lookup can be constructed from the dictionary.
    new DictionaryLookup(dict);
    this.dictionary = dict;
    this.isCaseSensitive = caseSensitive;
  }

  @Override
  public String[] getTags(String word) {
    if (!isCaseSensitive) {
      word = word.toLowerCase(Locale.ROOT);
    }

    List<WordData> data = new DictionaryLookup(dictionary).lookup(word);
    if (data != null && !data.isEmpty()) {
      List<String> tags = new ArrayList<>(data.size());
      for (WordData aData : data) {
        CharSequence tag = aData.getTag();
        if (tag != null) {
          tags.add(tag.toString());
        }
      }
      if (!tags.isEmpty()) {
        return tags.toArray(new String[0]);
      }
    }
    return null;
  }

  @Override
  public boolean isCaseSensitive() {
    return isCaseSensitive;
  }
}
