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
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.GazetteerEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the derived-division loader against a small project-authored fixture; the rows
 * are synthetic and carry no data from the publisher.
 */
public class OvertureGazetteerTest {

  private static String row(String id, String name, String alternates, String lat,
      String lon, String country, String subtype, String population) {
    return String.join("\t", id, name, alternates, lat, lon, country, subtype, population);
  }

  private static final String FIXTURE = String.join("\n",
      "# synthetic fixture in the derived division format",
      row("d1", "Australia", "AU,Commonwealth of Australia", "-25.0", "134.0",
          "AU", "country", "26000000"),
      row("d2", "Bavaria", "Bayern", "48.9", "11.4", "DE", "region", "13000000"),
      row("d3", "Sydney", "", "-33.87", "151.21", "AU", "locality", "5300000"),
      row("d4", "Newtown", "", "-33.90", "151.18", "AU", "neighborhood", "15000"),
      "") + "\n";

  private static OvertureGazetteer gazetteer() throws IOException {
    return OvertureGazetteer.load(new ByteArrayInputStream(FIXTURE.getBytes()));
  }

  @Test
  void testCountryAndRegionNamesResolve() throws IOException {
    final OvertureGazetteer gazetteer = gazetteer();
    assertEquals("AU", gazetteer.lookup("Australia").get(0).countryCode());
    assertEquals("d2", gazetteer.lookup("bayern").get(0).recordId());
    assertEquals(GazetteerEntry.FEATURE_CLASS_ADMIN,
        gazetteer.lookup("Bavaria").get(0).featureClass());
  }

  @Test
  void testSubtypeMapping() throws IOException {
    final OvertureGazetteer gazetteer = gazetteer();
    assertEquals(GazetteerEntry.FEATURE_CLASS_CITY,
        gazetteer.lookup("Sydney").get(0).featureClass());
    assertEquals(GazetteerEntry.FEATURE_CLASS_POI,
        gazetteer.lookup("Newtown").get(0).featureClass());
  }

  @Test
  void testAlternateNamesAndRanking() throws IOException {
    final List<GazetteerEntry> found = gazetteer().lookup("Commonwealth of Australia");
    assertEquals("d1", found.get(0).recordId());
  }

  @Test
  void testByIdByRegionAndSources() throws IOException {
    final OvertureGazetteer gazetteer = gazetteer();
    assertEquals("Sydney", gazetteer.byId(OvertureGazetteer.SOURCE, "d3").get().name());
    assertTrue(gazetteer.byId("geonames", "d3").isEmpty());
    // the most populous entry represents the region
    assertEquals("Australia", gazetteer.byRegion("au").get().name());
    assertEquals(java.util.Set.of(OvertureGazetteer.SOURCE), gazetteer.sources());
  }

  @Test
  void testMalformedContentFailsLoud() {
    assertThrows(IllegalArgumentException.class, () -> OvertureGazetteer.load(
        new ByteArrayInputStream("too\tfew\n".getBytes())));
    assertThrows(IllegalArgumentException.class, () -> OvertureGazetteer.load(
        new ByteArrayInputStream(
            (row("d9", "Nowhere", "", "not-a-lat", "0", "DE", "region", "1") + "\n")
                .getBytes())));
    assertThrows(IllegalArgumentException.class, () -> OvertureGazetteer.load(
        new ByteArrayInputStream("# only a header\n".getBytes())));
    assertThrows(IllegalArgumentException.class,
        () -> OvertureGazetteer.load((InputStream) null));
  }

  @Test
  void testLookupValidation() throws IOException {
    final OvertureGazetteer gazetteer = gazetteer();
    assertThrows(IllegalArgumentException.class, () -> gazetteer.lookup(null));
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byId(null, "d1"));
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byRegion(null));
  }
}
