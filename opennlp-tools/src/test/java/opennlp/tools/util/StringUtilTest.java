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

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.normalizer.UnicodeWhitespace;
import opennlp.tools.util.normalizer.UnicodeWhitespace.RelatedCharacter;
import opennlp.tools.util.normalizer.UnicodeWhitespace.WhitespaceCharacter;

/**
 * Tests for the {@link StringUtil} class.
 */
public class StringUtilTest {

  private static final int[] INFO_SEPARATORS = {0x001C, 0x001D, 0x001E, 0x001F};

  private static final int DESERET_CAPITAL_BEE = 0x10412; // supplementary-plane letter
  private static final int DESERET_SMALL_BEE = 0x1043A;
  private static final int GRINNING_FACE = 0x1F600; // emoji, two chars

  /**
   * Restores {@link WhitespaceMode} property resolution after each test, so no mode
   * state leaks.
   */
  @AfterEach
  void resetWhitespaceMode() {
    WhitespaceMode.reset();
  }

  private static List<WhitespaceCharacter> whitespace() {
    return UnicodeWhitespace.all();
  }

  private static List<RelatedCharacter> lookalikes() {
    return UnicodeWhitespace.lookalikes();
  }

  private static Stream<CharSequence> nonStringCharSequences() {
    return Stream.of(
        new StringBuilder("a\u00A0b"),
        new StringBuffer("a\u00A0b"),
        CharBuffer.wrap("a\u00A0b".toCharArray()));
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static String join(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  // -------------------------------------------------------------------------
  // isWhitespace / isUnicodeWhitespace (existing pins, kept)
  // -------------------------------------------------------------------------

  @Test
  void testNoBreakSpace() {
    Assertions.assertTrue(StringUtil.isWhitespace(0x00A0));
    Assertions.assertTrue(StringUtil.isWhitespace(0x2007));
    Assertions.assertTrue(StringUtil.isWhitespace(0x202F));

    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x00A0));
    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x2007));
    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x202F));
  }

  /**
   * Pins the exact semantics of {@link StringUtil#isWhitespace(int)} under the default
   * {@link WhitespaceMode#UNICODE}, at the code points where the JVM predicates and the
   * Unicode {@code White_Space} property disagree, so the predicate cannot drift silently:
   * it excludes the {@code U+001C..U+001F} information separators and includes the next
   * line control {@code U+0085}, agreeing with {@link StringUtil#isUnicodeWhitespace(int)}.
   */
  @Test
  void testIsWhitespaceBoundaryCodePointsDefaultIsUnicode() {
    for (int cp = 0x0009; cp <= 0x000D; cp++) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
    }
    Assertions.assertTrue(StringUtil.isWhitespace(0x0020));

    for (int cp : INFO_SEPARATORS) {
      Assertions.assertFalse(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
      Assertions.assertFalse(StringUtil.isWhitespace((char) cp), "U+" + Integer.toHexString(cp));
    }

    Assertions.assertTrue(StringUtil.isWhitespace(0x0085));
    Assertions.assertTrue(StringUtil.isWhitespace((char) 0x0085));

    int[] separators = {0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
        0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x205F, 0x3000};
    for (int cp : separators) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
    }

    Assertions.assertFalse(StringUtil.isWhitespace(0x200B));
    Assertions.assertFalse(StringUtil.isWhitespace(0xFEFF));
  }

  /**
   * Pins the exact semantics of {@link StringUtil#isWhitespace(int)} under
   * {@link WhitespaceMode#LEGACY}: the union of {@link Character#isWhitespace(int)} and the
   * {@code Zs} category, the opposite of the Unicode {@code White_Space} set at
   * {@code U+001C..U+001F} and {@code U+0085}. Trained sentence-detector and tokenizer
   * models built under this definition depend on it.
   */
  @Test
  void testIsWhitespaceBoundaryCodePointsUnderLegacyMode() {
    WhitespaceMode.setActive(WhitespaceMode.LEGACY);

    for (int cp = 0x0009; cp <= 0x000D; cp++) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
    }
    Assertions.assertTrue(StringUtil.isWhitespace(0x0020));

    for (int cp : INFO_SEPARATORS) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
      Assertions.assertTrue(StringUtil.isWhitespace((char) cp), "U+" + Integer.toHexString(cp));
    }

    Assertions.assertFalse(StringUtil.isWhitespace(0x0085));
    Assertions.assertFalse(StringUtil.isWhitespace((char) 0x0085));

    int[] separators = {0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
        0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x205F, 0x3000};
    for (int cp : separators) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
    }

    Assertions.assertFalse(StringUtil.isWhitespace(0x200B));
    Assertions.assertFalse(StringUtil.isWhitespace(0xFEFF));
  }

  /**
   * The {@link StringUtil#isUnicodeWhitespace(int)} facade must agree with the Unicode
   * {@code White_Space} reference implementation on every code point, including the
   * deltas to the legacy predicate ({@code U+0085} in, {@code U+001C..U+001F} out).
   */
  @Test
  void testIsUnicodeWhitespaceDelegates() {
    for (int cp = 0x0000; cp <= 0x3000; cp++) {
      Assertions.assertEquals(
          UnicodeWhitespace.isWhitespace(cp),
          StringUtil.isUnicodeWhitespace(cp), "U+" + Integer.toHexString(cp));
    }
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace(0x0085));
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace((char) 0x0085));
    for (int cp : INFO_SEPARATORS) {
      Assertions.assertFalse(StringUtil.isUnicodeWhitespace(cp), "U+" + Integer.toHexString(cp));
      Assertions.assertFalse(StringUtil.isUnicodeWhitespace((char) cp),
          "U+" + Integer.toHexString(cp));
    }
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace(' '));
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace((char) 0x00A0));
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace(0x2028));
    Assertions.assertFalse(StringUtil.isUnicodeWhitespace('a'));
    Assertions.assertFalse(StringUtil.isUnicodeWhitespace(0x200B));
  }

  // -------------------------------------------------------------------------
  // splitOnUnicodeWhitespace
  // -------------------------------------------------------------------------

  @Test
  void testSplitOnUnicodeWhitespaceNullThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.splitOnUnicodeWhitespace(null));
  }

  @Test
  void testSplitOnUnicodeWhitespaceEmptyAndWhitespaceOnly() {
    Assertions.assertArrayEquals(new String[0], StringUtil.splitOnUnicodeWhitespace(""));
    Assertions.assertArrayEquals(new String[0], StringUtil.splitOnUnicodeWhitespace("   "));
    Assertions.assertArrayEquals(new String[0], StringUtil.splitOnUnicodeWhitespace("\t\n\r"));
  }

  @Test
  void testSplitOnUnicodeWhitespaceAsciiBasics() {
    Assertions.assertArrayEquals(new String[] {"a"}, StringUtil.splitOnUnicodeWhitespace("a"));
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace("a b"));
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace("  a\tb  "));
    Assertions.assertArrayEquals(new String[] {"hello", "world"},
        StringUtil.splitOnUnicodeWhitespace("hello   world"));
  }

  @Test
  void testSplitOnUnicodeWhitespaceCollapsesMixedRuns() {
    // Tab, space, NBSP, NEL, ideographic space between two tokens — one boundary.
    final String input = "left" + "\t \u00A0\u0085\u3000" + "right";
    Assertions.assertArrayEquals(new String[] {"left", "right"},
        StringUtil.splitOnUnicodeWhitespace(input));
  }

  @Test
  void testSplitOnUnicodeWhitespacePreservesInternalNonWhitespace() {
    // Hyphen, punctuation, digits stay inside the term.
    Assertions.assertArrayEquals(new String[] {"well-known", "C++", "3.14"},
        StringUtil.splitOnUnicodeWhitespace("well-known  C++  3.14"));
  }

  @ParameterizedTest
  @MethodSource("whitespace")
  void testSplitOnUnicodeWhitespaceEveryWhiteSpaceCodePointSeparates(WhitespaceCharacter ws) {
    final String sep = cp(ws.codePoint());
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace("a" + sep + "b"),
        () -> ws.toUnicodeNotation() + " must separate tokens");
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace(sep + "a" + sep + sep + "b" + sep),
        () -> ws.toUnicodeNotation() + " leading/trailing/repeated runs must collapse");
  }

  @ParameterizedTest
  @MethodSource("lookalikes")
  void testSplitOnUnicodeWhitespaceLookalikesDoNotSeparate(RelatedCharacter related) {
    // ZWSP/BOM/etc. are not White_Space — they stay glued inside the token.
    final String glue = cp(related.codePoint());
    Assertions.assertArrayEquals(new String[] {"a" + glue + "b"},
        StringUtil.splitOnUnicodeWhitespace("a" + glue + "b"),
        () -> related.toUnicodeNotation() + " must remain inside the token");
  }

  @ParameterizedTest
  @ValueSource(ints = {0x001C, 0x001D, 0x001E, 0x001F})
  void testSplitOnUnicodeWhitespaceInfoSeparatorsDoNotSeparate(int infoSep) {
    // Character.isWhitespace includes these; Unicode White_Space does not.
    Assertions.assertArrayEquals(new String[] {"a" + cp(infoSep) + "b"},
        StringUtil.splitOnUnicodeWhitespace("a" + cp(infoSep) + "b"),
        () -> "U+" + Integer.toHexString(infoSep));
  }

  @Test
  void testSplitOnUnicodeWhitespacePdfStyleTypographicSpaces() {
    // Newspaper/PDF extracts often mix NBSP, figure space, thin/hair/narrow spaces.
    final String input = "New" + cp(0x00A0) + "York" + cp(0x2007) + "Times"
        + cp(0x2009) + cp(0x200A) + cp(0x202F) + "Inc.";
    Assertions.assertArrayEquals(new String[] {"New", "York", "Times", "Inc."},
        StringUtil.splitOnUnicodeWhitespace(input));
  }

  @Test
  void testSplitOnUnicodeWhitespaceLineAndParagraphSeparators() {
    Assertions.assertArrayEquals(new String[] {"a", "b", "c"},
        StringUtil.splitOnUnicodeWhitespace("a\u2028b\u2029c"));
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace("a\r\nb"));
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace("a\n\nb"));
  }

  @Test
  void testSplitOnUnicodeWhitespaceSupplementaryPlaneTerms() {
    // Terms may contain supplementary-plane code points (2 chars each); the scanner
    // must advance by charCount so it does not split inside a surrogate pair.
    final String bee = cp(DESERET_CAPITAL_BEE);
    final String face = cp(GRINNING_FACE);
    Assertions.assertEquals(2, bee.length());
    Assertions.assertEquals(2, face.length());

    Assertions.assertArrayEquals(new String[] {bee, face},
        StringUtil.splitOnUnicodeWhitespace(bee + " " + face));
    Assertions.assertArrayEquals(new String[] {bee + face},
        StringUtil.splitOnUnicodeWhitespace(bee + face));
    Assertions.assertArrayEquals(new String[] {bee, "x", face},
        StringUtil.splitOnUnicodeWhitespace(bee + "\u00A0x\u3000" + face));
  }

  @Test
  void testSplitOnUnicodeWhitespaceManyAlternatingTokens() {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 50; i++) {
      if (i > 0) {
        sb.append(i % 2 == 0 ? ' ' : '\u00A0');
      }
      sb.append((char) ('a' + (i % 26)));
    }
    final String[] tokens = StringUtil.splitOnUnicodeWhitespace(sb.toString());
    Assertions.assertEquals(50, tokens.length);
    for (int i = 0; i < 50; i++) {
      Assertions.assertEquals(String.valueOf((char) ('a' + (i % 26))), tokens[i]);
    }
  }

  @Test
  void testSplitOnUnicodeWhitespaceLongWhitespaceRun() {
    final String run = " \t\u00A0".repeat(200);
    Assertions.assertArrayEquals(new String[] {"x", "y"},
        StringUtil.splitOnUnicodeWhitespace(run + "x" + run + "y" + run));
  }

  @Test
  void testSplitOnUnicodeWhitespaceSingleWhitespaceBetweenEmptyYieldsEmpty() {
    // Only whitespace → empty array, never [""].
    for (WhitespaceCharacter ws : UnicodeWhitespace.all()) {
      Assertions.assertArrayEquals(new String[0],
          StringUtil.splitOnUnicodeWhitespace(cp(ws.codePoint())),
          () -> ws.toUnicodeNotation());
    }
  }

  @ParameterizedTest
  @MethodSource("nonStringCharSequences")
  void testSplitOnUnicodeWhitespaceAcceptsCharSequenceImplementations(CharSequence input) {
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace(input));
  }

  @Test
  void testSplitOnUnicodeWhitespaceIgnoresLegacyMode() {
    // The Unicode helpers are unconditional; LEGACY must not change them.
    final String withNel = "a\u0085b";
    final String withInfo = "a\u001Cb";
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace(withNel));
    Assertions.assertArrayEquals(new String[] {"a\u001Cb"},
        StringUtil.splitOnUnicodeWhitespace(withInfo));

    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        StringUtil.splitOnUnicodeWhitespace(withNel));
    Assertions.assertArrayEquals(new String[] {"a\u001Cb"},
        StringUtil.splitOnUnicodeWhitespace(withInfo));
  }

  @Test
  void testSplitOnUnicodeWhitespaceAllTwentyFiveAsMixedSeparators() {
    // Build "t0 <ws0> t1 <ws1> ... t24 <ws24> t25" using every White_Space code point.
    final List<WhitespaceCharacter> all = UnicodeWhitespace.all();
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < all.size(); i++) {
      sb.append('t').append(i);
      sb.appendCodePoint(all.get(i).codePoint());
    }
    sb.append('t').append(all.size());

    final String[] expected = IntStream.rangeClosed(0, all.size())
        .mapToObj(i -> "t" + i)
        .toArray(String[]::new);
    Assertions.assertArrayEquals(expected, StringUtil.splitOnUnicodeWhitespace(sb.toString()));
  }

  // -------------------------------------------------------------------------
  // trimUnicodeWhitespace
  // -------------------------------------------------------------------------

  @Test
  void testTrimUnicodeWhitespaceNullThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StringUtil.trimUnicodeWhitespace(null));
  }

  @Test
  void testTrimUnicodeWhitespaceEmptyAndWhitespaceOnly() {
    Assertions.assertEquals("", StringUtil.trimUnicodeWhitespace(""));
    Assertions.assertEquals("", StringUtil.trimUnicodeWhitespace("   "));
    Assertions.assertEquals("", StringUtil.trimUnicodeWhitespace("\t\n\r\u00A0\u0085"));
  }

  @Test
  void testTrimUnicodeWhitespacePreservesInternalWhitespace() {
    Assertions.assertEquals("a b", StringUtil.trimUnicodeWhitespace("  a b  "));
    Assertions.assertEquals("a\u00A0b", StringUtil.trimUnicodeWhitespace("\ta\u00A0b\n"));
    Assertions.assertEquals("a  \t  b", StringUtil.trimUnicodeWhitespace("a  \t  b"));
  }

  @ParameterizedTest
  @MethodSource("whitespace")
  void testTrimUnicodeWhitespaceEveryWhiteSpaceCodePoint(WhitespaceCharacter ws) {
    final String sep = cp(ws.codePoint());
    Assertions.assertEquals("x", StringUtil.trimUnicodeWhitespace(sep + "x" + sep),
        () -> ws.toUnicodeNotation());
    Assertions.assertEquals("x y", StringUtil.trimUnicodeWhitespace(sep + "x y" + sep),
        () -> ws.toUnicodeNotation() + " must not trim internal ASCII space");
    Assertions.assertEquals("", StringUtil.trimUnicodeWhitespace(sep + sep + sep),
        () -> ws.toUnicodeNotation() + " whitespace-only");
  }

  @ParameterizedTest
  @MethodSource("lookalikes")
  void testTrimUnicodeWhitespaceLookalikesAreNotTrimmed(RelatedCharacter related) {
    final String glue = cp(related.codePoint());
    // Leading/trailing lookalikes stay — they are not White_Space.
    Assertions.assertEquals(glue + "x" + glue,
        StringUtil.trimUnicodeWhitespace(glue + "x" + glue),
        () -> related.toUnicodeNotation());
    // Surrounded by real whitespace, lookalike still survives as content.
    Assertions.assertEquals(glue + "x" + glue,
        StringUtil.trimUnicodeWhitespace(" " + glue + "x" + glue + " "),
        () -> related.toUnicodeNotation() + " after real trim");
  }

  @ParameterizedTest
  @ValueSource(ints = {0x001C, 0x001D, 0x001E, 0x001F})
  void testTrimUnicodeWhitespaceInfoSeparatorsAreNotTrimmed(int infoSep) {
    final String sep = cp(infoSep);
    Assertions.assertEquals(sep + "x" + sep, StringUtil.trimUnicodeWhitespace(sep + "x" + sep));
    Assertions.assertEquals(sep + "x" + sep,
        StringUtil.trimUnicodeWhitespace(" " + sep + "x" + sep + " "));
  }

  @Test
  void testTrimUnicodeWhitespaceSupplementaryPlaneEdges() {
    final String bee = cp(DESERET_CAPITAL_BEE);
    final String face = cp(GRINNING_FACE);
    Assertions.assertEquals(bee,
        StringUtil.trimUnicodeWhitespace("\u00A0" + bee + "\u3000"));
    Assertions.assertEquals(bee + " " + face,
        StringUtil.trimUnicodeWhitespace("\u0085" + bee + " " + face + "\t"));
    // Trim must not chop a surrogate pair when the edge is a supplementary char.
    Assertions.assertEquals(bee, StringUtil.trimUnicodeWhitespace(bee));
    Assertions.assertEquals(face, StringUtil.trimUnicodeWhitespace(face));
  }

  @Test
  void testTrimUnicodeWhitespaceMixedLeadingTrailing() {
    final String leading = "\t\u00A0\u2007\u202F\u3000";
    final String trailing = "\u0085\u2028\u2029\n";
    Assertions.assertEquals("keep me",
        StringUtil.trimUnicodeWhitespace(leading + "keep me" + trailing));
  }

  @Test
  void testTrimUnicodeWhitespaceOnlyLeadingOrOnlyTrailing() {
    Assertions.assertEquals("x", StringUtil.trimUnicodeWhitespace("\u00A0\u00A0x"));
    Assertions.assertEquals("x", StringUtil.trimUnicodeWhitespace("x\u00A0\u00A0"));
    Assertions.assertEquals("x", StringUtil.trimUnicodeWhitespace("x"));
  }

  @Test
  void testTrimUnicodeWhitespaceIgnoresLegacyMode() {
    Assertions.assertEquals("a\u0085b", StringUtil.trimUnicodeWhitespace(" a\u0085b "));
    Assertions.assertEquals("\u001Cx\u001C",
        StringUtil.trimUnicodeWhitespace("\u001Cx\u001C"));

    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    Assertions.assertEquals("a\u0085b", StringUtil.trimUnicodeWhitespace(" a\u0085b "));
    // Info separators still not trimmed under LEGACY — Unicode helper is unconditional.
    Assertions.assertEquals("\u001Cx\u001C",
        StringUtil.trimUnicodeWhitespace("\u001Cx\u001C"));
    // But NEL is still trimmed (Unicode), even though LEGACY isWhitespace(NEL) is false.
    Assertions.assertEquals("x", StringUtil.trimUnicodeWhitespace("\u0085x\u0085"));
  }

  @ParameterizedTest
  @MethodSource("nonStringCharSequences")
  void testTrimUnicodeWhitespaceAcceptsCharSequenceImplementations(CharSequence input) {
    Assertions.assertEquals("a\u00A0b", StringUtil.trimUnicodeWhitespace(input));
  }

  @Test
  void testTrimUnicodeWhitespaceDoesNotMatchStringTrimOnNbsp() {
    // String.trim() only strips <= U+0020; NBSP must still be trimmed by our helper.
    final String padded = "\u00A0hello\u00A0";
    Assertions.assertEquals(padded, padded.trim());
    Assertions.assertEquals("hello", StringUtil.trimUnicodeWhitespace(padded));
  }

  // -------------------------------------------------------------------------
  // isUnicodeBlank
  // -------------------------------------------------------------------------

  @Test
  void testIsUnicodeBlankNullAndEmpty() {
    Assertions.assertTrue(StringUtil.isUnicodeBlank(null));
    Assertions.assertTrue(StringUtil.isUnicodeBlank(""));
  }

  @Test
  void testIsUnicodeBlankAsciiWhitespace() {
    Assertions.assertTrue(StringUtil.isUnicodeBlank(" "));
    Assertions.assertTrue(StringUtil.isUnicodeBlank(" \t\n\r\f"));
    Assertions.assertFalse(StringUtil.isUnicodeBlank("a"));
    Assertions.assertFalse(StringUtil.isUnicodeBlank(" a "));
  }

  @ParameterizedTest
  @MethodSource("whitespace")
  void testIsUnicodeBlankEveryWhiteSpaceCodePoint(WhitespaceCharacter ws) {
    Assertions.assertTrue(StringUtil.isUnicodeBlank(cp(ws.codePoint())),
        () -> ws.toUnicodeNotation());
    Assertions.assertTrue(StringUtil.isUnicodeBlank(cp(ws.codePoint()).repeat(3)),
        () -> ws.toUnicodeNotation() + " repeated");
    Assertions.assertFalse(StringUtil.isUnicodeBlank(cp(ws.codePoint()) + "x"),
        () -> ws.toUnicodeNotation() + " with content");
  }

  @Test
  void testIsUnicodeBlankAllTwentyFiveTogether() {
    final String allWs = UnicodeWhitespace.all().stream()
        .map(ws -> cp(ws.codePoint()))
        .collect(Collectors.joining());
    Assertions.assertTrue(StringUtil.isUnicodeBlank(allWs));
    Assertions.assertFalse(StringUtil.isUnicodeBlank(allWs + "."));
  }

  @ParameterizedTest
  @MethodSource("lookalikes")
  void testIsUnicodeBlankLookalikesAreNotBlank(RelatedCharacter related) {
    Assertions.assertFalse(StringUtil.isUnicodeBlank(cp(related.codePoint())),
        () -> related.toUnicodeNotation());
    Assertions.assertFalse(StringUtil.isUnicodeBlank(" " + cp(related.codePoint()) + " "),
        () -> related.toUnicodeNotation() + " surrounded by real whitespace");
  }

  @ParameterizedTest
  @ValueSource(ints = {0x001C, 0x001D, 0x001E, 0x001F})
  void testIsUnicodeBlankInfoSeparatorsAreNotBlank(int infoSep) {
    Assertions.assertFalse(StringUtil.isUnicodeBlank(cp(infoSep)));
    // JVM String.isBlank uses Character.isWhitespace, which *does* treat info separators
    // as blank — pin that we disagree.
    Assertions.assertTrue(cp(infoSep).isBlank());
    Assertions.assertFalse(StringUtil.isUnicodeBlank(cp(infoSep)));
  }

  @Test
  void testIsUnicodeBlankDisagreesWithStringIsBlankOnNbsp() {
    Assertions.assertFalse("\u00A0".isBlank()); // JDK: NBSP is not Character.isWhitespace
    Assertions.assertTrue(StringUtil.isUnicodeBlank("\u00A0"));
    Assertions.assertFalse("\u0085".isBlank()); // JDK: NEL likewise
    Assertions.assertTrue(StringUtil.isUnicodeBlank("\u0085"));
  }

  @Test
  void testIsUnicodeBlankSupplementaryPlaneContent() {
    Assertions.assertFalse(StringUtil.isUnicodeBlank(cp(DESERET_CAPITAL_BEE)));
    Assertions.assertFalse(StringUtil.isUnicodeBlank(cp(GRINNING_FACE)));
    Assertions.assertFalse(StringUtil.isUnicodeBlank("\u00A0" + cp(GRINNING_FACE)));
  }

  @Test
  void testIsUnicodeBlankIgnoresLegacyMode() {
    Assertions.assertTrue(StringUtil.isUnicodeBlank("\u0085"));
    Assertions.assertFalse(StringUtil.isUnicodeBlank("\u001C"));

    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    Assertions.assertTrue(StringUtil.isUnicodeBlank("\u0085"));
    Assertions.assertFalse(StringUtil.isUnicodeBlank("\u001C"));
  }

  @ParameterizedTest
  @MethodSource("nonStringCharSequences")
  void testIsUnicodeBlankAcceptsCharSequenceImplementations(CharSequence input) {
    // "a\u00A0b" is not blank.
    Assertions.assertFalse(StringUtil.isUnicodeBlank(input));
  }

  // -------------------------------------------------------------------------
  // Cross-helper invariants
  // -------------------------------------------------------------------------

  @Test
  void testBlankIffSplitEmptyAndTrimEmpty() {
    final String[] samples = {
        "",
        "   ",
        "\t\n\u00A0\u0085\u3000",
        "x",
        " x ",
        "a\u00A0b",
        "\u001C",
        "\u200B",
        cp(DESERET_CAPITAL_BEE),
        join(0x00A0, DESERET_CAPITAL_BEE, 0x3000),
        UnicodeWhitespace.all().stream().map(ws -> cp(ws.codePoint()))
            .collect(Collectors.joining())
    };
    for (String sample : samples) {
      final boolean blank = StringUtil.isUnicodeBlank(sample);
      final String[] split = StringUtil.splitOnUnicodeWhitespace(sample);
      final String trimmed = StringUtil.trimUnicodeWhitespace(sample);
      Assertions.assertEquals(blank, split.length == 0,
          () -> "blank iff split empty for: " + Arrays.toString(sample.codePoints().toArray()));
      Assertions.assertEquals(blank, trimmed.isEmpty(),
          () -> "blank iff trim empty for: " + Arrays.toString(sample.codePoints().toArray()));
    }
  }

  @Test
  void testTrimThenSplitEqualsSplit() {
    // Leading/trailing whitespace is ignored by split, so trim-then-split is a no-op.
    final String[] samples = {
        "  a  b  ",
        "\u00A0hello\u3000world\u0085",
        "\t\t alone \n",
        "a\u2007b\u2009c"
    };
    for (String sample : samples) {
      Assertions.assertArrayEquals(
          StringUtil.splitOnUnicodeWhitespace(sample),
          StringUtil.splitOnUnicodeWhitespace(StringUtil.trimUnicodeWhitespace(sample)),
          () -> sample);
    }
  }

  @Test
  void testJoinSplitTokensWithSpaceRoundTripShape() {
    final String[] tokens = StringUtil.splitOnUnicodeWhitespace(
        "alpha\u00A0beta\u3000gamma");
    Assertions.assertArrayEquals(new String[] {"alpha", "beta", "gamma"}, tokens);
    Assertions.assertArrayEquals(tokens,
        StringUtil.splitOnUnicodeWhitespace(String.join(" ", tokens)));
  }

  @Test
  void testSplitDoesNotEmitEmptyTokensAroundSeparators() {
    // Regex split("\\s+") on a leading-space string yields a leading "" with limit -1;
    // our scanner must never emit empty strings.
    for (String sample : List.of(" a", "a ", " a ", "  a  b  ", "\u00A0a\u00A0")) {
      for (String token : StringUtil.splitOnUnicodeWhitespace(sample)) {
        Assertions.assertFalse(token.isEmpty(), () -> "empty token from: '" + sample + "'");
        Assertions.assertFalse(StringUtil.isUnicodeBlank(token),
            () -> "whitespace-only token from: '" + sample + "'");
      }
    }
  }

  // -------------------------------------------------------------------------
  // Existing non-whitespace StringUtil coverage
  // -------------------------------------------------------------------------

  @Test
  void testToLowerCase() {
    Assertions.assertEquals("test", StringUtil.toLowerCase("TEST"));
    Assertions.assertEquals("simple", StringUtil.toLowerCase("SIMPLE"));
  }

  @Test
  void testToUpperCase() {
    Assertions.assertEquals("TEST", StringUtil.toUpperCase("test"));
    Assertions.assertEquals("SIMPLE", StringUtil.toUpperCase("simple"));
  }

  @Test
  void testIsEmpty() {
    Assertions.assertTrue(StringUtil.isEmpty(""));
    Assertions.assertFalse(StringUtil.isEmpty("a"));
  }

  @Test
  void testIsEmptyWithNullString() {
    Assertions.assertThrows(NullPointerException.class, () -> StringUtil.isEmpty(null));
  }

  @Test
  void testLowercaseBeyondBMP() {
    int[] codePoints = new int[] {65, DESERET_CAPITAL_BEE, 67};
    int[] expectedCodePoints = new int[] {97, DESERET_SMALL_BEE, 99};
    String input = new String(codePoints, 0, codePoints.length);
    String lc = StringUtil.toLowerCase(input);
    Assertions.assertArrayEquals(expectedCodePoints, lc.codePoints().toArray());
  }
}
