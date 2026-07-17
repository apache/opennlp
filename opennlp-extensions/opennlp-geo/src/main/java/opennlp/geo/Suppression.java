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

import java.util.Locale;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.util.StringUtil;

/**
 * One rule hiding base entries from an {@link OverlayGazetteer}: a place name with optional
 * country and feature-class filters, so a rule can be as narrow as one place reading. A rule
 * matches an entry when the name equals the entry's canonical name or any of its alternate names
 * case-insensitively, the country filter, when present, equals the entry's country code, and the
 * feature-class filter, when present, equals the entry's feature class case-insensitively.
 *
 * <p>An entry without a country code never matches a rule carrying a country filter, and an entry
 * without a feature class never matches a rule carrying a feature-class filter: a filter demands
 * the evidence it names.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 *
 * @param name         The place name to suppress, matched case-insensitively against the
 *                     canonical and alternate names. Must not be {@code null} or blank.
 * @param countryCode  The <a href="https://www.iso.org/iso-3166-country-codes.html">ISO
 *                     3166-1</a> alpha-2 country filter, two ASCII letters of either case, or
 *                     {@code null} to match any country.
 * @param featureClass The feature-class filter, conventionally one of the
 *                     {@code FEATURE_CLASS_*} constants on {@link GazetteerEntry}, or
 *                     {@code null} to match any feature class. Must not be blank when present.
 * @since 3.0.0
 */
@ThreadSafe
public record Suppression(String name, String countryCode, String featureClass) {

  /**
   * Creates a rule.
   *
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null} or blank,
   *     {@code countryCode} is present but not two ASCII letters, or {@code featureClass} is
   *     present but blank.
   */
  public Suppression {
    if (name == null || StringUtil.isBlank(name)) {
      throw new IllegalArgumentException("Name must not be null or blank");
    }
    if (countryCode != null) {
      countryCode = GazetteerIndex.normalizeRegionCode(countryCode);
    }
    if (featureClass != null && StringUtil.isBlank(featureClass)) {
      throw new IllegalArgumentException("FeatureClass must be null when absent, not blank");
    }
  }

  /**
   * Creates a rule suppressing every entry with a name, in any country and of any feature class.
   *
   * @param name The place name to suppress. Must not be {@code null} or blank.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null} or blank.
   */
  public Suppression(String name) {
    this(name, null, null);
  }

  /**
   * Tests whether this rule matches an entry.
   *
   * @param entry The entry to test. Must not be {@code null}.
   * @return {@code true} if the entry matches the name and every present filter.
   * @throws IllegalArgumentException Thrown if {@code entry} is {@code null}.
   */
  public boolean matches(GazetteerEntry entry) {
    if (entry == null) {
      throw new IllegalArgumentException("Entry must not be null");
    }
    if (countryCode != null && !countryCode.equals(entry.countryCode())) {
      return false;
    }
    if (featureClass != null
        && (entry.featureClass() == null || !featureClass.equalsIgnoreCase(entry.featureClass()))) {
      return false;
    }
    final String folded = name.toLowerCase(Locale.ROOT);
    if (folded.equals(entry.name().toLowerCase(Locale.ROOT))) {
      return true;
    }
    for (final String alternate : entry.alternateNames()) {
      if (folded.equals(alternate.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }
}
