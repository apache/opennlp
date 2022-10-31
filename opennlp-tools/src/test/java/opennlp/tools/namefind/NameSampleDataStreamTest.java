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

import java.io.IOException;
import java.io.ObjectStreamException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import opennlp.tools.formats.ResourceAsStreamFactory;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MockInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is the test class for {@link NameSampleDataStream}..
 */
public class NameSampleDataStreamTest {

  private static final String person = "person";
  private static final String date = "date";
  private static final String location = "location";
  private static final String organization = "organization";

  /**
   * Create a string from a array section.
   *
   * @param tokens the tokens
   * @param nameSpan the section
   * @return the string
   */
  private static String sublistToString(String[] tokens, Span nameSpan) {
    StringBuilder sb = new StringBuilder();
    for (int i = nameSpan.getStart(); i < nameSpan.getEnd(); i++) {
      sb.append(tokens[i]).append(" ");
    }

    return sb.toString().trim();
  }

  /**
   * Create a NameSampleDataStream from a corpus with entities annotated but
   * without nameType and validate it.
   *
   * @throws Exception
   */
  @Test
  public void testWithoutNameTypes() throws Exception {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/namefind/AnnotatedSentences.txt");

    NameSampleDataStream ds = new NameSampleDataStream(
        new PlainTextByLineStream(in, StandardCharsets.ISO_8859_1));

    NameSample ns = ds.read();

    String[] expectedNames = { "Alan McKennedy", "Julie", "Marie Clara",
        "Stefanie Schmidt", "Mike", "Stefanie Schmidt", "George", "Luise",
        "George Bauer", "Alisa Fernandes", "Alisa", "Mike Sander",
        "Stefan Miller", "Stefan Miller", "Stefan Miller", "Elenor Meier",
        "Gina Schneider", "Bruno Schulz", "Michel Seile", "George Miller",
        "Miller", "Peter Schubert", "Natalie" };

    List<String> names = new ArrayList<>();
    List<Span> spans = new ArrayList<>();

    while (ns != null) {
      for (Span nameSpan : ns.getNames()) {
        names.add(sublistToString(ns.getSentence(), nameSpan));
        spans.add(nameSpan);
      }
      ns = ds.read();
    }

    ds.close();

    assertEquals(expectedNames.length, names.size());
    assertEquals(createDefaultSpan(6,8), spans.get(0));
    assertEquals(createDefaultSpan(3,4), spans.get(1));
    assertEquals(createDefaultSpan(1,3), spans.get(2));
    assertEquals(createDefaultSpan(4,6), spans.get(3));
    assertEquals(createDefaultSpan(1,2), spans.get(4));
    assertEquals(createDefaultSpan(4,6), spans.get(5));
    assertEquals(createDefaultSpan(2,3), spans.get(6));
    assertEquals(createDefaultSpan(16,17), spans.get(7));
    assertEquals(createDefaultSpan(18,20), spans.get(8));
    assertEquals(createDefaultSpan(0,2), spans.get(9));
    assertEquals(createDefaultSpan(0,1), spans.get(10));
    assertEquals(createDefaultSpan(3,5), spans.get(11));
    assertEquals(createDefaultSpan(3,5), spans.get(12));
    assertEquals(createDefaultSpan(10,12), spans.get(13));
    assertEquals(createDefaultSpan(1,3), spans.get(14));
    assertEquals(createDefaultSpan(6,8), spans.get(15));
    assertEquals(createDefaultSpan(6,8), spans.get(16));
    assertEquals(createDefaultSpan(8,10), spans.get(17));
    assertEquals(createDefaultSpan(12,14), spans.get(18));
    assertEquals(createDefaultSpan(1,3), spans.get(19));
    assertEquals(createDefaultSpan(0,1), spans.get(20));
    assertEquals(createDefaultSpan(2,4), spans.get(21));
    assertEquals(createDefaultSpan(5,6), spans.get(22));
  }

