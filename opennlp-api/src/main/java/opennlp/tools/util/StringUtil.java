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

package opennlp.tools.util;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.util.normalizer.UnicodeWhitespace;

public class StringUtil {

  private static final Logger logger = LoggerFactory.getLogger(StringUtil.class);

  /**
   * The ten ASCII digit strings {@code "0"} to {@code "9"}, indexed by digit value. Precomputed so
   * code folding digits to ASCII does not allocate a new single-character string per digit; the
   * list is immutable and safe to share.
   */
  public static final List<String> ASCII_DIGIT_STRINGS =
      List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

  /**
   * Determines if the specified {@link Character} is a whitespace under the active
   * {@link WhitespaceMode}: the Unicode {@code White_Space} property by default, or, when
   * the {@value WhitespaceMode#MODE_PROPERTY} system property selects
   * {@link WhitespaceMode#LEGACY}, the union of {@link Character#isWhitespace(int)} and the
   * Unicode {@code Zs} category ({@link Character#SPACE_SEPARATOR}) that OpenNLP 1.x and 2.x
   * used.
   *
   * <p>Tokenization, corpus format parsing, and feature generation all resolve whitespace
   * through this method, so they share one {@link WhitespaceMode} for the life of the
   * process; use {@link #isUnicodeWhitespace(char)} directly where the Unicode definition
   * should apply unconditionally, such as user-text normalization with no trained-model
   * dependency.</p>
   *
   * @param charCode The character to check.
   *
   * @return {@code true} if {@code charCode} represents a white space under the active
   *     {@link WhitespaceMode}, {@code false} otherwise.
   */
  public static boolean isWhitespace(char charCode) {
    return WhitespaceMode.current() == WhitespaceMode.LEGACY
        ? isLegacyWhitespace(charCode) : isUnicodeWhitespace(charCode);
  }

  /**
   * Determines if the specified code point is a whitespace under the active
   * {@link WhitespaceMode}; see {@link #isWhitespace(char)} for details.
   *
   * @param charCode An int representation of a character to check.
   *
   * @return {@code true} if {@code charCode} represents a white space under the active
   *     {@link WhitespaceMode}, {@code false} otherwise.
   */
  public static boolean isWhitespace(int charCode) {
    return WhitespaceMode.current() == WhitespaceMode.LEGACY
        ? isLegacyWhitespace(charCode) : isUnicodeWhitespace(charCode);
  }

  /**
   * The OpenNLP 1.x/2.x definition: {@link Character#isWhitespace(int)} or the Unicode
   * {@code Zs} category.
   */
  private static boolean isLegacyWhitespace(int charCode) {
    return Character.isWhitespace(charCode) ||
        Character.getType(charCode) == Character.SPACE_SEPARATOR;
  }

  /**
   * Determines if the specified {@link Character} is a whitespace under the Unicode
   * {@code White_Space} property; delegates to
   * {@link opennlp.tools.util.normalizer.UnicodeWhitespace#isWhitespace(int)}. This is also
   * what {@link #isWhitespace(char)} resolves to under the default {@link WhitespaceMode}.
   *
   * @param charCode The character to check.
   *
   * @return {@code true} if {@code charCode} has the {@code White_Space} property,
   *     {@code false} otherwise.
   */
  public static boolean isUnicodeWhitespace(char charCode) {
    return UnicodeWhitespace.isWhitespace(charCode);
  }

  /**
   * Determines if the specified code point is a whitespace under the Unicode
   * {@code White_Space} property; delegates to
   * {@link opennlp.tools.util.normalizer.UnicodeWhitespace#isWhitespace(int)}. This is also
   * what {@link #isWhitespace(int)} resolves to under the default {@link WhitespaceMode}.
   *
   * @param charCode An int representation of a character to check.
   *
   * @return {@code true} if {@code charCode} has the {@code White_Space} property,
   *     {@code false} otherwise.
   */
  public static boolean isUnicodeWhitespace(int charCode) {
    return UnicodeWhitespace.isWhitespace(charCode);
  }

