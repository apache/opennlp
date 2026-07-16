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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.geo.GazetteerEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the GeoNames main-format loader against a small project-authored fixture; the
 * rows are synthetic and carry no data from the publisher.
 */
public class GeoNamesGazetteerTest {

  private static String row(String id, String name, String ascii, String alternates,
      String lat, String lon, String featureClass, String country, String population) {
    return String.join("\t", id, name, ascii, alternates, lat, lon, featureClass,
        "XXX", country, "", "", "", "", "", population, "", "", "Etc/UTC", "2026-01-01");
  }

  private static final String FIXTURE = String.join("\n",
      row("1", "München", "Munchen", "Munich,Monaco di Baviera", "48.14", "11.58",
          "P", "DE", "1500000"),
      row("2", "Paris", "Paris", "Lutetia", "48.85", "2.35", "P", "FR", "2100000"),
      row("3", "Paris", "Paris", "", "33.66", "-95.56", "P", "US", "25000"),
      row("4", "Texas", "Texas", "", "31.25", "-99.25", "A", "US", "29000000"),
      "") + "\n";

  private static GeoNamesGazetteer gazetteer() throws IOException {
    return GeoNamesGazetteer.load(
        new ByteArrayInputStream(FIXTURE.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void testLookupMatchesCanonicalAsciiAndAlternateNames() throws IOException {
    final GeoNamesGazetteer gazetteer = gazetteer();
    assertEquals("1", gazetteer.lookup("München").get(0).recordId());
    assertEquals("1", gazetteer.lookup("munchen").get(0).recordId());
    assertEquals("1", gazetteer.lookup("MUNICH").get(0).recordId());
    assertEquals("1", gazetteer.lookup("Monaco di Baviera").get(0).recordId());
    assertTrue(gazetteer.lookup("Springfield").isEmpty());
  }

  @Test
  void testCandidatesAreRankedByPopulation() throws IOException {
    final List<GazetteerEntry> candidates = gazetteer().lookup("Paris");
    assertEquals(2, candidates.size());
    assertEquals("FR", candidates.get(0).countryCode());
    assertEquals("US", candidates.get(1).countryCode());
  }

  @Test
  void testFeatureClassMapping() throws IOException {
    assertEquals(GazetteerEntry.FEATURE_CLASS_CITY,
        gazetteer().lookup("Paris").get(0).featureClass());
    assertEquals(GazetteerEntry.FEATURE_CLASS_ADMIN,
        gazetteer().lookup("Texas").get(0).featureClass());
  }

  @Test
  void testByIdAndByRegionAndSources() throws IOException {
    final GeoNamesGazetteer gazetteer = gazetteer();
    assertEquals("Paris", gazetteer.byId(GeoNamesGazetteer.SOURCE, "2").get().name());
    assertTrue(gazetteer.byId("elsewhere", "2").isEmpty());
    // the most populous entry represents the region
    assertEquals("Texas", gazetteer.byRegion("us").get().name());
    assertEquals(Set.of(GeoNamesGazetteer.SOURCE), gazetteer.sources());
  }

  @Test
  void testByRegionUnknownCodeReturnsEmpty() throws IOException {
    // well-formed but absent from the fixture
    assertTrue(gazetteer().byRegion("XX").isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "C", "CHE", "C1"})
  void testByRegionMalformedCodeFailsLoud(String malformed) throws IOException {
    final GeoNamesGazetteer gazetteer = gazetteer();
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byRegion(malformed));
  }

  @ParameterizedTest
  @MethodSource("malformedContent")
  void testMalformedContentFailsLoud(String content) {
    assertThrows(IllegalArgumentException.class, () -> GeoNamesGazetteer.load(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
  }

  private static List<String> malformedContent() {
    return List.of(
        "too\tfew\tcolumns\n",
        row("5", "Nowhere", "Nowhere", "", "not-a-lat", "0", "P", "DE", "1") + "\n",
        "");
  }

  @Test
  void testNullStreamFailsLoud() {
    assertThrows(IllegalArgumentException.class,
        () -> GeoNamesGazetteer.load((InputStream) null));
  }

  @Test
  void testLookupValidation() throws IOException {
    final GeoNamesGazetteer gazetteer = gazetteer();
    assertThrows(IllegalArgumentException.class, () -> gazetteer.lookup(null));
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byId(null, "1"));
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byRegion(null));
  }
}
