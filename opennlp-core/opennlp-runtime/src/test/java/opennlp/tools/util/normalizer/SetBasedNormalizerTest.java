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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SetBasedNormalizerTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static String quotes(String text) {
    return QuoteCharSequenceNormalizer.getInstance().normalize(text).toString();
  }

  private static String digits(String text) {
    return DigitCharSequenceNormalizer.getInstance().normalize(text).toString();
  }

  private static String invisible(String text) {
    return InvisibleCharSequenceNormalizer.getInstance().normalize(text).toString();
  }

  private static String ellipsis(String text) {
    return EllipsisCharSequenceNormalizer.getInstance().normalize(text).toString();
  }

  private static String bullet(String text) {
    return BulletCharSequenceNormalizer.getInstance().normalize(text).toString();
  }

  // --- quotes ------------------------------------------------------------------------------

  @Test
  void testQuotesFoldSingleAndDouble() {
    assertEquals("don't", quotes("don" + cp(0x2019) + "t"));   // right single quote
    assertEquals("\"hi\"", quotes(cp(0x201C) + "hi" + cp(0x201D))); // curly double quotes
    assertEquals("\"x\"", quotes(cp(0x00AB) + "x" + cp(0x00BB)));   // guillemets
    assertEquals("'y'", quotes(cp(0x2039) + "y" + cp(0x203A)));     // single angle quotes
    assertEquals("'", quotes(cp(0xFF07)));   // fullwidth apostrophe
    assertEquals("\"", quotes(cp(0xFF02)));  // fullwidth quotation mark
    assertEquals("'", quotes(cp(0x02BC)));   // modifier letter apostrophe
  }

  @Test
  void testQuotesLeaveAsciiAndNonQuotesAlone() {
    assertEquals("'a' \"b\"", quotes("'a' \"b\""));
    assertEquals("abc", quotes("abc"));
    assertEquals(cp(0x2014), quotes(cp(0x2014))); // em dash is not a quote
  }

  @Test
  void testQuotesSingleton() {
    assertSame(QuoteCharSequenceNormalizer.getInstance(), QuoteCharSequenceNormalizer.getInstance());
  }

  // --- digits ------------------------------------------------------------------------------

  @Test
  void testDigitsMapDecimalDigitsToAscii() {
    assertEquals("123", digits(cp(0x0661) + cp(0x0662) + cp(0x0663))); // arabic-indic 1 2 3
    assertEquals("12", digits(cp(0x0967) + cp(0x0968)));               // devanagari 1 2
    assertEquals("15", digits(cp(0xFF11) + cp(0xFF15)));               // fullwidth 1 5
    assertEquals("a5b", digits("a" + cp(0x0665) + "b"));               // arabic-indic 5
  }

  @Test
  void testDigitsLeaveAsciiAndNonDecimalNumeralsAlone() {
    assertEquals("0123456789", digits("0123456789"));
    assertEquals(cp(0x00B2), digits(cp(0x00B2)));   // superscript two (category No)
    assertEquals(cp(0x2160), digits(cp(0x2160)));   // roman numeral one (category Nl)
    assertEquals(cp(0x00BD), digits(cp(0x00BD)));   // vulgar fraction one half (category No)
    assertEquals("abc", digits("abc"));
  }

  @Test
  void testDigitsSingleton() {
    assertSame(DigitCharSequenceNormalizer.getInstance(), DigitCharSequenceNormalizer.getInstance());
  }

  @Test
  void testDigitsReturnAsciiDigitTextUncopied() {
    // ASCII digits are already their own fold, so digit-clean text short-circuits uncopied.
    final String text = "version 42 of 2026";
    assertSame(text, DigitCharSequenceNormalizer.getInstance().normalize(text));
  }

  // --- invisible / bidi controls -----------------------------------------------------------

  @Test
  void testInvisibleRemovesFormatAndBidiControls() {
    assertEquals("ab", invisible("a" + cp(0xFEFF) + "b"));    // byte order mark
    assertEquals("ab", invisible("a" + cp(0x200B) + "b"));    // zero width space
    assertEquals("ab", invisible("a" + cp(0x2060) + "b"));    // word joiner
    assertEquals("softhyphen", invisible("soft" + cp(0x00AD) + "hyphen"));
    assertEquals("evil", invisible(cp(0x202E) + "evil" + cp(0x202C))); // bidi override + pop
  }

  @Test
  void testInvisibleKeepsJoinersVariationSelectorsAndText() {
    final String zwj = "a" + cp(0x200D) + "b";   // zero width joiner is meaningful
    assertEquals(zwj, invisible(zwj));
    final String zwnj = "a" + cp(0x200C) + "b";  // zero width non-joiner is meaningful
    assertEquals(zwnj, invisible(zwnj));
    final String family = cp(0x1F468) + cp(0x200D) + cp(0x1F469); // ZWJ emoji sequence preserved
    assertEquals(family, invisible(family));
    assertEquals("hello", invisible("hello"));
  }

  @Test
  void testInvisibleSingleton() {
    assertSame(InvisibleCharSequenceNormalizer.getInstance(),
        InvisibleCharSequenceNormalizer.getInstance());
  }

  // --- ellipsis ----------------------------------------------------------------------------

  @Test
  void testEllipsisExpandsToAsciiDots() {
    assertEquals("...", ellipsis(cp(0x2026)));               // horizontal ellipsis
    assertEquals("wait...", ellipsis("wait" + cp(0x2026)));
    assertEquals("..", ellipsis(cp(0x2025)));                // two dot leader
    assertEquals("...", ellipsis("..."));                    // ascii dots unchanged
  }

  @Test
  void testEllipsisSingleton() {
    assertSame(EllipsisCharSequenceNormalizer.getInstance(),
        EllipsisCharSequenceNormalizer.getInstance());
  }

  // --- bullets -----------------------------------------------------------------------------

  @Test
  void testBulletsBecomeSeparatorSpaces() {
    assertEquals(" item", bullet(cp(0x2022) + "item"));      // bullet
    assertEquals(" item", bullet(cp(0x25E6) + "item"));      // white bullet
    assertEquals("a b", bullet("a" + cp(0x2043) + "b"));     // hyphen bullet
  }

  @Test
  void testBulletsLeaveMiddleDotAndTextAlone() {
    assertEquals(cp(0x00B7), bullet(cp(0x00B7)));            // middle dot kept (Catalan)
    assertEquals("plain", bullet("plain"));
  }

  @Test
  void testBulletSingleton() {
    assertSame(BulletCharSequenceNormalizer.getInstance(),
        BulletCharSequenceNormalizer.getInstance());
  }
}
