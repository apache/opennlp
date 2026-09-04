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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the in-memory gazetteer over caller-supplied entries: the documented third-party
 * path shares the bundled table's folding and ranking without loading the bundled data.
 */
public class InMemoryGazetteerTest {

  private static GazetteerEntry entry(String recordId, String name, List<String> alternateNames,
      GeoPoint location, List<String> containment, long population) {
    return new GazetteerEntry("customsource", recordId, name, alternateNames, location, "US",
        containment, population, GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
  }

  @Test
  void testFromEntriesBuildsACustomGazetteer() {
    // Build a gazetteer over caller-supplied entries without touching the bundled table,
    // and get the same folding and ranking behavior.
    final GazetteerEntry smallville = entry("sv-1", "Smallville", List.of("Small Ville"),
        new GeoPoint(38.0, -97.0), List.of("Kansas"), 45001L);
    final GazetteerEntry bigtown = entry("bt-1", "Bigtown", List.of(),
        new GeoPoint(40.0, -75.0), List.of(), 250000L);
    final InMemoryGazetteer custom = InMemoryGazetteer.fromEntries(List.of(smallville, bigtown));
    assertEquals(Set.of("customsource"), custom.sources());
    assertEquals("sv-1", custom.lookup("smallville").get(0).recordId()); // case folded
    assertEquals("sv-1", custom.lookup("Small Ville").get(0).recordId()); // alternate name
    assertEquals("bt-1", custom.byRegion("US").orElseThrow().recordId()); // most populous
    assertEquals(smallville, custom.byId("customsource", "sv-1").orElseThrow());
  }

  @Test
  void testFromEntriesValidatesItsInput() {
    assertThrows(IllegalArgumentException.class, () -> InMemoryGazetteer.fromEntries(null));
    assertThrows(IllegalArgumentException.class,
        () -> InMemoryGazetteer.fromEntries(Arrays.asList((GazetteerEntry) null)));
    final GazetteerEntry once = entry("sv-1", "Smallville", List.of(),
        new GeoPoint(38.0, -97.0), List.of(), 45001L);
    assertThrows(IllegalArgumentException.class,
        () -> InMemoryGazetteer.fromEntries(List.of(once, once))); // duplicate (source, recordId)
  }

  @Test
  void testEmptyEntrySetCreatesAnEmptyGazetteer() {
    final InMemoryGazetteer empty = InMemoryGazetteer.fromEntries(List.of());

    assertTrue(empty.sources().isEmpty());
    assertTrue(empty.lookup("Paris").isEmpty());
    assertTrue(empty.byId("source", "record").isEmpty());
    assertTrue(empty.byRegion("US").isEmpty());
  }
}
