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

/**
 * Tests for the {@link WhitespaceTokenizer} class.
 */
public class WhitespaceTokenizerTest extends TestCase {

  /**
   * Tests if it can tokenize whitespace separated tokens.
   */
  public void testWhitespaceTokenization() {
    
    String text = "a b c  d     e                f    ";

    String[] tokenizedText = WhitespaceTokenizer.INSTANCE.tokenize(text);
    
    assertTrue("a".equals(tokenizedText[0]));
    assertTrue("b".equals(tokenizedText[1]));
    assertTrue("c".equals(tokenizedText[2]));
    assertTrue("d".equals(tokenizedText[3]));
    assertTrue("e".equals(tokenizedText[4]));
    assertTrue("f".equals(tokenizedText[5]));
    
    assertTrue(tokenizedText.length == 6);
  }
}