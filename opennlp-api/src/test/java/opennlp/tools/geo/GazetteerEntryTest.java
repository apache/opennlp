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
package opennlp.tools.geo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GazetteerEntryTest {

  private static final GeoPoint TOKYO = new GeoPoint(35.6839, 139.7744);

  private static GazetteerEntry entry(String source, String recordId, String name,
                                      List<String> alternateNames, GeoPoint location,
                                      String countryCode, List<String> containment,
                                      long population, String featureClass,
                                      Map<String, AttributeValue> attributes) {
    return new GazetteerEntry(source, recordId, name, alternateNames, location, countryCode,
        containment, population, featureClass, attributes);
  }

  private static GazetteerEntry validEntry() {
    return entry("naturalearth", "1159151479", "Tokyo", List.of("Tokio"), TOKYO, "JP",
        List.of("Tokyo"), 37977000L, "CITY", Map.of());
  }

  @Test
  void testHoldsComponents() {
    final GazetteerEntry e = validEntry();
    assertEquals("naturalearth", e.source());
    assertEquals("1159151479", e.recordId());
    assertEquals("Tokyo", e.name());
    assertEquals(List.of("Tokio"), e.alternateNames());
    assertEquals(TOKYO, e.location());
    assertEquals("JP", e.countryCode());
    assertEquals(List.of("Tokyo"), e.containment());
    assertEquals(37977000L, e.population());
    assertEquals("CITY", e.featureClass());
    assertEquals(Map.of(), e.attributes());
  }

  @Test
  void testAcceptsAbsentOptionalComponents() {
    final GazetteerEntry e = entry("s", "1", "Nowhere", List.of(), TOKYO, null, List.of(), 0L,
        null, Map.of());
    assertNull(e.countryCode());
    assertNull(e.featureClass());
    assertEquals(0L, e.population());
    assertTrue(e.alternateNames().isEmpty());
    assertTrue(e.containment().isEmpty());
  }

  /**
   * Asserts that the ten-argument constructor yields an entry without a bounding box and the
   * canonical constructor carries one, usable for containment checks against the location.
   */
  @Test
  void testBoundingBoxIsOptionalAndCarried() {
    assertNull(validEntry().boundingBox());
    final GeoBoundingBox box = new GeoBoundingBox(138.9, 35.0, 140.9, 36.1);
    final GazetteerEntry e = new GazetteerEntry("naturalearth", "1159151479", "Tokyo",
        List.of(), TOKYO, box, "JP", List.of(), 37977000L, "CITY", Map.of());
    assertEquals(box, e.boundingBox());
    assertTrue(e.boundingBox().contains(e.location()));
  }

  @Test
  void testDefensivelyCopiesCollections() {
    final List<String> alternateNames = new ArrayList<>(Arrays.asList("Tokio"));
    final List<String> containment = new ArrayList<>(Arrays.asList("Tokyo"));
    final Map<String, AttributeValue> attributes = new HashMap<>();
    attributes.put("adm1", new AttributeValue("Tokyo-to", "naturalearth", ""));
    final GazetteerEntry e = entry("s", "1", "Tokyo", alternateNames, TOKYO, "JP", containment,
        1L, "CITY", attributes);

    alternateNames.add("mutated");
    containment.add("mutated");
    attributes.put("mutated", new AttributeValue("v", "s", ""));

    assertEquals(List.of("Tokio"), e.alternateNames());
    assertEquals(List.of("Tokyo"), e.containment());
    assertEquals(1, e.attributes().size());
    assertThrows(UnsupportedOperationException.class, () -> e.alternateNames().add("x"));
    assertThrows(UnsupportedOperationException.class, () -> e.containment().add("x"));
    assertThrows(UnsupportedOperationException.class,
        () -> e.attributes().put("x", new AttributeValue("v", "s", "")));
  }

  @Test
  void testRejectsNullOrEmptySource() {
    assertMessage("Source must not be null or empty", () -> entry(null, "1", "n", List.of(),
        TOKYO, null, List.of(), 0L, null, Map.of()));
    assertMessage("Source must not be null or empty", () -> entry("", "1", "n", List.of(),
        TOKYO, null, List.of(), 0L, null, Map.of()));
  }

  @Test
  void testRejectsNullOrEmptyRecordId() {
    assertMessage("RecordId must not be null or empty", () -> entry("s", null, "n", List.of(),
        TOKYO, null, List.of(), 0L, null, Map.of()));
    assertMessage("RecordId must not be null or empty", () -> entry("s", "", "n", List.of(),
        TOKYO, null, List.of(), 0L, null, Map.of()));
  }

  @Test
  void testRejectsNullOrEmptyName() {
    assertMessage("Name must not be null or empty", () -> entry("s", "1", null, List.of(),
        TOKYO, null, List.of(), 0L, null, Map.of()));
    assertMessage("Name must not be null or empty", () -> entry("s", "1", "", List.of(),
        TOKYO, null, List.of(), 0L, null, Map.of()));
  }

  @Test
  void testRejectsBadAlternateNames() {
    assertMessage("AlternateNames must not be null", () -> entry("s", "1", "n", null,
        TOKYO, null, List.of(), 0L, null, Map.of()));
    assertMessage("AlternateNames must not contain a null or empty element",
        () -> entry("s", "1", "n", Arrays.asList("ok", null), TOKYO, null, List.of(), 0L, null,
            Map.of()));
    assertMessage("AlternateNames must not contain a null or empty element",
        () -> entry("s", "1", "n", Arrays.asList("ok", ""), TOKYO, null, List.of(), 0L, null,
            Map.of()));
  }

  @Test
  void testRejectsNullLocation() {
    assertMessage("Location must not be null", () -> entry("s", "1", "n", List.of(), null, null,
        List.of(), 0L, null, Map.of()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "J", "JPN", "jp", "J1", "-9"})
  void testRejectsMalformedCountryCode(String countryCode) {
    assertMessage("CountryCode must be an ISO 3166-1 alpha-2 code", () -> entry("s", "1", "n",
        List.of(), TOKYO, countryCode, List.of(), 0L, null, Map.of()));
  }

  @Test
  void testRejectsBadContainment() {
    assertMessage("Containment must not be null", () -> entry("s", "1", "n", List.of(), TOKYO,
        null, null, 0L, null, Map.of()));
    assertMessage("Containment must not contain a null or empty element",
        () -> entry("s", "1", "n", List.of(), TOKYO, null, Arrays.asList("ok", null), 0L, null,
            Map.of()));
    assertMessage("Containment must not contain a null or empty element",
        () -> entry("s", "1", "n", List.of(), TOKYO, null, Arrays.asList(""), 0L, null, Map.of()));
  }

  @Test
  void testRejectsNegativePopulation() {
    assertMessage("Population must not be negative", () -> entry("s", "1", "n", List.of(), TOKYO,
        null, List.of(), -1L, null, Map.of()));
  }

  @Test
  void testRejectsEmptyFeatureClass() {
    assertMessage("FeatureClass must be null when unknown, not empty", () -> entry("s", "1", "n",
        List.of(), TOKYO, null, List.of(), 0L, "", Map.of()));
  }

  @Test
  void testRejectsBadAttributes() {
    assertMessage("Attributes must not be null", () -> entry("s", "1", "n", List.of(), TOKYO,
        null, List.of(), 0L, null, null));
    final Map<String, AttributeValue> nullValue = new HashMap<>();
    nullValue.put("key", null);
    assertMessage("Attributes must not contain a null or empty key or a null value",
        () -> entry("s", "1", "n", List.of(), TOKYO, null, List.of(), 0L, null, nullValue));
    final Map<String, AttributeValue> emptyKey = new HashMap<>();
    emptyKey.put("", new AttributeValue("v", "s", ""));
    assertMessage("Attributes must not contain a null or empty key or a null value",
        () -> entry("s", "1", "n", List.of(), TOKYO, null, List.of(), 0L, null, emptyKey));
  }

  private static void assertMessage(String expected, Executable e) {
    final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, e);
    assertTrue(thrown.getMessage().startsWith(expected), thrown.getMessage());
  }
}
