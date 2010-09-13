/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package opennlp.tools.namefind;

import static org.junit.Assert.assertTrue;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringList;

import org.junit.Before;
import org.junit.Test;

/**
  *Tests for the {@link DictionaryNameFinder} class.
  */
public class DictionaryNameFinderTest{

  private Dictionary mDictionary = new Dictionary();
  private TokenNameFinder mNameFinder;

  public DictionaryNameFinderTest() {

    StringList vanessa = new StringList(new String[]{"Vanessa"});
    mDictionary.put(vanessa);

    StringList vanessaWilliams = new
        StringList(new String[]{"Vanessa",
        "Williams"});
    mDictionary.put(vanessaWilliams);

    StringList max = new StringList(new String[]{"Max"});
    mDictionary.put(max);
  }

  @Before
  public void setUp() throws Exception {
    mNameFinder = new DictionaryNameFinder(mDictionary);
  }

  @Test
  public void testSingleTokeNameAtSentenceStart() {

    String sentence = "Max a b c d";

    SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    String tokens[] = tokenizer.tokenize(sentence);

    Span names[] = mNameFinder.find(tokens);

    assertTrue(names.length == 1);
    assertTrue(names[0].getStart() == 0 && names[0].getEnd() == 1);
  }

  @Test
  public void testSingleTokeNameInsideSentence() {
    String sentence = "a b  Max c d";

    SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    String tokens[] = tokenizer.tokenize(sentence);

    Span names[] = mNameFinder.find(tokens);

    assertTrue(names.length == 1);
    assertTrue(names[0].getStart() == 2 && names[0].getEnd() == 3);
  }

  @Test
  public void testSingleTokeNameAtSentenceEnd() {
    String sentence = "a b c Max";

    SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    String tokens[] = tokenizer.tokenize(sentence);

    Span names[] = mNameFinder.find(tokens);

    assertTrue(names.length == 1);
    assertTrue(names[0].getStart() == 3 && names[0].getEnd() == 4);
  }

  @Test
  public void testLastMatchingTokenNameIsChoosen() {
    String sentence[] = {"a", "b", "c", "Vanessa"};

    Span names[] = mNameFinder.find(sentence);

    assertTrue(names.length == 1);
    assertTrue(names[0].getStart() == 3 && names[0].getEnd() == 4);
  }

  @Test
  public void testLongerTokenNameIsPreferred() {
    String sentence[] = {"a", "b", "c", "Vanessa", "Williams"};

    Span names[] = mNameFinder.find(sentence);

    assertTrue(names.length == 1);
    assertTrue(names[0].getStart() == 3 && names[0].getEnd() == 5);
  }
}