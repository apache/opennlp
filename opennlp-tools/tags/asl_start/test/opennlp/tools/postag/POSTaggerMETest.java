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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;
import opennlp.maxent.PlainTextByLineDataStream;

/**
 * Tests for the {@link POSTaggerME} class.
 */
public class POSTaggerMETest extends TestCase {

  public void testPOSTagger() throws IOException {
    
    InputStream in = getClass().getClassLoader().getResourceAsStream(
    "opennlp/tools/postag/AnnotatedSentences.txt");
    
    POSModel posModel = POSTaggerME.train(new WordTagSampleStream(new PlainTextByLineDataStream(
        new InputStreamReader(in))), null, null, 5);
    
    POSTagger tagger = new POSTaggerME(posModel);
    
    String tags[] = tagger.tag(new String[] {
        "The",
    	"driver",
    	"got",
    	"badly",
    	"injured",
    	"."});
    
    assertEquals(6, tags.length);
    
    assertEquals(tags[0], "DT");
    assertEquals(tags[1], "NN");
    assertEquals(tags[2], "VBD");
    assertEquals(tags[3], "RB");
    assertEquals(tags[4], "VBN");
    assertEquals(tags[5], ".");
  }
}