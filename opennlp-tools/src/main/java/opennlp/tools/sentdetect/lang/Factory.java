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


package opennlp.tools.sentdetect.lang;

import java.util.Collections;

import opennlp.tools.dictionary.AbbreviationDictionary;
import opennlp.tools.sentdetect.DefaultEndOfSentenceScanner;
import opennlp.tools.sentdetect.DefaultSDContextGenerator;
import opennlp.tools.sentdetect.EndOfSentenceScanner;
import opennlp.tools.sentdetect.SDContextGenerator;
import opennlp.tools.sentdetect.lang.th.SentenceContextGenerator;

public class Factory {
  
  public static final char[] defaultEosCharacters = new char[] { '.', '!', '?' };

  public EndOfSentenceScanner createEndOfSentenceScanner(String languageCode) {
    if ("th".equals(languageCode)) {
      return new DefaultEndOfSentenceScanner(new char[]{' ','\n'});
    }

    return new DefaultEndOfSentenceScanner(defaultEosCharacters);
  }
  
  public SDContextGenerator createSentenceContextGenerator(String languageCode, AbbreviationDictionary dict) {
    if ("th".equals(languageCode)) {
      return new SentenceContextGenerator();
    }

    return new DefaultSDContextGenerator(dict, defaultEosCharacters);
  }

  @Deprecated // always pass the abb dictionary, null is allowed.
  public SDContextGenerator createSentenceContextGenerator(String languageCode) {
    return new DefaultSDContextGenerator(Collections.<String>emptySet(), defaultEosCharacters);
  }
}