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
import java.util.List;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoResolution;
import opennlp.tools.geo.Geocoder;
import opennlp.tools.util.Span;

/**
 * A {@link Geocoder} that resolves each mention by a population prior: candidates come from
 * {@link Gazetteer#lookup(CharSequence)} and the winner is the first under the module's
 * deterministic candidate order (population descending, then the {@code CITY} over {@code ADMIN}
 * over {@code POI} feature-class prior on population ties, then source and record id). The
 * candidates are re-sorted here, so the result does not depend on the gazetteer's own return
 * order.
 *
 * <p><b>Confidence is a documented heuristic, not a probability.</b> For a single candidate it
 * is {@code 0.9}; for several it is {@code 0.5 + 0.4 * (p1 - p2) / (p1 + p2)}, where {@code p1}
 * and {@code p2} are the populations of the winner and the runner-up ({@code 0.5} when both are
 * unknown). The value is monotonic in the relative population separation: a dead tie scores
 * {@code 0.5}, a dominant winner approaches {@code 0.9}, and {@code 1.0} is never reported
 * because a prior with no context cannot certify a resolution. Document-context-aware scoring
 * (co-occurring mentions constraining each other) is the named follow-up, as a second
 * {@link Geocoder} implementation behind the same contract; the whole document is already part
 * of {@link #resolve(CharSequence, List)} for exactly that reason, and this implementation
 * deliberately does not read it beyond the mention spans.</p>
 *
 * <p>Mentions are resolved independently and in input order; a mention with no candidates is
 * omitted from the result. Instances are immutable and thread-safe when the supplied
 * {@link Gazetteer} is (the bundled one is).</p>
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

  @Override
  public List<GeoResolution> resolve(CharSequence text, List<Span> locationMentions) {
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
      final List<GazetteerEntry> candidates = new ArrayList<>(gazetteer.lookup(mentionText));
      if (candidates.isEmpty()) {
        continue; // unresolved mentions are omitted, never fabricated
      }
      candidates.sort(CandidateRanking.BY_PRIOR);
      resolutions.add(new GeoResolution(mention, candidates.get(0), confidence(candidates)));
    }
    return resolutions;
  }

  // The heuristic prior documented in the class javadoc: monotonic in the relative population
  // separation between the winner and the runner-up, bounded away from certainty.
  private static double confidence(List<GazetteerEntry> rankedCandidates) {
    if (rankedCandidates.size() == 1) {
      return SINGLE_CANDIDATE_CONFIDENCE;
    }
    final long first = rankedCandidates.get(0).population();
    final long second = rankedCandidates.get(1).population();
    final long total = first + second;
    if (total == 0) {
      return TIE_CONFIDENCE;
    }
    return TIE_CONFIDENCE + SEPARATION_WEIGHT * ((double) (first - second) / total);
  }
}
