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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the manual's bring-your-own-gazetteer example (docbkx {@code geo.xml}, section
 * {@code tools.geo.custom}): {@link CrmGazetteer} is the implementation the chapter shows, and
 * this test asserts the composition results the chapter states plus the contract corners every
 * implementation must honor. A change breaking this test breaks the manual.
 */
public class CustomGazetteerExampleTest {

  private static final GazetteerEntry PLANT = new GazetteerEntry("crm", "cust-1", "Acme Plant 3",
      List.of(), new GeoPoint(33.75, -84.39), "US", List.of(), 0L,
      GazetteerEntry.FEATURE_CLASS_POI, Map.of());

  @Test
  void testManualExampleProducesTheDocumentedResults() throws IOException {
    final Gazetteer crm = new CrmGazetteer(List.of(PLANT));
    final Gazetteer gazetteer =
        new OverlayGazetteer(BundledGazetteer.getInstance(), crm, List.of());

    final GazetteerEntry plant = gazetteer.lookup("Acme Plant 3").get(0);
    assertEquals("crm", plant.source());
    assertEquals(new GeoPoint(33.75, -84.39), plant.location());
    assertEquals(plant, gazetteer.lookup("acme plant 3").get(0),
        "the example implementation matches names case-insensitively");
    assertFalse(gazetteer.lookup("Tokyo").isEmpty(),
        "the base still answers everything else");
  }

  /** The contract corners the interface documents, honored by the example implementation. */
  @Test
  void testExampleImplementationHonorsTheGazetteerContract() throws IOException {
    final Gazetteer crm = new CrmGazetteer(List.of(PLANT));
    assertEquals(Optional.of(PLANT), crm.byId("crm", "cust-1"));
    assertEquals(Optional.empty(), crm.byId("naturalearth", "cust-1"),
        "record ids are scoped to the implementation's own source");
    assertEquals(Optional.of(PLANT), crm.byRegion("us"),
        "region codes match in either case");
    assertEquals(Optional.empty(), crm.byRegion("DE"));
    assertEquals(Set.of("crm"), crm.sources());
    assertTrue(crm.lookup("unknown").isEmpty());

    assertThrows(IllegalArgumentException.class, () -> new CrmGazetteer(null));
    assertThrows(IllegalArgumentException.class, () -> crm.lookup(null));
    assertThrows(IllegalArgumentException.class, () -> crm.byId(null, "cust-1"));
    assertThrows(IllegalArgumentException.class, () -> crm.byId("crm", null));
    assertThrows(IllegalArgumentException.class, () -> crm.byRegion("USA"));
    assertThrows(IllegalArgumentException.class, () -> crm.byRegion(null));
  }
}
