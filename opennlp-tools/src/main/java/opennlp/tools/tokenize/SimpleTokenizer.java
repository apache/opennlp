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

package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Performs tokenization using character classes.
 */
public class SimpleTokenizer extends AbstractTokenizer {

  static class CharacterEnum {
    static final CharacterEnum WHITESPACE = new CharacterEnum("whitespace");
    static final CharacterEnum ALPHABETIC = new CharacterEnum("alphabetic");
    static final CharacterEnum NUMERIC = new CharacterEnum("numeric");
    static final CharacterEnum OTHER = new CharacterEnum("other");

    private String name;

    private CharacterEnum(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }
  
  public static final SimpleTokenizer INSTANCE;

  static {
    INSTANCE = new SimpleTokenizer();
  }

  /**
   * @deprecated Use INSTANCE field instead to obtain an instance, constructor
   *     will be made private in the future.
   */
  @Deprecated
  public SimpleTokenizer() {
  }

  public Span[] tokenizePos(String s) {
    CharacterEnum charType = CharacterEnum.WHITESPACE;
    CharacterEnum state = charType;

    List<Span> tokens = new ArrayList<>();
    int sl = s.length();
    int start = -1;
    char pc = 0;
    for (int ci = 0; ci < sl; ci++) {
      char c = s.charAt(ci);
      if (StringUtil.isWhitespace(c)) {
        charType = CharacterEnum.WHITESPACE;
      }
      else if (Character.isLetter(c)) {
        charType = CharacterEnum.ALPHABETIC;
      }
      else if (Character.isDigit(c)) {
        charType = CharacterEnum.NUMERIC;
      }
      else {
        charType = CharacterEnum.OTHER;
      }
      if (state == CharacterEnum.WHITESPACE) {
        if (charType != CharacterEnum.WHITESPACE) {
          start = ci;
        }
      }
      else {
        if (charType != state || charType == CharacterEnum.OTHER && c != pc) {
          tokens.add(new Span(start, ci));
          start = ci;
        }
      }
      state = charType;
      pc = c;
    }
    if (charType != CharacterEnum.WHITESPACE) {
      tokens.add(new Span(start, sl));
    }
    return tokens.toArray(new Span[tokens.size()]);
  }
}
