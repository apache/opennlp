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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link StringUtil} class.
 */

public class StringUtilTest {

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
   * Pins the exact semantics of {@link StringUtil#isWhitespace(int)} at the code points where
   * the JVM predicates and the Unicode {@code White_Space} property disagree, so the predicate
   * cannot drift silently. The predicate is the union of {@link Character#isWhitespace(int)}
   * and the {@code Zs} category: unlike the Unicode {@code White_Space} set it includes the
   * {@code U+001C..U+001F} information separators and excludes the next line control
   * {@code U+0085}. Features of trained sentence-detector and tokenizer models are built on
   * this predicate, so it stays frozen; user-text code uses the Unicode set instead
   * (see {@code opennlp.tools.util.normalizer.UnicodeWhitespace}).
   */
  @Test
  void testIsWhitespaceBoundaryCodePoints() {
    // ASCII whitespace controls and the space.
    for (int cp = 0x0009; cp <= 0x000D; cp++) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
    }
    Assertions.assertTrue(StringUtil.isWhitespace(0x0020));

    // The information separators are included (via Character.isWhitespace); the Unicode
    // White_Space set excludes them.
    for (int cp = 0x001C; cp <= 0x001F; cp++) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
      Assertions.assertTrue(StringUtil.isWhitespace((char) cp), "U+" + Integer.toHexString(cp));
    }

    // The next line control is excluded (Cc, and not covered by Character.isWhitespace);
    // the Unicode White_Space set includes it.
    Assertions.assertFalse(StringUtil.isWhitespace(0x0085));
    Assertions.assertFalse(StringUtil.isWhitespace((char) 0x0085));

    // The Zs space separators and the line/paragraph separators are included.
    int[] separators = {0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
        0x2008, 0x2009, 0x200A, 0x2028, 0x2029, 0x205F, 0x3000};
    for (int cp : separators) {
      Assertions.assertTrue(StringUtil.isWhitespace(cp), "U+" + Integer.toHexString(cp));
    }

    // Zero-width format characters are not whitespace, neither here nor in the Unicode set.
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
          opennlp.tools.util.normalizer.UnicodeWhitespace.isWhitespace(cp),
          StringUtil.isUnicodeWhitespace(cp), "U+" + Integer.toHexString(cp));
    }
    // The deltas to the legacy predicate.
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace(0x0085));
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace((char) 0x0085));
    for (int cp = 0x001C; cp <= 0x001F; cp++) {
      Assertions.assertFalse(StringUtil.isUnicodeWhitespace(cp), "U+" + Integer.toHexString(cp));
      Assertions.assertFalse(StringUtil.isUnicodeWhitespace((char) cp),
          "U+" + Integer.toHexString(cp));
    }
    // Members and non-members on both overloads.
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace(' '));
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace((char) 0x00A0));
    Assertions.assertTrue(StringUtil.isUnicodeWhitespace(0x2028));
    Assertions.assertFalse(StringUtil.isUnicodeWhitespace('a'));
    Assertions.assertFalse(StringUtil.isUnicodeWhitespace(0x200B));
  }

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
    // should raise a NPE
    Assertions.assertThrows(NullPointerException.class, () -> {
      // should raise a NPE
      StringUtil.isEmpty(null);
    });
  }

  @Test
  void testLowercaseBeyondBMP() {
    int[] codePoints = new int[] {65, 66578, 67};    //A,Deseret capital BEE,C
    int[] expectedCodePoints = new int[] {97, 66618, 99};//a,Deseret lowercase b,c
    String input = new String(codePoints, 0, codePoints.length);
    String lc = StringUtil.toLowerCase(input);
    Assertions.assertArrayEquals(expectedCodePoints, lc.codePoints().toArray());
  }
}
