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

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import opennlp.tools.util.Span;

/**
 * Name finder based on a series of regular expressions.
 */
public final class RegexNameFinder implements TokenNameFinder {

  private final Pattern mPatterns[];

  public RegexNameFinder(Pattern patterns[]) {
    if (patterns == null || patterns.length == 0) {
      throw new IllegalArgumentException("patterns must not be null or emtpy!");
    }

    mPatterns = patterns;
  }

  public Span[] find(String tokens[]) {
    Map<Integer, Integer> sentencePosTokenMap = new HashMap<Integer, Integer>();

    StringBuffer sentenceString = new StringBuffer(tokens.length *  10);

    for (int i = 0; i < tokens.length; i++) {

      int startIndex = sentenceString.length();
      sentencePosTokenMap.put(startIndex, i);

      sentenceString.append(tokens[i]);

      int endIndex = sentenceString.length();
      sentencePosTokenMap.put(endIndex,
          new Integer(i));

      if (i < tokens.length - 1) {
        sentenceString.append(' ');
      }
    }

    Collection<Span> annotations = new LinkedList<Span>();

    for (int i = 0; i < mPatterns.length; i++) {
      Matcher matcher = mPatterns[i].matcher(sentenceString);

      while (matcher.find()) {
        Integer tokenStartIndex =
            sentencePosTokenMap.get(matcher.start());
        Integer tokenEndIndex =
            sentencePosTokenMap.get(matcher.end());

        if (tokenStartIndex != null && tokenEndIndex != null) {
          Span annotation = new Span(tokenStartIndex.intValue(),
              tokenEndIndex.intValue());

          annotations.add(annotation);
        }
      }
    }

    return annotations.toArray(
        new Span[annotations.size()]);
  }
  
  public void clearAdaptiveData() {
    // nothing to clear
  }
}
