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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;

/**
 * A {@link Gazetteer} composing a base gazetteer with user-supplied changes: additions from a
 * second gazetteer, and {@link Suppression} rules hiding base entries. Consumers see one merged
 * view, so every component downstream of the gazetteer seam picks the changes up without knowing
 * they exist.
 *
 * <p>The additions side is any {@link Gazetteer} implementation: a {@link UserGazetteer} loaded
 * from a file, an entry set ingested from a database, or a remote service client. Additions rank
 * first in {@link #lookup(CharSequence)}, before the base candidates; each side keeps its own
 * internal ranking.</p>
 *
 * <p>Suppressions hide entries of the base only, never additions, so a rule plus an addition
 * with the same name replaces a base entry. A suppressed entry is hidden from every query,
 * including {@link #byId(String, String)} and {@link #byRegion(String)}.</p>
 *
 * <p>Instances are immutable and as thread-safe as the composed gazetteers, which the
 * {@link Gazetteer} contract requires to be thread-safe themselves.</p>
 */
@ThreadSafe
public final class OverlayGazetteer implements Gazetteer {

  private final Gazetteer base;
  private final Gazetteer additions;
  private final List<Suppression> suppressions;

  /**
   * Initializes the overlay.
   *
   * @param base         The gazetteer being overlaid. Must not be {@code null}.
   * @param additions    The gazetteer whose entries overlay the base, or {@code null} when the
   *                     overlay only suppresses.
   * @param suppressions The rules hiding base entries, possibly empty. Must not be {@code null}
   *                     or contain {@code null} elements.
   * @throws IllegalArgumentException Thrown if {@code base} or {@code suppressions} is
   *     {@code null}, {@code suppressions} contains a {@code null} element, or the overlay would
   *     change nothing because {@code additions} is {@code null} and {@code suppressions} is
   *     empty.
   */
  public OverlayGazetteer(Gazetteer base, Gazetteer additions, List<Suppression> suppressions) {
    if (base == null) {
      throw new IllegalArgumentException("base must not be null");
    }
    if (suppressions == null) {
      throw new IllegalArgumentException("suppressions must not be null");
    }
    for (final Suppression suppression : suppressions) {
      if (suppression == null) {
        throw new IllegalArgumentException("suppressions must not contain a null element");
      }
    }
    if (additions == null && suppressions.isEmpty()) {
      throw new IllegalArgumentException(
          "An overlay without additions and without suppressions changes nothing; "
              + "use the base gazetteer directly");
    }
    this.base = base;
    this.additions = additions;
    this.suppressions = List.copyOf(suppressions);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Candidates from the additions come first, then the base candidates that no suppression
   * matches; each side keeps its own documented ranking.</p>
   */
  @Override
  public List<GazetteerEntry> lookup(CharSequence name) throws IOException {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    final List<GazetteerEntry> merged = new ArrayList<>();
    if (additions != null) {
      merged.addAll(additions.lookup(name));
    }
    for (final GazetteerEntry entry : base.lookup(name)) {
      if (!suppressed(entry)) {
        merged.add(entry);
      }
    }
    return Collections.unmodifiableList(merged);
  }

  /**
   * {@inheritDoc}
   *
   * <p>The additions are consulted first; a base record is returned only when no suppression
   * matches it.</p>
   */
  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) throws IOException {
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    if (recordId == null) {
      throw new IllegalArgumentException("recordId must not be null");
    }
    if (additions != null) {
      final Optional<GazetteerEntry> added = additions.byId(source, recordId);
      if (added.isPresent()) {
        return added;
      }
    }
    return base.byId(source, recordId).filter(entry -> !suppressed(entry));
  }

  /**
   * {@inheritDoc}
   *
   * <p>The representative is the more populous of the two sides' representatives, the additions
   * winning ties. When the base's representative is suppressed the base contributes no candidate
   * at all, because the seam offers no second choice; the additions' representative, when there
   * is one, then stands alone.</p>
   */
  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) throws IOException {
    final Optional<GazetteerEntry> fromBase =
        base.byRegion(isoCountryCode).filter(entry -> !suppressed(entry));
    if (additions == null) {
      return fromBase;
    }
    final Optional<GazetteerEntry> fromAdditions = additions.byRegion(isoCountryCode);
    if (fromAdditions.isEmpty() || fromBase.isEmpty()) {
      return fromAdditions.isPresent() ? fromAdditions : fromBase;
    }
    return fromAdditions.get().population() >= fromBase.get().population()
        ? fromAdditions : fromBase;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> sources() {
    final Set<String> union = new LinkedHashSet<>();
    if (additions != null) {
      union.addAll(additions.sources());
    }
    union.addAll(base.sources());
    return Collections.unmodifiableSet(union);
  }

  /** {@return {@code true} if any suppression rule matches {@code entry}} */
  private boolean suppressed(GazetteerEntry entry) {
    for (final Suppression suppression : suppressions) {
      if (suppression.matches(entry)) {
        return true;
      }
    }
    return false;
  }
}
