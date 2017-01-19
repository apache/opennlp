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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.Span;

/**
 * Name finder based on a series of regular expressions.
 */
public final class RegexNameFinder implements TokenNameFinder {

  private Pattern mPatterns[];
  private String sType;
  private Map<String, Pattern[]> regexMap;

  public RegexNameFinder(Map<String, Pattern[]> regexMap) {
    this.regexMap = Objects.requireNonNull(regexMap, "regexMap must not be null");
  }

  public RegexNameFinder(Pattern patterns[], String type) {
    if (patterns == null || patterns.length == 0) {
      throw new IllegalArgumentException("patterns must not be null or empty!");
    }

    mPatterns = patterns;
    sType = type;
  }

  /**
   * use constructor {@link #RegexNameFinder(Pattern[], String)}
   * for single types, and/or constructor
   * {@link #RegexNameFinder(Map)}
   */
  @Deprecated
  public RegexNameFinder(Pattern patterns[]) {
    if (patterns == null || patterns.length == 0) {
      throw new IllegalArgumentException("patterns must not be null or empty!");
    }

    mPatterns = patterns;
    sType = null;
  }

  @Override
  public Span[] find(String tokens[]) {
    Map<Integer, Integer> sentencePosTokenMap = new HashMap<>();

    StringBuilder sentenceString = new StringBuilder(tokens.length * 10);

    for (int i = 0; i < tokens.length; i++) {

      int startIndex = sentenceString.length();
      sentencePosTokenMap.put(startIndex, i);

      sentenceString.append(tokens[i]);

      int endIndex = sentenceString.length();
      sentencePosTokenMap.put(endIndex, i + 1);

      if (i < tokens.length - 1) {
        sentenceString.append(' ');
      }
    }

    Collection<Span> annotations = new LinkedList<>();

    if (mPatterns == null && regexMap != null) {
      for (Map.Entry<String, Pattern[]> entry : regexMap.entrySet()) {
        for (Pattern mPattern : entry.getValue()) {
          Matcher matcher = mPattern.matcher(sentenceString);

          while (matcher.find()) {
            Integer tokenStartIndex =
                sentencePosTokenMap.get(matcher.start());
            Integer tokenEndIndex =
                sentencePosTokenMap.get(matcher.end());

            if (tokenStartIndex != null && tokenEndIndex != null) {
              Span annotation = new Span(tokenStartIndex, tokenEndIndex, entry.getKey());
              annotations.add(annotation);
            }
          }
        }
      }
    } else {
      for (Pattern mPattern : mPatterns) {
        Matcher matcher = mPattern.matcher(sentenceString);

        while (matcher.find()) {
          Integer tokenStartIndex =
              sentencePosTokenMap.get(matcher.start());
          Integer tokenEndIndex =
              sentencePosTokenMap.get(matcher.end());

          if (tokenStartIndex != null && tokenEndIndex != null) {
            Span annotation = new Span(tokenStartIndex, tokenEndIndex, sType);
            annotations.add(annotation);
          }
        }
      }
    }


    return annotations.toArray(
        new Span[annotations.size()]);
  }

  /**
   * NEW. This method removes the need for tokenization, but returns the Span
   * with character indices, rather than word.
   *
   * @param text
   * @return
   */
  public Span[] find(String text) {
    return getAnnotations(text);
  }

  private Span[] getAnnotations(String text) {
    Collection<Span> annotations = new LinkedList<>();
    if (mPatterns == null && regexMap != null) {
      for (Map.Entry<String, Pattern[]> entry : regexMap.entrySet()) {
        for (Pattern mPattern : entry.getValue()) {
          Matcher matcher = mPattern.matcher(text);

          while (matcher.find()) {
            Integer tokenStartIndex = matcher.start();
            Integer tokenEndIndex = matcher.end();
            Span annotation = new Span(tokenStartIndex, tokenEndIndex, entry.getKey());
            annotations.add(annotation);

          }
        }
      }
    } else {
      for (Pattern mPattern : mPatterns) {
        Matcher matcher = mPattern.matcher(text);

        while (matcher.find()) {
          Integer tokenStartIndex = matcher.start();
          Integer tokenEndIndex = matcher.end();
          Span annotation = new Span(tokenStartIndex, tokenEndIndex, sType);
          annotations.add(annotation);

        }
      }
    }

    return annotations.toArray(
        new Span[annotations.size()]);
  }

  @Override
  public void clearAdaptiveData() {
    // nothing to clear
  }

  public Pattern[] getmPatterns() {
    return mPatterns;
  }

  public void setmPatterns(Pattern[] mPatterns) {
    this.mPatterns = mPatterns;
  }

  public String getsType() {
    return sType;
  }

  public void setsType(String sType) {
    this.sType = sType;
  }
}
