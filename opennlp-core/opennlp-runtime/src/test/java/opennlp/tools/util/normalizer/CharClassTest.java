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
    // Offset 2 is the second space, swallowed into the collapsed run; it maps to the single
    // normalized space (1), not forward to 'b'.
    assertEquals(1, nt.toNormalizedOffset(2));
    assertEquals(2, nt.toNormalizedOffset(3));
    assertEquals(3, nt.toNormalizedOffset(4));
  }

  @Test
  void testCollapseMappedOffsetsInsideRunOfMixedUnicodeWhitespace() {
    // A run of three different Unicode spaces (no-break, ideographic, en) collapses to one ASCII
    // space. Every original offset inside that run must map to the single collapsed space, not
    // jump forward to 'b'.
    final String text = "a\u00A0\u3000\u2002b";
    final NormalizedText nt = WS.collapseMapped(text);
    assertEquals("a b", nt.normalized());

    assertEquals(0, nt.toNormalizedOffset(0));   // 'a'
    assertEquals(1, nt.toNormalizedOffset(1));   // no-break space, run start
    assertEquals(1, nt.toNormalizedOffset(2));   // ideographic space, inside the run
    assertEquals(1, nt.toNormalizedOffset(3));   // en space, inside the run
    assertEquals(2, nt.toNormalizedOffset(4));   // 'b'
    assertEquals(3, nt.toNormalizedOffset(5));   // end

    // And the reverse direction reports the run at its original start.
    assertEquals(0, nt.toOriginalOffset(0));
    assertEquals(1, nt.toOriginalOffset(1));
    assertEquals(4, nt.toOriginalOffset(2));
  }

  @Test
  void testCollapseMappedOffsetsAcrossTabRun() {
    // Five tabs are one whitespace run and collapse to a single space.
    final NormalizedText nt = WS.collapseMapped("a\t\t\t\t\tb");
    assertEquals("a b", nt.normalized());
    assertEquals(0, nt.toNormalizedOffset(0)); // 'a'
    assertEquals(1, nt.toNormalizedOffset(1)); // first tab, run start
    assertEquals(1, nt.toNormalizedOffset(3)); // a tab in the middle of the run
    assertEquals(1, nt.toNormalizedOffset(5)); // last tab
    assertEquals(2, nt.toNormalizedOffset(6)); // 'b'
    assertEquals(3, nt.toNormalizedOffset(7)); // end
  }

  @Test
  void testCollapseMappedOffsetsAcrossNewlineRun() {
    // CR + LF + tab is one whitespace run; newlines are whitespace and collapse like any other.
    final NormalizedText nt = WS.collapseMapped("a\r\n\tb");
    assertEquals("a b", nt.normalized());
    assertEquals(0, nt.toNormalizedOffset(0)); // 'a'
    assertEquals(1, nt.toNormalizedOffset(1)); // CR, run start
    assertEquals(1, nt.toNormalizedOffset(2)); // LF, inside the run
    assertEquals(1, nt.toNormalizedOffset(3)); // tab, inside the run
    assertEquals(2, nt.toNormalizedOffset(4)); // 'b'
  }

  @Test
  void testCollapseMappedSingleCharAndEmptyInputs() {
    // Empty input: only the end sentinel exists.
    final NormalizedText empty = WS.collapseMapped("");
    assertEquals("", empty.normalized());
    assertEquals(0, empty.toNormalizedOffset(0));
    assertEquals(0, empty.toOriginalOffset(0));

    // A lone non-whitespace char passes through unchanged.
    assertEquals("a", WS.collapseMapped("a").normalized());

    // A lone whitespace char becomes one space and is NOT trimmed (trimming is a separate op).
    final NormalizedText oneTab = WS.collapseMapped("\t");
    assertEquals(" ", oneTab.normalized());
    assertEquals(0, oneTab.toNormalizedOffset(0));
    assertEquals(1, oneTab.toNormalizedOffset(1));

    // The whole string is a single whitespace run: it collapses to one space, every interior
    // offset maps back to that space, and the reverse reports the run's start.
    final NormalizedText allWs = WS.collapseMapped("\t\t\t");
    assertEquals(" ", allWs.normalized());
    assertEquals(0, allWs.toNormalizedOffset(0)); // run start
    assertEquals(0, allWs.toNormalizedOffset(1)); // inside the run
    assertEquals(0, allWs.toNormalizedOffset(2)); // inside the run
    assertEquals(1, allWs.toNormalizedOffset(3)); // end
    assertEquals(0, allWs.toOriginalOffset(0));   // the space reports the run start
    assertEquals(3, allWs.toOriginalOffset(1));   // end sentinel -> original length
  }

  @Test
  void testCollapseMappedKeepsSurrogatePairOffsets() {
    // A supplementary code point (grinning-face emoji) is two UTF-16 units; each surrogate keeps
    // its own original offset, and a whitespace run right after it still collapses correctly.
    final NormalizedText nt = WS.collapseMapped(GRINNING_FACE + "\t\tb");
    assertEquals(GRINNING_FACE + " b", nt.normalized());
    assertEquals(0, nt.toNormalizedOffset(0)); // high surrogate
    assertEquals(1, nt.toNormalizedOffset(1)); // low surrogate
    assertEquals(2, nt.toNormalizedOffset(2)); // first tab -> the collapsed space
    assertEquals(2, nt.toNormalizedOffset(3)); // second tab, inside the run
    assertEquals(3, nt.toNormalizedOffset(4)); // 'b'
    assertEquals(4, nt.toNormalizedOffset(5)); // end
    assertEquals(0, nt.toOriginalOffset(0));
    assertEquals(1, nt.toOriginalOffset(1));
    assertEquals(2, nt.toOriginalOffset(2));
    assertEquals(4, nt.toOriginalOffset(3));
  }

  @Test
  void testNormalizeMappedShrinksSupplementaryDashSafely() {
    // A supplementary dash (Yezidi hyphen, two UTF-16 units) normalizes to a single ASCII hyphen.
    // The output is shorter than the input, but the offset map stays consistent: the dash's
    // low-surrogate offset still resolves to the hyphen, and following text stays aligned. This is
    // the offset-aware counterpart to the plain (non-mapped) normalize used in the DL components.
    final NormalizedText nt = DASH.normalizeMapped("x" + YEZIDI_HYPHEN + "y");
    assertEquals("x-y", nt.normalized());
    assertEquals(0, nt.toNormalizedOffset(0)); // 'x'
    assertEquals(1, nt.toNormalizedOffset(1)); // dash high surrogate -> '-'
    assertEquals(1, nt.toNormalizedOffset(2)); // dash low surrogate, same '-'
    assertEquals(2, nt.toNormalizedOffset(3)); // 'y'
    assertEquals(3, nt.toNormalizedOffset(4)); // end
    assertEquals(0, nt.toOriginalOffset(0));
    assertEquals(1, nt.toOriginalOffset(1)); // '-' reports the dash start
    assertEquals(3, nt.toOriginalOffset(2)); // 'y' at original offset 3
    assertEquals(4, nt.toOriginalOffset(3));
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
