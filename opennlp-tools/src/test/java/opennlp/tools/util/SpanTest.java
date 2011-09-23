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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the {@link Span} class.
 */
public class SpanTest {

  /**
   * Test for {@link Span#getStart()}.
   */
  @Test
  public void testGetStart() {
    assertEquals(5, new Span(5, 6).getStart());
  }

  /**
   * Test for {@link Span#getEnd()}.
   */
  @Test
  public void testGetEnd() {
    assertEquals(6, new Span(5, 6).getEnd());
  }

  /**
   * Test for {@link Span#length()}.
   */
  @Test
  public void testLength() {
    assertEquals(11, new Span(10, 21).length());
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  public void testContains() {
    Span a = new Span(500, 900);
    Span b = new Span(520, 600);

    assertEquals(true, a.contains(b));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  public void testContainsWithEqual() {
    Span a = new Span(500, 900);

    assertEquals(true, a.contains(a));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  public void testContainsWithLowerIntersect() {
    Span a = new Span(500, 900);
    Span b = new Span(450, 1000);

    assertEquals(false, a.contains(b));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  @Test
  public void testContainsWithHigherIntersect() {
    Span a = new Span(500, 900);
    Span b = new Span(500, 1000);

    assertEquals(false, a.contains(b));
  }

  /**
   * Test for {@link Span#contains(int)}.
   */
  @Test
  public void testContainsInt() {
    Span a = new Span(10, 300);

    /* NOTE: here the span does not contain the endpoint marked as the end
     * for the span.  This is because the end should be placed one past the
     * true end for the span.  The indexes used must observe the same
     * requirements for the contains function.
     */
    assertFalse(a.contains(9));
    assertTrue(a.contains(10));
    assertTrue(a.contains(200));
    assertTrue(a.contains(299));
    assertFalse(a.contains(300));
  }

  /**
   * Test for {@link Span#startsWith(Span)}.
   */
  @Test
  public void testStartsWith() {
    Span a = new Span(10, 50);
    Span b = new Span(10, 12);

    assertTrue(a.startsWith(a));

    assertTrue(a.startsWith(b));

    assertFalse(b.startsWith(a));
  }

  /**
   * Test for {@link Span#intersects(Span)}.
   */
  @Test
  public void testIntersects() {
    Span a = new Span(10, 50);
    Span b = new Span(40, 100);

    assertTrue(a.intersects(b));
    assertTrue(b.intersects(a));

    Span c = new Span(10, 20);
    Span d = new Span(40, 50);

    assertFalse(c.intersects(d));
    assertFalse(d.intersects(c));

    assertTrue(b.intersects(d));
  }

  /**
   * Test for {@link Span#crosses(Span)}.
   */
  @Test
  public void testCrosses() {
    Span a = new Span(10, 50);
    Span b = new Span(40, 100);

    assertTrue(a.crosses(b));
    assertTrue(b.crosses(a));

    Span c = new Span(10, 20);
    Span d = new Span(40, 50);

    assertFalse(c.crosses(d));
    assertFalse(d.crosses(c));

    assertFalse(b.crosses(d));
  }

  /**
   * Test for {@link Span#compareTo(Object)}.
   */
  @Test
  public void testCompareToLower() {
    Span a = new Span(100, 1000);
    Span b = new Span(10, 50);

    assertEquals(true, a.compareTo(b) > 0);
  }

  /**
   * Test for {@link Span#compareTo(Object)}.
   */
  @Test
  public void testCompareToHigher() {
    Span a = new Span(100, 200);
    Span b = new Span(300, 400);

    assertEquals(true, a.compareTo(b) < 0);
  }

  /**
   * Test for {@link Span#compareTo(Object)}.
   */
  @Test
  public void testCompareToEquals() {
    Span a = new Span(30, 1000);
    Span b = new Span(30, 1000);

    assertEquals(true, a.compareTo(b) == 0);
  }

  /**
   * Test for {@link Span#hashCode()}.
   */
  @Test
  public void testhHashCode() {
    assertEquals(new Span(10, 11), new Span(10, 11));
  }

  /**
   * Test for {@link Span#equals(Object)}.
   */
  @Test
  public void testEqualsWithNull() {
    Span a = new Span(0, 0);

    assertEquals(a.equals(null), false);
  }

  /**
   * Test for {@link Span#equals(Object)}.
   */
  @Test
  public void testEquals() {
    Span a1 = new Span(100, 1000, "test");
    Span a2 = new Span(100, 1000, "test");

    assertTrue(a1.equals(a2));
    
    // end is different
    Span b1 = new Span(100, 100, "test");
    assertFalse(a1.equals(b1));
    
    // type is different
    Span c1 = new Span(100, 1000, "Test");
    assertFalse(a1.equals(c1));
    
    Span d1 = new Span(100, 1000);
    
    assertFalse(d1.equals(a1));
    assertFalse(a1.equals(d1));
    
  }

  /**
   * Test for {@link Span#toString()}.
   */
  @Test
  public void testToString() {
    new Span(50, 100).toString();
  }
}
