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
package opennlp.tools.util.normalizer;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static opennlp.tools.util.normalizer.NormalizerTestUtil.cp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfusablesTest {

  @Test
  void testCyrillicLetterIsConfusableWithLatin() {
    final String cyrillicA = cp(0x0430); // CYRILLIC SMALL LETTER A, looks like Latin 'a'
    assertTrue(Confusables.confusable(cyrillicA, "a"));
    assertFalse(Confusables.confusable(cyrillicA, "b"));
  }

  @Test
  void testHomoglyphSpoofWordReducesToLatinSpelling() {
    final String spoof = "p" + cp(0x0430) + "yp" + cp(0x0430) + "l"; // paypal with Cyrillic a's
    assertTrue(Confusables.confusable(spoof, "paypal"));
    assertEquals(Confusables.skeleton("paypal"), Confusables.skeleton(spoof));
  }

  @Test
  void testHorizontalEllipsisFoldsToThreeFullStops() {
    assertEquals(Confusables.skeleton("..."), Confusables.skeleton(cp(0x2026)));
    assertTrue(Confusables.confusable(cp(0x2026), "..."));
  }

  @Test
  void testDistinctWordsAreNotConfusable() {
    assertFalse(Confusables.confusable("cat", "dog"));
  }

  @Test
  void testSkeletonIsIdempotent() {
    final String skeleton = Confusables.skeleton(cp(0x0430) + "bc");
    assertEquals(skeleton, Confusables.skeleton(skeleton));
  }

  @Test
  void testNormalizerProducesTheSkeleton() {
    final String spoof = "p" + cp(0x0430) + "yp" + cp(0x0430) + "l";
    assertEquals(Confusables.skeleton(spoof),
        ConfusableSkeletonCharSequenceNormalizer.getInstance().normalize(spoof).toString());
  }

  @Test
  void testMultipleCyrillicLookalikesFold() {
    final String spoof = "d" + cp(0x0430) + "t" + cp(0x0430); // "data" with Cyrillic a's
    assertEquals(Confusables.skeleton("data"), Confusables.skeleton(spoof));
  }

  @Test
  void testSkeletonReturnsCleanAsciiUnchanged() {
    // No code point of the text is a prototype key, so the fast path returns the input as-is.
    final String clean = "hello";
    assertSame(clean, Confusables.skeleton(clean));
  }

  @Test
  void testSkeletonStillMapsAsciiPrototypeKeys() {
    // ASCII code points that ARE prototype keys must defeat the fast path and map as before.
    assertEquals("rn", Confusables.skeleton("m"));
    assertEquals("l", Confusables.skeleton("I"));
    assertEquals("l", Confusables.skeleton("1"));
    assertEquals("O", Confusables.skeleton("0"));
    assertEquals("corn", Confusables.skeleton("com"));
  }

  @Test
  void testSkeletonStillDecomposesPrecomposedText() {
    // A precomposed character is not in NFD form, so it must take the full path and decompose,
    // and the result must agree with the already-decomposed spelling (canonical equivalence).
    final String precomposed = cp(0x00E9);          // e with acute, single code point
    final String decomposed = "e" + cp(0x0301);     // e + combining acute
    assertEquals(decomposed, Confusables.skeleton(precomposed));
    assertEquals(Confusables.skeleton(precomposed), Confusables.skeleton(decomposed));
  }

  @Test
  void testSkeletonReturnsNfdStableNonKeyTextUnchanged() {
    // Non-ASCII, already in NFD form, and no prototype key: the fast path applies.
    final String hiragana = cp(0x3042);
    assertSame(hiragana, Confusables.skeleton(hiragana));
  }

  @Test
  void testSkeletonStillMapsNonAsciiPrototypeKeys() {
    // U+4E00 is a prototype key (maps to the prolonged sound mark), so it must still map.
    assertEquals(cp(0x30FC), Confusables.skeleton(cp(0x4E00)));
  }

  @Test
  void testTermConfusableFoldDimension() {
    final String spoof = "p" + cp(0x0430) + "yp" + cp(0x0430) + "l";
    final TermAnalyzer analyzer = TermAnalyzer.builder().confusableFold().build();
    assertEquals(Confusables.skeleton("paypal"), analyzer.analyze(spoof).get(0).normalized());
  }

  @Test
  void testNullArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Confusables.skeleton(null));
    assertThrows(IllegalArgumentException.class, () -> Confusables.confusable(null, "a"));
    assertThrows(IllegalArgumentException.class, () -> Confusables.confusable("a", null));
  }

  @Test
  void tableLoadsEveryMapping() throws Exception {
    // Completeness audit against bundled UTS #39 confusables.txt 18.0.0, like the CaseFolding
    // C+F count: a data update that adds or drops a mapping trips this for a conscious bump.
    final Map<Integer, String> table;
    try (InputStream in = Confusables.class.getResourceAsStream("confusables.txt")) {
      table = Confusables.parse(in);
    }
    assertEquals(6712, table.size());
    assertEquals("a", table.get(0x0430)); // CYRILLIC SMALL LETTER A
    assertEquals(null, table.get(0x00C7)); // LATIN CAPITAL LETTER C WITH CEDILLA unmapped in 18.0
  }

  @Test
  void ogonekNoLongerSkeletonsToLatinI() {
    // Unicode 18.0 removed the OGONEK (U+02DB) → LATIN SMALL LETTER I mapping that 17.0 had.
    // Combined with NFD, 17.0 skeletons treated ogonek as "i"; 18.0 leaves it as itself.
    final String ogonek = cp(0x02DB);
    assertFalse(Confusables.confusable(ogonek, "i"));
    assertEquals(ogonek, Confusables.skeleton(ogonek));
  }

}
