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
 * Tests for the {@link Span} class.
 */

public class SpanTest {

  /**
   * Test for {@link Span#getStart()}.
   */
  @Test
  void testGetStart() {
    Assertions.assertEquals(5, new Span(5, 6).getStart());
  }

  /**
   * Test for {@link Span#getEnd()}.
   */
  @Test
  void testGetEnd() {
    Assertions.assertEquals(6, new Span(5, 6).getEnd());
  }

  /**
   * Test for {@link Span#length()}.
   */
  @Test
  void testLength() {
    Assertions.assertEquals(11, new Span(10, 21).length());
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  void testContains() {
    Span a = new Span(500, 900);
    Span b = new Span(520, 600);

    Assertions.assertTrue(a.contains(b));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  void testContainsWithEqual() {
    Span a = new Span(500, 900);
    Assertions.assertTrue(a.contains(a));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  void testContainsWithLowerIntersect() {
    Span a = new Span(500, 900);
    Span b = new Span(450, 1000);
    Assertions.assertFalse(a.contains(b));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  void testContainsWithHigherIntersect() {
    Span a = new Span(500, 900);
    Span b = new Span(500, 1000);
    Assertions.assertFalse(a.contains(b));
  }

  /**
   * Test for {@link Span#contains(int)}.
   */
  @Test
  void testContainsInt() {
    Span a = new Span(10, 300);

    /* NOTE: here the span does not contain the endpoint marked as the end
     * for the span.  This is because the end should be placed one past the
     * true end for the span.  The indexes used must observe the same
     * requirements for the contains function.
     */
    Assertions.assertFalse(a.contains(9));
    Assertions.assertTrue(a.contains(10));
    Assertions.assertTrue(a.contains(200));
    Assertions.assertTrue(a.contains(299));
    Assertions.assertFalse(a.contains(300));
  }

  /**
   * Test for {@link Span#startsWith(Span)}.
   */
  @Test
  void testStartsWith() {
    Span a = new Span(10, 50);
    Span b = new Span(10, 12);

    Assertions.assertTrue(a.startsWith(a));
    Assertions.assertTrue(a.startsWith(b));
    Assertions.assertFalse(b.startsWith(a));
  }

  /**
   * Test for {@link Span#intersects(Span)}.
   */
  @Test
  void testIntersects() {
    Span a = new Span(10, 50);
    Span b = new Span(40, 100);

    Assertions.assertTrue(a.intersects(b));
    Assertions.assertTrue(b.intersects(a));

    Span c = new Span(10, 20);
    Span d = new Span(40, 50);

    Assertions.assertFalse(c.intersects(d));
    Assertions.assertFalse(d.intersects(c));
    Assertions.assertTrue(b.intersects(d));
  }

  /**
   * Test for {@link Span#crosses(Span)}.
   */
  @Test
  void testCrosses() {
    Span a = new Span(10, 50);
    Span b = new Span(40, 100);

    Assertions.assertTrue(a.crosses(b));
    Assertions.assertTrue(b.crosses(a));

    Span c = new Span(10, 20);
    Span d = new Span(40, 50);

    Assertions.assertFalse(c.crosses(d));
    Assertions.assertFalse(d.crosses(c));
    Assertions.assertFalse(b.crosses(d));
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToLower() {
    Span a = new Span(100, 1000);
    Span b = new Span(10, 50);
    Assertions.assertTrue(a.compareTo(b) > 0);
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToHigher() {
    Span a = new Span(100, 200);
    Span b = new Span(300, 400);
    Assertions.assertTrue(a.compareTo(b) < 0);
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToEquals() {
    Span a = new Span(30, 1000);
    Span b = new Span(30, 1000);
    Assertions.assertEquals(0, a.compareTo(b));
  }

  ///

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToEqualsSameType() {
    Span a = new Span(30, 1000, "a");
    Span b = new Span(30, 1000, "a");
    Assertions.assertEquals(0, a.compareTo(b));
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToEqualsDiffType1() {
    Span a = new Span(30, 1000, "a");
    Span b = new Span(30, 1000, "b");
    Assertions.assertEquals(-1, a.compareTo(b));
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToEqualsDiffType2() {
    Span a = new Span(30, 1000, "b");
    Span b = new Span(30, 1000, "a");
    Assertions.assertEquals(1, a.compareTo(b));
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToEqualsNullType1() {
    Span a = new Span(30, 1000);
    Span b = new Span(30, 1000, "b");
    Assertions.assertEquals(1, a.compareTo(b));
  }

  /**
   * Test for {@link Span#compareTo(Span)}.
   */
  @Test
  void testCompareToEqualsNullType2() {
    Span a = new Span(30, 1000, "b");
    Span b = new Span(30, 1000);
    Assertions.assertEquals(-1, a.compareTo(b));
  }

  /**
   * Test for {@link Span#hashCode()}.
   */
  @Test
  void testhHashCode() {
    Assertions.assertEquals(new Span(10, 11), new Span(10, 11));
  }

  /**
   * Test for {@link Span#equals(Object)}.
   */
  @Test
  void testEqualsWithNull() {
    Span a = new Span(0, 0);
    Assertions.assertNotNull(a);
  }

  /**
   * Test for {@link Span#equals(Object)}.
   */
  @Test
  void testEquals() {
    Span a1 = new Span(100, 1000, "test");
    Span a2 = new Span(100, 1000, "test");
    Assertions.assertEquals(a1, a2);

    // end is different
    Span b1 = new Span(100, 100, "test");
    Assertions.assertNotEquals(a1, b1);

    // type is different
    Span c1 = new Span(100, 1000, "Test");
    Assertions.assertNotEquals(a1, c1);

    Span d1 = new Span(100, 1000);
    Assertions.assertNotEquals(d1, a1);
    Assertions.assertNotEquals(a1, d1);

  }

  /**
   * Test for {@link Span#toString()}.
   */
  @Test
  void testToString() {
    Assertions.assertEquals("[50..100)", new Span(50, 100).toString());
    Assertions.assertEquals("[50..100) myType", new Span(50, 100, "myType").toString());
  }

  @Test
  void testTrim() {
    String string1 = "  12 34  ";
    Span span1 = new Span(0, string1.length());
    Assertions.assertEquals("12 34", span1.trim(string1).getCoveredText(string1));
  }

  @Test
  void testTrimWhitespaceSpan() {
    String string1 = "              ";
    Span span1 = new Span(0, string1.length());
    Assertions.assertEquals("", span1.trim(string1).getCoveredText(string1));
  }

  /**
   * Test if it fails to construct span with invalid start
   */
  @Test
  void testTooSmallStart() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Span(-1, 100));
  }

  /**
   * Test if it fails to construct span with invalid end
   */
  @Test
  void testTooSmallEnd() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Span(50, -1));
  }

  /**
   * Test if it fails to construct span with start > end
   */
  @Test
  void testStartLargerThanEnd() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Span(100, 50));
  }
}
