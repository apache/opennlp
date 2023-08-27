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
 * A {@link TokenNameFinder} implementation based on a series of regular expressions.
 */
public final class RegexNameFinder implements TokenNameFinder {

  private Pattern[] mPatterns;
  private String sType;
  private Map<String, Pattern[]> regexMap;

  /**
   * Initializes a {@link RegexNameFinder} instance.
   * 
   * @param regexMap A {@link Map} where the key is a type, and the value is a
   *                 {@link Pattern[]}. Must not be {@code null}.
   */
  public RegexNameFinder(Map<String, Pattern[]> regexMap) {
    this.regexMap = Objects.requireNonNull(regexMap, "regexMap must not be null");
  }

  /**
   * Initializes a {@link RegexNameFinder} instance.
   *
   * @param patterns The {@link Pattern[] patterns} to use.
   *                 Must not be {@code null} and not be empty.
   * @param type The type to use.
   *
   * @throws IllegalArgumentException Thrown if {@code patterns} were {@code null} or empty.
   */
  public RegexNameFinder(Pattern[] patterns, String type) {
    if (patterns == null || patterns.length == 0) {
      throw new IllegalArgumentException("patterns must not be null or empty!");
    }

    mPatterns = patterns;
    sType = type;
  }

  @Override
  public Span[] find(String[] tokens) {
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

    if (regexMap != null) {
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

    return annotations.toArray(new Span[0]);
  }

  /**
   * Finds {@link Span spans} with character indices, rather than word.
   *
   * @param text The text to use.
   * @return A {@link Span[]} representing the annotations.
   */
  public Span[] find(String text) {
    return getAnnotations(text);
  }

  private Span[] getAnnotations(String text) {
    Collection<Span> annotations = new LinkedList<>();
    if (regexMap != null) {
      for (Map.Entry<String, Pattern[]> entry : regexMap.entrySet()) {
        for (Pattern mPattern : entry.getValue()) {
          Matcher matcher = mPattern.matcher(text);
          while (matcher.find()) {
            Span annotation = new Span(matcher.start(), matcher.end(), entry.getKey());
            annotations.add(annotation);
          }
        }
      }
    } else {
      for (Pattern mPattern : mPatterns) {
        Matcher matcher = mPattern.matcher(text);
        while (matcher.find()) {
          Span annotation = new Span(matcher.start(), matcher.end(), sType);
          annotations.add(annotation);
        }
      }
    }

    return annotations.toArray(new Span[0]);
  }

  @Override
  public void clearAdaptiveData() {
    // nothing to clear
  }

  /**
   * @return Retrieves the {@link Pattern matching patterns} used.
   */
  public Pattern[] getMatchingPatterns() {
    return mPatterns;
  }

  /**
   * @param mPatterns The {@link Pattern matching patterns} to be set.
   */
  public void setMatchingPatterns(Pattern[] mPatterns) {
    this.mPatterns = mPatterns;
  }

  /**
   * @return Retrieves the {@link Span} type used.
   */
  public String getSpanType() {
    return sType;
  }

  /**
   * @param sType Sets a (different) {@link Span} type.
   */
  public void setSpanType(String sType) {
    this.sType = sType;
  }
}
