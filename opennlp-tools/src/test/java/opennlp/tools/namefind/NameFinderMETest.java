/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;

import opennlp.model.AbstractModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

import org.junit.Test;

/**
 * This is the test class for {@link NameFinderME}.
 * <p>
 * A proper testing and evaluation of the name finder
 * is only possible  with a large corpus which contains
 * a huge amount of test sentences.
 * <p>
 * The scope of this test is to make sure that the name finder
 * code can be executed. This test can not detect
 * mistakes which lead to incorrect feature generation
 * or other mistakes which decrease the tagging
 * performance of the name finder.
 * <p>
 * In this test the {@link NameFinderME} is trained with
 * a small amount of training sentences and then the
 * computed model is used to predict sentences from the
 * training sentences.
 */
public class NameFinderMETest {
  
  private final String TYPE = "default";

  @Test
  public void testNameFinder() throws Exception {

    // train the name finder

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/AnnotatedSentences.txt");

    String encoding = "ISO-8859-1";

    ObjectStream<NameSample> sampleStream = 
          new NameSampleDataStream(
          new PlainTextByLineStream(new InputStreamReader(in, encoding)));
    
    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", TYPE, sampleStream,
        Collections.<String, Object>emptyMap(), 70, 1);

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
    assertEquals(new Span(0, 1, TYPE), names[0]);

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
    assertEquals(new Span(1, 2, TYPE), names[0]);
    assertEquals(new Span(4, 6, TYPE), names[1]);
  }
  
  /**
   * Train NamefinderME using AnnotatedSentencesWithTypes.txt with "person"
   * nameType and try the model in a sample text.
   *  
   * @throws Exception
   */
  @Test
  public void testNameFinderWithTypes() throws Exception {

    // train the name finder

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/AnnotatedSentencesWithTypes.txt");

    String encoding = "ISO-8859-1";

    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in, encoding)));

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", TYPE, sampleStream,
        Collections.<String, Object>emptyMap(), 70, 1);

    NameFinderME nameFinder = new NameFinderME(nameFinderModel);

    // now test if it can detect the sample sentences

    String[] sentence2 = new String[] { "Hi", "Mike", ",", "it's", "Stefanie",
        "Schmidt", "." };

    Span[] names2 = nameFinder.find(sentence2);

    assertEquals(2, names2.length);
    assertEquals(new Span(1, 2, "person"), names2[0]);
    assertEquals(new Span(4, 6, "person"), names2[1]);
    assertEquals("person", names2[0].getType());
    assertEquals("person", names2[1].getType());

    String sentence[] = { "Alisa", "appreciated", "the", "hint", "and",
        "enjoyed", "a", "delicious", "traditional", "meal." };

    Span names[] = nameFinder.find(sentence);

    assertEquals(1, names.length);
    assertEquals(new Span(0, 1, "person"), names[0]);
    assertTrue(hasOtherAsOutcome(nameFinderModel));
  }

  /**
   * Train NamefinderME using OnlyWithNames.train. The goal is to check if the model validator accepts it.
   * This is related to the issue OPENNLP-9
   * 
   * @throws Exception
   */
  @Test
  public void testOnlyWithNames() throws Exception {

    // train the name finder

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/OnlyWithNames.train");

    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in)));

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", TYPE, 
        sampleStream, Collections.<String, Object>emptyMap(), 70, 1);

    NameFinderME nameFinder = new NameFinderME(nameFinderModel);

    // now test if it can detect the sample sentences

    String[] sentence = ("Neil Abercrombie Anibal Acevedo-Vila Gary Ackerman " +
    		"Robert Aderholt Daniel Akaka Todd Akin Lamar Alexander Rodney Alexander").split("\\s+");

    Span[] names1 = nameFinder.find(sentence);

    assertEquals(new Span(0, 2, TYPE), names1[0]);
    assertEquals(new Span(2, 4, TYPE), names1[1]);
    assertEquals(new Span(4, 6, TYPE), names1[2]);
    assertTrue(!hasOtherAsOutcome(nameFinderModel));
  }
  
  /**
   * Train NamefinderME using OnlyWithNamesWithTypes.train. The goal is to check if the model validator accepts it.
   * This is related to the issue OPENNLP-9
   * 
   * @throws Exception
   */
  @Test
  public void testOnlyWithNamesWithTypes() throws Exception {

    // train the name finder

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/OnlyWithNamesWithTypes.train");

    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in)));

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", TYPE, 
        sampleStream, Collections.<String, Object>emptyMap(), 70, 1);

    NameFinderME nameFinder = new NameFinderME(nameFinderModel);

    // now test if it can detect the sample sentences

    String[] sentence = ("Neil Abercrombie Anibal Acevedo-Vila Gary Ackerman " +
    		"Robert Aderholt Daniel Akaka Todd Akin Lamar Alexander Rodney Alexander").split("\\s+");

    Span[] names1 = nameFinder.find(sentence);

    assertEquals(new Span(0, 2, "person"), names1[0]);
    assertEquals(new Span(2, 4, "person"), names1[1]);
    assertEquals(new Span(4, 6, "person"), names1[2]);
    assertEquals("person", names1[2].getType());
    
    assertTrue(!hasOtherAsOutcome(nameFinderModel));
  }
  
  /**
   * Train NamefinderME using OnlyWithNames.train. The goal is to check if the model validator accepts it.
   * This is related to the issue OPENNLP-9
   * 
   * @throws Exception
   */
  @Test
  public void testOnlyWithEntitiesWithTypes() throws Exception {

    // train the name finder

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/OnlyWithEntitiesWithTypes.train");

    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in)));

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", TYPE, 
        sampleStream, Collections.<String, Object>emptyMap(), 70, 1);

    NameFinderME nameFinder = new NameFinderME(nameFinderModel);

    // now test if it can detect the sample sentences

    String[] sentence = ("NATO United States Barack Obama").split("\\s+");

    Span[] names1 = nameFinder.find(sentence);

    assertEquals(new Span(0, 1, "location"), names1[0]);
    assertEquals(new Span(1, 3, "person"), names1[1]);
    assertEquals("person", names1[2].getType());
    assertTrue(!hasOtherAsOutcome(nameFinderModel));
  }
  
  private boolean hasOtherAsOutcome(TokenNameFinderModel nameFinderModel) {
	  AbstractModel model = nameFinderModel.getNameFinderModel();
	  for (int i = 0; i < model.getNumOutcomes(); i++) {
	      String outcome = model.getOutcome(i);
	      if (outcome.equals(NameFinderME.OTHER)) {
	        return true;
	      }
	    }
	  return false;
  }
  
  @Test
  public void testDropOverlappingSpans() {
    Span spans[] = new Span[] {new Span(1, 10), new Span(1,11), new Span(1,11), new Span(5, 15)};
    Span remainingSpan[] = NameFinderME.dropOverlappingSpans(spans);
    
    assertEquals(new Span(1, 11), remainingSpan[0]);
  }

  /**
   * Train NamefinderME using voa1.train with several
   * nameTypes and try the model in a sample text.
   * 
   * @throws Exception
   */
  @Test
  public void testNameFinderWithMultipleTypes() throws Exception {

    // train the name finder

    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/voa1.train");

    ObjectStream<NameSample> sampleStream = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in)));

    TokenNameFinderModel nameFinderModel = NameFinderME.train("en", TYPE, 
        sampleStream, Collections.<String, Object>emptyMap(), 70, 1);

    NameFinderME nameFinder = new NameFinderME(nameFinderModel);

    // now test if it can detect the sample sentences

    String[] sentence = new String[] { "U", ".", "S", ".", "President",
        "Barack", "Obama", "has", "arrived", "in", "South", "Korea", ",",
        "where", "he", "is", "expected", "to", "show", "solidarity", "with",
        "the", "country", "'", "s", "president", "in", "demanding", "North",
        "Korea", "move", "toward", "ending", "its", "nuclear", "weapons",
        "programs", "." };

    Span[] names1 = nameFinder.find(sentence);

    assertEquals(new Span(0, 4, "location"), names1[0]);
    assertEquals(new Span(5, 7, "person"), names1[1]);
    assertEquals(new Span(10, 12, "location"), names1[2]);
    assertEquals(new Span(28, 30, "location"), names1[3]);
    assertEquals("location", names1[0].getType());
    assertEquals("person", names1[1].getType());
    assertEquals("location", names1[2].getType());
    assertEquals("location", names1[3].getType());

    sentence = new String[] { "Scott", "Snyder", "is", "the", "director", "of",
        "the", "Center", "for", "U", ".", "S", ".", "Korea", "Policy", "." };
    
    Span[] names2 = nameFinder.find(sentence);
    
    assertEquals(2, names2.length);
    assertEquals(new Span(0, 2, "person"), names2[0]);
    assertEquals(new Span(7, 15, "organization"), names2[1]);
    assertEquals("person", names2[0].getType());
    assertEquals("organization", names2[1].getType());
  }
  
}
