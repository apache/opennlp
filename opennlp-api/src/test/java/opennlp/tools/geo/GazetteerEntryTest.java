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
  void testRejectsNullEmptyOrBlankSource() {
    for (final String source : new String[] {null, "", " "}) {
      assertMessage("source must not be null or blank", () -> entry(source, "1", "n", List.of(),
          TOKYO, null, List.of(), 0L, null, Map.of()));
    }
  }

  @Test
  void testRejectsNullEmptyOrBlankRecordId() {
    for (final String recordId : new String[] {null, "", "\t"}) {
      assertMessage("recordId must not be null or blank", () -> entry("s", recordId, "n",
          List.of(), TOKYO, null, List.of(), 0L, null, Map.of()));
    }
  }

  @Test
  void testRejectsNullEmptyOrBlankName() {
    for (final String name : new String[] {null, "", "  "}) {
      assertMessage("name must not be null or blank", () -> entry("s", "1", name, List.of(),
          TOKYO, null, List.of(), 0L, null, Map.of()));
    }
  }

  @Test
  void testRejectsBadAlternateNames() {
    assertMessage("alternateNames must not be null", () -> entry("s", "1", "n", null,
        TOKYO, null, List.of(), 0L, null, Map.of()));
    for (final String alternateName : new String[] {null, "", " "}) {
      assertMessage("alternateNames must not contain a null or blank element",
          () -> entry("s", "1", "n", Arrays.asList("ok", alternateName), TOKYO, null, List.of(),
              0L, null, Map.of()));
    }
  }

  @Test
  void testRejectsNullLocation() {
    assertMessage("location must not be null", () -> entry("s", "1", "n", List.of(), null, null,
        List.of(), 0L, null, Map.of()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "J", "JPN", "jp", "J1", "-9"})
  void testRejectsMalformedCountryCode(String countryCode) {
    assertMessage("countryCode must be an ISO 3166-1 alpha-2 code", () -> entry("s", "1", "n",
        List.of(), TOKYO, countryCode, List.of(), 0L, null, Map.of()));
  }

  @Test
  void testRejectsBadContainment() {
    assertMessage("containment must not be null", () -> entry("s", "1", "n", List.of(), TOKYO,
        null, null, 0L, null, Map.of()));
    for (final String level : new String[] {null, "", " "}) {
      assertMessage("containment must not contain a null or blank element",
          () -> entry("s", "1", "n", List.of(), TOKYO, null, Arrays.asList("ok", level), 0L, null,
              Map.of()));
    }
  }

  @Test
  void testRejectsNegativePopulation() {
    assertMessage("population must not be negative", () -> entry("s", "1", "n", List.of(), TOKYO,
        null, List.of(), -1L, null, Map.of()));
  }

  @Test
  void testRejectsBlankFeatureClass() {
    for (final String featureClass : new String[] {"", " "}) {
      assertMessage("featureClass must be null when unknown, not blank", () -> entry("s", "1",
          "n", List.of(), TOKYO, null, List.of(), 0L, featureClass, Map.of()));
    }
  }

  @Test
  void testRejectsBadAttributes() {
    assertMessage("attributes must not be null", () -> entry("s", "1", "n", List.of(), TOKYO,
        null, List.of(), 0L, null, null));
    final Map<String, AttributeValue> nullValue = new HashMap<>();
    nullValue.put("key", null);
    assertMessage("attributes must not contain a null or blank key or a null value",
        () -> entry("s", "1", "n", List.of(), TOKYO, null, List.of(), 0L, null, nullValue));
    for (final String key : new String[] {"", " "}) {
      final Map<String, AttributeValue> blankKey = new HashMap<>();
      blankKey.put(key, new AttributeValue("v", "s", ""));
      assertMessage("attributes must not contain a null or blank key or a null value",
          () -> entry("s", "1", "n", List.of(), TOKYO, null, List.of(), 0L, null, blankKey));
    }
  }

  private static void assertMessage(String expected, Executable e) {
    final IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, e);
    assertTrue(thrown.getMessage().startsWith(expected), thrown.getMessage());
  }
}
