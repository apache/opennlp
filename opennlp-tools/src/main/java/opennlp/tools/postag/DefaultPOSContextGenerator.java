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


package opennlp.tools.postag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Cache;
import opennlp.tools.util.StringList;

/**
 * A default {@link POSContextGenerator context generator} for a {@link POSTagger}.
 *
 * @see POSTagger
 * @see POSTaggerME
 */
public class DefaultPOSContextGenerator implements POSContextGenerator {

  protected final String SE = "*SE*";
  protected final String SB = "*SB*";
  private static final int PREFIX_LENGTH = 4;
  private static final int SUFFIX_LENGTH = 4;

  private static final Pattern hasCap = Pattern.compile("[A-Z]");
  private static final Pattern hasNum = Pattern.compile("[0-9]");

  private Cache<String, String[]> contextsCache;
  private Object wordsKey;

  private final Dictionary dict;

  /**
   * Initializes a {@link DefaultPOSContextGenerator} instance.
   * A cache size of {@code 0} will be used as default.
   *
   * @param dict The {@link Dictionary} to be used.
   */
  public DefaultPOSContextGenerator(Dictionary dict) {
    this(0,dict);
  }

  /**
   * Initializes a {@link DefaultPOSContextGenerator} instance.
   *
   * @param cacheSize The size of the {@link Cache} to set.
   *                  Must be greater than {@code 0} to have an effect.
   * @param dict The {@link Dictionary} to be used.
   */
  public DefaultPOSContextGenerator(int cacheSize, Dictionary dict) {
    this.dict = dict;

    if (cacheSize > 0) {
      contextsCache = new Cache<>(cacheSize);
    }
  }

  protected static String[] getPrefixes(String lex) {
    String[] prefs = new String[PREFIX_LENGTH];
    for (int li = 0; li < PREFIX_LENGTH; li++) {
      prefs[li] = lex.substring(0, StrictMath.min(li + 1, lex.length()));
    }
    return prefs;
  }

  protected static String[] getSuffixes(String lex) {
    String[] suffs = new String[SUFFIX_LENGTH];
    for (int li = 0; li < SUFFIX_LENGTH; li++) {
      suffs[li] = lex.substring(StrictMath.max(lex.length() - li - 1, 0));
    }
    return suffs;
  }

  /**
   * Returns the context for making a postag decision at the specified token {@code index}
   * given the specified {@code tokens} and previous {@code tags}.
   *
   * @param index The index of the token for which the context is provided.
   * @param sequence The token sequence representing a sentence.
   * @param priorDecisions The tags assigned to the previous words in the sentence.
   * @param additionalContext The context for additional information.
   *                          
   * @return The context for making a postag decision at the specified token {@code index}
   *     given the specified {@code tokens} and previous {@code tags}.
   */
  @Override
  public String[] getContext(int index, String[] sequence, String[] priorDecisions,
      Object[] additionalContext) {
    return getContext(index,sequence,priorDecisions);
  }

  /**
   * Returns the context for making a postag decision at the specified token {@code index}
   * given the specified {@code tokens} and previous {@code tags}.
   *
   * @param index The index of the token for which the context is provided.
   * @param tokens The tokens representing a sentence.
   * @param tags The tags assigned to the previous words in the sentence.
   *             
   * @return The context for making a postag decision at the specified token {@code index}
   *     given the specified {@code tokens} and previous {@code tags}.
   */
  public String[] getContext(int index, Object[] tokens, String[] tags) {
    String next, nextnext = null, lex, prev, prevprev = null;
    String tagprev, tagprevprev;
    tagprev = tagprevprev = null;

    lex = tokens[index].toString();
    if (tokens.length > index + 1) {
      next = tokens[index + 1].toString();
      if (tokens.length > index + 2)
        nextnext = tokens[index + 2].toString();
      else
        nextnext = SE; // Sentence End

    }
    else {
      next = SE; // Sentence End
    }

    if (index - 1 >= 0) {
      prev =  tokens[index - 1].toString();
      tagprev =  tags[index - 1];

      if (index - 2 >= 0) {
        prevprev = tokens[index - 2].toString();
        tagprevprev = tags[index - 2];
      }
      else {
        prevprev = SB; // Sentence Beginning
      }
    }
    else {
      prev = SB; // Sentence Beginning
    }
    String cacheKey = index + tagprev + tagprevprev;
    if (contextsCache != null) {
      if (wordsKey == tokens) {
        String[] cachedContexts = contextsCache.get(cacheKey);
        if (cachedContexts != null) {
          return cachedContexts;
        }
      }
      else {
        contextsCache.clear();
        wordsKey = tokens;
      }
    }
    List<String> e = new ArrayList<>();
    e.add("default");
    // add the word itself
    e.add("w=" + lex);

    if (dict == null || !dict.contains(new StringList(lex))) {
      // do some basic suffix analysis
      String[] suffs = getSuffixes(lex);
      for (String suff : suffs) {
        e.add("suf=" + suff);
      }

      String[] prefs = getPrefixes(lex);
      for (String pref : prefs) {
        e.add("pre=" + pref);
      }
      // see if the word has any special characters
      if (lex.indexOf('-') != -1) {
        e.add("h");
      }

      if (hasCap.matcher(lex).find()) {
        e.add("c");
      }

      if (hasNum.matcher(lex).find()) {
        e.add("d");
      }
    }
    // add the words and pos's of the surrounding context
    if (prev != null) {
      e.add("p=" + prev);
      if (tagprev != null) {
        e.add("t=" + tagprev);
      }
      if (prevprev != null) {
        e.add("pp=" + prevprev);
        if (tagprevprev != null) {
          e.add("t2=" + tagprevprev + "," + tagprev);
        }
      }
    }

    if (next != null) {
      e.add("n=" + next);
      if (nextnext != null) {
        e.add("nn=" + nextnext);
      }
    }
    String[] contexts = e.toArray(new String[0]);
    if (contextsCache != null) {
      contextsCache.put(cacheKey,contexts);
    }
    return contexts;
  }

}
