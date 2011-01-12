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

package opennlp.tools.formats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import opennlp.tools.formats.Conll03NameSampleStream.LANGUAGE;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;
import org.junit.Test;

/**
 * Test for the {@link Conll03NameSampleStream} class.
 */
public class Conll03NameSampleStreamTest {

  private static final String ENGLISH_SAMPLE = "conll2003-en.sample";
  private static final String GERMAN_SAMPLE = "conll2003-de.sample";
  
	
  private static ObjectStream<NameSample> openData(LANGUAGE lang, String name) throws IOException {
    InputStream in = Conll03NameSampleStreamTest.class.getResourceAsStream("/opennlp/tools/formats/" + name);

    return new Conll03NameSampleStream(lang, in, Conll02NameSampleStream.GENERATE_PERSON_ENTITIES);
  }

  @Test
  public void testParsingEnglishSample() throws IOException {

    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.EN, ENGLISH_SAMPLE);

    NameSample personName = sampleStream.read();
    assertNotNull(personName);

    assertEquals(9, personName.getSentence().length);
    assertEquals(0, personName.getNames().length);
    assertEquals(true, personName.isClearAdaptiveDataSet());

    personName = sampleStream.read();

    assertNotNull(personName);

    assertEquals(2, personName.getSentence().length);
    assertEquals(1, personName.getNames().length);
    assertEquals(false, personName.isClearAdaptiveDataSet());

    Span nameSpan = personName.getNames()[0];
    assertEquals(0, nameSpan.getStart());
    assertEquals(2, nameSpan.getEnd());

    assertNull(sampleStream.read());
  }
  
  @Test(expected=IOException.class)
  public void testParsingEnglishSampleWithGermanAsLanguage() throws IOException {
    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.DE, ENGLISH_SAMPLE);
    sampleStream.read();
  }
  
  @Test(expected=IOException.class)
  public void testParsingGermanSampleWithEnglishAsLanguage() throws IOException {
	  ObjectStream<NameSample> sampleStream = openData(LANGUAGE.EN, GERMAN_SAMPLE);
	  sampleStream.read();
  }
  
  @Test
  public void testParsingGermanSample() throws IOException {

    ObjectStream<NameSample> sampleStream = openData(LANGUAGE.DE, GERMAN_SAMPLE);
    
    NameSample personName = sampleStream.read();
    assertNotNull(personName);
    
    assertEquals(5, personName.getSentence().length);
    assertEquals(0, personName.getNames().length);
    assertEquals(true, personName.isClearAdaptiveDataSet());
  }
}