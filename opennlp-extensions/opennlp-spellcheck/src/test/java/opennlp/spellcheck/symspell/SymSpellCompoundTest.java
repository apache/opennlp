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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import opennlp.spellcheck.SuggestItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Coverage of {@link SymSpell#lookupCompound}: merging run-on words, splitting
 * wrongly-inserted spaces, correcting mixed multi-token sentences, and the edge cases
 * (clean phrase, empty / blank, unknown token, validation).
 *
 * <p>Uses the committed tiny fixtures via {@link TinyDictionary} for the reading-grade
 * cases, plus a couple of focused hand-built dictionaries where a precise bigram-driven
 * split/merge needs to be asserted deterministically.</p>
 */
public class SymSpellCompoundTest {

  private SymSpell tiny;

  @BeforeEach
  void setUp() throws Exception {
    tiny = TinyDictionary.load();
  }

  private static String correct(SymSpell engine, String input) {
    final List<SuggestItem> r = engine.lookupCompound(input, 2);
    assertEquals(1, r.size(), "lookupCompound always returns a singleton list");
    return r.get(0).term();
  }

  // ------------------------------------------------------------------
  // Merge: a wrongly-inserted/absent space joins two words.
  // ------------------------------------------------------------------

  @Test
  void mergesRunOnWordsFromFixtures() {
    assertEquals("hello world", correct(tiny, "helloworld"));
    assertEquals("spell checker", correct(tiny, "spellchecker"));
  }

  @Test
  void mergesUsingBigramModel() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    s.add("hello", 4569100L);
    s.add("world", 5705975L);
    s.addBigram("hello", "world", 5432109L);
    assertEquals("hello world", correct(s, "helloworld"));
  }

  // ------------------------------------------------------------------
  // Split: a wrongly-inserted space is removed / re-segmented.
  // ------------------------------------------------------------------

  @Test
  void splitsWronglySeparatedWords() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    s.add("members", 1226734L);
    assertEquals("members", correct(s, "mem bers"));
  }

  @Test
  void reSegmentsMisplacedSpace() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    s.add("hello", 4569100L);
    s.add("world", 5705975L);
    s.addBigram("hello", "world", 5432109L);
    assertEquals("hello world", correct(s, "hel loworld"));
  }

  // ------------------------------------------------------------------
  // Mixed sentences: several typos across tokens, with and without merges.
  // ------------------------------------------------------------------

  @Test
  void correctsMixedMultiTokenSentence() {
    assertEquals("the quick brown fox", correct(tiny, "teh quikc broen fxo"));
  }

  @Test
  void correctsSentenceCombiningMergeAndTypo() {
    final SymSpell s = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    s.add("the", 23135851162L);
    s.add("quick", 119476788L);
    // "thequikc" = merged "the"+"quikc" with a transposition typo in the second word.
    assertEquals("the quick", correct(s, "thequikc"));
  }

  @Test
  void leavesAlreadyCorrectPhraseUntouched() {
    final List<SuggestItem> r = tiny.lookupCompound("the quick brown fox", 2);
    assertEquals("the quick brown fox", r.get(0).term());
    assertEquals(0, r.get(0).editDistance());
  }

  // ------------------------------------------------------------------
  // Edge cases.
  // ------------------------------------------------------------------

  @Test
  void emptyAndBlankInputProduceEmptyResult() {
    assertEquals("", correct(tiny, ""));
    assertEquals("", correct(tiny, "   "));
  }

  @Test
  void unknownTokenIsKeptVerbatim() {
    assertEquals("zzzzqqqq", correct(tiny, "zzzzqqqq"));
  }

  @Test
  void negativeMaxEditDistanceIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> tiny.lookupCompound("helloworld", -1));
  }

  @Test
  void nullInputIsRejected() {
    assertThrows(NullPointerException.class, () -> tiny.lookupCompound(null, 2));
  }

  // ------------------------------------------------------------------
  // Whitespace boundary code points.
  // ------------------------------------------------------------------

  @Test
  void noBreakSpaceSeparatesTerms() {
    // Since 3.0 lookupCompound splits terms on the Unicode White_Space set, so the
    // NBSP-joined pair is two known terms. The reported edit distance is still 1 because
    // it is measured against the raw input, where the NBSP differs from the space.
    final List<SuggestItem> r = tiny.lookupCompound("hello" + cp(0x00A0) + "world", 2);
    assertEquals(1, r.size());
    assertEquals("hello world", r.get(0).term());
    assertEquals(1, r.get(0).editDistance());
  }

  @Test
  void noBreakSpaceJoinedTripleIsFullyRepaired() {
    // Since 3.0 the three NBSP-separated words are three terms; under the previous ASCII
    // \s+ split this was one term and the single-split heuristic could not repair it.
    String input = "hello" + cp(0x00A0) + "world" + cp(0x00A0) + "hello";
    assertEquals("hello world hello", correct(tiny, input));
  }

  @Test
  void nextLineControlJoinedTripleIsFullyRepaired() {
    // U+0085 NEL carries the Unicode White_Space property, so since 3.0 it separates terms.
    String input = "hello" + cp(0x0085) + "world" + cp(0x0085) + "hello";
    assertEquals("hello world hello", correct(tiny, input));
  }

  @Test
  void lineSeparatorJoinedTripleIsFullyRepaired() {
    // U+2028 LINE SEPARATOR is Unicode White_Space, so since 3.0 it separates terms.
    String input = "hello" + cp(0x2028) + "world" + cp(0x2028) + "hello";
    assertEquals("hello world hello", correct(tiny, input));
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }
}
