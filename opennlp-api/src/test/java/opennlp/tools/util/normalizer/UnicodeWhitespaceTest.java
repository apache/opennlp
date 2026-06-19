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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.normalizer.UnicodeWhitespace.Category;
import opennlp.tools.util.normalizer.UnicodeWhitespace.RelatedCharacter;
import opennlp.tools.util.normalizer.UnicodeWhitespace.WhitespaceCharacter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnicodeWhitespaceTest {

  private static List<WhitespaceCharacter> whitespace() {
    return UnicodeWhitespace.all();
  }

  private static List<RelatedCharacter> lookalikes() {
    return UnicodeWhitespace.lookalikes();
  }

  // Maps the JDK's Unicode general category to our enum, used as an independent oracle.
  private static Category jdkCategory(int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL -> Category.Cc;
      case Character.SPACE_SEPARATOR -> Category.Zs;
      case Character.LINE_SEPARATOR -> Category.Zl;
      case Character.PARAGRAPH_SEPARATOR -> Category.Zp;
      case Character.FORMAT -> Category.Cf;
      default -> null;
    };
  }

  @Test
  void testWhitespaceSetHasExactly25() {
    assertEquals(25, UnicodeWhitespace.all().size());
  }

  @Test
  void testLookalikeSetHasExactly6() {
    assertEquals(6, UnicodeWhitespace.lookalikes().size());
  }

  @Test
  void testRelatedCharacterExposesAttributes() {
    final var bom = UnicodeWhitespace.lookalikes().stream()
        .filter(r -> r.codePoint() == 0xFEFF).findFirst().orElseThrow();
    assertEquals("zero width no-break space", bom.name());
    assertEquals("BOM", bom.abbreviation());
    assertFalse(bom.note().isBlank());
    assertEquals("U+FEFF", bom.toUnicodeNotation());
  }

  @ParameterizedTest
  @MethodSource("whitespace")
  void testEachWhitespaceCharIsSelfConsistent(WhitespaceCharacter ws) {
    assertTrue(UnicodeWhitespace.isWhitespace(ws.codePoint()),
        () -> ws.toUnicodeNotation() + " should be whitespace");
    assertEquals(ws, UnicodeWhitespace.byCodePoint(ws.codePoint()).orElseThrow());
    assertFalse(UnicodeWhitespace.isLookalike(ws.codePoint()),
        () -> ws.toUnicodeNotation() + " must not also be a look-alike");
    assertNotNull(ws.category());
    assertNotNull(ws.breaking());
    assertNotNull(ws.abbreviation());
    assertFalse(ws.name().isBlank());
  }

  @ParameterizedTest
  @MethodSource("whitespace")
  void testAllWhitespaceIsInTheBmp(WhitespaceCharacter ws) {
    // Every Unicode White_Space code point is in the Basic Multilingual Plane (one char).
    assertTrue(ws.codePoint() <= 0xFFFF, ws::toUnicodeNotation);
    assertEquals(1, Character.charCount(ws.codePoint()));
  }

  @ParameterizedTest
  @MethodSource("whitespace")
  void testCategoryMatchesJdkUnicodeData(WhitespaceCharacter ws) {
    // Independent cross-check: our hand-entered category must agree with the JDK's UCD.
    assertEquals(jdkCategory(ws.codePoint()), ws.category(), ws::toUnicodeNotation);
  }

  @Test
  void testCodePointsAreUniqueAndStrictlyAscending() {
    final int[] cps = UnicodeWhitespace.codePoints();
    for (int i = 1; i < cps.length; i++) {
      assertTrue(cps[i] > cps[i - 1],
          "code points must be unique and ascending at index " + i);
    }
  }

  @Test
  void testCodePointsMatchAllOrder() {
    final int[] fromRecords = whitespace().stream().mapToInt(WhitespaceCharacter::codePoint).toArray();
    assertArrayEqualsInt(fromRecords, UnicodeWhitespace.codePoints());
  }

  @Test
  void testCodePointsReturnsDefensiveCopy() {
    final int[] first = UnicodeWhitespace.codePoints();
    first[0] = -999;
    assertEquals(0x0009, UnicodeWhitespace.codePoints()[0]);
  }

  @ParameterizedTest
  @MethodSource("lookalikes")
  void testLookalikesAreNotWhitespace(RelatedCharacter related) {
    assertFalse(UnicodeWhitespace.isWhitespace(related.codePoint()),
        () -> related.toUnicodeNotation() + " is White_Space=no");
    assertTrue(UnicodeWhitespace.byCodePoint(related.codePoint()).isEmpty());
    assertTrue(UnicodeWhitespace.isLookalike(related.codePoint()));
    // Every look-alike is a format character in the UCD.
    assertEquals(Category.Cf, jdkCategory(related.codePoint()), related::toUnicodeNotation);
  }

  @Test
  void testLineBreaksAreExactlyTheSeven() {
    final Set<Integer> expected = Set.of(0x000A, 0x000B, 0x000C, 0x000D, 0x0085, 0x2028, 0x2029);
    assertEquals(expected, UnicodeWhitespace.lineBreaks().stream()
        .map(WhitespaceCharacter::codePoint).collect(Collectors.toSet()));
  }

  @Test
  void testNonBreakingAreExactlyTheThree() {
    final Set<Integer> expected = Set.of(0x00A0, 0x2007, 0x202F);
    assertEquals(expected, UnicodeWhitespace.nonBreaking().stream()
        .map(WhitespaceCharacter::codePoint).collect(Collectors.toSet()));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x0008, 0x000E, 0x001F, 0x0021, 0x1FFF, 0x200B, 0x202A, 0x2FFF, 0x3001})
  void testNeighboringCodePointsAreNotWhitespace(int codePoint) {
    assertFalse(UnicodeWhitespace.isWhitespace(codePoint),
        () -> String.format("U+%04X must not be whitespace", codePoint));
  }

  @Test
  void testIncludesNbspAndNelThatJavaIsWhitespaceOmits() {
    // Documents the deliberate divergence from Character.isWhitespace.
    assertTrue(UnicodeWhitespace.isWhitespace(0x00A0));
    assertFalse(Character.isWhitespace(0x00A0));
    assertTrue(UnicodeWhitespace.isWhitespace(0x0085));
    assertFalse(Character.isWhitespace(0x0085));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x001C, 0x001D, 0x001E, 0x001F})
  void testExcludesInfoSeparatorsThatJavaIsWhitespaceIncludes(int codePoint) {
    assertFalse(UnicodeWhitespace.isWhitespace(codePoint));
    assertTrue(Character.isWhitespace(codePoint));
  }

  @Test
  void testIncludesTabThatIsSpaceCharOmits() {
    // Character.isSpaceChar excludes the control whitespace; ours includes it.
    assertTrue(UnicodeWhitespace.isWhitespace(0x0009));
    assertFalse(Character.isSpaceChar(0x0009));
  }

  @Test
  void testByCodePointUnknownIsEmpty() {
    assertTrue(UnicodeWhitespace.byCodePoint('A').isEmpty());
    assertTrue(UnicodeWhitespace.byCodePoint(0x200B).isEmpty(), "a look-alike is not whitespace");
  }

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, Character.MAX_CODE_POINT + 1, Integer.MAX_VALUE})
  void testIsWhitespaceHandlesOutOfRangeSafely(int codePoint) {
    assertFalse(UnicodeWhitespace.isWhitespace(codePoint));
    assertFalse(UnicodeWhitespace.isLookalike(codePoint));
  }

  @Test
  void testReferenceListsAreImmutable() {
    assertThrows(UnsupportedOperationException.class,
        () -> UnicodeWhitespace.all().add(null));
    assertThrows(UnsupportedOperationException.class,
        () -> UnicodeWhitespace.lookalikes().add(null));
    assertThrows(UnsupportedOperationException.class,
        () -> UnicodeWhitespace.lineBreaks().add(null));
    assertThrows(UnsupportedOperationException.class,
        () -> UnicodeWhitespace.nonBreaking().add(null));
  }

  @Test
  void testToUnicodeNotationIsZeroPadded() {
    assertEquals("U+0009", UnicodeWhitespace.byCodePoint(0x0009).orElseThrow().toUnicodeNotation());
    assertEquals("U+00A0", UnicodeWhitespace.byCodePoint(0x00A0).orElseThrow().toUnicodeNotation());
    assertEquals("U+3000", UnicodeWhitespace.byCodePoint(0x3000).orElseThrow().toUnicodeNotation());
  }

  @Test
  void testLineBreakAndNonBreakingFlagsAgreeWithBreaking() {
    final WhitespaceCharacter lf = UnicodeWhitespace.byCodePoint(0x000A).orElseThrow();
    assertTrue(lf.isLineBreak());
    assertFalse(lf.isNonBreaking());

    final WhitespaceCharacter nbsp = UnicodeWhitespace.byCodePoint(0x00A0).orElseThrow();
    assertTrue(nbsp.isNonBreaking());
    assertFalse(nbsp.isLineBreak());

    final WhitespaceCharacter space = UnicodeWhitespace.byCodePoint(0x0020).orElseThrow();
    assertFalse(space.isLineBreak());
    assertFalse(space.isNonBreaking());
  }

  private static void assertArrayEqualsInt(int[] expected, int[] actual) {
    assertEquals(Arrays.toString(expected), Arrays.toString(actual));
    assertTrue(IntStream.range(0, expected.length).allMatch(i -> expected[i] == actual[i]));
  }
}
