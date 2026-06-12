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

package opennlp.spellcheck.distance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Cross-implementation behaviour of the two {@link EditDistance} metrics:
 * the default {@link DamerauOSADistance} (a single adjacent transposition counts as
 * one edit) and the selectable {@link LevenshteinDistance} (a transposition counts
 * as two edits: one deletion plus one insertion).
 *
 * <p>The cases here are re-authored from the canonical examples used by the SymSpell
 * reference test suite; they are not copied verbatim.</p>
 */
public class EditDistanceTest {

  private final EditDistance damerau = DamerauOSADistance.INSTANCE;
  private final EditDistance levenshtein = LevenshteinDistance.INSTANCE;

  private void bothAgree(String a, String b, int expected) {
    assertEquals(expected, damerau.distance(a, b, 9), "Damerau-OSA(" + a + "," + b + ")");
    assertEquals(expected, levenshtein.distance(a, b, 9), "Levenshtein(" + a + "," + b + ")");
  }

  // ------------------------------------------------------------------
  // Distances both metrics agree on (no transpositions involved).
  // ------------------------------------------------------------------

  @Test
  void bothMetricsAgreeWhenNoTransposition() {
    bothAgree("kitten", "kitten", 0);
    bothAgree("cat", "car", 1);
    bothAgree("kitten", "sitting", 3);
    bothAgree("flaw", "lawn", 2);
    bothAgree("sunday", "saturday", 3);
    bothAgree("abc", "abcd", 1);
    bothAgree("abcd", "abc", 1);
  }

  // ------------------------------------------------------------------
  // The defining difference: adjacent transposition cost.
  // ------------------------------------------------------------------

  @Test
  void transpositionCostsOneInDamerauButTwoInLevenshtein() {
    final String[][] swaps = {
        {"ca", "ac"},
        {"converse", "covnerse"},
        {"abcd", "abdc"},
        {"teh", "the"},
        {"hte", "the"},
    };
    for (String[] s : swaps) {
      assertEquals(1, damerau.distance(s[0], s[1], 4),
          "an adjacent transposition is a single edit in Damerau-OSA: " + s[0] + "/" + s[1]);
      assertEquals(2, levenshtein.distance(s[0], s[1], 4),
          "an adjacent transposition is two edits in Levenshtein: " + s[0] + "/" + s[1]);
    }
  }

  @Test
  void transpositionAtTheTighterCutoffOnlyDamerauMatches() {
    // With max=1 the swap is still reachable for Damerau, but not for Levenshtein.
    assertEquals(1, damerau.distance("ca", "ac", 1));
    assertEquals(-1, levenshtein.distance("ca", "ac", 1));
  }

  // ------------------------------------------------------------------
  // Threshold / cutoff contract: returns -1 when distance > max.
  // ------------------------------------------------------------------

  @Test
  void aboveMaxReturnsMinusOneForBoth() {
    assertEquals(-1, damerau.distance("kitten", "sitting", 2));
    assertEquals(-1, levenshtein.distance("kitten", "sitting", 2));
    assertEquals(-1, damerau.distance("abc", "xyz", 2));
    assertEquals(-1, levenshtein.distance("abc", "xyz", 2));
  }

  @Test
  void exactlyAtMaxIsReturnedNotCutOff() {
    assertEquals(3, damerau.distance("kitten", "sitting", 3));
    assertEquals(3, levenshtein.distance("kitten", "sitting", 3));
  }

  @Test
  void maxZeroOnlyAcceptsIdenticalStrings() {
    assertEquals(0, damerau.distance("word", "word", 0));
    assertEquals(0, levenshtein.distance("word", "word", 0));
    assertEquals(-1, damerau.distance("word", "ward", 0));
    assertEquals(-1, levenshtein.distance("word", "ward", 0));
  }

  @Test
  void lengthDifferenceBeyondMaxIsCutEarly() {
    // "a" vs "aaaaaa" differ by 5 insertions; cut off whenever max < 5.
    for (int max = 0; max <= 6; max++) {
      final int expected = max >= 5 ? 5 : -1;
      assertEquals(expected, damerau.distance("a", "aaaaaa", max), "Damerau max=" + max);
      assertEquals(expected, levenshtein.distance("a", "aaaaaa", max), "Levenshtein max=" + max);
    }
  }

  // ------------------------------------------------------------------
  // Empty strings and ordering symmetry.
  // ------------------------------------------------------------------

  @Test
  void emptyStrings() {
    assertEquals(0, damerau.distance("", "", 2));
    assertEquals(3, damerau.distance("", "abc", 5));
    assertEquals(3, damerau.distance("abc", "", 5));
    assertEquals(0, levenshtein.distance("", "", 2));
    assertEquals(3, levenshtein.distance("", "abc", 5));
    assertEquals(3, levenshtein.distance("abc", "", 5));
  }

  @Test
  void distanceIsSymmetric() {
    assertEquals(damerau.distance("abcd", "abdc", 4), damerau.distance("abdc", "abcd", 4));
    assertEquals(levenshtein.distance("flaw", "lawn", 4), levenshtein.distance("lawn", "flaw", 4));
  }

  // ------------------------------------------------------------------
  // Unicode: Damerau-OSA works on code points (supplementary chars == 1 symbol).
  // ------------------------------------------------------------------

  @Test
  void damerauTreatsSupplementaryCharAsSingleSymbol() {
    final String a = new String(Character.toChars(0x1F600)); // grinning face
    final String b = new String(Character.toChars(0x1F601)); // grinning face w/ eyes
    assertEquals(1, damerau.distance(a, b, 2));
  }

  @Test
  void damerauHandlesAccentedLatin() {
    // "café" -> "cafe" is a single substitution of one accented letter.
    assertEquals(1, damerau.distance("café", "cafe", 2));
  }

  // ------------------------------------------------------------------
  // Argument validation.
  // ------------------------------------------------------------------

  @Test
  void negativeMaxIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> damerau.distance("a", "b", -1));
    assertThrows(IllegalArgumentException.class, () -> levenshtein.distance("a", "b", -1));
  }

  @Test
  void nullInputsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> damerau.distance(null, "b", 1));
    assertThrows(IllegalArgumentException.class, () -> damerau.distance("a", null, 1));
    assertThrows(IllegalArgumentException.class, () -> levenshtein.distance(null, "b", 1));
    assertThrows(IllegalArgumentException.class, () -> levenshtein.distance("a", null, 1));
  }
}
