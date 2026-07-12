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
import java.util.List;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoResolution;
import opennlp.tools.geo.Geocoder;
import opennlp.tools.util.Span;

/**
 * A {@link Geocoder} that resolves each mention by a population prior: candidates come from
 * {@link Gazetteer#lookup(CharSequence)} and the winner is the first under the module's
 * deterministic candidate order (population descending, then a CITY/ADMIN/POI feature-class prior
 * on population ties, then source and record id). Multi-candidate lists are re-sorted here, so the
 * result does not depend on the gazetteer's own return order.
 *
 * <p>Confidence is a documented heuristic, not a probability. A single candidate scores
 * {@code 0.9}; several score {@code 0.5 + 0.4 * (p1 - p2) / (p1 + p2)} over the winner and
 * runner-up populations {@code p1} and {@code p2} ({@code 0.5} when both are unknown), clamped to
 * {@code [0, 1]} and never {@code 1.0}.</p>
 *
 * <p>Mentions are resolved independently and in input order; a mention with no candidates is
 * omitted from the result. Instances are immutable and thread-safe when the supplied
 * {@link Gazetteer} is.</p>
 */
public final class PopulationPriorGeocoder implements Geocoder {

  private static final double SINGLE_CANDIDATE_CONFIDENCE = 0.9;
  private static final double TIE_CONFIDENCE = 0.5;
  private static final double SEPARATION_WEIGHT = 0.4;

  private final Gazetteer gazetteer;

  /**
   * Creates a geocoder over the given gazetteer.
   *
   * @param gazetteer The gazetteer to resolve against. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code gazetteer} is {@code null}.
   */
  public PopulationPriorGeocoder(Gazetteer gazetteer) {
    if (gazetteer == null) {
      throw new IllegalArgumentException("Gazetteer must not be null");
    }
    this.gazetteer = gazetteer;
  }

  /** {@inheritDoc} */
  @Override
  public List<GeoResolution> resolve(CharSequence text, List<Span> locationMentions)
      throws IOException {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    if (locationMentions == null) {
      throw new IllegalArgumentException("LocationMentions must not be null");
    }
    for (final Span mention : locationMentions) {
      if (mention == null) {
        throw new IllegalArgumentException(
            "LocationMentions must not contain a null element, got: " + locationMentions);
      }
      if (mention.getEnd() > text.length()) {
        throw new IllegalArgumentException("Mention " + mention
            + " is outside the text, whose length is " + text.length());
      }
    }
    final List<GeoResolution> resolutions = new ArrayList<>(locationMentions.size());
    for (final Span mention : locationMentions) {
      final CharSequence mentionText = text.subSequence(mention.getStart(), mention.getEnd());
      final List<GazetteerEntry> found = gazetteer.lookup(mentionText);
      if (found.isEmpty()) {
        continue; // unresolved mentions are omitted, never fabricated
      }
      final List<GazetteerEntry> candidates;
      if (found.size() == 1) {
        candidates = found;
      } else {
        // Re-sort: the Gazetteer contract only promises a best-effort ranking, so the result
        // must not depend on the implementation's own return order (OPENNLP-1879).
        final List<GazetteerEntry> ranked = new ArrayList<>(found);
        ranked.sort(CandidateRanking.BY_PRIOR);
        candidates = ranked;
      }
      resolutions.add(new GeoResolution(mention, candidates.get(0), confidence(candidates)));
    }
    return resolutions;
  }

  /**
   * {@return the heuristic confidence for the ranked candidates} Monotonic in the relative
   * population separation between the winner and the runner-up, computed in {@code double} so
   * populations near {@link Long#MAX_VALUE} cannot overflow, and clamped to the {@code [0, 1]}
   * contract of {@link GeoResolution}.
   */
  private static double confidence(List<GazetteerEntry> rankedCandidates) {
    if (rankedCandidates.size() == 1) {
      return SINGLE_CANDIDATE_CONFIDENCE;
    }
    final double first = rankedCandidates.get(0).population();
    final double second = rankedCandidates.get(1).population();
    final double total = first + second;
    if (total == 0.0) {
      return TIE_CONFIDENCE;
    }
    final double raw = TIE_CONFIDENCE + SEPARATION_WEIGHT * ((first - second) / total);
    return Math.min(1.0, Math.max(0.0, raw));
  }
}
