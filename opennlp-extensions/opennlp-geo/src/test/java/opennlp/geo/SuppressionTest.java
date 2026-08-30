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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuppressionTest {

  private static final GazetteerEntry MOBILE = new GazetteerEntry("test", "1", "Mobile",
      List.of("Mobile City"), new GeoPoint(30.69, -88.04), "US", List.of(), 187000L,
      GazetteerEntry.FEATURE_CLASS_CITY, Map.of());

  private static final GazetteerEntry UNTAGGED = new GazetteerEntry("test", "2", "Mobile",
      List.of(), new GeoPoint(0.0, 0.0), null, List.of(), 0L, null, Map.of());

  @Test
  void testMatchesCanonicalAndAlternateNamesCaseInsensitively() {
    assertTrue(new Suppression("mobile").matches(MOBILE));
    assertTrue(new Suppression("MOBILE CITY").matches(MOBILE));
    assertFalse(new Suppression("Mobile Bay").matches(MOBILE));
  }

  @Test
  void testCountryFilterDemandsAMatchingCode() {
    assertTrue(new Suppression("Mobile", "us", null).matches(MOBILE));
    assertFalse(new Suppression("Mobile", "GB", null).matches(MOBILE));
    assertFalse(new Suppression("Mobile", "US", null).matches(UNTAGGED),
        "an entry without a country code never matches a country filter");
  }

  @Test
  void testFeatureClassFilterDemandsAMatchingClass() {
    assertTrue(new Suppression("Mobile", null, "city").matches(MOBILE));
    assertFalse(new Suppression("Mobile", null, "ADMIN").matches(MOBILE));
    assertFalse(new Suppression("Mobile", null, "CITY").matches(UNTAGGED),
        "an entry without a feature class never matches a feature-class filter");
  }

  @Test
  void testCountryCodeIsNormalizedToUppercase() {
    assertEquals("US", new Suppression("Mobile", "us", null).countryCode());
    assertEquals(new Suppression("Mobile", "US", null), new Suppression("Mobile", "us", null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"U", "USA", "U1"})
  void testRejectsAMalformedCountryFilter(String countryCode) {
    assertThrows(IllegalArgumentException.class,
        () -> new Suppression("Mobile", countryCode, null));
  }

  @Test
  void testRejectsInvalidArguments() {
    assertThrows(IllegalArgumentException.class, () -> new Suppression(null));
    assertThrows(IllegalArgumentException.class, () -> new Suppression(" "));
    assertThrows(IllegalArgumentException.class, () -> new Suppression("Mobile", null, " "));
    assertThrows(IllegalArgumentException.class,
        () -> new Suppression("Mobile").matches(null));
  }
}
