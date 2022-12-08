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
 * A default {@link TokenContextGenerator} which produces events for maxent decisions
 * for tokenization.
 */
public class DefaultTokenContextGenerator implements TokenContextGenerator {

  protected final Set<String> inducedAbbreviations;

  /**
   * Initializes a plain {@link DefaultTokenContextGenerator} instance.
   */
  public DefaultTokenContextGenerator() {
    this(Collections.emptySet());
  }

  /**
   * Initializes a customized {@link DefaultTokenContextGenerator} instance via
   * a set of {@code inducedAbbreviations}.
   *
   * @param inducedAbbreviations The induced abbreviations to be used for this instance.
   */
  public DefaultTokenContextGenerator(Set<String> inducedAbbreviations) {
    this.inducedAbbreviations = inducedAbbreviations;
  }

  @Override
  public String[] getContext(String sentence, int index) {
    List<String> preds = createContext(sentence, index);
    String[] context = new String[preds.size()];
    preds.toArray(context);
    return context;
  }

  /**
   * Computes a {@link List} of features for the specified {@code sentence}
   * at the specified {@code index}. Extensions of {@link DefaultTokenContextGenerator}
   * can override this method to create a customized behaviour.
   *
   * @param sentence
   *          The sentence to create features for.
   * @param index
   *          The positional index. Must be a non-negative number or {@code 0}.
   * @return A {@link List} of features for the specified {@code sentence} string
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
   * Helper function for {@link #createContext} that appends to a given {@code key}
   * a fixed text sequence depending on {@code c}. The resulting combination is added
   * to the given list {@code preds}.
   *
   * @param key The input string to process.
   * @param c   A character used to discriminate which fixed text shall be appended.
   * @param preds The list into which the resulting combinations will be added.
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
