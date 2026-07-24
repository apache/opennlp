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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeoBoundingBoxTest {

  @Test
  void testHoldsEdges() {
    final GeoBoundingBox box = new GeoBoundingBox(-74.3, 40.4, -73.7, 41.0);
    assertEquals(-74.3, box.west());
    assertEquals(40.4, box.south());
    assertEquals(-73.7, box.east());
    assertEquals(41.0, box.north());
  }

  @ParameterizedTest
  @CsvSource({"-180.001, true", "180.001, true", "NaN, true",
      "-180.001, false", "180.001, false", "NaN, false"})
  void testRejectsOutOfRangeLongitude(double longitude, boolean west) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
      if (west) {
        new GeoBoundingBox(longitude, 0.0, 0.0, 1.0);
      } else {
        new GeoBoundingBox(0.0, 0.0, longitude, 1.0);
      }
    });
    assertTrue(e.getMessage().contains("must be in [-180, 180]"), e.getMessage());
  }

  @ParameterizedTest
  @CsvSource({"-90.001, true", "90.001, true", "NaN, true",
      "-90.001, false", "90.001, false", "NaN, false"})
  void testRejectsOutOfRangeLatitude(double latitude, boolean south) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
      if (south) {
        new GeoBoundingBox(0.0, latitude, 1.0, 45.0);
      } else {
        new GeoBoundingBox(0.0, -45.0, 1.0, latitude);
      }
    });
    assertTrue(e.getMessage().contains("must be in [-90, 90]"), e.getMessage());
  }

  @Test
  void testRejectsInvertedLatitudes() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new GeoBoundingBox(0.0, 10.0, 1.0, 9.0));
    assertEquals("South must not be greater than north, got: 10.0 > 9.0", e.getMessage());
  }

  @Test
  void testContainsInsideAndOutside() {
    final GeoBoundingBox box = new GeoBoundingBox(-74.3, 40.4, -73.7, 41.0);
    assertTrue(box.contains(new GeoPoint(40.7, -74.0)));
    assertTrue(box.contains(new GeoPoint(40.4, -74.3)), "edges are included");
    assertTrue(box.contains(new GeoPoint(41.0, -73.7)), "edges are included");
    assertFalse(box.contains(new GeoPoint(41.1, -74.0)), "north of the box");
    assertFalse(box.contains(new GeoPoint(40.7, -73.6)), "east of the box");
  }

  /**
   * A box from longitude {@code 170} to {@code -170} crosses the antimeridian per the RFC 7946
   * convention: it contains the longitudes beyond {@code 170} and below {@code -170}, and not
   * the ones in between.
   */
  @Test
  void testAntimeridianCrossingContains() {
    final GeoBoundingBox fiji = new GeoBoundingBox(170.0, -25.0, -170.0, -10.0);
    assertTrue(fiji.contains(new GeoPoint(-18.0, 178.0)));
    assertTrue(fiji.contains(new GeoPoint(-18.0, -178.0)));
    assertFalse(fiji.contains(new GeoPoint(-18.0, 0.0)));
    assertFalse(fiji.contains(new GeoPoint(-18.0, 169.9)));
  }

  @Test
  void testCenter() {
    final GeoBoundingBox box = new GeoBoundingBox(-74.3, 40.4, -73.7, 41.0);
    assertEquals(new GeoPoint(40.7, -74.0), box.center());
  }

  /** The center of an antimeridian-crossing box lies on the covered side, not at longitude 0. */
  @ParameterizedTest
  @CsvSource({"170.0, -170.0, 180.0", "150.0, -170.0, 170.0", "170.0, -150.0, -170.0"})
  void testAntimeridianCrossingCenter(double west, double east, double centerLongitude) {
    final GeoBoundingBox box = new GeoBoundingBox(west, -20.0, east, -10.0);
    assertEquals(new GeoPoint(-15.0, centerLongitude), box.center());
  }

  @Test
  void testContainsRejectsNull() {
    final GeoBoundingBox box = new GeoBoundingBox(0.0, 0.0, 1.0, 1.0);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> box.contains(null));
    assertEquals("Point must not be null", e.getMessage());
  }
}
