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


package opennlp.tools.namefind;

import java.util.LinkedList;
import java.util.List;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;

/**
 * This is a dictionary based name finder, it scans text
 * for names inside a dictionary.
 */
public class DictionaryNameFinder implements TokenNameFinder {

  private Dictionary mDictionary;

  /**
   * Initializes the current instance.
   *
   * @param dictionary
   */
  public DictionaryNameFinder(Dictionary dictionary) {
    mDictionary = dictionary;
  }

  public Span[] find(String[] tokenStrings) {
    List<Span> foundNames = new LinkedList<Span>();

    for (int startToken = 0; startToken < tokenStrings.length; startToken++) {

      Span foundName = null;
      String  tokens[] = new String[]{};

      for (int endToken = startToken; endToken < tokenStrings.length; endToken++) {

        if ((endToken - startToken + 1) > mDictionary.getMaxTokenCount()) {
          break;
        }
        else {
          tokens = new String[(endToken - startToken + 1)];
          System.arraycopy(tokenStrings, startToken, tokens, 0, (endToken - startToken + 1));

          StringList tokenList = new StringList(tokens);

          if (mDictionary.contains(tokenList)) {
            foundName = new Span(startToken, endToken + 1);
          }
        }
      }

      if (foundName != null) {
        foundNames.add(foundName);
      }
    }

    return foundNames.toArray(new Span[foundNames.size()]);
  }

  public void clearAdaptiveData() {
    // nothing to clear
  }
}
