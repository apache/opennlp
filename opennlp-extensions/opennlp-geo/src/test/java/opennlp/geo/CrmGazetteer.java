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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;

/**
 * The manual's reference example of bringing your own {@link Gazetteer}: a minimal, correct
 * implementation over an application-internal store. Two maps are enough to join the seam, and
 * there is no registration anywhere; the instance is handed to whatever consumes the interface.
 * The geo chapter of the manual shows this implementation, and
 * {@link CustomGazetteerExampleTest} asserts the behavior the chapter states.
 *
 * <p>Lookup matches the canonical name case-insensitively; alternate names are not indexed,
 * which is this implementation's documented matching choice. Instances are immutable and
 * thread-safe.</p>
 */
final class CrmGazetteer implements Gazetteer {

  /** The dataset identifier this gazetteer scopes its record ids to. */
  static final String SOURCE = "crm";

  private final Map<String, GazetteerEntry> byName;
  private final Map<String, GazetteerEntry> byId;

  /**
   * Indexes the entries by folded canonical name and by record id.
   *
   * @param entries The entries to serve. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code entries} is {@code null}.
   */
  CrmGazetteer(List<GazetteerEntry> entries) {
    if (entries == null) {
      throw new IllegalArgumentException("entries must not be null");
    }
    final Map<String, GazetteerEntry> names = new HashMap<>();
    final Map<String, GazetteerEntry> ids = new HashMap<>();
    for (final GazetteerEntry entry : entries) {
      names.put(entry.name().toLowerCase(Locale.ROOT), entry);
      ids.put(entry.recordId(), entry);
    }
    this.byName = Map.copyOf(names);
    this.byId = Map.copyOf(ids);
  }

  /** {@inheritDoc} */
  @Override
  public List<GazetteerEntry> lookup(CharSequence name) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    final GazetteerEntry found = byName.get(name.toString().toLowerCase(Locale.ROOT));
    return found == null ? List.of() : List.of(found);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) {
    if (source == null || recordId == null) {
      throw new IllegalArgumentException("source and recordId must not be null");
    }
    return SOURCE.equals(source) ? Optional.ofNullable(byId.get(recordId)) : Optional.empty();
  }

  /**
   * {@inheritDoc}
   *
   * <p>The representative of a region is its most populous entry.</p>
   */
  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    if (isoCountryCode == null || isoCountryCode.length() != 2
        || !isAsciiLetter(isoCountryCode.charAt(0)) || !isAsciiLetter(isoCountryCode.charAt(1))) {
      throw new IllegalArgumentException(
          "isoCountryCode must be two ASCII letters, got: " + isoCountryCode);
    }
    final String code = isoCountryCode.toUpperCase(Locale.ROOT);
    GazetteerEntry best = null;
    for (final GazetteerEntry entry : byId.values()) {
      if (code.equals(entry.countryCode())
          && (best == null || entry.population() > best.population())) {
        best = entry;
      }
    }
    return Optional.ofNullable(best);
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> sources() {
    return Set.of(SOURCE);
  }

  /** {@return {@code true} if {@code c} is an ASCII letter} */
  private static boolean isAsciiLetter(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }
}
