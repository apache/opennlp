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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The gazetteer seam: name, identifier, and region lookup over a set of place records. A bundled
 * table, a downloaded dataset, an ingested database, and a remote service client are all
 * implementations of this one interface; each record's {@link GazetteerEntry#source() source} tag
 * says where it came from.
 *
 * <p>Name matching semantics (case and accent folding, tokenization) are the implementation's
 * concern and must be documented by the implementation.</p>
 *
 * <p>Implementations must be immutable and thread-safe after construction, so one instance can be
 * shared across threads for concurrent lookups.</p>
 */
public interface Gazetteer {

  /**
   * Finds candidate entries whose canonical or alternate name matches, best-effort ranked (the
   * most plausible candidate first, by whatever ranking the implementation documents).
   *
   * @param name The place name to look up. Must not be {@code null}.
   * @return The matching entries, never {@code null}; empty when nothing matches.
   * @throws IllegalArgumentException Thrown if {@code name} is {@code null}.
   * @throws IOException Thrown if a backing store or remote service fails. An in-memory
   *     implementation never throws it.
   */
  List<GazetteerEntry> lookup(CharSequence name) throws IOException;

  /**
   * Finds the entry with a source-scoped identifier.
   *
   * @param source   The dataset identifier the record identifier is scoped to. Must not be
   *                 {@code null}.
   * @param recordId The record identifier within that dataset. Must not be {@code null}.
   * @return The entry, or empty when this gazetteer has no such record.
   * @throws IllegalArgumentException Thrown if {@code source} or {@code recordId} is {@code null}.
   * @throws IOException Thrown if a backing store or remote service fails. An in-memory
   *     implementation never throws it.
   */
  Optional<GazetteerEntry> byId(String source, String recordId) throws IOException;

  /**
   * Finds a representative entry for an ISO 3166-1 alpha-2 region code.
   *
   * @param isoCountryCode The ISO 3166-1 alpha-2 code, two ASCII capital letters. Must not be
   *                       {@code null}.
   * @return A representative entry for the region (which entry represents a region is the
   *     implementation's documented choice, for example the capital or the most populous record),
   *     or empty when the region is unknown to this gazetteer.
   * @throws IllegalArgumentException Thrown if {@code isoCountryCode} is {@code null}.
   * @throws IOException Thrown if a backing store or remote service fails. An in-memory
   *     implementation never throws it.
   */
  Optional<GazetteerEntry> byRegion(String isoCountryCode) throws IOException;

  /**
   * {@return the dataset identifiers this gazetteer serves; never {@code null} or empty} Every
   * {@link GazetteerEntry#source()} returned by the other methods is a member of this set.
   */
  Set<String> sources();
}
