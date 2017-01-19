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

package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.StringUtil;

/**
 * Generate events for maxent decisions for tokenization.
 */
public class DefaultTokenContextGenerator implements TokenContextGenerator {

  protected final Set<String> inducedAbbreviations;

  /**
   * Creates a default context generator for tokenizer.
   */
  public DefaultTokenContextGenerator() {
    this(Collections.emptySet());
  }

  /**
   * Creates a default context generator for tokenizer.
   *
   * @param inducedAbbreviations the induced abbreviations
   */
  public DefaultTokenContextGenerator(Set<String> inducedAbbreviations) {
    this.inducedAbbreviations = inducedAbbreviations;
  }

  /* (non-Javadoc)
   * @see opennlp.tools.tokenize.TokenContextGenerator#getContext(java.lang.String, int)
   */
  public String[] getContext(String sentence, int index) {
    List<String> preds = createContext(sentence, index);
    String[] context = new String[preds.size()];
    preds.toArray(context);
    return context;
  }

  /**
   * Returns an {@link ArrayList} of features for the specified sentence string
   * at the specified index. Extensions of this class can override this method
   * to create a customized {@link TokenContextGenerator}
   *
   * @param sentence
   *          the token been analyzed
   * @param index
   *          the index of the character been analyzed
   * @return an {@link ArrayList} of features for the specified sentence string
   *         at the specified index.
   */
  protected List<String> createContext(String sentence, int index) {
    List<String> preds = new ArrayList<>();
    String prefix = sentence.substring(0, index);
    String suffix = sentence.substring(index);
    preds.add("p=" + prefix);
    preds.add("s=" + suffix);
    if (index > 0) {
      addCharPreds("p1", sentence.charAt(index - 1), preds);
      if (index > 1) {
        addCharPreds("p2", sentence.charAt(index - 2), preds);
        preds.add("p21=" + sentence.charAt(index - 2) + sentence.charAt(index - 1));
      }
      else {
        preds.add("p2=bok");
      }
      preds.add("p1f1=" + sentence.charAt(index - 1) + sentence.charAt(index));
    }
    else {
      preds.add("p1=bok");
    }
    addCharPreds("f1", sentence.charAt(index), preds);
    if (index + 1 < sentence.length()) {
      addCharPreds("f2", sentence.charAt(index + 1), preds);
      preds.add("f12=" + sentence.charAt(index) + sentence.charAt(index + 1));
    }
    else {
      preds.add("f2=bok");
    }
    if (sentence.charAt(0) == '&' && sentence.charAt(sentence.length() - 1) == ';') {
      preds.add("cc");//character code
    }

    if (index == sentence.length() - 1 && inducedAbbreviations.contains(sentence)) {
      preds.add("pabb");
    }

    return preds;
  }


  /**
   * Helper function for getContext.
   */
  protected void addCharPreds(String key, char c, List<String> preds) {
    preds.add(key + "=" + c);
    if (Character.isLetter(c)) {
      preds.add(key + "_alpha");
      if (Character.isUpperCase(c)) {
        preds.add(key + "_caps");
      }
    }
    else if (Character.isDigit(c)) {
      preds.add(key + "_num");
    }
    else if (StringUtil.isWhitespace(c)) {
      preds.add(key + "_ws");
    }
    else {
      if (c == '.' || c == '?' || c == '!') {
        preds.add(key + "_eos");
      }
      else if (c == '`' || c == '"' || c == '\'') {
        preds.add(key + "_quote");
      }
      else if (c == '[' || c == '{' || c == '(') {
        preds.add(key + "_lp");
      }
      else if (c == ']' || c == '}' || c == ')') {
        preds.add(key + "_rp");
      }
    }
  }
}
