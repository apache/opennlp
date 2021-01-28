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

package opennlp.tools.sentdetect.segment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class LanguageTool {

  private LanguageRule languageRule;

  private String languageName;

  private Map<String, Object> cache;

  public static final String MAX_LOOKBEHIND_LENGTH_PARAM = "maxLookbehindLength";

  public static final int DEFAULT_MAX_LOOKBEHIND_LENGTH = 100;

  private int maxLookbehindLength;

  private Map<String, Object> parameterMap;

  private List<Rule> breakRuleList;

  private Pattern noBreakPattern;

  public LanguageTool(String languageName, LanguageRule languageRule) {
    this(languageName, languageRule, Collections.emptyMap());
  }

  public LanguageTool(String languageName, LanguageRule languageRule, Map<String, Object> paramMap) {
    this.languageRule = languageRule;
    this.languageName = languageName;
    parameterMap = new HashMap<String, Object>(paramMap);
    if (parameterMap.get(MAX_LOOKBEHIND_LENGTH_PARAM) != null) {
      this.maxLookbehindLength = (int) parameterMap.get(MAX_LOOKBEHIND_LENGTH_PARAM);
    } else {
      this.maxLookbehindLength = DEFAULT_MAX_LOOKBEHIND_LENGTH;
    }
    init();
  }

  private void init() {

    this.cache = new ConcurrentHashMap<String, Object>();
    this.breakRuleList = new ArrayList<Rule>();
    StringBuilder noBreakPatternBuilder = new StringBuilder();

    for (Rule rule : languageRule.getRuleList()) {

      if (rule.isBreak()) {
        breakRuleList.add(rule);
      } else {
        if (noBreakPatternBuilder.length() > 0) {
          noBreakPatternBuilder.append('|');
        }
        String patternString = createNoBreakPatternString(rule);
        noBreakPatternBuilder.append(patternString);
      }
    }

    if (noBreakPatternBuilder.length() > 0) {
      String noBreakPatternString = noBreakPatternBuilder.toString();
      noBreakPattern = compile(noBreakPatternString);
    } else {
      noBreakPattern = null;
    }

  }

  public Map<String, Object> getParameterMap() {
    return parameterMap;
  }

  public LanguageRule getLanguageRule() {
    return languageRule;
  }

  public String getLanguageName() {
    return languageName;
  }

  public Map<String, Object> getCache() {
    return cache;
  }

  public Pattern compile(String regex) {
    String key = "PATTERN_" + regex;
    Pattern pattern = (Pattern) getCache().get(key);
    if (pattern == null) {
      pattern = Pattern.compile(regex);
      getCache().put(key, pattern);
    }
    return pattern;
  }

  public List<Rule> getBreakRuleList() {
    return breakRuleList;
  }

  public Pattern getNoBreakPattern() {
    return noBreakPattern;
  }

  private String createNoBreakPatternString(Rule rule) {

    StringBuilder patternBuilder = new StringBuilder();

    // As Java does not allow infinite length patterns
    // in lookbehind, before pattern need to be shortened.
    String beforePattern = RuleUtil.finitize(rule.getBeforePattern(), maxLookbehindLength);
    String afterPattern = rule.getAfterPattern();

    patternBuilder.append("(?:");
    if (beforePattern.length() > 0) {
      patternBuilder.append(beforePattern);
    }
    if (afterPattern.length() > 0) {
      patternBuilder.append(afterPattern);
    }
    patternBuilder.append(")");
    return patternBuilder.toString();
  }
}
