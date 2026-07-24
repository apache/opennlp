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
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoBoundingBox;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the user-file loader over synthetic customer-site rows: the format positions, the
 * bounding-box and attribute columns, the location fallback, and the fail-loud paths with their
 * line numbers.
 */
public class UserGazetteerTest {

  private static final String FIXTURE = String.join("\n",
      "# customer sites",
      "",
      "plant-3\tAcme Plant 3\tPlant 3|The Plant\t33.75\t-84.39\tUS\tPOI\t0\t\t"
          + "Georgia|Fulton County\taddress=123 Main St, Atlanta GA 30303",
      "campus-e\tAcme Campus East\t\t\t\t\tPOI\t0\t-84.5,33.6,-84.2,33.9",
      "hq\tAcme HQ\t\t48.86\t2.35\tFR\tPOI\t12",
      "") + "\n";

  private static UserGazetteer gazetteer() throws IOException {
    return load(FIXTURE);
  }

  private static UserGazetteer load(String content) throws IOException {
    return UserGazetteer.load(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), "customer");
  }

  @Test
  void testLookupMatchesCanonicalAndAlternateNamesCaseInsensitively() throws IOException {
    final UserGazetteer gazetteer = gazetteer();
    assertEquals("plant-3", gazetteer.lookup("Acme Plant 3").get(0).recordId());
    assertEquals("plant-3", gazetteer.lookup("the plant").get(0).recordId());
    assertEquals("plant-3", gazetteer.lookup("PLANT 3").get(0).recordId());
    assertTrue(gazetteer.lookup("Acme Plant 4").isEmpty());
  }

  @Test
  void testRowComponentsArriveInTheirColumns() throws IOException {
    final GazetteerEntry plant = gazetteer().lookup("Acme Plant 3").get(0);
    assertEquals("customer", plant.source());
    assertEquals(List.of("Plant 3", "The Plant"), plant.alternateNames());
    assertEquals(new GeoPoint(33.75, -84.39), plant.location());
    assertNull(plant.boundingBox());
    assertEquals("US", plant.countryCode());
    assertEquals(GazetteerEntry.FEATURE_CLASS_POI, plant.featureClass());
    assertEquals(0L, plant.population());
    assertEquals(List.of("Georgia", "Fulton County"), plant.containment());
    assertEquals("123 Main St, Atlanta GA 30303", plant.attributes().get("address").value());
    assertEquals("customer", plant.attributes().get("address").source());
  }

  /** A row without coordinates takes its location from the bounding box's center. */
  @Test
  void testBoundingBoxRowDerivesItsLocation() throws IOException {
    final GazetteerEntry campus = gazetteer().lookup("Acme Campus East").get(0);
    assertEquals(new GeoBoundingBox(-84.5, 33.6, -84.2, 33.9), campus.boundingBox());
    assertEquals(new GeoPoint(33.75, -84.35), campus.location());
    assertTrue(campus.boundingBox().contains(campus.location()));
    assertNull(campus.countryCode());
  }

  @Test
  void testByIdIsScopedToTheSourceTag() throws IOException {
    final UserGazetteer gazetteer = gazetteer();
    assertEquals("Acme HQ", gazetteer.byId("customer", "hq").orElseThrow().name());
    assertEquals(Optional.empty(), gazetteer.byId("geonames", "hq"));
    assertEquals(Optional.empty(), gazetteer.byId("customer", "absent"));
    assertEquals(Set.of("customer"), gazetteer.sources());
  }

  @Test
  void testByRegionFindsTheMostPopulousEntry() throws IOException {
    assertEquals("hq", gazetteer().byRegion("fr").orElseThrow().recordId());
    assertEquals(Optional.empty(), gazetteer().byRegion("DE"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "one-coordinate\tHalf\t\t33.0\t\t\t\t\t",
      "no-location\tNowhere",
      "bad-box\tBox\t\t\t\t\t\t\t1.0,2.0,3.0",
      "bad-attribute\tAttr\t\t1.0\t2.0\t\t\t\t\t\tnovalue",
      "bad-attribute\tAttr\t\t1.0\t2.0\t\t\t\t\t\t=value",
      "bad-country\tPlace\t\t1.0\t2.0\tUSA",
      "bad-population\tPlace\t\t1.0\t2.0\t\t\tmany",
      "only-one-column"})
  void testRejectsMalformedRowsWithTheLineNumber(String row) {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> load(row + "\n"));
    assertTrue(e.getMessage().startsWith("line 1 "), e.getMessage());
  }

  @Test
  void testRejectsARepeatedRecordId() {
    final String content = "a\tPlace One\t\t1.0\t2.0\na\tPlace Two\t\t3.0\t4.0\n";
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> load(content));
    assertEquals("line 2 repeats record id: a", e.getMessage());
  }

  @Test
  void testRejectsARepeatedAttributeKey() {
    final String content = "a\tPlace\t\t1.0\t2.0\t\t\t\t\t\tk=1\tk=2\n";
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> load(content));
    assertTrue(e.getMessage().contains("the attribute key repeats: k"), e.getMessage());
  }

  @Test
  void testRejectsContentWithoutRows() {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> load("# only a comment\n\n"));
    assertEquals("the table contains no rows", e.getMessage());
  }

  @Test
  void testRejectsInvalidArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> UserGazetteer.load((InputStream) null, "customer"));
    assertThrows(IllegalArgumentException.class, () -> load(""));
    final InputStream in = new ByteArrayInputStream(new byte[0]);
    assertThrows(IllegalArgumentException.class, () -> UserGazetteer.load(in, null));
    assertThrows(IllegalArgumentException.class, () -> UserGazetteer.load(in, " "));
    assertThrows(IllegalArgumentException.class, () -> gazetteer().lookup(null));
    assertThrows(IllegalArgumentException.class, () -> gazetteer().byId(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> gazetteer().byId("customer", null));
  }

  @Test
  void testLoadsSuppressionRules() throws IOException {
    final String content = String.join("\n",
        "# rules",
        "Mobile\tUS\tCITY",
        "Springfield\tUS",
        "Nowhere",
        "Everywhere\t\tADMIN",
        "") + "\n";
    final List<Suppression> rules = UserGazetteer.loadSuppressions(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    assertEquals(4, rules.size());
    assertEquals(new Suppression("Mobile", "US", "CITY"), rules.get(0));
    assertEquals(new Suppression("Springfield", "US", null), rules.get(1));
    assertEquals(new Suppression("Nowhere"), rules.get(2));
    assertEquals(new Suppression("Everywhere", null, "ADMIN"), rules.get(3));
  }

  @Test
  void testSuppressionFileMayBeEmpty() throws IOException {
    assertEquals(List.of(), UserGazetteer.loadSuppressions(
        new ByteArrayInputStream("# nothing\n".getBytes(StandardCharsets.UTF_8))));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a\tb\tc\td", "\tUS", "name\tUSA"})
  void testRejectsMalformedSuppressionsWithTheLineNumber(String line) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> UserGazetteer.loadSuppressions(
            new ByteArrayInputStream((line + "\n").getBytes(StandardCharsets.UTF_8))));
    assertTrue(e.getMessage().startsWith("line 1 "), e.getMessage());
  }
}
