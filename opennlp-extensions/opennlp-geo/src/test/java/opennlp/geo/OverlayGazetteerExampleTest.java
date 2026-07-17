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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoBoundingBox;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the manual's gazetteer-overlay example (docbkx {@code geo.xml}, section
 * {@code tools.geo.overlay}) verbatim: the same two user files, the same composition code, and
 * the exact outputs the chapter states. A change breaking this test breaks the manual.
 */
public class OverlayGazetteerExampleTest {

  /** The customer places file exactly as printed in the manual. */
  private static final String CUSTOMER_PLACES = String.join("\n",
      "# id\tname\talternates\tlat\tlon\tcc\tclass\tpop\tbbox\tcontainment\tattributes",
      "plant-3\tAcme Plant 3\tPlant 3|The Plant\t33.75\t-84.39\tUS\tPOI\t0\t\t"
          + "Georgia|Fulton County\taddress=123 Main St, Atlanta GA 30303",
      "campus-east\tAcme Campus East\t\t\t\tUS\tPOI\t0\t-84.5,33.6,-84.2,33.9",
      "") + "\n";

  /** The suppression file exactly as printed in the manual. */
  private static final String SUPPRESS = String.join("\n",
      "# name\tcountry\tfeature class",
      "Mobile\tUS\tCITY",
      "") + "\n";

  @Test
  void testManualExampleProducesTheDocumentedResults(@TempDir Path dir) throws IOException {
    final Path customerPlaces = dir.resolve("customer_places.txt");
    Files.writeString(customerPlaces, CUSTOMER_PLACES, StandardCharsets.UTF_8);
    final Path suppress = dir.resolve("suppress.txt");
    Files.writeString(suppress, SUPPRESS, StandardCharsets.UTF_8);

    final Gazetteer base = BundledGazetteer.getInstance();
    final Gazetteer customer = UserGazetteer.load(customerPlaces, "customer");
    final List<Suppression> rules = UserGazetteer.loadSuppressions(suppress);
    final Gazetteer gazetteer = new OverlayGazetteer(base, customer, rules);

    final GazetteerEntry plant = gazetteer.lookup("Plant 3").get(0);
    assertEquals("Acme Plant 3", plant.name());
    assertEquals("customer", plant.source());
    assertEquals("123 Main St, Atlanta GA 30303", plant.attributes().get("address").value());

    assertTrue(gazetteer.lookup("Mobile").isEmpty(),
        "the Alabama city is suppressed");
    assertFalse(gazetteer.lookup("Tokyo").isEmpty(),
        "unaffected base entries stay visible");

    // The premise behind the suppression rule: the base alone does know the city.
    final GazetteerEntry mobile = base.lookup("Mobile").get(0);
    assertEquals("US", mobile.countryCode());
    assertEquals(GazetteerEntry.FEATURE_CLASS_CITY, mobile.featureClass());
    assertEquals(Optional.empty(), gazetteer.byId(mobile.source(), mobile.recordId()),
        "a suppressed entry is hidden from every query");

    // The campus row has no coordinates: its location is the bounding box's center.
    final GazetteerEntry campus = gazetteer.lookup("Acme Campus East").get(0);
    assertEquals(new GeoBoundingBox(-84.5, 33.6, -84.2, 33.9), campus.boundingBox());
    assertEquals(new GeoPoint(33.75, -84.35), campus.location());
    assertTrue(campus.boundingBox().contains(campus.location()));
  }
}
