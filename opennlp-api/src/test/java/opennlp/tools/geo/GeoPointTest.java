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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeoPointTest {

  @Test
  void testHoldsCoordinates() {
    final GeoPoint point = new GeoPoint(35.6839, 139.7744);
    assertEquals(35.6839, point.latitude());
    assertEquals(139.7744, point.longitude());
  }

  @ParameterizedTest
  @CsvSource({"-90.0, -180.0", "90.0, 180.0", "0.0, 0.0"})
  void testAcceptsRangeBoundaries(double latitude, double longitude) {
    final GeoPoint point = new GeoPoint(latitude, longitude);
    assertEquals(latitude, point.latitude());
    assertEquals(longitude, point.longitude());
  }

  @ParameterizedTest
  @CsvSource({"-90.001", "90.001", "NaN"})
  void testRejectsOutOfRangeLatitude(double latitude) {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(latitude, 0.0));
    assertTrue(e.getMessage().startsWith("Latitude must be in [-90, 90]"), e.getMessage());
  }

  @ParameterizedTest
  @CsvSource({"-180.001", "180.001", "NaN"})
  void testRejectsOutOfRangeLongitude(double longitude) {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, longitude));
    assertTrue(e.getMessage().startsWith("Longitude must be in [-180, 180]"), e.getMessage());
  }
}
