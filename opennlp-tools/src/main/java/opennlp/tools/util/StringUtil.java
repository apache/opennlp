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

public class StringUtil {

  /**
   * Determines if the specified character is a whitespace.
   *
   * A character is considered a whitespace when one
   * of the following conditions is meet:
   *
   * <ul>
   * <li>Its a {@link Character#isWhitespace(int)} whitespace.</li>
   * <li>Its a part of the Unicode Zs category ({@link Character#SPACE_SEPARATOR}).</li>
   * </ul>
   *
   * <code>Character.isWhitespace(int)</code> does not include no-break spaces.
   * In OpenNLP no-break spaces are also considered as white spaces.
   *
   * @param charCode
   * @return true if white space otherwise false
   */
  public static boolean isWhitespace(char charCode) {
    return Character.isWhitespace(charCode)  ||
        Character.getType(charCode) == Character.SPACE_SEPARATOR;
  }

  /**
   * Determines if the specified character is a whitespace.
   *
   * A character is considered a whitespace when one
   * of the following conditions is meet:
   *
   * <ul>
   * <li>Its a {@link Character#isWhitespace(int)} whitespace.</li>
   * <li>Its a part of the Unicode Zs category ({@link Character#SPACE_SEPARATOR}).</li>
   * </ul>
   *
   * <code>Character.isWhitespace(int)</code> does not include no-break spaces.
   * In OpenNLP no-break spaces are also considered as white spaces.
   *
   * @param charCode
   * @return true if white space otherwise false
   */
  public static boolean isWhitespace(int charCode) {
    return Character.isWhitespace(charCode)  ||
        Character.getType(charCode) == Character.SPACE_SEPARATOR;
  }


  /**
   * Converts to lower case independent of the current locale via
   * {@link Character#toLowerCase(char)} which uses mapping information
   * from the UnicodeData file.
   *
   * @param string
   * @return lower cased String
   */
  public static String toLowerCase(CharSequence string) {
    char lowerCaseChars[] = new char[string.length()];

    for (int i = 0; i < string.length(); i++) {
      lowerCaseChars[i] = Character.toLowerCase(string.charAt(i));
    }

    return new String(lowerCaseChars);
  }

  /**
   * Converts to upper case independent of the current locale via
   * {@link Character#toUpperCase(char)} which uses mapping information
   * from the UnicodeData file.
   *
   * @param string
   * @return upper cased String
   */
  public static String toUpperCase(CharSequence string) {
    char upperCaseChars[] = new char[string.length()];

    for (int i = 0; i < string.length(); i++) {
      upperCaseChars[i] = Character.toUpperCase(string.charAt(i));
    }

    return new String(upperCaseChars);
  }

  /**
   * Returns <tt>true</tt> if {@link CharSequence#length()} is
   * <tt>0</tt> or <tt>null</tt>.
   *
   * @return <tt>true</tt> if {@link CharSequence#length()} is <tt>0</tt>, otherwise
   *         <tt>false</tt>
   *
   * @since 1.5.1
   */
  public static boolean isEmpty(CharSequence theString) {
    return theString.length() == 0;
  }

  /**
   * Get mininum of three values.
   * @param a number a
   * @param b number b
   * @param c number c
   * @return the minimum
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
   * Computes the Levenshtein distance of two strings in a matrix.
   * Based on pseudo-code provided here:
   * https://en.wikipedia.org/wiki/Levenshtein_distance#Computing_Levenshtein_distance
   * which in turn is based on the paper Wagner, Robert A.; Fischer, Michael J. (1974),
   * "The String-to-String Correction Problem", Journal of the ACM 21 (1): 168-173
   * @param wordForm the form
   * @param lemma the lemma
   * @return the distance
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
   * @param wordForm the token
   * @param lemma the target lemma
   * @param distance the levenshtein distance
   * @param permutations the number of permutations
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
        permutations.append('R').append(Integer.toString(wordFormLength - 1))
            .append(wordForm.charAt(wordFormLength - 1)).append(lemma.charAt(lemmaLength - 1));
        lemmaLength--;
        wordFormLength--;
        continue;
      }
      if (lemmaLength > 0 && (distance[wordFormLength][lemmaLength - 1]
          < distance[wordFormLength][lemmaLength])) {
        permutations.append('I').append(Integer.toString(wordFormLength))
            .append(lemma.charAt(lemmaLength - 1));
        lemmaLength--;
        continue;
      }
      if (wordFormLength > 0 && (distance[wordFormLength - 1][lemmaLength]
          < distance[wordFormLength][lemmaLength])) {
        permutations.append('D').append(Integer.toString(wordFormLength - 1))
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
   * Read predicted SES by the lemmatizer model and apply the
   * permutations to obtain the lemma from the wordForm.
   * @param wordForm the wordForm
   * @param permutations the permutations predicted by the lemmatizer model
   * @return the lemma
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
      //System.err.println("-> NextOP: " + nextOperation);
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
        //System.err.println("-> ROP: " + lemma.toString());
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
        //System.err.println("-> IOP " + lemma.toString());
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
   * Get the SES required to go from a word to a lemma.
   * @param wordForm the word
   * @param lemma the lemma
   * @return the shortest edit script
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
