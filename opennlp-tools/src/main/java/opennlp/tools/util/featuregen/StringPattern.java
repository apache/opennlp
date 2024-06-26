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

package opennlp.tools.util.featuregen;

/**
 * Recognizes predefined patterns in strings.
 */
public class StringPattern {

  private static final int INITAL_CAPITAL_LETTER = 0x1;
  private static final int ALL_CAPITAL_LETTER = 0x1 << 1;
  private static final int ALL_LOWERCASE_LETTER = 0x1 << 2;
  private static final int ALL_LETTERS = 0x1 << 3;
  private static final int ALL_DIGIT = 0x1 << 4;
  private static final int ALL_HIRAGANA = 0x1 << 5;
  private static final int ALL_KATAKANA = 0x1 << 6;
  private static final int CONTAINS_PERIOD = 0x1 << 7;
  private static final int CONTAINS_COMMA = 0x1 << 8;
  private static final int CONTAINS_SLASH = 0x1 << 9;
  private static final int CONTAINS_DIGIT = 0x1 << 10;
  private static final int CONTAINS_HYPHEN = 0x1 << 11;
  private static final int CONTAINS_LETTERS = 0x1 << 12;
  private static final int CONTAINS_UPPERCASE = 0x1 << 13;

  private final int pattern;

  private final int digits;

  private StringPattern(int pattern, int digits) {
    this.pattern = pattern;
    this.digits = digits;
  }

  public static StringPattern recognize(String token) {

    int pattern = ALL_CAPITAL_LETTER | ALL_LOWERCASE_LETTER | ALL_DIGIT | ALL_LETTERS
        | ALL_HIRAGANA | ALL_KATAKANA;

    int digits = 0;

    for (int i = 0; i < token.length(); i++) {
      final char ch = token.charAt(i);
      final int letterType = Character.getType(ch);
      boolean isLetter = letterType == Character.UPPERCASE_LETTER ||
          letterType == Character.LOWERCASE_LETTER ||
          letterType == Character.TITLECASE_LETTER ||
          letterType == Character.MODIFIER_LETTER ||
          letterType == Character.OTHER_LETTER;

      if (isLetter) {
        pattern |= CONTAINS_LETTERS;
        pattern &= ~ALL_DIGIT;

        if (letterType == Character.UPPERCASE_LETTER) {
          if (i == 0) {
            pattern |= INITAL_CAPITAL_LETTER;
          }

          pattern |= CONTAINS_UPPERCASE;

          pattern &= ~ALL_LOWERCASE_LETTER;
        } else {
          pattern &= ~ALL_CAPITAL_LETTER;
        }
      } else {
        // contains chars other than letter, this means
        // it can not be one of these:
        pattern &= ~ALL_LETTERS;
        pattern &= ~ALL_CAPITAL_LETTER;
        pattern &= ~ALL_LOWERCASE_LETTER;

        if (letterType == Character.DECIMAL_DIGIT_NUMBER) {
          pattern |= CONTAINS_DIGIT;
          pattern &= ~(ALL_HIRAGANA | ALL_KATAKANA);
          digits++;
        } else {
          pattern &= ~ALL_DIGIT;
        }

        switch (ch) {
          case ',':
            pattern |= CONTAINS_COMMA;
            break;

          case '.':
            pattern |= CONTAINS_PERIOD;
            break;

          case '/':
            pattern |= CONTAINS_SLASH;
            break;

          case '-':
            pattern |= CONTAINS_HYPHEN;
            break;

          default:
            break;
        }
      }

      // for Japanese...
      final int codePoint = token.codePointAt(i);
      final Character.UnicodeScript us = Character.UnicodeScript.of(codePoint);
      if (us != Character.UnicodeScript.COMMON) {
        if (us == Character.UnicodeScript.LATIN) {
          pattern &= ~(ALL_HIRAGANA | ALL_KATAKANA);
        }
        else if (us == Character.UnicodeScript.HAN) {
          pattern &= ~(ALL_HIRAGANA | ALL_KATAKANA | ALL_LOWERCASE_LETTER);
        }
        else if (us == Character.UnicodeScript.HIRAGANA) {
          pattern &= ~(ALL_KATAKANA | ALL_LOWERCASE_LETTER);
        }
        else if (us == Character.UnicodeScript.KATAKANA) {
          pattern &= ~(ALL_HIRAGANA | ALL_LOWERCASE_LETTER);
        }
      }
      else {
        if (ch != '・' && ch != 'ー' && ch != '〜')
          pattern &= ~(ALL_HIRAGANA | ALL_KATAKANA);
      }
    }

    return new StringPattern(pattern, digits);
  }

  /**
   * @return {@code true} if all characters are letters.
   */
  public boolean isAllLetter() {
    return (pattern & ALL_LETTERS) > 0;
  }

  /**
   * @return {@code true} if first letter is capital.
   */
  public boolean isInitialCapitalLetter() {
    return (pattern & INITAL_CAPITAL_LETTER) > 0;
  }

  /**
   * @return {@code true} if all letters are capital.
   */
  public boolean isAllCapitalLetter() {
    return (pattern & ALL_CAPITAL_LETTER) > 0;
  }

  /**
   * @return {@code true} if all letters are lower case.
   */
  public boolean isAllLowerCaseLetter() {
    return (pattern & ALL_LOWERCASE_LETTER) > 0;
  }

  /**
   * @return {@code true} if all chars are digits.
   */
  public boolean isAllDigit() {
    return (pattern & ALL_DIGIT) > 0;
  }

  /**
   * @return {@code true} if all chars are hiragana.
   */
  public boolean isAllHiragana() {
    return (pattern & ALL_HIRAGANA) > 0;
  }

  /**
   * @return {@code true} if all chars are katakana.
   */
  public boolean isAllKatakana() {
    return (pattern & ALL_KATAKANA) > 0;
  }

  /**
   * Retrieves the number of digits.
   */
  public int digits() {
    return digits;
  }

  /**
   * @return {@code true} if a period is contained.
   */
  public boolean containsPeriod() {
    return (pattern & CONTAINS_PERIOD) > 0;
  }

  /**
   * @return {@code true} if a comma is contained.
   */
  public boolean containsComma() {
    return (pattern & CONTAINS_COMMA) > 0;
  }

  /**
   * @return {@code true} if a slash is contained.
   */
  public boolean containsSlash() {
    return (pattern & CONTAINS_SLASH) > 0;
  }

  /**
   * @return {@code true} if a digit is contained.
   */
  public boolean containsDigit() {
    return (pattern & CONTAINS_DIGIT) > 0;
  }

  /**
   * @return {@code true} if a hypen is contained.
   */
  public boolean containsHyphen() {
    return (pattern & CONTAINS_HYPHEN) > 0;
  }

  /**
   * @return {@code true} if a letters are contained.
   */
  public boolean containsLetters() {
    return (pattern & CONTAINS_LETTERS) > 0;
  }
}
