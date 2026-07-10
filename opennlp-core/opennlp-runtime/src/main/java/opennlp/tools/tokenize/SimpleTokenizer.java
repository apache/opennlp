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
import opennlp.tools.util.normalizer.UnicodeWhitespace;

/**
 * A basic {@link Tokenizer} implementation which performs tokenization
 * using character classes.
 * <p>
 * The whitespace class is the Unicode {@code White_Space} set
 * ({@link UnicodeWhitespace#isWhitespace(int)}). Since 3.0 the next line control
 * {@code U+0085} separates tokens and the {@code U+001C}..{@code U+001F} information
 * separators no longer do (they fall into the OTHER class), matching the standard
 * instead of the JVM predicates.
 * <p>
 * With {@link #setKeepNewLines(boolean)} enabled, the line separator code points
 * {@code \n}, {@code \r} and, since 3.0, the next line control {@code U+0085}, the line
 * separator {@code U+2028} and the paragraph separator {@code U+2029} are returned as
 * tokens of their own; all other whitespace is dropped.
 * <p>
 * To obtain an instance of this tokenizer use the static final
 * {@link #INSTANCE} field.
 */
public class SimpleTokenizer extends AbstractTokenizer {

  static class CharacterEnum {
    static final CharacterEnum WHITESPACE = new CharacterEnum("whitespace");
    static final CharacterEnum ALPHABETIC = new CharacterEnum("alphabetic");
    static final CharacterEnum NUMERIC = new CharacterEnum("numeric");
    static final CharacterEnum OTHER = new CharacterEnum("other");

    private final String name;

    private CharacterEnum(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  /**
   * Use this static reference to retrieve an instance of the
   * {@link SimpleTokenizer}.
   */
  public static final SimpleTokenizer INSTANCE = new SimpleTokenizer();

  private SimpleTokenizer() {
  }

  @Override
  public Span[] tokenizePos(String s) {
    CharacterEnum charType = CharacterEnum.WHITESPACE;
    CharacterEnum state = charType;

    List<Span> tokens = new ArrayList<>();
    int sl = s.length();
    int start = -1;
    char pc = 0;
    for (int ci = 0; ci < sl; ci++) {
      char c = s.charAt(ci);
      if (UnicodeWhitespace.isWhitespace(c)) {
        charType = CharacterEnum.WHITESPACE;
      }
      else if (Character.isAlphabetic(c)) {
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
      if (keepNewLines && isLineSeparator(c)) {
        // Use the separator's own position: after a preceding whitespace character (or at
        // the start of the input) `start` still points at stale, or no, token content.
        tokens.add(new Span(ci, ci + 1));
        start = ci + 1;
      }
      state = charType;
      pc = c;
    }
    if (charType != CharacterEnum.WHITESPACE) {
      tokens.add(new Span(start, sl));
    }
    return tokens.toArray(new Span[0]);
  }

  // The line separator code points emitted as tokens under keepNewLines: the ASCII pair,
  // plus NEL (U+0085) and the Unicode line and paragraph separators, which count as
  // whitespace since 3.0. (The previous version compared against the Character category
  // constants LINE_SEPARATOR and LETTER_NUMBER, whose byte values happen to be 13 and 10.)
  private boolean isLineSeparator(char character) {
    return character == '\n' || character == '\r'
        || character == '\u0085' || character == '\u2028' || character == '\u2029';
  }

}
