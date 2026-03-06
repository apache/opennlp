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

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.GermEval2014NameSampleStream.NerLayer;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.Span;

/**
 * Test for the {@link GermEval2014NameSampleStream} class.
 */
public class GermEval2014NameSampleStreamTest extends AbstractSampleStreamTest {

  private static final String SAMPLE = "germeval2014.sample";

  private static final int ALL_TYPES =
      GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES
          | GermEval2014NameSampleStream.GENERATE_ORGANIZATION_ENTITIES
          | GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES
          | GermEval2014NameSampleStream.GENERATE_MISC_ENTITIES;

  @Test
  void testParsingSampleFirstSentence() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      // First sentence: 14 tokens
      Assertions.assertEquals(14, sample.getSentence().length);
      Assertions.assertEquals("Schartau", sample.getSentence()[0]);
      Assertions.assertEquals(".", sample.getSentence()[13]);

      // Comment line means clear adaptive data
      Assertions.assertTrue(sample.isClearAdaptiveDataSet());

      // 4 outer entities: Schartau (PER), Tagesspiegel (ORG), Fischer (PER), Berlin (LOC)
      Assertions.assertEquals(4, sample.getNames().length);

      // Verify Schartau = PER at position 0
      final Span schartau = findSpanAt(sample.getNames(), 0);
      Assertions.assertNotNull(schartau);
      Assertions.assertEquals("person", schartau.getType());
      Assertions.assertEquals(0, schartau.getStart());
      Assertions.assertEquals(1, schartau.getEnd());

      // Verify Tagesspiegel = ORG at position 4
      final Span tagesspiegel = findSpanAt(sample.getNames(), 4);
      Assertions.assertNotNull(tagesspiegel);
      Assertions.assertEquals("organization", tagesspiegel.getType());

      // Verify Fischer = PER at position 9
      final Span fischer = findSpanAt(sample.getNames(), 9);
      Assertions.assertNotNull(fischer);
      Assertions.assertEquals("person", fischer.getType());

