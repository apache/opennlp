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
 * A geographic bounding box in <a href="https://epsg.org/crs_4326/WGS-84.html">WGS84
 * (EPSG:4326)</a> decimal degrees, with the axes ordered west, south, east, north as in
 * <a href="https://datatracker.ietf.org/doc/html/rfc7946#section-5">RFC 7946, section 5</a>.
 *
 * <p>Following the same section, a box whose {@link #west() west} edge is greater than its
 * {@link #east() east} edge crosses the antimeridian: it covers the longitudes from west up to
 * {@code 180} and from {@code -180} up to east. {@link #contains(GeoPoint)} and {@link #center()}
 * honor that convention.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 *
 * @param west  The western longitude edge in decimal degrees, in {@code [-180, 180]}.
 * @param south The southern latitude edge in decimal degrees, in {@code [-90, 90]}. Must not be
 *              greater than {@code north}.
 * @param east  The eastern longitude edge in decimal degrees, in {@code [-180, 180]}. May be less
 *              than {@code west}, which means the box crosses the antimeridian.
 * @param north The northern latitude edge in decimal degrees, in {@code [-90, 90]}.
 */
@ThreadSafe
public record GeoBoundingBox(double west, double south, double east, double north) {

  /**
   * Creates a bounding box.
   *
   * @throws IllegalArgumentException Thrown if a longitude is not in {@code [-180, 180]}, a
   *     latitude is not in {@code [-90, 90]}, any edge is {@code NaN}, or {@code south} is
   *     greater than {@code north}.
   */
  public GeoBoundingBox {
    if (!(west >= -180.0 && west <= 180.0)) {
      throw new IllegalArgumentException("West must be in [-180, 180], got: " + west);
    }
    if (!(east >= -180.0 && east <= 180.0)) {
      throw new IllegalArgumentException("East must be in [-180, 180], got: " + east);
    }
    if (!(south >= -90.0 && south <= 90.0)) {
      throw new IllegalArgumentException("South must be in [-90, 90], got: " + south);
    }
    if (!(north >= -90.0 && north <= 90.0)) {
      throw new IllegalArgumentException("North must be in [-90, 90], got: " + north);
    }
    if (south > north) {
      throw new IllegalArgumentException(
          "South must not be greater than north, got: " + south + " > " + north);
    }
  }

  /**
   * Tests whether a point lies within this box, edges included. A box crossing the antimeridian
   * contains the longitudes on both sides of it.
   *
   * @param point The point to test. Must not be {@code null}.
   * @return {@code true} if the point lies within the box.
   * @throws IllegalArgumentException Thrown if {@code point} is {@code null}.
   */
  public boolean contains(GeoPoint point) {
    if (point == null) {
      throw new IllegalArgumentException("Point must not be null");
    }
    if (point.latitude() < south || point.latitude() > north) {
      return false;
    }
    final double longitude = point.longitude();
    if (west <= east) {
      return longitude >= west && longitude <= east;
    }
    return longitude >= west || longitude <= east;
  }

  /**
   * {@return the center of this box} For a box crossing the antimeridian the center lies on the
   * shorter, covered side, so the center of a box from {@code 170} to {@code -170} is at
   * longitude {@code 180}, not {@code 0}.
   */
  public GeoPoint center() {
    final double latitude = (south + north) / 2.0;
    if (west <= east) {
      return new GeoPoint(latitude, (west + east) / 2.0);
    }
    final double span = 360.0 - (west - east);
    final double longitude = west + span / 2.0;
    return new GeoPoint(latitude, longitude > 180.0 ? longitude - 360.0 : longitude);
  }
}
