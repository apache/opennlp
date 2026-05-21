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
import opennlp.spellcheck.Verbosity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SymSpellTest {

  private SymSpell symSpell;

  @BeforeEach
  void setUp() {
    symSpell = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    symSpell.add("the", 23135851162L);
    symSpell.add("of", 13151942776L);
    symSpell.add("members", 1226734L);
    symSpell.add("member", 7560493L);
    symSpell.add("hello", 4569100L);
    symSpell.add("world", 5705975L);
    symSpell.add("a", 9081174698L);
  }

  @Test
  void exactMatchHasZeroDistance() {
    final List<SuggestItem> result = symSpell.lookup("members", Verbosity.TOP, 2);
    assertFalse(result.isEmpty());
    assertEquals("members", result.get(0).term());
    assertEquals(0, result.get(0).editDistance());
  }

  @Test
  void correctsSingleEditTypo() {
    final List<SuggestItem> result = symSpell.lookup("membrs", Verbosity.TOP, 2);
    assertFalse(result.isEmpty());
    assertEquals("members", result.get(0).term());
    assertTrue(result.get(0).editDistance() <= 2);
  }

  @Test
  void verbosityAllReturnsMultipleWithinDistance() {
    final List<SuggestItem> result = symSpell.lookup("membe", Verbosity.ALL, 2);
    // "member" (1) and "members" (2) are both within distance 2.
    assertTrue(result.size() >= 2);
    // Natural ordering: ascending edit distance.
    for (int i = 1; i < result.size(); i++) {
      assertTrue(result.get(i - 1).compareTo(result.get(i)) <= 0);
    }
  }

  @Test
  void noSuggestionForFarTerm() {
    final List<SuggestItem> result = symSpell.lookup("zzzzzzzz", Verbosity.TOP, 2);
    assertTrue(result.isEmpty());
  }

  @Test
  void defaultLookupOverloadUsesTopVerbosity() {
    final List<SuggestItem> result = symSpell.lookup("helo");
    assertFalse(result.isEmpty());
    assertEquals("hello", result.get(0).term());
  }

  @Test
  void lookupCompoundSplitsMergedWords() {
    final SymSpell sc = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    sc.add("hello", 4569100L);
    sc.add("world", 5705975L);
    final List<SuggestItem> result = sc.lookupCompound("helloworld", 2);
    assertEquals(1, result.size());
    assertEquals("hello world", result.get(0).term());
  }

  @Test
  void lookupCompoundMergesSplitWords() {
    final SymSpell sc = new SymSpell(SymSpellConfig.builder().maxDictionaryEditDistance(2).build());
    sc.add("members", 1226734L);
    final List<SuggestItem> result = sc.lookupCompound("mem bers", 2);
    assertEquals(1, result.size());
    assertEquals("members", result.get(0).term());
  }
}
