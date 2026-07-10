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
 * The gazetteer seam: name, identifier, and region lookup over a set of place records.
 *
 * <p>This interface is the contract every location dataset sits behind. A bundled public-domain
 * table, a user-downloaded dataset, a user-ingested database, and a remote service client are all
 * implementations of this one seam, so a consumer written against {@code Gazetteer} never changes
 * when the data tier does. Nothing in the contract names or presumes a particular dataset; each
 * record's {@link GazetteerEntry#source() source} tag says where it came from.</p>
 *
 * <p>Name matching semantics (case and accent folding, tokenization) are the implementation's
 * concern. The reference implementations fold both indexed names and queries through the same
 * normalization chain used for text matching elsewhere in OpenNLP (NFC, case fold, accent fold),
 * which is the recommended behavior; an implementation with different semantics must document
 * them.</p>
 *
 * <p>Relationship to {@link opennlp.tools.entitylinker.EntityLinker}: that interface is the
 * generic entity enrichment contract (any entity type, initialization through properties,
 * sentence-segmented input), and its implementations may well be backed by a {@code Gazetteer}.
 * New consumers that look up place records or resolve location mentions should target this
 * interface and {@link Geocoder}; {@code EntityLinker} remains the contract for generic entity
 * enrichment beyond locations.</p>
 *
 * <p>Implementations must be immutable and thread-safe after construction: one instance is meant
 * to be shared across an application's threads for concurrent lookups.</p>
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
   * <p>The alpha-2 code is a stable join key across annotation layers: any region-level signal
   * that decodes to the same code (a flag emoji does, for example) can resolve against the same
   * lookup without any dataset-specific identifier.</p>
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