  /**
   * Splits {@code input} on runs of Unicode {@code White_Space}. Leading and trailing
   * runs are ignored, so whitespace-only input yields an empty array. This is a
   * code-point scan, not a regular expression.
   *
   * @param input The text to split. Must not be {@code null}.
   * @return The non-whitespace terms in order.
   * @throws IllegalArgumentException If {@code input} is {@code null}.
   */
  public static String[] splitOnUnicodeWhitespace(CharSequence input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    final List<String> terms = new ArrayList<>();
    final int n = input.length();
    int start = -1;
    int i = 0;
    while (i < n) {
      final int cp = Character.codePointAt(input, i);
      if (isUnicodeWhitespace(cp)) {
        if (start >= 0) {
          terms.add(input.subSequence(start, i).toString());
          start = -1;
        }
      } else if (start < 0) {
        start = i;
      }
      i += Character.charCount(cp);
    }
    if (start >= 0) {
      terms.add(input.subSequence(start, n).toString());
    }
    return terms.toArray(new String[0]);
  }

  /**
   * Trims leading and trailing runs of Unicode {@code White_Space}, the same set
   * {@link #splitOnUnicodeWhitespace(CharSequence)} breaks terms on.
   *
   * @param input The text to trim. Must not be {@code null}.
   * @return The trimmed string; may be empty when {@code input} is whitespace-only.
   * @throws IllegalArgumentException If {@code input} is {@code null}.
   */
  public static String trimUnicodeWhitespace(CharSequence input) {
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    int start = 0;
    int end = input.length();
    while (start < end) {
      final int cp = Character.codePointAt(input, start);
      if (!isUnicodeWhitespace(cp)) {
        break;
      }
      start += Character.charCount(cp);
    }
    while (end > start) {
      final int cp = Character.codePointBefore(input, end);
      if (!isUnicodeWhitespace(cp)) {
        break;
      }
      end -= Character.charCount(cp);
    }
    return input.subSequence(start, end).toString();
  }

  /**
   * {@code true} when {@code input} is {@code null}, empty, or consists only of Unicode
   * {@code White_Space} code points.
   *
   * @param input The text to test; {@code null} is treated as blank.
   * @return {@code true} if there is no non-whitespace code point.
   */
  public static boolean isUnicodeBlank(CharSequence input) {
    if (input == null || input.length() == 0) {
      return true;
    }
    int i = 0;
    while (i < input.length()) {
      final int cp = Character.codePointAt(input, i);
      if (!isUnicodeWhitespace(cp)) {
        return false;
      }
      i += Character.charCount(cp);
    }
    return true;
  }

  /**
   * Converts a {@link CharSequence} to lower case, independent of the current
   * {@link java.util.Locale} via {@link Character#toLowerCase(int)} which uses
   * mapping information from the UnicodeData file.
   *
   * @param string The {@link CharSequence} to transform.
   * @return The lower-cased String.
   */
  public static String toLowerCase(CharSequence string) {
    int[] cp = string.codePoints().map(Character::toLowerCase).toArray();
    return new String(cp, 0, cp.length);
  }

  public static CharBuffer toLowerCaseCharBuffer(CharSequence sequence) {
    CharBuffer result = CharBuffer.allocate(sequence.length());
    for (int cp : sequence.codePoints().toArray()) {
      for (char c : Character.toChars(Character.toLowerCase(cp))) {
        result.append(c);
      }
    }
    result.clear();
    return result;
  }

  /*
  public static CharBuffer toLowerCaseCharBuffer(CharSequence string) {
    int[] cp = string.codePoints().map(Character::toLowerCase).toArray();
    CharBuffer result = CharBuffer.allocate(string.length());
    for (int j : cp) {
      char[] chars = Character.toChars(j);
      result.put(chars, 0, chars.length);
    }
    result.clear();
    return result;
  }
  */

  /**
   * Converts a {@link CharSequence} to upper case, independent of the current
   * {@link java.util.Locale} via {@link Character#toUpperCase(char)} which uses
   * mapping information from the UnicodeData file.
   *
   * @param string The {@link CharSequence} to transform.
   * @return The upper-cased String
   */
  public static String toUpperCase(CharSequence string) {
    char[] upperCaseChars = new char[string.length()];

    for (int i = 0; i < string.length(); i++) {
      upperCaseChars[i] = Character.toUpperCase(string.charAt(i));
    }

    return new String(upperCaseChars);
  }

  /**
   * @return {@code true} if {@link CharSequence#length()} is {@code 0} or {@code null}, otherwise
   *         {@code false}
   *
   * @since 1.5.1
   */
  public static boolean isEmpty(CharSequence theString) {
    return theString.length() == 0;
  }

