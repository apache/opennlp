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

import java.util.Iterator;

import junit.framework.TestCase;

/**
 * Tests for the {@link StringList} class.
 */
public class StringListTest extends TestCase {

  /**
   * Tests {@link StringList#getToken(int)}.
   */
  public void testGetToken() {
    StringList l = new StringList("a", "b");
    
    assertEquals(2, l.size());
    
    assertEquals("a", l.getToken(0));
    assertEquals("b", l.getToken(1));
  }

  /**
   * Tests {@link StringList#iterator()}.
   */
  public void testItertor() {
    StringList l = new StringList("a");
    
    Iterator<String> it = l.iterator();
    
    assertTrue(it.hasNext());
    assertEquals("a", it.next());
    assertFalse(it.hasNext());
    
    // now test with more than one string
    l = new StringList("a", "b", "c");
    it = l.iterator();
    
    assertTrue(it.hasNext());
    assertEquals("a", it.next());
    
    assertTrue(it.hasNext());
    assertEquals("b", it.next());
    
    assertTrue(it.hasNext());
    assertEquals("c", it.next());

    assertFalse(it.hasNext());
  }

  /**
   * Tests {@link StringList#compareToIgnoreCase(StringList)}.
   */
  public void testCompareToIgnoreCase() {
    assertTrue(new StringList("a", "b").compareToIgnoreCase(
        new StringList("A", "B")));
  }

  /**
   * Tests {@link StringList#equals(Object)}.
   */
  public void testEquals() {
    assertEquals(new StringList("a", "b"),
        new StringList("a", "b"));
    
    assertFalse(new StringList("a", "b").equals(
        new StringList("A", "B")));
  }
  
  /**
   * Tests {@link StringList#hashCode()}.
   */
  public void testHashCode() {
    assertEquals(new StringList("a", "b").hashCode(), 
        new StringList("a", "b").hashCode());
  }
  
  /**
   * Tests {@link StringList#toString()}.
   */
  public void testToString() {
    new StringList("a", "b").toString();
  }
}