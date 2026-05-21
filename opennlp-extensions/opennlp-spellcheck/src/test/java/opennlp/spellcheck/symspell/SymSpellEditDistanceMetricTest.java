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
import opennlp.spellcheck.distance.DamerauOSADistance;
import opennlp.spellcheck.distance.EditDistance;
import opennlp.spellcheck.distance.LevenshteinDistance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates, at the engine level, how the injected {@link EditDistance} changes
 * lookup results. A single adjacent transposition is one edit under the default
 * {@link DamerauOSADistance} but two edits under {@link LevenshteinDistance}, so at a
 * tight {@code maxEditDistance} the two metrics disagree on whether a transposed typo
 * is correctable. This is the same effect measured by the eval-tests accuracy delta.
 */
public class SymSpellEditDistanceMetricTest {

  private static SymSpell engine(EditDistance ed) {
    final SymSpell s = new SymSpell(SymSpellConfig.builder()
        .maxDictionaryEditDistance(2)
        .editDistance(ed)
        .build());
    s.add("the", 23135851162L);
    s.add("and", 12997637966L);
    s.add("receive", 56473829L);
    s.add("form", 100000L);
    s.add("from", 1838693953L);
    return s;
  }

  @Test
  void damerauCorrectsSingleTranspositionAtDistanceOne() {
    final SymSpell damerau = engine(DamerauOSADistance.INSTANCE);
    // "teh" -> "the" is one transposition: distance 1 for Damerau-OSA.
    final List<SuggestItem> r = damerau.lookup("teh", Verbosity.TOP, 1);
    assertEquals("the", r.get(0).term());
    assertEquals(1, r.get(0).editDistance());
  }

  @Test
  void levenshteinNeedsDistanceTwoForTheSameTransposition() {
    final SymSpell lev = engine(LevenshteinDistance.INSTANCE);
    // The transposition is two edits for plain Levenshtein, so distance 1 finds nothing.
    assertTrue(lev.lookup("teh", Verbosity.TOP, 1).isEmpty());
    // It is recoverable once two edits are allowed.
    final List<SuggestItem> r = lev.lookup("teh", Verbosity.TOP, 2);
    assertEquals("the", r.get(0).term());
    assertEquals(2, r.get(0).editDistance());
  }

  @Test
  void bothMetricsAgreeOnNonTransposedTypos() {
    // "recieve" -> "receive" involves a transposition of i/e ... assert the simpler
    // substitution case where both metrics behave identically: "fonm" -> "form".
    final SymSpell damerau = engine(DamerauOSADistance.INSTANCE);
    final SymSpell lev = engine(LevenshteinDistance.INSTANCE);
    final SuggestItem d = damerau.lookup("fonm", Verbosity.TOP, 2).get(0);
    final SuggestItem l = lev.lookup("fonm", Verbosity.TOP, 2).get(0);
    assertEquals("form", d.term());
    assertEquals("form", l.term());
    assertEquals(d.editDistance(), l.editDistance());
  }
}
