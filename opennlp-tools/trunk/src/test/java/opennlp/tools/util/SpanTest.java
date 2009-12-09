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

import junit.framework.Assert;
import junit.framework.TestCase;

/**
 * Tests for the {@link Span} class.
 */
public class SpanTest extends TestCase {

  /**
   * Test for {@link Span#getStart()}.
   */
  public void testGetStart() {
    Assert.assertEquals(5, new Span(5, 6).getStart());
  }

  /**
   * Test for {@link Span#getEnd()}.
   */
  public void testGetEnd() {
    Assert.assertEquals(6, new Span(5, 6).getEnd());
  }

  /**
   * Test for {@link Span#length()}.
   */
  public void testLength() {
    Assert.assertEquals(11, new Span(10, 21).length());
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  public void testContains() {
    Span a = new Span(500, 900);
    Span b = new Span(520, 600);

    Assert.assertEquals(true, a.contains(b));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  public void testContainsWithEqual() {
    Span a = new Span(500, 900);

    Assert.assertEquals(true, a.contains(a));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  public void testContainsWithLowerIntersect() {
    Span a = new Span(500, 900);
    Span b = new Span(450, 1000);

    Assert.assertEquals(false, a.contains(b));
  }

  /**
   * Test for {@link Span#contains(Span)}.
   */
  public void testContainsWithHigherIntersect() {
    Span a = new Span(500, 900);
    Span b = new Span(500, 1000);

    Assert.assertEquals(false, a.contains(b));
  }

  /**
   * Test for {@link Span#contains(int)}.
   */
  public void testContainsInt() {
    Span a = new Span(10, 300);

    Assert.assertFalse(a.contains(9));
    Assert.assertTrue(a.contains(10));
    Assert.assertTrue(a.contains(200));
    Assert.assertTrue(a.contains(300));
    Assert.assertFalse(a.contains(301));
  }

  /**
   * Test for {@link Span#startsWith(Span)}.
   */
  public void testStartsWith() {
    Span a = new Span(10, 50);
    Span b = new Span(10, 12);

    Assert.assertTrue(a.startsWith(a));

    Assert.assertTrue(a.startsWith(b));

    Assert.assertFalse(b.startsWith(a));

  }

  /**
   * Test for {@link Span#intersects(Span)}.
   */
  public void testIntersects() {
    Span a = new Span(10, 50);
    Span b = new Span(40, 100);

    Assert.assertTrue(a.intersects(b));
    Assert.assertTrue(b.intersects(a));

    Span c = new Span(10, 20);
    Span d = new Span(40, 50);

    Assert.assertFalse(c.intersects(d));
    Assert.assertFalse(d.intersects(c));

    Assert.assertTrue(b.intersects(d));
  }

  /**
   * Test for {@link Span#crosses(Span)}.
   */
  public void testCrosses() {
    Span a = new Span(10, 50);
    Span b = new Span(40, 100);

    Assert.assertTrue(a.crosses(b));
    Assert.assertTrue(b.crosses(a));

    Span c = new Span(10, 20);
    Span d = new Span(40, 50);

    Assert.assertFalse(c.crosses(d));
    Assert.assertFalse(d.crosses(c));

    Assert.assertFalse(b.crosses(d));
  }

  /**
   * Test for {@link Span#compareTo(Object)}.
   */
  public void testCompareToLower() {
    Span a = new Span(100, 1000);
    Span b = new Span(10, 50);

    Assert.assertEquals(true, a.compareTo(b) > 0);
  }

  /**
   * Test for {@link Span#compareTo(Object)}.
   */
  public void testCompareToHigher() {
    Span a = new Span(100, 200);
    Span b = new Span(300, 400);

    Assert.assertEquals(true, a.compareTo(b) < 0);
  }

  /**
   * Test for {@link Span#compareTo(Object)}.
   */
  public void testCompareToEquals() {
    Span a = new Span(30, 1000);
    Span b = new Span(30, 1000);

    Assert.assertEquals(true, a.compareTo(b) == 0);
  }

  /**
   * Test for {@link Span#hashCode()}.
   */
  public void testhHashCode() {
    Assert.assertEquals(new Span(10, 11), new Span(10, 11));
  }

  /**
   * Test for {@link Span#equals(Object)}.
   */
  public void testEqualsWithNull() {
    Span a = new Span(0, 0);

    Assert.assertEquals(a.equals(null), false);
  }

  /**
   * Test for {@link Span#equals(Object)}.
   */
  public void testEquals() {
    Span a1 = new Span(100, 1000, "test");
    Span a2 = new Span(100, 1000, "test");

    Assert.assertTrue(a1.equals(a2));
    
    Span b1 = new Span(100, 100, "test");
    Assert.assertFalse(a1.equals(b1));
    
    Span c1 = new Span(100, 1000, "Test");
    Assert.assertFalse(a1.equals(c1));
    
  }

  /**
   * Test for {@link Span#toString()}.
   */
  public void testToString() {
    new Span(50, 100).toString();
  }
}
