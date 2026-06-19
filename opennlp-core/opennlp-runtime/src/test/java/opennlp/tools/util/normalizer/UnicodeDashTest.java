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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.normalizer.UnicodeDash.Category;
import opennlp.tools.util.normalizer.UnicodeDash.DashCharacter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnicodeDashTest {

  private static List<DashCharacter> dashes() {
    return UnicodeDash.all();
  }

  // Maps the running JDK's general category to our enum, or null if it cannot be expressed (which
  // includes code points the JDK's Unicode version does not yet assign).
  private static Category jdkCategory(int codePoint) {
    return switch (Character.getType(codePoint)) {
      case Character.DASH_PUNCTUATION -> Category.Pd;
      case Character.MATH_SYMBOL -> Category.Sm;
      case Character.OTHER_PUNCTUATION -> Category.Po;
      default -> null;
    };
  }

  @Test
  void testDashSetHasExactly31() {
    assertEquals(31, UnicodeDash.all().size());
  }

  @ParameterizedTest
  @MethodSource("dashes")
  void testEachDashIsSelfConsistent(DashCharacter dash) {
    assertTrue(UnicodeDash.isDash(dash.codePoint()), dash::toUnicodeNotation);
    assertEquals(dash, UnicodeDash.byCodePoint(dash.codePoint()).orElseThrow());
    assertNotNull(dash.category());
    assertFalse(dash.name().isBlank());
  }

  @ParameterizedTest
  @MethodSource("dashes")
  void testCategoryMatchesJdkUnicodeDataWhenAssigned(DashCharacter dash) {
    final Category jdk = jdkCategory(dash.codePoint());
    // Skip code points the running JVM's Unicode version does not assign yet (e.g. newer dashes).
    if (Character.getType(dash.codePoint()) != Character.UNASSIGNED) {
      assertEquals(jdk, dash.category(), dash::toUnicodeNotation);
    }
  }

  @Test
  void testCodePointsAreUniqueAndStrictlyAscending() {
    final int[] cps = UnicodeDash.codePoints();
    for (int i = 1; i < cps.length; i++) {
      assertTrue(cps[i] > cps[i - 1], "dash code points must be unique and ascending");
    }
  }

  @Test
  void testMathematicalAreExactlyTheThreeMinusSigns() {
    final Set<Integer> math = UnicodeDash.mathematical().stream()
        .map(DashCharacter::codePoint).collect(Collectors.toSet());
    assertEquals(Set.of(0x207B, 0x208B, 0x2212), math);
    UnicodeDash.mathematical().forEach(d -> {
      assertTrue(d.isMathematical());
      assertEquals(Category.Sm, d.category());
    });
  }

  @Test
  void testDefaultDashSetExcludesMathematicalMinusSigns() {
    final int[] defaults = UnicodeDash.defaultDashCodePoints();
    assertEquals(UnicodeDash.all().size() - 3, defaults.length);
    for (final int codePoint : defaults) {
      assertFalse(UnicodeDash.byCodePoint(codePoint).orElseThrow().isMathematical(),
          () -> String.format("U+%04X must not be a math minus", codePoint));
    }
    assertFalse(Arrays.stream(defaults).anyMatch(cp -> cp == 0x2212));
  }

  @Test
  void testHyphenMinusIsTheCanonicalTarget() {
    assertEquals(0x002D, UnicodeDash.HYPHEN_MINUS);
    assertTrue(UnicodeDash.isDash(0x002D));
    assertEquals(Category.Pd, UnicodeDash.byCodePoint(0x002D).orElseThrow().category());
  }

  @Test
  void testSupplementaryDashesArePresent() {
    for (final int codePoint : new int[] {0x10D6E, 0x10EAD}) {
      assertTrue(UnicodeDash.isDash(codePoint));
      assertTrue(UnicodeDash.byCodePoint(codePoint).orElseThrow().isSupplementary());
    }
  }

  @Test
  void testBmpDashIsNotSupplementary() {
    assertFalse(UnicodeDash.byCodePoint(0x2014).orElseThrow().isSupplementary());
  }

  @Test
  void testDashToUnicodeNotation() {
    assertEquals("U+2014", UnicodeDash.byCodePoint(0x2014).orElseThrow().toUnicodeNotation());
    assertEquals("U+10EAD", UnicodeDash.byCodePoint(0x10EAD).orElseThrow().toUnicodeNotation());
  }

  @ParameterizedTest
  @ValueSource(ints = {0x00AD, 0x002E, 0x0041, 0x0020, 0x007E, 0x1F600})
  void testNonDashesAreNotDashes(int codePoint) {
    // Notably U+00AD SOFT HYPHEN is a format character, not a dash, and must not be treated as one.
    assertFalse(UnicodeDash.isDash(codePoint));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE, Character.MAX_CODE_POINT + 1, Integer.MAX_VALUE})
  void testIsDashIsRangeSafe(int codePoint) {
    assertFalse(UnicodeDash.isDash(codePoint));
  }

  @Test
  void testByCodePointUnknownIsEmpty() {
    assertTrue(UnicodeDash.byCodePoint('A').isEmpty());
    assertTrue(UnicodeDash.byCodePoint(0x00AD).isEmpty());
  }

  @Test
  void testReferenceListIsImmutable() {
    assertThrows(UnsupportedOperationException.class, () -> UnicodeDash.all().add(null));
    assertThrows(UnsupportedOperationException.class, () -> UnicodeDash.mathematical().add(null));
  }

  @Test
  void testArrayAccessorsReturnDefensiveCopies() {
    final int[] all = UnicodeDash.codePoints();
    all[0] = -1;
    assertEquals(0x002D, UnicodeDash.codePoints()[0]);

    final int[] defaults = UnicodeDash.defaultDashCodePoints();
    defaults[0] = -1;
    assertEquals(0x002D, UnicodeDash.defaultDashCodePoints()[0]);
  }
}
