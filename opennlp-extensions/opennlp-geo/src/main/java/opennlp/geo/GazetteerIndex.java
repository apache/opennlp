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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import opennlp.tools.geo.GazetteerEntry;

/**
 * The shared in-memory index behind the file-loading gazetteers: entries keyed by
 * every name variant (case-insensitively), by record id, and by country, where the
 * country representative is the most populous entry.
 *
 * <p>Mutable while loading; {@link #freeze()} ranks every candidate list by the
 * population prior and must be called once before queries.</p>
 */
final class GazetteerIndex {

  private final Map<String, List<GazetteerEntry>> byName = new HashMap<>();
  private final Map<String, GazetteerEntry> byId = new HashMap<>();
  private final Map<String, GazetteerEntry> byCountry = new HashMap<>();

  /** Indexes one entry under its canonical and alternate names. */
  void add(GazetteerEntry entry) {
    index(entry.name(), entry);
    for (final String alternate : entry.alternateNames()) {
      index(alternate, entry);
    }
    byId.put(entry.recordId(), entry);
    if (entry.countryCode() != null) {
      byCountry.merge(entry.countryCode(), entry,
          (a, b) -> a.population() >= b.population() ? a : b);
    }
  }

  /** Ranks every candidate list by the population prior; call once after loading. */
  void freeze() {
    for (final List<GazetteerEntry> candidates : byName.values()) {
      candidates.sort(CandidateRanking.BY_PRIOR);
    }
  }

  /** @return {@code true} if nothing was indexed. */
  boolean isEmpty() {
    return byId.isEmpty();
  }

  /** Finds candidates for a name, ranked; empty when nothing matches. */
  List<GazetteerEntry> lookup(CharSequence name) {
    final List<GazetteerEntry> found = byName.get(name.toString().toLowerCase(Locale.ROOT));
    return found == null ? List.of() : Collections.unmodifiableList(found);
  }

  /** Finds the entry with a record id. */
  Optional<GazetteerEntry> byId(String recordId) {
    return Optional.ofNullable(byId.get(recordId));
  }

  /** Finds the most populous entry of a country. */
  Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    return Optional.ofNullable(byCountry.get(isoCountryCode.toUpperCase(Locale.ROOT)));
  }

  private void index(String name, GazetteerEntry entry) {
    final List<GazetteerEntry> entries =
        byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>(2));
    if (!entries.contains(entry)) {
      entries.add(entry);
    }
  }
}
