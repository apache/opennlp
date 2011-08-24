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


package opennlp.tools.tokenize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Performs tokenization using character classes.
 */
public class SimpleTokenizer extends AbstractTokenizer {
  
  public static final SimpleTokenizer INSTANCE;
  
  static {
    INSTANCE = new SimpleTokenizer();
  }
  
  /**
   * @deprecated Use INSTANCE field instead to obtain an instance, constructor
   * will be made private in the future.
   */
  @Deprecated
  public SimpleTokenizer() {
  }
  
  public Span[] tokenizePos(String s) {
    CharacterEnum charType = CharacterEnum.WHITESPACE;
    CharacterEnum state = charType;

    List<Span> tokens = new ArrayList<Span>();
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


  /**
   *
   * @param args
   *
   * @throws IOException
   */
  @Deprecated
  public static void main(String[] args) throws IOException {
    if (args.length != 0) {
      System.err.println("Usage:  java opennlp.tools.tokenize.SimpleTokenizer < sentences");
      System.exit(1);
    }
    opennlp.tools.tokenize.Tokenizer tokenizer = new SimpleTokenizer();
    java.io.BufferedReader inReader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
    for (String line = inReader.readLine(); line != null; line = inReader.readLine()) {
      if (line.equals("")) {
        System.out.println();
      }
      else {
        String[] tokens = tokenizer.tokenize(line);
        if (tokens.length > 0) {
          System.out.print(tokens[0]);
        }
        for (int ti=1,tn=tokens.length;ti<tn;ti++) {
          System.out.print(" "+tokens[ti]);
        }
        System.out.println();
      }
    }
  }

}

class CharacterEnum {
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
