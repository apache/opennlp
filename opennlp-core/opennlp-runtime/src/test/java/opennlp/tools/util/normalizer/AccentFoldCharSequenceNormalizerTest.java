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

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AccentFoldCharSequenceNormalizerTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static String fold(String text) {
    return AccentFoldCharSequenceNormalizer.getInstance().normalize(text).toString();
  }

  @Test
  void testFoldsLatinAccents() {
    assertEquals("cafe", fold("caf" + cp(0x00E9)));        // cafe with acute e
    assertEquals("naive", fold("na" + cp(0x00EF) + "ve")); // naive with diaeresis i
    assertEquals("Muller", fold("M" + cp(0x00FC) + "ller")); // Muller with umlaut u
    assertEquals("anos", fold("a" + cp(0x00F1) + "os"));   // anos with tilde n
  }

  @Test
  void testMapsStrokeAndLigatureLetters() {
    assertEquals("o", fold(cp(0x00F8)));   // o with stroke
    assertEquals("ae", fold(cp(0x00E6)));  // ae ligature
    assertEquals("oe", fold(cp(0x0153)));  // oe ligature
    assertEquals("Strasse", fold("Stra" + cp(0x00DF) + "e")); // eszett
    assertEquals("th", fold(cp(0x00FE)));  // thorn
    assertEquals("l", fold(cp(0x0142)));   // l with stroke
    assertEquals("i", fold(cp(0x0131)));   // dotless i
  }

  @Test
  void testEveryStrokeAndLigatureLetterMaps() {
    assertEquals("o", fold(cp(0x00F8)));   // o with stroke
    assertEquals("O", fold(cp(0x00D8)));   // O with stroke
    assertEquals("ae", fold(cp(0x00E6)));  // ae
    assertEquals("AE", fold(cp(0x00C6)));  // AE
    assertEquals("oe", fold(cp(0x0153)));  // oe
    assertEquals("OE", fold(cp(0x0152)));  // OE
    assertEquals("ss", fold(cp(0x00DF)));  // eszett
    assertEquals("SS", fold(cp(0x1E9E)));  // capital eszett
    assertEquals("th", fold(cp(0x00FE)));  // thorn
    assertEquals("TH", fold(cp(0x00DE)));  // capital thorn
    assertEquals("d", fold(cp(0x00F0)));   // eth
    assertEquals("D", fold(cp(0x00D0)));   // capital eth
    assertEquals("d", fold(cp(0x0111)));   // d with stroke
    assertEquals("D", fold(cp(0x0110)));   // D with stroke
    assertEquals("l", fold(cp(0x0142)));   // l with stroke
    assertEquals("L", fold(cp(0x0141)));   // L with stroke
    assertEquals("h", fold(cp(0x0127)));   // h with stroke
    assertEquals("H", fold(cp(0x0126)));   // H with stroke
    assertEquals("i", fold(cp(0x0131)));   // dotless i
  }

  @Test
  void testLeadingCombiningMarkWithNoBaseIsKept() {
    // A combining mark with no preceding base (baseScript == null) must be kept, not dropped.
    final String input = cp(0x0301) + "x"; // combining acute, then x
    assertEquals(input, fold(input));
  }

  @Test
  void testFoldsGreekAndCyrillicAccents() {
    assertEquals(cp(0x03B1), fold(cp(0x03AC))); // Greek alpha with tonos -> alpha
    assertEquals(cp(0x0438), fold(cp(0x0439))); // Cyrillic short i -> i
  }

  @Test
  void testLeavesAsciiUnchanged() {
    assertEquals("hello world", fold("hello world"));
  }

  @Test
  void testDoesNotTouchDevanagariArabicOrHebrewMarks() {
    // The critical guard: marks on non-folded scripts are essential orthography and must survive.
    final String devanagari = cp(0x0915) + cp(0x093E); // ka + aa vowel sign
    assertEquals(devanagari, fold(devanagari));

    final String arabic = cp(0x0628) + cp(0x064E);     // beh + fatha (a nonspacing mark)
    assertEquals(arabic, fold(arabic));
    assertTrue(fold(arabic).indexOf(0x064E) >= 0, "the Arabic fatha must not be stripped");

    final String hebrew = cp(0x05D0) + cp(0x05B8);     // alef + qamats (a nonspacing mark)
    assertEquals(hebrew, fold(hebrew));
    assertTrue(fold(hebrew).indexOf(0x05B8) >= 0, "the Hebrew point must not be stripped");
  }

  @Test
  void testScriptScopeIsConfigurable() {
    // With no folded scripts, Latin accents are preserved.
    final AccentFoldCharSequenceNormalizer none =
        new AccentFoldCharSequenceNormalizer(Set.of(), false);
    assertEquals("caf" + cp(0x00E9), none.normalize("caf" + cp(0x00E9)).toString());

    // Widening the scope to Arabic folds an Arabic mark that the default leaves untouched.
    final AccentFoldCharSequenceNormalizer arabicToo =
        new AccentFoldCharSequenceNormalizer(Set.of(Character.UnicodeScript.ARABIC), false);
    assertEquals(cp(0x0628), arabicToo.normalize(cp(0x0628) + cp(0x064E)).toString());
  }

  @Test
  void testStrokeLetterMappingIsConfigurable() {
    final AccentFoldCharSequenceNormalizer noStroke =
        new AccentFoldCharSequenceNormalizer(Set.of(Character.UnicodeScript.LATIN), false);
    assertEquals(cp(0x00DF), noStroke.normalize(cp(0x00DF)).toString()); // eszett kept as-is
  }

  @Test
  void testComposesAfterCaseFold() {
    final CharSequenceNormalizer pipeline = new AggregateCharSequenceNormalizer(
        CaseFoldCharSequenceNormalizer.getInstance(),
        AccentFoldCharSequenceNormalizer.getInstance());
    assertEquals("cafe", pipeline.normalize("CAF" + cp(0x00C9)).toString()); // CAFE with acute E
  }

  @Test
  void testInstanceIsSharedSingleton() {
    assertSame(AccentFoldCharSequenceNormalizer.getInstance(),
        AccentFoldCharSequenceNormalizer.getInstance());
  }
}