  /**
   * Determines whether a {@link CharSequence} is blank: empty, or made up entirely of
   * code points that {@link #isWhitespace(int)} accepts. Unlike
   * {@link String#isBlank()}, this follows the toolkit's whitespace definition, which
   * includes the no-break spaces the JDK predicate leaves out, so a value spelled
   * entirely from them cannot pass a blank check as content.
   *
   * @param theString The {@link CharSequence} to examine. Must not be {@code null}.
   * @return {@code true} if {@code theString} is empty or all whitespace.
   */
  public static boolean isBlank(CharSequence theString) {
    for (int i = 0; i < theString.length(); ) {
      final int codePoint = Character.codePointAt(theString, i);
      if (!isWhitespace(codePoint)) {
        return false;
      }
      i += Character.charCount(codePoint);
    }
    return true;
  }

  /**
   * Get the minimum of three values.
   *
   * @param a number a
   * @param b number b
   * @param c number c
   * @return the minimum among the three parameters {@code a}, {@code b} or {@code c}.
   */
  private static int minimum(int a, int b, int c) {
    int minValue;
    minValue = a;
    if (b < minValue) {
      minValue = b;
    }
    if (c < minValue) {
      minValue = c;
    }
    return minValue;
  }

  /**
   * Computes the <i>Levenshtein</i> distance of two strings in a matrix.
   * <p>
   * Based on this
   * <a href="https://en.wikipedia.org/wiki/Levenshtein_distance#Computing_Levenshtein_distance">
   * pseudo-code</a> which in turn is based on the paper Wagner, Robert A.; Fischer, Michael J. (1974),
   * "The String-to-String Correction Problem", Journal of the ACM 21 (1): 168-173
   * 
   * @param wordForm The form as input.
   * @param lemma The target lemma.
   * @return A 2-dimensional Levenshtein distance matrix.
   */
  public static int[][] levenshteinDistance(String wordForm, String lemma) {
    int wordLength = wordForm.length();
    int lemmaLength = lemma.length();
    int cost;
    int[][] distance = new int[wordLength + 1][lemmaLength + 1];

    if (wordLength == 0) {
      return distance;
    }
    if (lemmaLength == 0) {
      return distance;
    }
    //fill in the rows of column 0
    for (int i = 0; i <= wordLength; i++) {
      distance[i][0] = i;
    }
    //fill in the columns of row 0
    for (int j = 0; j <= lemmaLength; j++) {
      distance[0][j] = j;
    }
    //fill in the rest of the matrix calculating the minimum distance
    for (int i = 1; i <= wordLength; i++) {
      int s_i = wordForm.charAt(i - 1);
      for (int j = 1; j <= lemmaLength; j++) {
        if (s_i == lemma.charAt(j - 1)) {
          cost = 0;
        } else {
          cost = 1;
        }
        //obtain minimum distance from calculating deletion, insertion, substitution
        distance[i][j] = minimum(distance[i - 1][j] + 1, distance[i][j - 1]
            + 1, distance[i - 1][j - 1] + cost);
      }
    }
    return distance;
  }

  /**
   * Computes the Shortest Edit Script (SES) to convert a word into its lemma.
   * This is based on Chrupala's PhD thesis (2008).
   *
   * @param wordForm The token.
   * @param lemma The target lemma.
   * @param distance A 2-dimensional Levenshtein distance matrix.
   * @param permutations The number of permutations.
   */
  public static void computeShortestEditScript(String wordForm, String lemma,
      int[][] distance, StringBuffer permutations) {

    int n = distance.length;
    int m = distance[0].length;

    int wordFormLength = n - 1;
    int lemmaLength = m - 1;
    while (true) {

      if (distance[wordFormLength][lemmaLength] == 0) {
        break;
      }
      if ((lemmaLength > 0 && wordFormLength > 0) && (distance[wordFormLength - 1][lemmaLength - 1]
          < distance[wordFormLength][lemmaLength])) {
        permutations.append('R').append(wordFormLength - 1)
            .append(wordForm.charAt(wordFormLength - 1)).append(lemma.charAt(lemmaLength - 1));
        lemmaLength--;
        wordFormLength--;
        continue;
      }
      if (lemmaLength > 0 && (distance[wordFormLength][lemmaLength - 1]
          < distance[wordFormLength][lemmaLength])) {
        permutations.append('I').append(wordFormLength)
            .append(lemma.charAt(lemmaLength - 1));
        lemmaLength--;
        continue;
      }
      if (wordFormLength > 0 && (distance[wordFormLength - 1][lemmaLength]
          < distance[wordFormLength][lemmaLength])) {
        permutations.append('D').append(wordFormLength - 1)
            .append(wordForm.charAt(wordFormLength - 1));
        wordFormLength--;
        continue;
      }
      if ((wordFormLength > 0 && lemmaLength > 0) && (distance[wordFormLength - 1][lemmaLength - 1]
          == distance[wordFormLength][lemmaLength])) {
        wordFormLength--;
        lemmaLength--;
        continue ;
      }
      if (wordFormLength > 0 && (distance[wordFormLength - 1][lemmaLength]
          == distance[wordFormLength][lemmaLength])) {
        wordFormLength--;
        continue;
      }
      if (lemmaLength > 0 && (distance[wordFormLength][lemmaLength - 1]
          == distance[wordFormLength][lemmaLength])) {
        lemmaLength--;
      }
    }
  }

