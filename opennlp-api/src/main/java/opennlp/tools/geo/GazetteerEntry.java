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

import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;

/**
 * One gazetteer record: a named place with its location, coarse classification, and
 * dataset-specific extras. The identifier is scoped by its {@link #source() source} dataset, and
 * anything a dataset knows beyond the common core goes into the {@link #attributes() attributes}
 * map as provenance-tagged {@link AttributeValue}s.
 *
 * <p>The {@link #countryCode() country code} is an
 * <a href="https://www.iso.org/iso-3166-country-codes.html">ISO 3166-1</a> alpha-2 code.
 * Attribute keys follow the {@code ATTRIBUTE_KEY_*} convention published as constants on this
 * record; an entry only carries the keys its source actually provides, each with provenance in
 * {@link AttributeValue#source()}, and a key is never fabricated.</p>
 *
 * <p>Instances are immutable and thread-safe: the list and map components are defensively copied
 * to immutable views at construction.</p>
 *
 * @param source         The dataset identifier, for example {@code naturalearth}, {@code overture},
 *                       or {@code geonames}. Must not be {@code null} or empty.
 * @param recordId       The source-scoped stable identifier, opaque to consumers. Must not be
 *                       {@code null} or empty. Only ({@code source}, {@code recordId}) together
 *                       identify a record.
 * @param name           The canonical place name. Must not be {@code null} or empty.
 * @param alternateNames The alternate names, possibly empty. Must not be {@code null} or contain
 *                       {@code null} or empty elements.
 * @param location       The point location. Must not be {@code null}.
 * @param countryCode    The ISO 3166-1 alpha-2 country code, or {@code null} when not applicable
 *                       (for example a disputed territory the source assigns no code). When
 *                       present it must be exactly two ASCII capital letters.
 * @param containment    The administrative containment chain, outermost first, possibly empty.
 *                       Must not be {@code null} or contain {@code null} or empty elements.
 * @param population     The population, {@code 0} when unknown or genuinely zero. Must not be
 *                       negative. Consumers that rank or score by population must treat {@code 0}
 *                       as absent evidence, never as a confirmed empty place.
 * @param featureClass   The coarse, dataset-neutral feature class, conventionally one of the
 *                       {@code FEATURE_CLASS_*} constants on this record ({@link
 *                       #FEATURE_CLASS_CITY}, {@link #FEATURE_CLASS_ADMIN},
 *                       {@link #FEATURE_CLASS_POI}); {@code null} when unknown. Must not be
 *                       empty when present.
 * @param attributes     The dataset-specific extras keyed by attribute name, each value carrying
 *                       its own provenance. Must not be {@code null} or contain {@code null} or
 *                       empty keys or {@code null} values.
 */
@ThreadSafe
public record GazetteerEntry(
    String source,
    String recordId,
    String name,
    List<String> alternateNames,
    GeoPoint location,
    String countryCode,
    List<String> containment,
    long population,
    String featureClass,
    Map<String, AttributeValue> attributes) {

  /** The conventional feature class of a populated place (city, town, village, settlement). */
  public static final String FEATURE_CLASS_CITY = "CITY";

  /** The conventional feature class of an administrative area (region, district, province). */
  public static final String FEATURE_CLASS_ADMIN = "ADMIN";

  /** The conventional feature class of a point of interest that is not a settlement. */
  public static final String FEATURE_CLASS_POI = "POI";

  /** The conventional attribute key of a US FIPS code. */
  public static final String ATTRIBUTE_KEY_FIPS = "fips";

  /** The conventional attribute key of a US Census GEOID. */
  public static final String ATTRIBUTE_KEY_GEOID = "geoid";

  /** The conventional attribute key of a US Census ZIP Code Tabulation Area. */
  public static final String ATTRIBUTE_KEY_ZCTA = "zcta";

  /** The conventional attribute key of a Wikidata item id. */
  public static final String ATTRIBUTE_KEY_WIKIDATA = "wikidata";

  /** The conventional attribute key of a GeoNames id. */
  public static final String ATTRIBUTE_KEY_GEONAMES = "geonames";

  /** The conventional attribute key of a Who's On First id. */
  public static final String ATTRIBUTE_KEY_WHOSONFIRST = "whosonfirst";

  /**
   * Creates an entry.
   *
   * @throws IllegalArgumentException Thrown if any component violates its documented constraint.
   */
  public GazetteerEntry {
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("Source must not be null or empty");
    }
    if (recordId == null || recordId.isEmpty()) {
      throw new IllegalArgumentException("RecordId must not be null or empty");
    }
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Name must not be null or empty");
    }
    if (alternateNames == null) {
      throw new IllegalArgumentException("AlternateNames must not be null");
    }
    for (final String alternateName : alternateNames) {
      if (alternateName == null || alternateName.isEmpty()) {
        throw new IllegalArgumentException(
            "AlternateNames must not contain a null or empty element, got: " + alternateNames);
      }
    }
    if (location == null) {
      throw new IllegalArgumentException("Location must not be null");
    }
    if (countryCode != null && !isAlpha2(countryCode)) {
      throw new IllegalArgumentException(
          "CountryCode must be an ISO 3166-1 alpha-2 code (two ASCII capital letters) or null, got: "
              + countryCode);
    }
    if (containment == null) {
      throw new IllegalArgumentException("Containment must not be null");
    }
    for (final String level : containment) {
      if (level == null || level.isEmpty()) {
        throw new IllegalArgumentException(
            "Containment must not contain a null or empty element, got: " + containment);
      }
    }
    if (population < 0) {
      throw new IllegalArgumentException("Population must not be negative, got: " + population);
    }
    if (featureClass != null && featureClass.isEmpty()) {
      throw new IllegalArgumentException("FeatureClass must be null when unknown, not empty");
    }
    if (attributes == null) {
      throw new IllegalArgumentException("Attributes must not be null");
    }
    for (final Map.Entry<String, AttributeValue> entry : attributes.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getValue() == null) {
        throw new IllegalArgumentException(
            "Attributes must not contain a null or empty key or a null value, got: " + attributes);
      }
    }
    alternateNames = List.copyOf(alternateNames);
    containment = List.copyOf(containment);
    attributes = Map.copyOf(attributes);
  }

  /** {@return {@code true} if {@code code} is two ASCII uppercase letters}. */
  private static boolean isAlpha2(String code) {
    return code.length() == 2
        && code.charAt(0) >= 'A' && code.charAt(0) <= 'Z'
        && code.charAt(1) >= 'A' && code.charAt(1) <= 'Z';
  }
}
