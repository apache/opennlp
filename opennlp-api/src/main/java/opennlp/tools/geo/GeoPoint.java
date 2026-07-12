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

import opennlp.tools.commons.ThreadSafe;

/**
 * A geographic point in WGS84 decimal degrees. Both coordinates are range-validated at
 * construction and neither may be {@code NaN}.
 *
 * <p>Instances are immutable and thread-safe.</p>
 *
 * @param latitude  The latitude in decimal degrees, in {@code [-90, 90]}.
 * @param longitude The longitude in decimal degrees, in {@code [-180, 180]}.
 */
@ThreadSafe
public record GeoPoint(double latitude, double longitude) {

  /**
   * Creates a point.
   *
   * @throws IllegalArgumentException Thrown if {@code latitude} is not in {@code [-90, 90]} or
   *     {@code longitude} is not in {@code [-180, 180]}, including {@code NaN} for either.
   */
  public GeoPoint {
    if (!(latitude >= -90.0 && latitude <= 90.0)) {
      throw new IllegalArgumentException("Latitude must be in [-90, 90], got: " + latitude);
    }
    if (!(longitude >= -180.0 && longitude <= 180.0)) {
      throw new IllegalArgumentException("Longitude must be in [-180, 180], got: " + longitude);
    }
  }
}
