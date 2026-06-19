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

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.UnicodeWhitespace.WhitespaceCharacter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CharClassTest {

  private static final CharClass WS = CharClass.whitespace();
  private static final CharClass DASH = CharClass.dashes();

  // Non-ASCII test characters are built from code points (no literal glyphs, no Unicode escapes)
  // so the source stays pure ASCII and the intent is explicit. Tab and newline use \t and \n.
  private static final String NBSP = cp(0x00A0);
  private static final String IDEOGRAPHIC = cp(0x3000);
  private static final String EM_DASH = cp(0x2014);
  private static final String EN_DASH = cp(0x2013);
  private static final String FIGURE_DASH = cp(0x2012);
  private static final String MINUS_SIGN = cp(0x2212);
  private static final String YEZIDI_HYPHEN = cp(0x10EAD);
  private static final String GRINNING_FACE = cp(0x1F600);

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static CodePointSet lineBreaks() {
    return CodePointSet.of(UnicodeWhitespace.lineBreaks().stream()
        .mapToInt(WhitespaceCharacter::codePoint).toArray());
  }

  // --- membership --------------------------------------------------------------------------

  @Test
  void testWhitespacePresetMembership() {
    assertTrue(WS.contains(0x0020));
    assertTrue(WS.contains(0x0009));
    assertTrue(WS.contains(0x00A0));
    assertTrue(WS.contains(0x3000));
    assertTrue(WS.contains(0x2028));
    assertFalse(WS.contains('a'));
    assertFalse(WS.contains(0x200B), "zero width space is not whitespace");
  }

  @Test
  void testDashPresetMembershipExcludesMathMinus() {
    assertTrue(DASH.contains(0x2014));
    assertTrue(DASH.contains(0x2013));
    assertTrue(DASH.contains(0xFF0D));
    assertFalse(DASH.contains(0x2212), "math minus is excluded by default");
    assertFalse(DASH.contains('a'));
  }

  // --- normalize / collapse ----------------------------------------------------------------

  @Test
  void testNormalizeReplacesEachMemberOneForOne() {
    assertEquals("a  b", WS.normalize("a" + NBSP + IDEOGRAPHIC + "b"));
    assertEquals("well-known", DASH.normalize("well" + EM_DASH + "known"));
    assertEquals("a-b-c", DASH.normalize("a" + EN_DASH + "b" + FIGURE_DASH + "c"));
  }

  @Test
  void testNormalizeLeavesMathMinusUntouched() {
    assertEquals("5" + MINUS_SIGN + "3", DASH.normalize("5" + MINUS_SIGN + "3"));
  }

  @Test
  void testCollapseMergesRuns() {
    assertEquals("a b", WS.collapse("a" + NBSP + IDEOGRAPHIC + "b"));
    assertEquals(" a b ", WS.collapse("  a\t\tb  "));
    assertEquals("a-b", DASH.collapse("a" + EM_DASH + EN_DASH + EM_DASH + "b"));
  }

  @Test
  void testNormalizeAndCollapseHandleSupplementaryMembers() {
    assertEquals("x-y", DASH.normalize("x" + YEZIDI_HYPHEN + "y"));
    assertEquals("x-y", DASH.collapse("x" + YEZIDI_HYPHEN + YEZIDI_HYPHEN + "y"));
  }

  @Test
  void testEmptyAndAllMemberInputs() {
    assertEquals("", WS.normalize(""));
    assertEquals("", WS.collapse(""));
    assertEquals("", WS.trim(""));
    assertEquals("", WS.removeAll(""));
    assertArrayEquals(new String[0], WS.split(""));
    assertArrayEquals(new String[0], WS.split(" " + IDEOGRAPHIC));
  }

  // --- squish (collapsePreserving) ---------------------------------------------------------

  @Test
  void testCollapsePreservingKeepsLineBreaks() {
    final CodePointSet keep = lineBreaks();
    assertEquals("a\nb", WS.collapsePreserving("a\n\n\t\tb", keep, '\n'));
    assertEquals("a b", WS.collapsePreserving("a \t b", keep, '\n'));
    assertEquals("a\nb\nc", WS.collapsePreserving("a\n \tb \nc", keep, '\n'));
  }

  // --- trim / removeAll --------------------------------------------------------------------

  @Test
  void testTrim() {
    assertEquals("hello", WS.trim("\t hello" + IDEOGRAPHIC + IDEOGRAPHIC));
    assertEquals("noedge", WS.trim("noedge"));
    assertEquals("", WS.trim("  "));
    assertEquals("a b", WS.trim("  a b  "), "interior whitespace is preserved");
  }

  @Test
  void testRemoveAll() {
    assertEquals("abcd", WS.removeAll("a b\tc d"));
  }

  // --- split / splitSpans ------------------------------------------------------------------

  @Test
  void testSplitOnUnicodeWhitespace() {
    assertArrayEquals(new String[] {"one", "two", "three"},
        WS.split("one two" + IDEOGRAPHIC + IDEOGRAPHIC + "three"));
    assertArrayEquals(new String[] {"a", "b"}, WS.split("  a b  "));
  }

  @Test
  void testSplitSpansCarryOriginalOffsets() {
    final String text = "one two";
    final List<Span> spans = WS.splitSpans(text);
    assertEquals(2, spans.size());
    assertEquals(0, spans.get(0).getStart());
    assertEquals(3, spans.get(0).getEnd());
    assertEquals("one", spans.get(0).getCoveredText(text).toString());
    assertEquals(4, spans.get(1).getStart());
    assertEquals(7, spans.get(1).getEnd());
    assertEquals("two", spans.get(1).getCoveredText(text).toString());
  }

  @Test
  void testSplitSpansWithSupplementaryToken() {
    final String text = "a " + GRINNING_FACE + " b";
    final List<Span> spans = WS.splitSpans(text);
    assertEquals(3, spans.size());
    assertEquals("a", spans.get(0).getCoveredText(text).toString());
    assertEquals(GRINNING_FACE, spans.get(1).getCoveredText(text).toString());
    assertEquals("b", spans.get(2).getCoveredText(text).toString());
  }

  // --- custom classes ----------------------------------------------------------------------

  @Test
  void testCustomClass() {
    final CharClass vowelO = CharClass.of(CodePointSet.of('o'), '0');
    assertEquals("f00 bar", vowelO.normalize("foo bar"));
    assertEquals("f0", vowelO.collapse("foo"));
  }

  @Test
  void testWithAdditionalExtendsWithoutMutatingOriginal() {
    final CharClass extended = WS.withAdditional(CodePointSet.of('_'));
    assertTrue(extended.contains('_'));
    assertTrue(extended.contains(0x0020));
    assertEquals("a b c", extended.normalize("a_b c"));
    assertFalse(WS.contains('_'), "the preset must be unchanged");
  }

  @Test
  void testOfRejectsInvalidReplacement() {
    assertThrows(IllegalArgumentException.class,
        () -> CharClass.of(CodePointSet.of(0x20), -1));
    assertThrows(IllegalArgumentException.class,
        () -> CharClass.of(CodePointSet.of(0x20), Character.MAX_CODE_POINT + 1));
  }

  // --- offset-mapped variants --------------------------------------------------------------

  @Test
  void testCollapseMappedOffsets() {
    final NormalizedText nt = WS.collapseMapped("a  b");
    assertEquals("a b", nt.normalized());
    assertEquals(3, nt.offsets().normalizedLength());
    assertEquals(4, nt.offsets().originalLength());

    assertEquals(0, nt.toOriginalOffset(0));
    assertEquals(1, nt.toOriginalOffset(1));
    assertEquals(3, nt.toOriginalOffset(2));
    assertEquals(4, nt.toOriginalOffset(3));

    assertEquals(0, nt.toNormalizedOffset(0));
    assertEquals(1, nt.toNormalizedOffset(1));
    assertEquals(2, nt.toNormalizedOffset(3));
    assertEquals(3, nt.toNormalizedOffset(4));
  }

  @Test
  void testNormalizeMappedIsIdentityWhenNothingMatches() {
    final NormalizedText nt = WS.normalizeMapped("abc");
    assertEquals("abc", nt.normalized());
    for (int i = 0; i <= 3; i++) {
      assertEquals(i, nt.toOriginalOffset(i));
    }
  }

  @Test
  void testNormalizeMappedPreservesSupplementaryCopyOffsets() {
    final String text = "a" + GRINNING_FACE + "b";
    final NormalizedText nt = WS.normalizeMapped(text);
    assertEquals(text, nt.normalized());
    for (int i = 0; i <= text.length(); i++) {
      assertEquals(i, nt.toOriginalOffset(i));
    }
  }

  @Test
  void testNormalizeMappedCollapsesSupplementaryMemberToOneChar() {
    final String text = "x" + YEZIDI_HYPHEN + "y";
    final NormalizedText nt = DASH.normalizeMapped(text);
    assertEquals("x-y", nt.normalized());
    assertEquals(0, nt.toOriginalOffset(0));
    assertEquals(1, nt.toOriginalOffset(1));
    assertEquals(3, nt.toOriginalOffset(2));
    assertEquals(4, nt.toOriginalOffset(3));
  }

  @Test
  void testOffsetMapRejectsOutOfRange() {
    final OffsetMap map = WS.collapseMapped("ab").offsets();
    assertThrows(IndexOutOfBoundsException.class, () -> map.toOriginalOffset(-1));
    assertThrows(IndexOutOfBoundsException.class,
        () -> map.toOriginalOffset(map.normalizedLength() + 1));
    assertThrows(IndexOutOfBoundsException.class, () -> map.toNormalizedOffset(-1));
    assertThrows(IndexOutOfBoundsException.class,
        () -> map.toNormalizedOffset(map.originalLength() + 1));
  }

  @Test
  void testAccessorsExposeMembersAndReplacement() {
    assertEquals(0x0020, WS.replacement());
    assertEquals('-', DASH.replacement());
    assertTrue(WS.members().contains(0x00A0));
    assertFalse(WS.members().contains('a'));
  }

  @Test
  void testOffsetMapBuilderGrowsBeyondInitialCapacity() {
    // 26 output characters force the OffsetMap builder past its initial 16-entry buffer.
    final String text = "abcdefghijklmnopqrstuvwxyz";
    final NormalizedText nt = WS.normalizeMapped(text);
    assertEquals(text, nt.normalized());
    assertEquals(26, nt.offsets().normalizedLength());
    for (int i = 0; i <= text.length(); i++) {
      assertEquals(i, nt.toOriginalOffset(i));
    }
  }

  @Test
  void testNormalizeMappedWithSupplementaryReplacement() {
    // A supplementary replacement exercises the two-char substitution path of the offset map.
    final int penguin = 0x1F427;
    final CharClass toPenguin = CharClass.of(CodePointSet.of(' '), penguin);
    final NormalizedText nt = toPenguin.normalizeMapped("a b");
    assertEquals("a" + new String(Character.toChars(penguin)) + "b", nt.normalized());
    assertEquals(0, nt.toOriginalOffset(0));
    assertEquals(1, nt.toOriginalOffset(1));
    assertEquals(1, nt.toOriginalOffset(2));
    assertEquals(2, nt.toOriginalOffset(3));
  }
}
