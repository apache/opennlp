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
import morfologik.stemming.IStemmer;
import morfologik.stemming.WordData;

import opennlp.tools.postag.TagDictionary;

/**
 * A {@link TagDictionary} implementation based on Morfologik binary
 * dictionaries.
 * <p>
 * This implementation is thread-safe: the immutable {@link Dictionary} is shared
 * and each thread looks words up through its own {@link DictionaryLookup}, held in
 * a {@link ThreadLocal}, as {@link DictionaryLookup} instances are stateful and must
 * not be used concurrently. Call {@link #clearThreadLocalState()} to release the
 * lookup of the current thread.
 */
public class MorfologikTagDictionary implements TagDictionary {

  private final ThreadLocal<IStemmer> dictLookup;
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
    // Creates the first lookup now, so an unusable dictionary fails here.
    final IStemmer initialLookup = new DictionaryLookup(dict);
    this.dictLookup = ThreadLocal.withInitial(() -> new DictionaryLookup(dict));
    this.dictLookup.set(initialLookup);
    this.isCaseSensitive = caseSensitive;
  }

  @Override
  public String[] getTags(String word) {
    if (!isCaseSensitive) {
      word = word.toLowerCase(Locale.ROOT);
    }

    List<WordData> data = dictLookup.get().lookup(word);
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

  /**
   * Removes the {@link DictionaryLookup} of the current thread to prevent classloader
   * leaks in container environments. Call when the thread is returned to a pool or the
   * dictionary is no longer needed; a later lookup on the same thread creates a new one.
   */
  public void clearThreadLocalState() {
    dictLookup.remove();
  }
}
