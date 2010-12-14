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

import junit.framework.TestCase;

/**
 * Tests for the {@link EvaluatorUtil} class.
 */
public class FMeasureEvaluatorTest extends TestCase {
  
  private Span gold[] = {
      new Span(8, 9),
      new Span(9, 10),
      new Span(10, 12),
      new Span(13, 14),
      new Span(14, 15),
      new Span(15, 16)
  };
  
  private Span predicted[] = {
      new Span(14, 15),
      new Span(15, 16),
      new Span(100, 120),
      new Span(210, 220),
      new Span(220, 230)
  };
  
  /**
   * Test for the {@link EvaluatorUtil#countTruePositives(Span[], Span[])} method.
   */
  public void testCountTruePositives() {
    assertEquals(2, FMeasureEvaluator.countTruePositives(gold, predicted));
  }
  
  /**
   * Test for the {@link EvaluatorUtil#precision(Span[], Span[])} method.
   */
  public void testPrecision() {
    assertEquals(2d / predicted.length, FMeasureEvaluator.precision(gold, predicted));
  }
  
  /**
   * Test for the {@link EvaluatorUtil#recall(Span[], Span[])} method.
   */
  public void testRecall() {
    assertEquals(2d / gold.length, FMeasureEvaluator.recall(gold, predicted));
  } 
}