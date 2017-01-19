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

package opennlp.tools.namefind;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;

/**
 * This is a dictionary based name finder, it scans text
 * for names inside a dictionary.
 */
public class DictionaryNameFinder implements TokenNameFinder {

  private static final String DEFAULT_TYPE = "default";

  private Dictionary mDictionary;
  private final String type;

  /**
   * Initialized the current instance with he provided dictionary
   * and a type.
   *
   * @param dictionary
   * @param type the name type used for the produced spans
   */
  public DictionaryNameFinder(Dictionary dictionary, String type) {
    mDictionary = Objects.requireNonNull(dictionary, "dictionary must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
  }

  /**
   * Initializes the current instance with the provided dictionary.
   *
   * @param dictionary
   */
  public DictionaryNameFinder(Dictionary dictionary) {
    this(dictionary, DEFAULT_TYPE);
  }

  public Span[] find(String[] textTokenized) {
    List<Span> namesFound = new LinkedList<>();

    for (int offsetFrom = 0; offsetFrom < textTokenized.length; offsetFrom++) {
      Span nameFound = null;
      String tokensSearching[];

      for (int offsetTo = offsetFrom; offsetTo < textTokenized.length; offsetTo++) {
        int lengthSearching = offsetTo - offsetFrom + 1;

        if (lengthSearching > mDictionary.getMaxTokenCount()) {
          break;
        } else {
          tokensSearching = new String[lengthSearching];
          System.arraycopy(textTokenized, offsetFrom, tokensSearching, 0,
              lengthSearching);

          StringList entryForSearch = new StringList(tokensSearching);

          if (mDictionary.contains(entryForSearch)) {
            nameFound = new Span(offsetFrom, offsetTo + 1, type);
          }
        }
      }

      if (nameFound != null) {
        namesFound.add(nameFound);
        // skip over the found tokens for the next search
        offsetFrom += nameFound.length() - 1;
      }
    }
    return namesFound.toArray(new Span[namesFound.size()]);
  }

  public void clearAdaptiveData() {
    // nothing to clear
  }
}
