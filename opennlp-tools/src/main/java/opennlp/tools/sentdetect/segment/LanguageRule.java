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
import java.util.List;

/**
 * Represents rule for segmenting text in some language. Contains {@link Rule}
 * list.
 *
 */
public class LanguageRule {

  private List<Rule> ruleList;

  private String name;

  /**
   * Creates language rule.
   *
   * @param name language rule name
   * @param ruleList rule list (it will be shallow copied)
   */
  public LanguageRule(String name, List<Rule> ruleList) {
    this.ruleList = new ArrayList<Rule>(ruleList);
    this.name = name;
  }

  /**
   * Creates empty language rule.
   *
   * @param name language rule name
   */
  public LanguageRule(String name) {
    this(name, new ArrayList<Rule>());
  }

  /**
   * @return unmodifiable rules list
   */
  public List<Rule> getRuleList() {
    return Collections.unmodifiableList(ruleList);
  }

  /**
   * Adds rule to the end of rule list.
   * @param rule
   */
  public void addRule(Rule rule) {
    ruleList.add(rule);
  }

  /**
   * @return language rule name
   */
  public String getName() {
    return name;
  }

}
