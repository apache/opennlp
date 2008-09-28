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

package opennlp.tools.tokenize;

import junit.framework.TestCase;
import opennlp.tools.tokenize.SimpleTokenizer;

/**
 * Tests for the {@link SimpleTokenizer} class.
 */
public class SimpleTokenizerTest extends TestCase {
  
  private SimpleTokenizer mTokenizer = new SimpleTokenizer();
  
  /**
   * Tests if it can tokenize whitespace separated tokens.
   */
  public void testWhitespaceTokenization() {
    
    String text = "a b c  d     e                f    ";

    String[] tokenizedText = mTokenizer.tokenize(text);
    
    assertTrue("a".equals(tokenizedText[0]));
    assertTrue("b".equals(tokenizedText[1]));
    assertTrue("c".equals(tokenizedText[2]));
    assertTrue("d".equals(tokenizedText[3]));
    assertTrue("e".equals(tokenizedText[4]));
    assertTrue("f".equals(tokenizedText[5]));
    
    assertTrue(tokenizedText.length == 6);
  }
  
  /**
   * Tests if it can tokenize a word and a dot.
   */
  public void testWordDotTokenization() {
    String text = "a.";

    String[] tokenizedText = mTokenizer.tokenize(text);
    
    assertTrue("a".equals(tokenizedText[0]));
    assertTrue(".".equals(tokenizedText[1]));

    assertTrue(tokenizedText.length == 2);
  }
  
  /**
   * Tests if it can tokenize a word and numeric.
   */
  public void testWordNumericTokeniztation() {
    String text = "305KW";

    String[] tokenizedText = mTokenizer.tokenize(text);
    
    assertTrue("305".equals(tokenizedText[0]));
    assertTrue("KW".equals(tokenizedText[1]));

    assertTrue(tokenizedText.length == 2);
  }
  
  public void testWordWithOtherTokenization() {
    String text = "rebecca.sleep()";

    String[] tokenizedText = mTokenizer.tokenize(text);
    
    assertTrue("rebecca".equals(tokenizedText[0]));
    assertTrue(".".equals(tokenizedText[1]));
    assertTrue("sleep".equals(tokenizedText[2]));
    assertTrue("(".equals(tokenizedText[3]));
    assertTrue(")".equals(tokenizedText[4]));

    assertTrue(tokenizedText.length == 5);
  }
}