  /**
   * Reads the predicted Shortest Edit Script (SES) by a lemmatizer model and applies the
   * permutations to obtain the lemma from the {@code wordForm}.
   *
   * @param wordForm The wordForm as input.
   * @param permutations The permutations predicted by the lemmatizer model.
   * @return The decoded lemma.
   */
  public static String decodeShortestEditScript(String wordForm, String permutations) {

    StringBuffer lemma = new StringBuffer(wordForm).reverse();

    int permIndex = 0;
    while (true) {
      if (permutations.length() <= permIndex) {
        break;
      }
      //read first letter of permutation string
      char nextOperation = permutations.charAt(permIndex);
      if (logger.isTraceEnabled()) {
        logger.trace("-> NextOP: {}", nextOperation);
      }
      //go to the next permutation letter
      permIndex++;
      if (nextOperation == 'R') {
        String charAtPerm = Character.toString(permutations.charAt(permIndex));
        int charIndex = Integer.parseInt(charAtPerm);
        // go to the next character in the permutation buffer
        // which is the replacement character
        permIndex++;
        char replace = permutations.charAt(permIndex);
        //go to the next char in the permutation buffer
        // which is the candidate character
        permIndex++;
        char with = permutations.charAt(permIndex);

        if (lemma.length() <= charIndex) {
          return wordForm;
        }
        if (lemma.charAt(charIndex) == replace) {
          lemma.setCharAt(charIndex, with);
        }
        if (logger.isTraceEnabled()) {
          logger.trace("-> ROP: {}", lemma);
        }
        //go to next permutation
        permIndex++;

      } else if (nextOperation == 'I') {
        String charAtPerm = Character.toString(permutations.charAt(permIndex));
        int charIndex = Integer.parseInt(charAtPerm);
        permIndex++;
        //character to be inserted
        char in = permutations.charAt(permIndex);

        if (lemma.length() < charIndex) {
          return wordForm;
        }
        lemma.insert(charIndex, in);

        if (logger.isTraceEnabled()) {
          logger.trace("-> IOP {}", lemma);
        }
        //go to next permutation
        permIndex++;
      } else if (nextOperation == 'D') {
        String charAtPerm = Character.toString(permutations.charAt(permIndex));
        int charIndex = Integer.parseInt(charAtPerm);
        if (lemma.length() <= charIndex) {
          return wordForm;
        }
        lemma.deleteCharAt(charIndex);
        permIndex++;
        // go to next permutation
        permIndex++;
      }
    }
    return lemma.reverse().toString();
  }

  /**
   * @param wordForm The word as input.
   * @param lemma The target lemma.
   * @return Retrieves the Shortest Edit Script (SES) required to go from a word to a lemma.
   */
  public static String getShortestEditScript(String wordForm, String lemma) {
    String reversedWF = new StringBuffer(wordForm.toLowerCase()).reverse().toString();
    String reversedLemma = new StringBuffer(lemma.toLowerCase()).reverse().toString();
    StringBuffer permutations = new StringBuffer();
    String ses;
    if (!reversedWF.equals(reversedLemma)) {
      int[][]levenDistance = StringUtil.levenshteinDistance(reversedWF, reversedLemma);
      StringUtil.computeShortestEditScript(reversedWF, reversedLemma, levenDistance, permutations);
      ses = permutations.toString();
    } else {
      ses = "O";
    }
    return ses;
  }

}
