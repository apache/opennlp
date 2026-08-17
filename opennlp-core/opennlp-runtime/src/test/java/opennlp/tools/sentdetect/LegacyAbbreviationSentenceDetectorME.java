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

package opennlp.tools.sentdetect;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.StringList;
import opennlp.tools.util.StringUtil;

/**
 * A {@link SentenceDetectorME} whose abbreviation veto is the full text scan that
 * {@link SentenceDetectorME#isAcceptableBreak(CharSequence, int, int)} used before it was given a
 * bounded window. It is the oracle of {@link SentenceDetectorMEAbbreviationEquivalenceTest} and
 * the baseline of {@code SentenceDetectorMEAbbreviationBenchmark}, which is why it lives in its
 * own class rather than in either of them.
 */
class LegacyAbbreviationSentenceDetectorME extends SentenceDetectorME {

  private final Dictionary abbDict;
  private int vetoes;

  /**
   * @param model The {@link SentenceModel} to be used.
   * @param abbDict The {@link Dictionary} to veto with, may be {@code null}.
   */
  LegacyAbbreviationSentenceDetectorME(SentenceModel model, Dictionary abbDict) {
    super(model, abbDict);
    this.abbDict = abbDict;
  }

  @Override
  protected boolean isAcceptableBreak(CharSequence s, int fromIndex, int candidateIndex) {
    final boolean acceptable = decide(abbDict, s, fromIndex, candidateIndex);
    if (!acceptable) {
      vetoes++;
    }
    return acceptable;
  }

  /**
   * @return How often this instance vetoed a break, so a comparison run can show that it
   *     exercised the veto at all.
   */
  int vetoes() {
    return vetoes;
  }

  /**
   * The pre-rewrite implementation, kept verbatim.
   *
   * @param abbDict The abbreviation {@link Dictionary}, may be {@code null}.
   * @param s The {@link CharSequence} in which the break occurred.
   * @param fromIndex The start of the segment currently being evaluated.
   * @param candidateIndex The index of the candidate sentence ending.
   * @return {@code true} if the break is acceptable, {@code false} otherwise.
   */
  static boolean decide(Dictionary abbDict, CharSequence s, int fromIndex, int candidateIndex) {
    if (abbDict == null)
      return true;

    final String text = s.toString();
    final boolean caseSensitive = abbDict.isCaseSensitive();
    final String searchText = caseSensitive ? text : StringUtil.toLowerCase(text);
    for (StringList abb : abbDict) {
      final String abbToken = caseSensitive ? abb.getToken(0)
          : StringUtil.toLowerCase(abb.getToken(0));
      final int tokenLength = abbToken.length();
      int tokenStartPos = searchText.indexOf(abbToken, fromIndex);
      while (tokenStartPos != -1) {
        if (tokenStartPos > candidateIndex) {
          break; // past candidate position, no point searching further
        }
        if (tokenStartPos == fromIndex
            && searchText.substring(tokenStartPos, candidateIndex + 1).equals(abbToken)) {
          return false; // full abbreviation match at segment start -> no acceptable break
        }
        final char prevChar =
            s.charAt(tokenStartPos == fromIndex ? tokenStartPos : tokenStartPos - 1);
        if (tokenStartPos + tokenLength >= candidateIndex
            && (Character.isWhitespace(prevChar) || isApostrophe(prevChar) || prevChar == '(')) {
          return false; // in case of a valid abbreviation: the (sentence) break is not accepted
        }
        // Try next occurrence of this abbreviation in the text
        tokenStartPos = searchText.indexOf(abbToken, tokenStartPos + 1);
      }
    }
    return true; // no abbreviation(s) at given positions: valid sentence boundary
  }

  /**
   * @param c The character to check.
   * @return {@code true} if the character represents an apostrophe, {@code false} otherwise.
   */
  private static boolean isApostrophe(char c) {
    return c == '\'' || c == '`' || c == '´';
  }
}
