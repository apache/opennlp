///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2007 OpenNlp
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

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;
import junit.framework.TestCase;

/**
  *Tests for the {@link DictionaryNameFinder} class. 
  */
public class DictionaryNameFinderTetst extends TestCase {
  
  private Dictionary mDictionary = new Dictionary();
  private TokenNameFinder mNameFinder;
  
  public DictionaryNameFinderTetst() {
    
    StringList vanessa = new StringList(new String[]{"Vanessa"});
    mDictionary.put(vanessa);
    
    StringList vanessaWilliams = new 
        StringList(new String[]{"Vanessa", 
        "Williams"});
    mDictionary.put(vanessaWilliams);
    
    StringList max = new StringList(new String[]{"Max"});
    mDictionary.put(max);
  }
  
  protected void setUp() throws Exception {
    mNameFinder = new DictionaryNameFinder(mDictionary);
  }
  
  public void testSingleTokeNameAtSentenceStart() {
    
    String sentence = "Max a b c d";
    
    SimpleTokenizer tokenizer = new SimpleTokenizer();
    String tokens[] = tokenizer.tokenize(sentence);
    
    Span names[] = mNameFinder.find(tokens);
    
    assertTrue(names.length == 1);    
    assertTrue(names[0].getStart() == 0 && names[0].getEnd() == 1);
  }

  public void testSingleTokeNameInsideSentence() {
    String sentence = "a b  Max c d";
    
    SimpleTokenizer tokenizer = new SimpleTokenizer();
    String tokens[] = tokenizer.tokenize(sentence);
    
    Span names[] = mNameFinder.find(tokens);
    
    assertTrue(names.length == 1);    
    assertTrue(names[0].getStart() == 2 && names[0].getEnd() == 3);
  }

  public void testSingleTokeNameAtSentenceEnd() {
    String sentence = "a b c Max";
    
    SimpleTokenizer tokenizer = new SimpleTokenizer();
    String tokens[] = tokenizer.tokenize(sentence);
    
    Span names[] = mNameFinder.find(tokens);
    
    assertTrue(names.length == 1);    
    assertTrue(names[0].getStart() == 3 && names[0].getEnd() == 4);
  }
  
  public void testLastMatchingTokenNameIsChoosen() {
    String sentence[] = {"a", "b", "c", "Vanessa"};
    
    Span names[] = mNameFinder.find(sentence);
    
    assertTrue(names.length == 1);    
    assertTrue(names[0].getStart() == 3 && names[0].getEnd() == 4);
  }
  
  public void testLongerTokenNameIsPreferred() {
    String sentence[] = {"a", "b", "c", "Vanessa", "Williams"};
    
    Span names[] = mNameFinder.find(sentence);
    
    assertTrue(names.length == 1);    
    assertTrue(names[0].getStart() == 3 && names[0].getEnd() == 5);
  }
}