  private Span createDefaultSpan(int s, int e) {
    return new Span(s, e, NameSample.DEFAULT_TYPE);
  }

  /**
   * Checks that invalid spans cause an {@link ObjectStreamException} to be thrown.
   */
  @Test
  public void testWithoutNameTypeAndInvalidData() {

    try (NameSampleDataStream sampleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START> <START> Name <END>"))) {
      sampleStream.read();
      fail();
    } catch (IOException expected) {
      // the read above is expected to throw an exception
    }

    try (NameSampleDataStream sampleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START> Name <END> <END>"))) {
      sampleStream.read();
      fail();
    } catch (IOException expected) {
      // the read above is expected to throw an exception
    }

    try (NameSampleDataStream sampleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream(
            "<START> <START> Person <END> Street <END>"))) {
      sampleStream.read();
      fail();
    } catch (IOException expected) {
      // the read above is expected to throw an exception
    }
  }

  /**
   * Create a NameSampleDataStream from a corpus with entities annotated
   * with multiple nameTypes, like person, date, location and organization, and validate it.
   *
   * @throws Exception
   */
  @Test
  public void testWithNameTypes() throws Exception {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/namefind/voa1.train");

    NameSampleDataStream ds = new NameSampleDataStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

    Map<String, List<String>> names = new HashMap<>();
    Map<String, List<Span>> spans = new HashMap<>();

    NameSample ns;
    while ((ns = ds.read()) != null) {
      Span[] nameSpans = ns.getNames();

      for (Span nameSpan : nameSpans) {
        if (!names.containsKey(nameSpan.getType())) {
          names.put(nameSpan.getType(), new ArrayList<>());
          spans.put(nameSpan.getType(), new ArrayList<>());
        }
        names.get(nameSpan.getType()).add(sublistToString(ns.getSentence(), nameSpan));
        spans.get(nameSpan.getType()).add(nameSpan);
      }
    }
    ds.close();

    String[] expectedPerson = { "Barack Obama", "Obama", "Obama",
        "Lee Myung - bak", "Obama", "Obama", "Scott Snyder", "Snyder", "Obama",
        "Obama", "Obama", "Tim Peters", "Obama", "Peters" };

    String[] expectedDate = { "Wednesday", "Thursday", "Wednesday" };

    String[] expectedLocation = { "U . S .", "South Korea", "North Korea",
        "China", "South Korea", "North Korea", "North Korea", "U . S .",
        "South Korea", "United States", "Pyongyang", "North Korea",
        "South Korea", "Afghanistan", "Seoul", "U . S .", "China" };

    String[] expectedOrganization = {"Center for U . S . Korea Policy"};

    assertEquals(expectedPerson.length, names.get(person).size());
    assertEquals(expectedDate.length, names.get(date).size());
    assertEquals(expectedLocation.length, names.get(location).size());
    assertEquals(expectedOrganization.length, names.get(organization).size());

    assertEquals(new Span(5,7, person), spans.get(person).get(0));
    assertEquals(expectedPerson[0], names.get(person).get(0));
    assertEquals(new Span(10,11, person), spans.get(person).get(1));
    assertEquals(expectedPerson[1], names.get(person).get(1));
    assertEquals(new Span(29,30, person), spans.get(person).get(2));
    assertEquals(expectedPerson[2], names.get(person).get(2));
    assertEquals(new Span(23,27, person), spans.get(person).get(3));
    assertEquals(expectedPerson[3], names.get(person).get(3));
    assertEquals(new Span(1,2, person), spans.get(person).get(4));
    assertEquals(expectedPerson[4], names.get(person).get(4));
    assertEquals(new Span(8,9, person), spans.get(person).get(5));
    assertEquals(expectedPerson[5], names.get(person).get(5));
    assertEquals(new Span(0,2, person), spans.get(person).get(6));
    assertEquals(expectedPerson[6], names.get(person).get(6));
    assertEquals(new Span(25,26, person), spans.get(person).get(7));
    assertEquals(expectedPerson[7], names.get(person).get(7));
    assertEquals(new Span(1,2, person), spans.get(person).get(8));
    assertEquals(expectedPerson[8], names.get(person).get(8));
    assertEquals(new Span(6,7, person), spans.get(person).get(9));
    assertEquals(expectedPerson[9], names.get(person).get(9));
    assertEquals(new Span(14,15, person), spans.get(person).get(10));
    assertEquals(expectedPerson[10], names.get(person).get(10));
    assertEquals(new Span(0,2, person), spans.get(person).get(11));
    assertEquals(expectedPerson[11], names.get(person).get(11));
    assertEquals(new Span(12,13, person), spans.get(person).get(12));
    assertEquals(expectedPerson[12], names.get(person).get(12));
    assertEquals(new Span(12,13, person), spans.get(person).get(13));
    assertEquals(expectedPerson[13], names.get(person).get(13));

    assertEquals(new Span(7,8, date), spans.get(date).get(0));
    assertEquals(expectedDate[0], names.get(date).get(0));
    assertEquals(new Span(27,28, date), spans.get(date).get(1));
    assertEquals(expectedDate[1], names.get(date).get(1));
    assertEquals(new Span(15,16, date), spans.get(date).get(2));
    assertEquals(expectedDate[2], names.get(date).get(2));

    assertEquals(new Span(0, 4, location), spans.get(location).get(0));
    assertEquals(expectedLocation[0], names.get(location).get(0));
    assertEquals(new Span(10,12, location), spans.get(location).get(1));
    assertEquals(expectedLocation[1], names.get(location).get(1));
    assertEquals(new Span(28,30, location), spans.get(location).get(2));
    assertEquals(expectedLocation[2], names.get(location).get(2));
    assertEquals(new Span(3,4, location), spans.get(location).get(3));
    assertEquals(expectedLocation[3], names.get(location).get(3));
    assertEquals(new Span(5,7, location), spans.get(location).get(4));
    assertEquals(expectedLocation[4], names.get(location).get(4));
    assertEquals(new Span(16,18, location), spans.get(location).get(5));
    assertEquals(expectedLocation[5], names.get(location).get(5));
    assertEquals(new Span(1,3, location), spans.get(location).get(6));
    assertEquals(expectedLocation[6], names.get(location).get(6));
    assertEquals(new Span(5,9, location), spans.get(location).get(7));
    assertEquals(expectedLocation[7], names.get(location).get(7));
    assertEquals(new Span(0,2, location), spans.get(location).get(8));
    assertEquals(expectedLocation[8], names.get(location).get(8));
    assertEquals(new Span(4,6, location), spans.get(location).get(9));
    assertEquals(expectedLocation[9], names.get(location).get(9));
    assertEquals(new Span(10,11, location), spans.get(location).get(10));
    assertEquals(expectedLocation[10], names.get(location).get(10));
    assertEquals(new Span(6,8, location), spans.get(location).get(11));
    assertEquals(expectedLocation[11], names.get(location).get(11));
    assertEquals(new Span(4,6, location), spans.get(location).get(12));
    assertEquals(expectedLocation[12], names.get(location).get(12));
    assertEquals(new Span(10,11, location), spans.get(location).get(13));
    assertEquals(expectedLocation[13], names.get(location).get(13));
    assertEquals(new Span(12,13, location), spans.get(location).get(14));
    assertEquals(expectedLocation[14], names.get(location).get(14));
    assertEquals(new Span(5,9, location), spans.get(location).get(15));
    assertEquals(expectedLocation[15], names.get(location).get(15));
    assertEquals(new Span(11,12, location), spans.get(location).get(16));
    assertEquals(expectedLocation[16], names.get(location).get(16));

    assertEquals(new Span(7,15, organization), spans.get(organization).get(0));
    assertEquals(expectedOrganization[0], names.get(organization).get(0));

  }

  @Test
  public void testWithNameTypeAndInvalidData() {

    try (NameSampleDataStream sampleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream("<START:> Name <END>"))) {
      sampleStream.read();
      fail();
    } catch (IOException expected) {
      // the read above is expected to throw an exception
    }

    try (NameSampleDataStream sampleStream = new NameSampleDataStream(
        ObjectStreamUtils.createObjectStream(
            "<START:street> <START:person> Name <END> <END>"))) {
      sampleStream.read();
      fail();
    } catch (IOException expected) {
      // the read above is expected to throw an exception
    }
  }

  @Test
  public void testClearAdaptiveData() throws IOException {
    String trainingData = "a\n" +
        "b\n" +
        "c\n" +
        "\n" +
        "d\n";

    ObjectStream<String> untokenizedLineStream = new PlainTextByLineStream(
        new MockInputStreamFactory(trainingData), StandardCharsets.UTF_8);

    ObjectStream<NameSample> trainingStream = new NameSampleDataStream(untokenizedLineStream);

    assertFalse(trainingStream.read().isClearAdaptiveDataSet());
    assertFalse(trainingStream.read().isClearAdaptiveDataSet());
    assertFalse(trainingStream.read().isClearAdaptiveDataSet());
    assertTrue(trainingStream.read().isClearAdaptiveDataSet());
    assertNull(trainingStream.read());

    trainingStream.close();
  }

  @Test
  public void testHtmlNameSampleParsing() throws IOException {
    InputStreamFactory in = new ResourceAsStreamFactory(getClass(),
        "/opennlp/tools/namefind/html1.train");

    NameSampleDataStream ds = new NameSampleDataStream(
        new PlainTextByLineStream(in, StandardCharsets.UTF_8));

    NameSample ns = ds.read();

    assertEquals(1, ns.getSentence().length);
    assertEquals("<html>", ns.getSentence()[0]);

    ns = ds.read();
    assertEquals(1, ns.getSentence().length);
    assertEquals("<head/>", ns.getSentence()[0]);

    ns = ds.read();
    assertEquals(1, ns.getSentence().length);
    assertEquals("<body>", ns.getSentence()[0]);

    ns = ds.read();
    assertEquals(1, ns.getSentence().length);
    assertEquals("<ul>", ns.getSentence()[0]);

    // <li> <START:organization> Advanced Integrated Pest Management <END> </li>
    ns = ds.read();
    assertEquals(6, ns.getSentence().length);
    assertEquals("<li>", ns.getSentence()[0]);
    assertEquals("Advanced", ns.getSentence()[1]);
    assertEquals("Integrated", ns.getSentence()[2]);
    assertEquals("Pest", ns.getSentence()[3]);
    assertEquals("Management", ns.getSentence()[4]);
    assertEquals("</li>", ns.getSentence()[5]);
    assertEquals(new Span(1, 5, organization), ns.getNames()[0]);

    // <li> <START:organization> Bay Cities Produce Co., Inc. <END> </li>
    ns = ds.read();
    assertEquals(7, ns.getSentence().length);
    assertEquals("<li>", ns.getSentence()[0]);
    assertEquals("Bay", ns.getSentence()[1]);
    assertEquals("Cities", ns.getSentence()[2]);
    assertEquals("Produce", ns.getSentence()[3]);
    assertEquals("Co.,", ns.getSentence()[4]);
    assertEquals("Inc.", ns.getSentence()[5]);
    assertEquals("</li>", ns.getSentence()[6]);
    assertEquals(new Span(1, 6, organization), ns.getNames()[0]);

    ns = ds.read();
    assertEquals(1, ns.getSentence().length);
    assertEquals("</ul>", ns.getSentence()[0]);

    ns = ds.read();
    assertEquals(1, ns.getSentence().length);
    assertEquals("</body>", ns.getSentence()[0]);

    ns = ds.read();
    assertEquals(1, ns.getSentence().length);
    assertEquals("</html>", ns.getSentence()[0]);

    assertNull(ds.read());

    ds.close();
  }
}
