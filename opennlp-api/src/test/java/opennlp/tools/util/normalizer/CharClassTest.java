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

  @Test
  void testSquishNewlineAndTabEdgeCases() {
    final CodePointSet keep = lineBreaks();
    // A run of only newlines collapses to a single newline.
    assertEquals("a\nb", WS.collapsePreserving("a\n\n\nb", keep, '\n'));
    // Five tabs with no line break in the run collapse to a single space.
    assertEquals("a b", WS.collapsePreserving("a\t\t\t\t\tb", keep, '\n'));
    // A mixed run that contains a newline anywhere collapses to a newline, not a space.
    assertEquals("a\nb", WS.collapsePreserving("a \t\n\t b", keep, '\n'));
    // Squish does not trim: a leading newline run is preserved as one newline.
    assertEquals("\nabc", WS.collapsePreserving("\n\nabc", keep, '\n'));
    // Single-char inputs: a lone newline stays a newline, a lone tab becomes a space.
    assertEquals("\n", WS.collapsePreserving("\n", keep, '\n'));
    assertEquals(" ", WS.collapsePreserving("\t", keep, '\n'));
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

  // --- aligned variants (Alignment / AlignedText) ------------------------------------------

  private static void assertSpan(int start, int end, Span span) {
    assertEquals(start, span.getStart(), "start");
    assertEquals(end, span.getEnd(), "end");
  }

  @Test
  void testCollapseAlignedMapsRunToWholeExtent() {
    final AlignedText at = WS.collapseAligned("a  b");
    assertEquals("a b", at.normalized());
    assertSpan(0, 1, at.toOriginalSpan(0, 1)); // a
    assertSpan(1, 3, at.toOriginalSpan(1, 2)); // the collapsed space covers both originals
    assertSpan(3, 4, at.toOriginalSpan(2, 3)); // b
  }

  @Test
  void testRemoveAllAlignedDoesNotOverCover() {
    final AlignedText at = WS.removeAllAligned("a b c");
    assertEquals("abc", at.normalized());
    assertSpan(2, 3, at.toOriginalSpan(1, 2)); // "b" -> [2,3), not [2,4)
    assertSpan(0, 5, at.toOriginalSpan(0, 3));
  }

  @Test
  void testTrimAlignedDropsEdgesWithoutOverCovering() {
    final AlignedText at = WS.trimAligned("  ab  ");
    assertEquals("ab", at.normalized());
    assertEquals(6, at.alignment().originalLength());
    assertSpan(2, 4, at.toOriginalSpan(0, 2)); // "ab" sits at original [2,4)
    assertSpan(3, 4, at.toOriginalSpan(1, 2)); // "b"
  }

  @Test
  void testCollapsePreservingAlignedKeepsLineBreak() {
    final AlignedText at = WS.collapsePreservingAligned("a\n\n\t\tb", lineBreaks(), '\n');
    assertEquals("a\nb", at.normalized());
    assertSpan(1, 5, at.toOriginalSpan(1, 2)); // the preserved newline covers the whole run
  }

  @Test
  void testNormalizeAlignedAcrossSupplementaryDash() {
    final AlignedText at = DASH.normalizeAligned("x" + YEZIDI_HYPHEN + "y");
    assertEquals("x-y", at.normalized());
    assertSpan(0, 1, at.toOriginalSpan(0, 1)); // x
    assertSpan(1, 3, at.toOriginalSpan(1, 2)); // "-" maps back to the two-char Yezidi hyphen
    assertSpan(3, 4, at.toOriginalSpan(2, 3)); // y
  }

  // --- aligned edge cases (restore + extend the deleted *Mapped coverage) ------------------

  @Test
  void testCollapseAlignedAcrossMixedUnicodeWhitespaceRun() {
    final AlignedText at = WS.collapseAligned("a" + NBSP + IDEOGRAPHIC + cp(0x2002) + "b");
    assertEquals("a b", at.normalized());
    assertSpan(1, 4, at.toOriginalSpan(1, 2)); // the one space covers the three-char ws run
    assertSpan(4, 5, at.toOriginalSpan(2, 3)); // b
  }

  @Test
  void testCollapseAlignedAcrossTabRun() {
    final AlignedText at = WS.collapseAligned("a\t\t\t\t\tb");
    assertEquals("a b", at.normalized());
    assertSpan(1, 6, at.toOriginalSpan(1, 2)); // five tabs collapse to one space
    assertSpan(6, 7, at.toOriginalSpan(2, 3));
  }

  @Test
  void testCollapseAlignedAcrossNewlineRun() {
    final AlignedText at = WS.collapseAligned("a\r\n\tb");
    assertEquals("a b", at.normalized());
    assertSpan(1, 4, at.toOriginalSpan(1, 2));
  }

  @Test
  void testCollapseAlignedEmptySingleAndAllWhitespace() {
    assertEquals("", WS.collapseAligned("").normalized());
    assertEquals(0, WS.collapseAligned("").alignment().normalizedLength());

    final AlignedText single = WS.collapseAligned("a");
    assertEquals("a", single.normalized());
    assertSpan(0, 1, single.toOriginalSpan(0, 1));

    final AlignedText allWs = WS.collapseAligned("\t\t\t");
    assertEquals(" ", allWs.normalized()); // all whitespace collapses to one space, not empty
    assertSpan(0, 3, allWs.toOriginalSpan(0, 1));
  }

  @Test
  void testCollapseAlignedKeepsSurrogatePairOffsets() {
    final AlignedText at = WS.collapseAligned(GRINNING_FACE + "\t\tb");
    assertEquals(GRINNING_FACE + " b", at.normalized());
    assertSpan(0, 2, at.toOriginalSpan(0, 2)); // the emoji occupies two original chars
    assertSpan(2, 4, at.toOriginalSpan(2, 3)); // the collapsed tabs
    assertSpan(4, 5, at.toOriginalSpan(3, 4)); // b
  }

  @Test
  void testNormalizeAlignedIsIdentityWhenNothingMatches() {
    final AlignedText at = WS.normalizeAligned("abc");
    assertEquals("abc", at.normalized());
    for (int i = 0; i < 3; i++) {
      assertSpan(i, i + 1, at.toOriginalSpan(i, i + 1));
    }
  }

  @Test
  void testNormalizeAlignedPreservesSupplementaryNonMember() {
    final AlignedText at = WS.normalizeAligned("a" + GRINNING_FACE + "b");
    assertEquals("a" + GRINNING_FACE + "b", at.normalized());
    assertSpan(1, 3, at.toOriginalSpan(1, 3)); // the emoji passes through unchanged
  }

  @Test
  void testNormalizeAlignedExpandsToSupplementaryReplacement() {
    // A BMP member replaced by a supplementary code point grows by one char (1 -> 2).
    final CharClass toPenguin = CharClass.of(CodePointSet.of(' '), 0x1F427);
    final AlignedText at = toPenguin.normalizeAligned("a b");
    assertEquals("a" + cp(0x1F427) + "b", at.normalized());
    assertSpan(0, 1, at.toOriginalSpan(0, 1)); // a
    assertSpan(1, 2, at.toOriginalSpan(1, 3)); // both penguin halves come from the one space
    assertSpan(2, 3, at.toOriginalSpan(3, 4)); // b
  }

  @Test
  void testRemoveAllAlignedLeadingAndTrailingDeletions() {
    final AlignedText at = WS.removeAllAligned(" a b ");
    assertEquals("ab", at.normalized());
    assertSpan(1, 2, at.toOriginalSpan(0, 1)); // a (leading space deleted)
    assertSpan(3, 4, at.toOriginalSpan(1, 2)); // b (trailing space deleted, not over-covered)
  }

  @Test
  void testTrimAlignedAllWhitespaceIsEmpty() {
    final AlignedText at = WS.trimAligned("\t\t");
    assertEquals("", at.normalized());
    assertEquals(0, at.alignment().normalizedLength());
    assertEquals(2, at.alignment().originalLength());
  }

  @Test
  void testCollapsePreservingAlignedRunWithoutKeepCollapsesToReplacement() {
    final AlignedText at = WS.collapsePreservingAligned("a \t b", lineBreaks(), '\n');
    assertEquals("a b", at.normalized()); // no line break in the run -> plain space
    assertSpan(1, 4, at.toOriginalSpan(1, 2));
  }

  // Every aligned operation must produce exactly the same string as its plain counterpart; only the
  // alignment is extra. This pins that contract across a battery of inputs so the two code paths
  // cannot drift apart.
  @Test
  void testAlignedOperationsAgreeWithPlainOutput() {
    final CodePointSet keep = lineBreaks();
    final String[] inputs = {
        "",
        "abc",
        "  a b  ",
        "a" + NBSP + IDEOGRAPHIC + "b",
        "a\t\t\t\t\tb",
        "a\r\n\tb",
        "\n\nabc",
        "  ",
        GRINNING_FACE + "\t\tb",
        "x" + YEZIDI_HYPHEN + YEZIDI_HYPHEN + "y",
        "well" + EM_DASH + EN_DASH + "known",
        "5" + MINUS_SIGN + "3",
    };
    for (final CharClass charClass : new CharClass[] {WS, DASH}) {
      for (final String input : inputs) {
        assertEquals(charClass.normalize(input), charClass.normalizeAligned(input).normalized(),
            "normalize vs normalizeAligned for [" + input + "]");
        assertEquals(charClass.collapse(input), charClass.collapseAligned(input).normalized(),
            "collapse vs collapseAligned for [" + input + "]");
        assertEquals(charClass.trim(input), charClass.trimAligned(input).normalized(),
            "trim vs trimAligned for [" + input + "]");
        assertEquals(charClass.removeAll(input), charClass.removeAllAligned(input).normalized(),
            "removeAll vs removeAllAligned for [" + input + "]");
        assertEquals(charClass.collapsePreserving(input, keep, '\n'),
            charClass.collapsePreservingAligned(input, keep, '\n').normalized(),
            "collapsePreserving vs collapsePreservingAligned for [" + input + "]");
      }
    }
  }
}
