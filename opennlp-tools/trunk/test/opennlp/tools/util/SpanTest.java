///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2008 OpenNlp
// 
//This library is free software; you can redistribute it and/or
//modify it under the terms of the GNU Lesser General Public
//License as published by the Free Software Foundation; either
//version 2.1 of the License, or (at your option) any later version.
// 
//This library is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Lesser General Public License for more details.
// 
//You should have received a copy of the GNU Lesser General Public
//License along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//////////////////////////////////////////////////////////////////////////////

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
    Span a = new Span(100, 1000);
    Span b = new Span(100, 1000);

    Assert.assertEquals(a.equals(b), true);
  }
  
  /**
   * Test for {@link Span#toString()}.
   */
  public void testToString() {
    new Span(50, 100).toString();
  }
}