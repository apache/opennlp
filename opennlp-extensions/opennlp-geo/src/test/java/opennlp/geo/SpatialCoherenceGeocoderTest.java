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

package opennlp.geo;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;
import opennlp.tools.geo.GeoResolution;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that spatial coherence moves an ambiguous mention toward the document's other
 * mentions, while lone mentions keep the population prior.
 */
public class SpatialCoherenceGeocoderTest {

  private static GazetteerEntry place(String id, String name, double lat, double lon,
      String country, long population) {
    return new GazetteerEntry("test", id, name, List.of(), new GeoPoint(lat, lon),
        country, List.of(), population, GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
  }

  private static final BundledGazetteer GAZETTEER = BundledGazetteer.fromEntries(List.of(
      place("paris-fr", "Paris", 48.85, 2.35, "FR", 2_100_000),
      place("paris-tx", "Paris", 33.66, -95.56, "US", 25_000),
      place("dallas", "Dallas", 32.78, -96.80, "US", 1_300_000),
      place("houston", "Houston", 29.76, -95.36, "US", 2_300_000),
      place("london", "London", 51.51, -0.13, "GB", 8_800_000)));

  private final SpatialCoherenceGeocoder geocoder = new SpatialCoherenceGeocoder(GAZETTEER);

  private static List<Span> mentionsOf(String text, String... names) {
    return Arrays.stream(names)
        .map(name -> new Span(text.indexOf(name), text.indexOf(name) + name.length()))
        .toList();
  }

  @Test
  void testContextMovesParisToTexas() throws IOException {
    final String text = "the tour hits Dallas, Paris and Houston";
    final List<GeoResolution> resolutions =
        geocoder.resolve(text, mentionsOf(text, "Dallas", "Paris", "Houston"));
    assertEquals(3, resolutions.size());
    assertEquals("paris-tx", resolutions.get(1).entry().recordId());
    assertTrue(resolutions.get(1).confidence() > 0.5);
  }

  @Test
  void testLoneParisKeepsThePopulationPrior() throws IOException {
    final String text = "a week in Paris";
    final List<GeoResolution> resolutions =
        geocoder.resolve(text, mentionsOf(text, "Paris"));
    assertEquals(1, resolutions.size());
    assertEquals("paris-fr", resolutions.get(0).entry().recordId());
  }

  @Test
  void testEuropeanContextKeepsParisInFrance() throws IOException {
    final String text = "flights between London and Paris";
    final List<GeoResolution> resolutions =
        geocoder.resolve(text, mentionsOf(text, "London", "Paris"));
    assertEquals("paris-fr", resolutions.get(1).entry().recordId());
  }

  @Test
  void testUnresolvedMentionsAreOmitted() throws IOException {
    final String text = "Dallas and Atlantis";
    final List<GeoResolution> resolutions =
        geocoder.resolve(text, mentionsOf(text, "Dallas", "Atlantis"));
    assertEquals(1, resolutions.size());
    assertEquals("dallas", resolutions.get(0).entry().recordId());
    assertEquals(0.9d, resolutions.get(0).confidence());
  }

  @Test
  void testValidation() {
    assertThrows(IllegalArgumentException.class, () -> new SpatialCoherenceGeocoder(null));
    assertThrows(IllegalArgumentException.class, () -> geocoder.resolve(null, List.of()));
    assertThrows(IllegalArgumentException.class, () -> geocoder.resolve("x", null));
    assertThrows(IllegalArgumentException.class,
        () -> geocoder.resolve("x", List.of(new Span(0, 5))));
  }
}
