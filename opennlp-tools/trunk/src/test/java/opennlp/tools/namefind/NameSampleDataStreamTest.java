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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

/**
 * This is the test class for {@link NameSampleDataStream}..
 * 
 * @author William Colen
 */
public class NameSampleDataStreamTest extends TestCase {

  /**
   * Create a string from a array section.
   * 
   * @param tokens the tokens
   * @param nameSpan the section
   * @return the string
   */
  private String sublistToString(String[] tokens, Span nameSpan) {
    StringBuilder sb = new StringBuilder();
    for (int i = nameSpan.getStart(); i < nameSpan.getEnd(); i++) {
      sb.append(tokens[i] + " ");
    }

    return sb.toString().trim();
  }
  
  /**
   * Create a NameSampleDataStream from a corpus with entities annotated but
   * without nameType and validate it.
   * 
   * @throws Exception
   */
  public void testWithoutNameTypes() throws Exception {
    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/AnnotatedSentences.txt");

    String encoding = "ISO-8859-1";

    NameSampleDataStream ds = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in, encoding)));

    NameSample ns = ds.read();

    String[] expectedNames = { "Alan McKennedy", "Julie", "Marie Clara",
        "Stefanie Schmidt", "Mike", "Stefanie Schmidt", "George", "Luise",
        "Alisa Fernandes", "Alisa", "Mike Sander", "Stefan Miller",
        "Stefan Miller", "Stefan Miller", "Elenor Meier", "Gina Schneider",
        "Bruno Schulz", "Michel Seile", "George Miller", "Miller",
        "Peter Schubert", "Natalie" };

    List<String> names = new ArrayList<String>();
    List<Span> spans = new ArrayList<Span>();

    while (ns != null) {
      for (Span nameSpan : ns.getNames()) {
        names.add(sublistToString(ns.getSentence(), nameSpan));
        spans.add(nameSpan);
      }
      ns = ds.read();
    }

    assertEquals(expectedNames.length, names.size());
    assertEquals(new Span(6,8), spans.get(0));
    assertEquals(new Span(3,4), spans.get(1));
    assertEquals(new Span(1,3), spans.get(2));
    assertEquals(new Span(4,6), spans.get(3));
    assertEquals(new Span(1,2), spans.get(4));
    assertEquals(new Span(4,6), spans.get(5));
    assertEquals(new Span(2,3), spans.get(6));
    assertEquals(new Span(16,17), spans.get(7));
    assertEquals(new Span(0,2), spans.get(8));
    assertEquals(new Span(0,1), spans.get(9));
    assertEquals(new Span(3,5), spans.get(10));
    assertEquals(new Span(3,5), spans.get(11));
    assertEquals(new Span(10,12), spans.get(12));
    assertEquals(new Span(1,3), spans.get(13));
    assertEquals(new Span(6,8), spans.get(14));
    assertEquals(new Span(6,8), spans.get(15));
    assertEquals(new Span(8,10), spans.get(16));
    assertEquals(new Span(12,14), spans.get(17));
    assertEquals(new Span(1,3), spans.get(18));
    assertEquals(new Span(0,1), spans.get(19));
    assertEquals(new Span(2,4), spans.get(20));
    assertEquals(new Span(5,6), spans.get(21));
  }

  /**
   * Checks that invalid spans cause an {@link ObjectStreamException} to be thrown.
   */
  public void testWithoutNameTypeAndInvalidData() {
    NameSampleDataStream smapleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START> <START> Name <END>"));
    
    try {
      smapleStream.read();
      fail();
    } catch (ObjectStreamException e) {
    }
    
    smapleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START> Name <END> <END>"));
    
    try {
      smapleStream.read();
      fail();
    } catch (ObjectStreamException e) {
    }
    
    smapleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START> <START> Person <END> Street <END>"));
    
    try {
      smapleStream.read();
      fail();
    } catch (ObjectStreamException e) {
    }
  }
  
  /**
   * Create a NameSampleDataStream from a corpus with entities annotated
   * with multiple nameTypes, like person, date, location and organization, and validate it.
   * 
   * @throws Exception
   */
  public void testWithNameTypes() throws Exception {
    InputStream in = getClass().getClassLoader().getResourceAsStream(
        "opennlp/tools/namefind/voa1.train");

    NameSampleDataStream ds = new NameSampleDataStream(
        new PlainTextByLineStream(new InputStreamReader(in)));
    
    int person = 14;
    int date = 3;
    int location = 17;
    int organization = 1;


    Map<String, List<String>> names = new HashMap<String, List<String>>();

    NameSample ns;
    while ((ns = ds.read()) != null) {
      Span[] nameSpans = ns.getNames();

      for (int i = 0; i < nameSpans.length; i++) {
        if (!names.containsKey(nameSpans[i].getType())) {
          names.put(nameSpans[i].getType(), new ArrayList<String>());
        }
        names.get(nameSpans[i].getType())
            .add(sublistToString(ns.getSentence(), nameSpans[i]));
      }
    }
    
    // TODO: This test should be enhanced like testWithoutNameTypes()
    
    assertEquals(person, names.get("person").size());
    assertEquals(date, names.get("date").size());
    assertEquals(location, names.get("location").size());
    assertEquals(organization, names.get("organization").size());
  }
  
  public void testWithNameTypeAndInvalidData() {
    
    NameSampleDataStream smapleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START:> Name <END>"));
    
    try {
      smapleStream.read();
      fail();
    } catch (ObjectStreamException e) {
    }
    
    smapleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START:street> <START:person> Name <END> <END>"));
    
    try {
      smapleStream.read();
      fail();
    } catch (ObjectStreamException e) {
    }
  }
}
