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

package opennlp.tools.namefind;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;
import opennlp.maxent.GISModel;
import opennlp.maxent.PlainTextByLineDataStream;
import opennlp.model.EventStream;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link NameFinderME}.
 * 
 * A proper testing and evaluation of the name finder 
 * is only possible  with a large corpus which contains 
 * a huge amount of test sentences. 
 * 
 * The scope of this test is to make sure that the name finder
 * code can be executed. This test can not detect
 * mistakes which lead to incorrect feature generation
 * or other mistakes which decrease the tagging
 * performance of the name finder.
 * 
 * In this test the {@link NameFinderME} is trained with 
 * a small amount of training sentences and then the
 * computed model is used to predict sentences from the 
 * training sentences.
 */
public class NameFinderMETest extends TestCase {

  public void testNameFinder() throws IOException {
    
    // train the name finder
    
    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/AnnotatedSentences.txt");
    
    String encoding = "ISO-8859-1";
    
    EventStream es = new NameFinderEventStream(new NameSampleDataStream(
          new PlainTextByLineDataStream(new InputStreamReader(in, encoding))));
    
    GISModel nameFinderModel = NameFinderME.train(es, 70, 1);
    
    TokenNameFinder nameFinder = new NameFinderME(nameFinderModel);
    
    // now test if it can detect the sample sentences
    
    String sentence[] = {"Alisa",
    		"appreciated",
    		"the",
    		"hint",
    		"and",
    		"enjoyed",
    		"a",
    		"delicious",
    		"traditional",
    		"meal."}; 
    
    Span names[] = nameFinder.find(sentence);
    
    assertEquals(1, names.length);
    assertEquals(new Span(0, 1), names[0]);
    
    sentence = new String[] {
        "Hi",
        "Mike",
        ",",
        "it's",
        "Stefanie",
        "Schmidt",
        "."   
    };
    
    names = nameFinder.find(sentence);
    
    assertEquals(2, names.length);
    assertEquals(new Span(1, 2), names[0]);
    assertEquals(new Span(4, 6), names[1]);
  }
}