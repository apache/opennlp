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

/**
 * TODO: Move rules into profiles
 */
public class EnglishRule {
  private static LanguageRule languageRule = new LanguageRule("eng", new ArrayList<Rule>());

  public EnglishRule() {
    common();
    number();
    name();
    betweenPunctuation();
    list();
  }

  public LanguageRule getLanguageRule() {
    return languageRule;
  }


  private void common() {

    languageRule.addRule(new Rule(true, "\\n", ""));
    languageRule.addRule(new Rule(true, " ", "\\n"));

    languageRule.addRule(new Rule(true, "[\\.\\?!]+\\s+", "[^\\.]"));

    languageRule.addRule(new Rule(true, "[\\.\\?!]+", "\\s*(A |Being|Did|For|He|" +
        "How|However|I|In|It|Millions|More|She|That|The|There|They|We|What|When|Where|Who|Why)"));

    languageRule.addRule(new Rule(true, "[!?\\.-][\\\"\\'“”]\\s+", "[A-Z]"));

    languageRule.addRule(new Rule(true, "(?<=\\S)(!|\\?){3,}", "(?=(\\s|\\Z|$))"));

    languageRule.addRule(new Rule(false, "[\\.\\?!]+\\s*", "(?=[\\.\\?!])"));

    languageRule.addRule(new Rule(false, "([a-zA-z]°)\\.\\s*", "(?=\\d+)"));

    languageRule.addRule(new Rule(false, "\\s", "(?=[a-z])"));
  }

  private void number() {
    languageRule.addRule(new Rule(false, "\\d\\.", "(?=\\d)"));

  }

  private void name() {

    languageRule.addRule(new Rule(false, "(Mr|Mrs|Ms|Dr|p.m|a.m|tel)\\.", "\\s*"));

    languageRule.addRule(new Rule(true, "(P\\.M\\.|A\\.M\\.)", "\\s+"));

    languageRule.addRule(new Rule(false, "(?<=(?<=^)[A-Z]\\.\\s+|(?<=\\A)[A-Z]\\.\\s+|" +
        "[A-Z]\\.\\s+|(?<=^)[A-Z][a-z]\\.\\s+|(?<=\\A)[A-Z][a-z]\\.\\s+|(?<=\\s)[A-Z]" +
        "[a-z]\\.\\s)", "(?!(A |Being|Did|For|He|How|However|I|In|It|Millions|" +
        "More|She|That|The|There|They|We|What|When|Where|Who|Why))"));
  }

  private void betweenPunctuation() {

    languageRule.addRule(new Rule(false, "(?<=\\s)'(?:[^']|'[a-zA-Z])*'", ""));

    languageRule.addRule(new Rule(false, "(?<=\\s)‘(?:[^’]|’[a-zA-Z])*’", ""));

    languageRule.addRule(new Rule(false, "\"(?>[^\"\\\\]+|\\\\{2}|\\\\.)*\"", ""));

    languageRule.addRule(new Rule(false, "«(?>[^»\\\\]+|\\\\{2}|\\\\.)*»", ""));

    languageRule.addRule(new Rule(false, "“(?>[^”\\\\]+|\\\\{2}|\\\\.)*”", ""));

    languageRule.addRule(new Rule(false, "\\[(?>[^\\]\\\\]+|\\\\{2}|\\\\.)*\\]", ""));

    languageRule.addRule(new Rule(false, "\\((?>[^\\(\\)\\\\]+|\\\\{2}|\\\\.)*\\)", ""));

    languageRule.addRule(new Rule(false, "(?<=\\s)\\-\\-(?>[^\\-\\-])*\\-\\-", ""));
  }

  private void list() {

    languageRule.addRule(new Rule(false, "((?<=^)[a-z]\\.|(?<=\\A)[a-z]\\.|(?<=\\s)[a-z]\\.)",
        "\\s*(?!(A |Being|Did|For|He|How|However|I|In|It|Millions|More|She|That|The|There|" +
            "They|We|What|When|Where|Who|Why))"));

    //number_list
    languageRule.addRule(new Rule(false, "(?<=\\s)\\d{1,2}\\.(\\s)|^\\d{1,2}\\.(\\s)|" +
        "(?<=\\s)\\d{1,2}\\.(\\))|^\\d{1,2}\\.(\\))|(?<=\\s\\-)\\d{1,2}\\.(\\s)|" +
        "(?<=^\\-)\\d{1,2}\\.(\\s)|(?<=\\s\\⁃)\\d{1,2}\\.(\\s)|(?<=^\\⁃)\\d{1,2}\\.(\\s)|" +
        "(?<=\\s\\-)\\d{1,2}\\.(\\))|(?<=^\\-)\\d{1,2}\\.(\\))|(?<=\\s\\⁃)\\d{1,2}\\.(\\))|" +
        "(?<=^\\⁃)\\d{1,2}\\.(\\))|(\\•)\\s*\\d{1,2}\\.(\\s)|(?<=\\s)\\d{1,2}(\\))", "\\s*"));

    //number_list
    languageRule.addRule(new Rule(true, "", "\\s+((?<=\\s)\\d{1,2}\\.(?=\\s)|" +
        "^\\d{1,2}\\.(?=\\s)|(?<=\\s)\\d{1,2}\\.(?=\\))|^\\d{1,2}\\.(?=\\))|((?<=\\s)\\-)" +
        "\\d{1,2}\\.(?=\\s)|(^\\-)\\d{1,2}\\.(?=\\s)|((?<=\\s)\\⁃)\\d{1,2}\\.(?=\\s)|" +
        "(^\\⁃)\\d{1,2}\\.(?=\\s)|((?<=\\s)\\-)\\d{1,2}\\.(?=\\))|(^\\-)\\d{1,2}\\.(?=\\))|" +
        "((?<=\\s)\\⁃)\\d{1,2}\\.(?=\\))|(^\\⁃)\\d{1,2}\\.(?=\\))|(\\•)\\s*\\d{1,2}\\.(\\s)|" +
        "(?<=\\s)\\d{1,2}(?=\\)))"));
  }

}
