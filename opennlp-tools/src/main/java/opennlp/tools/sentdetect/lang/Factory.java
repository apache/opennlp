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

package opennlp.tools.sentdetect.lang;

import java.util.Collections;
import java.util.Set;

import opennlp.tools.sentdetect.DefaultEndOfSentenceScanner;
import opennlp.tools.sentdetect.DefaultSDContextGenerator;
import opennlp.tools.sentdetect.EndOfSentenceScanner;
import opennlp.tools.sentdetect.SDContextGenerator;
import opennlp.tools.sentdetect.lang.th.SentenceContextGenerator;

public class Factory {

  public static final char[] ptEosCharacters = new char[] { '.', '?', '!', ';',
      ':', '(', ')', '«', '»', '\'', '"' };

  public static final char[] defaultEosCharacters = new char[] { '.', '!', '?' };

  public static final char[] thEosCharacters = new char[] { ' ','\n' };

  public static final char[] jpEosCharacters = new char[] {'。', '！', '？'};

  public EndOfSentenceScanner createEndOfSentenceScanner(String languageCode) {

    return new DefaultEndOfSentenceScanner(getEOSCharacters(languageCode));
  }

  public EndOfSentenceScanner createEndOfSentenceScanner(
      char[] customEOSCharacters) {
    return new DefaultEndOfSentenceScanner(customEOSCharacters);
  }

  public SDContextGenerator createSentenceContextGenerator(String languageCode, Set<String> abbreviations) {

    if ("th".equals(languageCode)) {
      return new SentenceContextGenerator();
    } else if ("pt".equals(languageCode)) {
      return new DefaultSDContextGenerator(abbreviations, ptEosCharacters);
    }

    return new DefaultSDContextGenerator(abbreviations, defaultEosCharacters);
  }

  public SDContextGenerator createSentenceContextGenerator(
      Set<String> abbreviations, char[] customEOSCharacters) {
    return new DefaultSDContextGenerator(abbreviations, customEOSCharacters);
  }

  public SDContextGenerator createSentenceContextGenerator(String languageCode) {
    return createSentenceContextGenerator(languageCode, Collections.emptySet());
  }

  public char[] getEOSCharacters(String languageCode) {
    if ("th".equals(languageCode)) {
      return thEosCharacters;
    } else if ("pt".equals(languageCode)) {
      return ptEosCharacters;
    } else if ("jp".equals(languageCode)) {
      return jpEosCharacters;
    }

    return defaultEosCharacters;
  }
}
