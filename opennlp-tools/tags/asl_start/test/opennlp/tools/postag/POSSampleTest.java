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

package opennlp.tools.postag;

import opennlp.tools.util.ParseException;
import junit.framework.TestCase;

/**
 * Tests for the {@link POSSample} class.
 */
public class POSSampleTest extends TestCase {

  /**
   * Tests if it can parse a valid token_tag sentence.
   * 
   * @throws ParseException
   */
  public void testParse() throws ParseException {
    String sentence = "the_DT stories_NNS about_IN well-heeled_JJ " +
    		"communities_NNS and_CC developers_NNS";
    
    POSSample sample = POSSample.parse(sentence);
    
    assertEquals(sentence, sample.toString());
  }
  
  /**
   * Tests if it can parse an empty {@link String}.
   * @throws ParseException
   */
  public void testParseEmptyString() throws ParseException {
    
    String sentence = "";
    
    POSSample sample = POSSample.parse(sentence);
    
    assertEquals(sample.getSentence().length, 0);
    assertEquals(sample.getTags().length, 0);
    
    sample.toString();
  }
  
  /**
   * Tests if it can parse an empty token.
   * 
   * @throws ParseException
   */
  public void testParseEmtpyToken() throws ParseException {
    String sentence = "the_DT _NNS";
    
    POSSample sample = POSSample.parse(sentence);
     
    assertEquals(sample.getSentence()[1], "");
  }
  
  /**
   * Tests if it can parse an empty tag.
   * 
   * @throws ParseException
   */
  public void testParseEmtpyTag() throws ParseException {
    
    String sentence = "the_DT stories_";
    
    POSSample sample = POSSample.parse(sentence);
     
    assertEquals(sample.getTags()[1], "");
  }
  
  /**
   * Tests if an exception is thrown if there is only a token/tag
   * in the sentence.
   */
  public void testParseWithError() {
    String sentence = "the_DT stories";
    
    try {
      POSSample.parse(sentence);
    } catch (ParseException e) {
      return;
    }
    
    fail();
  }
}