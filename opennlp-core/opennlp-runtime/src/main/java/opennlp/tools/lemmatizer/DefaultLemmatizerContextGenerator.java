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

package opennlp.tools.lemmatizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Simple feature generator for learning statistical lemmatizers.
 * <p>
 * Features based on Grzegorz Chrupała. 2008.
 * <a href="http://grzegorz.chrupala.me/papers/phd-single.pdf">
 * Towards a Machine-Learning Architecture for Lexical Functional Grammar Parsing.
 * </a> PhD dissertation, Dublin City University
 */
public class DefaultLemmatizerContextGenerator implements LemmatizerContextGenerator {

  private static final int PREFIX_LENGTH = 5;
  private static final int SUFFIX_LENGTH = 7;

  private static final Pattern PATTERN_HAS_CAP = Pattern.compile("[A-Z]");
  private static final Pattern PATTERN_HAS_NUM = Pattern.compile("[0-9]");

  public DefaultLemmatizerContextGenerator() {
  }

  protected static String[] getPrefixes(String lex) {
    String[] prefs = new String[PREFIX_LENGTH];
    for (int li = 1; li < PREFIX_LENGTH; li++) {
      prefs[li] = lex.substring(0, StrictMath.min(li + 1, lex.length()));
    }
    return prefs;
  }

  protected static String[] getSuffixes(String lex) {
    String[] suffs = new String[SUFFIX_LENGTH];
    for (int li = 1; li < SUFFIX_LENGTH; li++) {
      suffs[li] = lex.substring(StrictMath.max(lex.length() - li - 1, 0));
    }
    return suffs;
  }

  @Override
  public String[] getContext(int index, String[] sequence, String[] priorDecisions,
      Object[] additionalContext) {
    return getContext(index, sequence, (String[]) additionalContext[0], priorDecisions);
  }

  @Override
  public String[] getContext(int index, String[] toks, String[] tags, String[] preds) {
    // Word
    String w0;
    // Tag
    String t0;
    // Previous prediction
    String p_1;

    String lex = toks[index];
    if (index < 1) {
      p_1 = "p_1=bos";
    }
    else {
      p_1 = "p_1=" + preds[index - 1];
    }

    w0 = "w0=" + toks[index];
    t0 = "t0=" + tags[index];

    List<String> features = new ArrayList<>();

    features.add(w0);
    features.add(t0);
    features.add(p_1);
    features.add(p_1 + t0);
    features.add(p_1 + w0);

    // do some basic suffix analysis
    String[] suffs = getSuffixes(lex);
    for (String suff : suffs) {
      features.add("suf=" + suff);
    }

    String[] prefs = getPrefixes(lex);
    for (String pref : prefs) {
      features.add("pre=" + pref);
    }
    // see if the word has any special characters
    if (lex.indexOf('-') != -1) {
      features.add("h");
    }

    if (PATTERN_HAS_CAP.matcher(lex).find()) {
      features.add("c");
    }

    if (PATTERN_HAS_NUM.matcher(lex).find()) {
      features.add("d");
    }

    return features.toArray(new String[0]);
  }
}