      // Verify Berlin = LOC at position 12
      final Span berlin = findSpanAt(sample.getNames(), 12);
      Assertions.assertNotNull(berlin);
      Assertions.assertEquals("location", berlin.getType());
    }
  }

  @Test
  void testOuterLayerEntities() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      // Skip first sentence
      sampleStream.read();
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      // Second sentence: 13 tokens (Bayern München...)
      Assertions.assertEquals(13, sample.getSentence().length);
      Assertions.assertEquals("Bayern", sample.getSentence()[0]);
      Assertions.assertEquals("München", sample.getSentence()[1]);
      Assertions.assertTrue(sample.isClearAdaptiveDataSet());

      // Outer layer: Bayern München (ORG), deutschen (LOCderiv) = 2 spans
      Assertions.assertEquals(2, sample.getNames().length);

      // Bayern München = ORG (0,2)
      final Span org = findSpanAt(sample.getNames(), 0);
      Assertions.assertNotNull(org);
      Assertions.assertEquals("organization", org.getType());
      Assertions.assertEquals(2, org.getEnd());

      // deutschen = LOCderiv (10,11)
      final Span locDeriv = findSpanAt(sample.getNames(), 10);
      Assertions.assertNotNull(locDeriv);
      Assertions.assertEquals("locationderiv", locDeriv.getType());
      Assertions.assertEquals(11, locDeriv.getEnd());
    }
  }

  @Test
  void testInnerLayerEntities() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.INNER)) {
      // Skip first sentence (all inner tags are O)
      final NameSample first = sampleStream.read();
      Assertions.assertNotNull(first);
      Assertions.assertEquals(0, first.getNames().length);

      // Second sentence has inner layer entities
      final NameSample sample = sampleStream.read();
      Assertions.assertNotNull(sample);

      // Inner layer: Bayern (LOC), München (LOC) = 2 spans
      Assertions.assertEquals(2, sample.getNames().length);

      final Span bayernLoc = findSpanAt(sample.getNames(), 0);
      Assertions.assertNotNull(bayernLoc);
      Assertions.assertEquals("location", bayernLoc.getType());
      Assertions.assertEquals(1, bayernLoc.getEnd());

      final Span muenchenLoc = findSpanAt(sample.getNames(), 1);
      Assertions.assertNotNull(muenchenLoc);
      Assertions.assertEquals("location", muenchenLoc.getType());
      Assertions.assertEquals(2, muenchenLoc.getEnd());
    }
  }

  @Test
  void testMiscEntityType() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      sampleStream.read(); // skip 1st
      sampleStream.read(); // skip 2nd
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      // Third sentence: "Ecce homo ist ein Werk ."
      Assertions.assertEquals(6, sample.getSentence().length);
      Assertions.assertFalse(sample.isClearAdaptiveDataSet());

      // Ecce homo = OTH -> misc
      Assertions.assertEquals(1, sample.getNames().length);
      final Span oth = sample.getNames()[0];
      Assertions.assertEquals("misc", oth.getType());
      Assertions.assertEquals(0, oth.getStart());
      Assertions.assertEquals(2, oth.getEnd());
    }
  }

  @Test
  void testPartEntityType() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      sampleStream.read(); // skip 1st
      sampleStream.read(); // skip 2nd
      sampleStream.read(); // skip 3rd
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      // Fourth sentence: "ARD-Programmchef Volker Herres sagte ."
      Assertions.assertEquals(5, sample.getSentence().length);

      // ARD-Programmchef = ORGpart, Volker Herres = PER
      Assertions.assertEquals(2, sample.getNames().length);

      final Span orgPart = findSpanAt(sample.getNames(), 0);
      Assertions.assertNotNull(orgPart);
      Assertions.assertEquals("organizationpart", orgPart.getType());
      Assertions.assertEquals(1, orgPart.getEnd());

      final Span person = findSpanAt(sample.getNames(), 1);
      Assertions.assertNotNull(person);
      Assertions.assertEquals("person", person.getType());
      Assertions.assertEquals(3, person.getEnd());
    }
  }

  @Test
  void testStreamExhaustion() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      sampleStream.read(); // 1st
      sampleStream.read(); // 2nd
      sampleStream.read(); // 3rd
      sampleStream.read(); // 4th
      Assertions.assertNull(sampleStream.read()); // end of stream
    }
  }

  @Test
  void testFilterPersonEntitiesOnly() throws IOException {
    try (final ObjectStream<NameSample> sampleStream =
             openData(GermEval2014NameSampleStream.GENERATE_PERSON_ENTITIES, NerLayer.OUTER)) {
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      // Only PER entities from first sentence: Schartau, Fischer
      Assertions.assertEquals(2, sample.getNames().length);
      for (final Span name : sample.getNames()) {
        Assertions.assertTrue(name.getType().startsWith("person"));
      }
    }
  }

  @Test
  void testFilterLocationEntitiesOnly() throws IOException {
    try (final ObjectStream<NameSample> sampleStream =
             openData(GermEval2014NameSampleStream.GENERATE_LOCATION_ENTITIES, NerLayer.OUTER)) {
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      // Only LOC entities from first sentence: Berlin
      Assertions.assertEquals(1, sample.getNames().length);
      Assertions.assertEquals("location", sample.getNames()[0].getType());
    }
  }

  @Test
  void testFilterNoEntities() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(0, NerLayer.OUTER)) {
      final NameSample sample = sampleStream.read();

      Assertions.assertNotNull(sample);
      Assertions.assertEquals(0, sample.getNames().length);
    }
  }

  @Test
  void testReset() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      final NameSample sample = sampleStream.read();
      sampleStream.reset();

      Assertions.assertEquals(sample, sampleStream.read());
    }
  }

  @Test
  void testDocumentBoundaryClearsAdaptiveData() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      final NameSample first = sampleStream.read();
      Assertions.assertTrue(first.isClearAdaptiveDataSet()); // has # comment

      final NameSample second = sampleStream.read();
      Assertions.assertTrue(second.isClearAdaptiveDataSet()); // has # comment

      final NameSample third = sampleStream.read();
      Assertions.assertFalse(third.isClearAdaptiveDataSet()); // no # comment

      final NameSample fourth = sampleStream.read();
      Assertions.assertFalse(fourth.isClearAdaptiveDataSet()); // no # comment
    }
  }

  @Test
  void testAllEntityTypesPresent() throws IOException {
    try (final ObjectStream<NameSample> sampleStream = openData(ALL_TYPES, NerLayer.OUTER)) {
      final Set<String> foundTypes = new HashSet<>();
      NameSample sample;
      while ((sample = sampleStream.read()) != null) {
        for (final Span name : sample.getNames()) {
          foundTypes.add(name.getType());
        }
      }
      // Should find: person, organization, location, locationderiv, misc, organizationpart
      Assertions.assertTrue(foundTypes.containsAll(
          Arrays.asList("person", "organization", "location", "misc")));
    }
  }

  private ObjectStream<NameSample> openData(final int types, final NerLayer layer)
      throws IOException {
    return new GermEval2014NameSampleStream(getFactory(SAMPLE), types, layer);
  }

  private Span findSpanAt(final Span[] spans, final int start) {
    for (final Span span : spans) {
      if (span.getStart() == start) {
        return span;
      }
    }
    return null;
  }
}
