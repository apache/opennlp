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

package opennlp.spellcheck.symspell;

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused coverage of {@link SymSpell#lookup} verbosity semantics, the
 * {@code maxEditDistance} bound, prefix handling, exact / unknown words, Unicode input
 * and argument validation. Distinct from {@code SymSpellTest} which covers the basics.
 */
public class SymSpellLookupTest {

  /** A small ASCII dictionary with a couple of equidistant neighbours for verbosity tests. */
  private static SymSpell ascii() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    s.add("bank", 100L);
    s.add("bonk", 80L);
    s.add("band", 90L);
    s.add("member", 7560493L);
    s.add("members", 1226734L);
    s.add("the", 23135851162L);
    return s;
  }

  private static List<String> terms(List<SuggestItem> items) {
    return items.stream().map(SuggestItem::term).toList();
  }

  // ------------------------------------------------------------------
  // Verbosity semantics.
  // ------------------------------------------------------------------

  @Test
  void topReturnsExactlyOneBestSuggestion() {
    // "bnk" is within distance 1 of both "bank" and "bonk"; TOP keeps only the best.
    final List<SuggestItem> r = ascii().lookup("bnk", Verbosity.TOP, 2);
    assertEquals(1, r.size());
    assertEquals("bank", r.get(0).term());
    assertEquals(1, r.get(0).editDistance());
  }

  @Test
  void closestReturnsAllAtTheMinimumDistanceOnly() {
    // "bank" and "bonk" are both distance 1 from "bnk"; "band" is distance 2 and excluded.
    final List<SuggestItem> r = ascii().lookup("bnk", Verbosity.CLOSEST, 2);
    assertEquals(List.of("bank", "bonk"), terms(r), "only the closest matches, freq-desc");
    assertTrue(r.stream().allMatch(i -> i.editDistance() == 1));
  }

  @Test
  void allReturnsEveryCandidateWithinDistanceSorted() {
    final List<SuggestItem> r = ascii().lookup("bnk", Verbosity.ALL, 2);
    assertEquals(List.of("bank", "bonk", "band"), terms(r));
    // Ascending edit distance, then descending frequency.
    for (int i = 1; i < r.size(); i++) {
      assertTrue(r.get(i - 1).compareTo(r.get(i)) <= 0);
    }
  }

  @Test
  void allKeepsTheExactMatchWhilstClosestStopsThere() {
    final SymSpell s = ascii();
    s.add("ban", 50L);
    // Exact match present; ALL still surfaces the further "bank"/"band"/"bonk" neighbours.
    final List<SuggestItem> all = s.lookup("ban", Verbosity.ALL, 2);
    assertEquals("ban", all.get(0).term());
    assertEquals(0, all.get(0).editDistance());
    assertTrue(all.size() > 1, "ALL keeps neighbours beyond the exact hit");
    // CLOSEST short-circuits on the exact (distance 0) hit.
    final List<SuggestItem> closest = s.lookup("ban", Verbosity.CLOSEST, 2);
    assertEquals(1, closest.size());
    assertEquals("ban", closest.get(0).term());
  }

  // ------------------------------------------------------------------
  // maxEditDistance bound.
  // ------------------------------------------------------------------

  @Test
  void maxEditDistanceZeroReturnsOnlyExactMatches() {
    final SymSpell s = ascii();
    final List<SuggestItem> exact = s.lookup("member", Verbosity.TOP, 0);
    assertEquals(1, exact.size());
    assertEquals("member", exact.get(0).term());
    assertEquals(0, exact.get(0).editDistance());
    // A one-edit typo yields nothing at maxEditDistance 0.
    assertTrue(s.lookup("membe", Verbosity.TOP, 0).isEmpty());
  }

  @Test
  void smallerMaxEditDistanceExcludesFartherCandidates() {
    final SymSpell s = ascii();
    // At distance 1 only "member" qualifies for "membe"; "members" (d=2) is filtered.
    final List<SuggestItem> near = s.lookup("membe", Verbosity.ALL, 1);
    assertEquals(List.of("member"), terms(near));
    // Widening to 2 admits "members".
    final List<SuggestItem> wide = s.lookup("membe", Verbosity.ALL, 2);
    assertTrue(wide.size() >= 2);
    assertTrue(terms(wide).contains("members"));
  }

  @Test
  void maxEditDistanceAboveDictionaryMaxIsRejected() {
    final SymSpell s = ascii();
    final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> s.lookup("bank", Verbosity.TOP, 3));
    assertTrue(ex.getMessage().contains("maxEditDistance"));
  }

  // ------------------------------------------------------------------
  // Prefix handling for long words (default prefixLength = 7).
  // ------------------------------------------------------------------

  @Test
  void correctsTypoBeyondThePrefixWindowOfALongWord() {
    final SymSpell s = new SymSpell(
        SymSpellConfig.builder().maxDictionaryEditDistance(2).prefixLength(7).build());
    s.add("definitely", 6655443L);
    // Trailing typo ("definitelyy") and an in-word typo past the 7-char prefix both resolve.
    assertEquals("definitely", s.lookup("definitelyy", Verbosity.TOP, 2).get(0).term());
    assertEquals("definitely", s.lookup("definately", Verbosity.TOP, 2).get(0).term());
  }

  // ------------------------------------------------------------------
  // Exact / unknown / empty.
  // ------------------------------------------------------------------

  @Test
  void exactMatchHasZeroDistanceAcrossAllVerbosities() {
    final SymSpell s = ascii();
    for (Verbosity v : Verbosity.values()) {
      final List<SuggestItem> r = s.lookup("the", v, 2);
      assertFalse(r.isEmpty(), "expected exact hit for verbosity " + v);
      assertEquals("the", r.get(0).term());
      assertEquals(0, r.get(0).editDistance());
    }
  }

  @Test
  void unknownFarTermYieldsNoSuggestions() {
    assertTrue(ascii().lookup("zzzzzzzz", Verbosity.ALL, 2).isEmpty());
  }

  @Test
  void emptyTermYieldsNoSuggestions() {
    assertTrue(ascii().lookup("", Verbosity.TOP, 2).isEmpty());
  }

  // ------------------------------------------------------------------
  // Unicode / non-ASCII.
  // ------------------------------------------------------------------

  @Test
  void correctsAccentedAndNonLatinTerms() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    s.add("café", 100L);
    s.add("naïve", 50L);
    s.add("straße", 40L);
    s.add("日本語", 30L);
    assertEquals("café", s.lookup("cafe", Verbosity.TOP, 2).get(0).term());
    assertEquals("naïve", s.lookup("naive", Verbosity.TOP, 2).get(0).term());
    assertEquals("straße", s.lookup("strase", Verbosity.TOP, 2).get(0).term());
    // A single-character substitution within a CJK term.
    assertEquals("日本語", s.lookup("日本誤", Verbosity.TOP, 2).get(0).term());
  }

  @Test
  void emojiTermIsTreatedByCodePoint() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    final String happy = "a" + new String(Character.toChars(0x1F600)) + "b";
    final String typo = "a" + new String(Character.toChars(0x1F601)) + "b";
    s.add(happy, 100L);
    final List<SuggestItem> r = s.lookup(typo, Verbosity.TOP, 2);
    assertEquals(happy, r.get(0).term());
    assertEquals(1, r.get(0).editDistance());
  }

  // ------------------------------------------------------------------
  // Argument validation.
  // ------------------------------------------------------------------

  @Test
  void nullArgumentsAreRejected() {
    final SymSpell s = ascii();
    assertThrows(NullPointerException.class, () -> s.lookup(null, Verbosity.TOP, 2));
    assertThrows(NullPointerException.class, () -> s.lookup("bank", null, 2));
    assertThrows(IllegalArgumentException.class, () -> s.lookup("bank", Verbosity.TOP, -1));
  }
}
