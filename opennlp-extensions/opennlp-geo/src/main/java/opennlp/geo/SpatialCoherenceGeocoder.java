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
import opennlp.tools.geo.GeoPoint;
import opennlp.tools.geo.GeoResolution;
import opennlp.tools.geo.Geocoder;
import opennlp.tools.util.Span;

/**
 * A {@link Geocoder} that disambiguates by spatial coherence: among a mention's
 * candidates, prefer the one closest to where the document's other mentions point,
 * because places named together tend to lie together.
 *
 * <p>Resolution runs in two passes. The first pass picks every mention's
 * population-prior candidate, giving a provisional picture of the document. The second
 * pass revisits each ambiguous mention and chooses the candidate with the smallest mean
 * great-circle distance to the other mentions' provisional picks, with the prior as the
 * tie-breaker. A mention with no other resolved mention to cohere with keeps its prior
 * pick, so a lone {@code Paris} stays the population favorite while a {@code Paris}
 * next to {@code Dallas} and {@code Houston} moves to Texas.</p>
 *
 * <p>Confidence reflects how decisive the choice was: a single candidate scores high,
 * and an ambiguous one scores by the separation between the best and second-best mean
 * distance, or between the top population priors when no coherence evidence exists.
 * Unresolved mentions are omitted, never fabricated. Instances hold no per-call state
 * and are safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public final class SpatialCoherenceGeocoder implements Geocoder {

  private static final double SINGLE_CANDIDATE_CONFIDENCE = 0.9;
  private static final double BASE_CONFIDENCE = 0.5;
  private static final double SEPARATION_WEIGHT = 0.4;
  private static final double EARTH_RADIUS_KM = 6371.0;

  private final Gazetteer gazetteer;

  /**
   * Initializes the geocoder.
   *
   * @param gazetteer The gazetteer to resolve against. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code gazetteer} is {@code null}.
   */
  public SpatialCoherenceGeocoder(Gazetteer gazetteer) {
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

    // first pass: candidates per mention, provisional pick by the population prior
    final List<Span> resolvedMentions = new ArrayList<>();
    final List<List<GazetteerEntry>> candidatesPerMention = new ArrayList<>();
    for (final Span mention : locationMentions) {
      final CharSequence mentionText = text.subSequence(mention.getStart(), mention.getEnd());
      final List<GazetteerEntry> found = gazetteer.lookup(mentionText);
      if (found.isEmpty()) {
        continue; // unresolved mentions are omitted, never fabricated
      }
      final List<GazetteerEntry> ranked = new ArrayList<>(found);
      ranked.sort(CandidateRanking.BY_PRIOR);
      resolvedMentions.add(mention);
      candidatesPerMention.add(ranked);
    }

    // second pass: move each ambiguous mention to its most coherent candidate
    final List<GeoResolution> resolutions = new ArrayList<>(resolvedMentions.size());
    for (int i = 0; i < resolvedMentions.size(); i++) {
      final List<GazetteerEntry> candidates = candidatesPerMention.get(i);
      if (candidates.size() == 1) {
        resolutions.add(new GeoResolution(resolvedMentions.get(i), candidates.get(0),
            SINGLE_CANDIDATE_CONFIDENCE));
        continue;
      }
      final List<GeoPoint> context = new ArrayList<>(resolvedMentions.size() - 1);
      for (int other = 0; other < resolvedMentions.size(); other++) {
        if (other != i) {
          context.add(candidatesPerMention.get(other).get(0).location());
        }
      }
      resolutions.add(resolveAmbiguous(resolvedMentions.get(i), candidates, context));
    }
    return resolutions;
  }

  /** Chooses among candidates by mean distance to the context, prior as tie-breaker. */
  private static GeoResolution resolveAmbiguous(Span mention,
      List<GazetteerEntry> candidates, List<GeoPoint> context) {
    if (context.isEmpty()) {
      return new GeoResolution(mention, candidates.get(0),
          BASE_CONFIDENCE + SEPARATION_WEIGHT * populationSeparation(candidates));
    }
    GazetteerEntry best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    double secondDistance = Double.POSITIVE_INFINITY;
    for (final GazetteerEntry candidate : candidates) {
      final double distance = meanDistanceKm(candidate.location(), context);
      if (distance < bestDistance) {
        secondDistance = bestDistance;
        bestDistance = distance;
        best = candidate;
      } else if (distance < secondDistance) {
        secondDistance = distance;
      }
    }
    final double separation = secondDistance == 0.0
        ? 0.0 : 1.0 - (bestDistance / secondDistance);
    return new GeoResolution(mention, best,
        BASE_CONFIDENCE + SEPARATION_WEIGHT * separation);
  }

  /** How decisively the top population beats the runner-up, in {@code [0, 1]}. */
  private static double populationSeparation(List<GazetteerEntry> candidates) {
    final double first = candidates.get(0).population();
    final double second = candidates.get(1).population();
    return first <= 0.0 ? 0.0 : 1.0 - (second / first);
  }

  private static double meanDistanceKm(GeoPoint from, List<GeoPoint> context) {
    double sum = 0.0;
    for (final GeoPoint to : context) {
      sum += distanceKm(from, to);
    }
    return sum / context.size();
  }

  /** The great-circle distance via the haversine formula. */
  private static double distanceKm(GeoPoint from, GeoPoint to) {
    final double latDelta = Math.toRadians(to.latitude() - from.latitude());
    final double lonDelta = Math.toRadians(to.longitude() - from.longitude());
    final double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
        + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
        * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
  }
}
