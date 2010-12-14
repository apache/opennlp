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

import java.util.Arrays;

import junit.framework.TestCase;

/**
 * Tests for the {@link Sequence} class.
 */
public class SequenceTest extends TestCase {

  /**
   * Tests the copy constructor {@link Sequence#Sequence(Sequence)}.
   */
  public void testCopyConstructor() {
    Sequence sequence = new Sequence();
    sequence.add("a", 10);
    sequence.add("b", 20);
    
    Sequence copy = new Sequence(sequence);

    assertEquals(sequence.getOutcomes(), copy.getOutcomes());
    assertTrue(Arrays.equals(sequence.getProbs(), copy.getProbs()));
    assertTrue(sequence.compareTo(copy) == 0);
  }
  
  /**
   * Tests {@link Sequence#add(String, double)}, also
   * tests {@link Sequence#getOutcomes()} and {@link Sequence#getProbs()}.
   */
  public void testAddMethod() {
    Sequence sequence = new Sequence();
    sequence.add("a", 10);
    
    // check if insert was successful
    assertEquals("a", sequence.getOutcomes().get(0));
    assertEquals(10d, sequence.getProbs()[0]);
  }
  
  /**
   * Tests {@link Sequence#compareTo(Sequence)}.
   */
  public void testCompareTo() {
    Sequence lowScore = new Sequence();
    lowScore.add("A", 1d);
    lowScore.add("B", 2d);
    lowScore.add("C", 3d);
    
    Sequence highScore = new Sequence();
    lowScore.add("A", 7d);
    lowScore.add("B", 8d);
    lowScore.add("C", 9d);
    
    assertEquals(-1, lowScore.compareTo(highScore));
    assertEquals(1, highScore.compareTo(lowScore));
    assertEquals(0, highScore.compareTo(highScore));
  }
  
  /**
   * Checks that {@link Sequence#toString()} is executable.
   */
  public void testToString() {
    new Sequence().toString();
    
    Sequence sequence = new Sequence();
    sequence.add("test", 0.1d);
    sequence.toString();
  }
}