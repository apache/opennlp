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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;
import opennlp.tools.geo.GeoResolution;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PopulationPriorGeocoderTest {

  private static final String[] ROWS = {
      "naturalearth;1;Springfield;;39.79968;-89.64399;US;Illinois;750000;CITY;",
      "naturalearth;2;Springfield;;37.18095;-93.29229;US;Missouri;450000;CITY;",
      "naturalearth;3;Springfield;;42.10148;-72.58982;US;Massachusetts;680000;CITY;",
      "naturalearth;4;Solo;;-7.55611;110.83167;ID;Central Java;100000;CITY;",
      "naturalearth;5;Tietown;;10.0;10.0;;;5000;ADMIN;",
      "naturalearth;6;Tietown;;11.0;11.0;;;5000;CITY;",
      "naturalearth;7;Ghostville;;20.0;20.0;;;0;CITY;",
      "naturalearth;8;Ghostville;;21.0;21.0;;;0;CITY;",
  };

  private static BundledGazetteer fixtureGazetteer() {
    final StringBuilder data = new StringBuilder();
    for (final String row : ROWS) {
      data.append(row).append('\n');
    }
    final InputStream in =
        new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.UTF_8));
    try {
      return new BundledGazetteer(BundledGazetteer.parse(in, "test-gazetteer.txt"));
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected IOException from an in-memory stream", e);
    }
  }

  private static PopulationPriorGeocoder geocoder() {
    return new PopulationPriorGeocoder(fixtureGazetteer());
  }

  private static Span mentionOf(String text, String mention) {
    final int start = text.indexOf(mention);
    assertTrue(start >= 0, () -> "Mention " + mention + " not found in " + text);
    return new Span(start, start + mention.length());
  }

  @Test
  void testResolvesAmbiguityByPopulation() throws IOException {
    final String text = "She grew up in Springfield.";
    final Span mention = mentionOf(text, "Springfield");
    final List<GeoResolution> resolutions = geocoder().resolve(text, List.of(mention));
    assertEquals(1, resolutions.size());
    assertEquals("1", resolutions.get(0).entry().recordId()); // the most populous Springfield
  }

  @Test
  void testUnresolvedMentionOmitted() throws IOException {
    final String text = "From Springfield to Atlantis.";
    final Span springfield = mentionOf(text, "Springfield");
    final Span atlantis = mentionOf(text, "Atlantis");
    final List<GeoResolution> resolutions =
        geocoder().resolve(text, List.of(springfield, atlantis));
    assertEquals(1, resolutions.size());
    assertSame(springfield, resolutions.get(0).mention());
  }

  @Test
  void testResultsAlignedToInputSpansInInputOrder() throws IOException {
    final String text = "Solo, then Springfield, then Tietown.";
    final Span solo = mentionOf(text, "Solo");
    final Span springfield = mentionOf(text, "Springfield");
    final Span tietown = mentionOf(text, "Tietown");
    final List<GeoResolution> resolutions =
        geocoder().resolve(text, List.of(solo, springfield, tietown));
    assertEquals(3, resolutions.size());
    assertSame(solo, resolutions.get(0).mention());
    assertSame(springfield, resolutions.get(1).mention());
    assertSame(tietown, resolutions.get(2).mention());
  }

  @Test
  void testSingleCandidateConfidence() throws IOException {
    final String text = "Landed in Solo today.";
    final List<GeoResolution> resolutions =
        geocoder().resolve(text, List.of(mentionOf(text, "Solo")));
    assertEquals(0.9, resolutions.get(0).confidence());
  }

  @Test
  void testTieBrokenByFeatureClassAtTieConfidence() throws IOException {
    final String text = "Tietown was quiet.";
    final List<GeoResolution> resolutions =
        geocoder().resolve(text, List.of(mentionOf(text, "Tietown")));
    assertEquals(1, resolutions.size());
    assertEquals("6", resolutions.get(0).entry().recordId()); // CITY wins the dead tie
    assertEquals(0.5, resolutions.get(0).confidence()); // and a dead tie is never confident
  }

  @Test
  void testUnknownPopulationsScoreAsTie() throws IOException {
    final String text = "Ghostville again.";
    final List<GeoResolution> resolutions =
        geocoder().resolve(text, List.of(mentionOf(text, "Ghostville")));
    assertEquals(0.5, resolutions.get(0).confidence());
    assertEquals("7", resolutions.get(0).entry().recordId()); // record id breaks the full tie
  }

  @Test
  void testConfidenceIsTheDocumentedSeparationFunction() throws IOException {
    final String text = "Back to Springfield.";
    final List<GeoResolution> resolutions =
        geocoder().resolve(text, List.of(mentionOf(text, "Springfield")));
    // Winner 750000, runner-up 680000: 0.5 + 0.4 * (70000 / 1430000).
    final double expected = 0.5 + 0.4 * (70000.0 / 1430000.0);
    assertEquals(expected, resolutions.get(0).confidence());
    assertTrue(resolutions.get(0).confidence() > 0.5);
    assertTrue(resolutions.get(0).confidence() < 0.9);
  }

  @Test
  void testEmptyMentionListAndEmptySpanResolveToNothing() throws IOException {
    final PopulationPriorGeocoder geocoder = geocoder();
    assertTrue(geocoder.resolve("Springfield", List.of()).isEmpty());
    assertTrue(geocoder.resolve("Springfield", List.of(new Span(3, 3))).isEmpty());
  }

  @Test
  void testDeterministicAcrossRuns() throws IOException {
    final String text = "Springfield, Solo, Tietown, Ghostville, and Atlantis.";
    final List<Span> mentions = List.of(
        mentionOf(text, "Springfield"), mentionOf(text, "Solo"), mentionOf(text, "Tietown"),
        mentionOf(text, "Ghostville"), mentionOf(text, "Atlantis"));
    final List<GeoResolution> first = geocoder().resolve(text, mentions);
    final List<GeoResolution> second = geocoder().resolve(text, mentions);
    assertEquals(first, second);
    assertEquals(4, first.size()); // Atlantis omitted
  }

  @Test
  void testDoesNotDependOnTheGazetteerReturnOrder() throws IOException {
    // A gazetteer that returns candidates worst-first: the geocoder re-ranks and still picks
    // the most populous entry.
    final GazetteerEntry small = new GazetteerEntry("stub", "small", "Springfield", List.of(),
        new GeoPoint(0.0, 0.0), null, List.of(), 1000L,
        GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
    final GazetteerEntry large = new GazetteerEntry("stub", "large", "Springfield", List.of(),
        new GeoPoint(1.0, 1.0), null, List.of(), 2000L,
        GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
    final Gazetteer worstFirst = fixedLookupGazetteer(small, large);
    final String text = "Springfield";
    final List<GeoResolution> resolutions = new PopulationPriorGeocoder(worstFirst)
        .resolve(text, List.of(new Span(0, text.length())));
    assertEquals("large", resolutions.get(0).entry().recordId());
  }

  @Test
  void testResolvesAgainstTheBundledTable() throws IOException {
    final String text = "The flight goes from Paris to Springfield.";
    final Span paris = mentionOf(text, "Paris");
    final Span springfield = mentionOf(text, "Springfield");
    final PopulationPriorGeocoder geocoder =
        new PopulationPriorGeocoder(BundledGazetteer.getInstance());
    final List<GeoResolution> resolutions = geocoder.resolve(text, List.of(paris, springfield));
    assertEquals(2, resolutions.size());
    assertEquals("FR", resolutions.get(0).entry().countryCode()); // not Paris, Texas
    assertEquals("US", resolutions.get(1).entry().countryCode());
    assertEquals(List.of("Massachusetts"), resolutions.get(1).entry().containment());
    for (final GeoResolution resolution : resolutions) {
      assertTrue(resolution.confidence() >= 0.0 && resolution.confidence() <= 1.0);
    }
  }

  @Test
  void testConfidenceStaysInRangeForAdversarialPopulations() throws IOException {
    // Populations near Long.MAX_VALUE would overflow long arithmetic (first + second turns
    // negative); the confidence is computed in double and clamped, so the [0, 1] contract of
    // GeoResolution holds even for an adversarial gazetteer.
    final String text = "Overflowville";
    final List<GeoResolution> tie = new PopulationPriorGeocoder(fixedLookupGazetteer(
        entryWithPopulation("adv-1", Long.MAX_VALUE),
        entryWithPopulation("adv-2", Long.MAX_VALUE)))
        .resolve(text, List.of(new Span(0, text.length())));
    assertEquals(0.5, tie.get(0).confidence()); // a dead tie at maximal magnitude

    final List<GeoResolution> dominant = new PopulationPriorGeocoder(fixedLookupGazetteer(
        entryWithPopulation("adv-max", Long.MAX_VALUE),
        entryWithPopulation("adv-one", 1L)))
        .resolve(text, List.of(new Span(0, text.length())));
    final double confidence = dominant.get(0).confidence();
    assertTrue(confidence >= 0.0 && confidence <= 1.0, "Out of range: " + confidence);
    assertTrue(confidence > 0.5, "A dominant winner must score above the tie: " + confidence);
    assertEquals("adv-max", dominant.get(0).entry().recordId());
  }

  @Test
  void testThirdPartyGazetteerUsingTheConstantsGetsTheFeatureClassPrior() throws IOException {
    // A gazetteer that labels its records with the published FEATURE_CLASS_* constants gets
    // the CITY-over-ADMIN tie-break on equal population, even though it is not the bundled
    // implementation and returns its candidates ADMIN first.
    final GazetteerEntry admin = new GazetteerEntry("ext", "admin-1", "Twinsburg", List.of(),
        new GeoPoint(41.3, -81.4), "US", List.of(), 5000L,
        GazetteerEntry.FEATURE_CLASS_ADMIN, Map.of());
    final GazetteerEntry city = new GazetteerEntry("ext", "city-1", "Twinsburg", List.of(),
        new GeoPoint(41.4, -81.5), "US", List.of(), 5000L,
        GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
    final String text = "Twinsburg";
    final List<GeoResolution> resolutions = new PopulationPriorGeocoder(
        fixedLookupGazetteer(admin, city))
        .resolve(text, List.of(new Span(0, text.length())));
    assertEquals("city-1", resolutions.get(0).entry().recordId());
    assertEquals(0.5, resolutions.get(0).confidence()); // still a population dead tie
  }

  private static GazetteerEntry entryWithPopulation(String recordId, long population) {
    return new GazetteerEntry("adv", recordId, "Overflowville", List.of(),
        new GeoPoint(0.0, 0.0), null, List.of(), population,
        GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
  }

  // A minimal third-party Gazetteer: every lookup returns the given entries in the given order.
  private static Gazetteer fixedLookupGazetteer(GazetteerEntry... entries) {
    final List<GazetteerEntry> result = List.of(entries);
    return new Gazetteer() {
      @Override
      public List<GazetteerEntry> lookup(CharSequence name) {
        return result;
      }

      @Override
      public Optional<GazetteerEntry> byId(String source, String recordId) {
        return Optional.empty();
      }

      @Override
      public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
        return Optional.empty();
      }

      @Override
      public Set<String> sources() {
        return Set.of(result.get(0).source());
      }
    };
  }

  @Test
  void testConstructorRejectsNullGazetteer() {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new PopulationPriorGeocoder(null));
    assertTrue(e.getMessage().startsWith("Gazetteer must not be null"), e.getMessage());
  }

  @Test
  void testResolveValidatesArguments() {
    final PopulationPriorGeocoder geocoder = geocoder();
    final IllegalArgumentException nullText = assertThrows(IllegalArgumentException.class,
        () -> geocoder.resolve(null, List.of()));
    assertTrue(nullText.getMessage().startsWith("Text must not be null"), nullText.getMessage());

    final IllegalArgumentException nullMentions = assertThrows(IllegalArgumentException.class,
        () -> geocoder.resolve("text", null));
    assertTrue(nullMentions.getMessage().startsWith("LocationMentions must not be null"),
        nullMentions.getMessage());

    final IllegalArgumentException nullElement = assertThrows(IllegalArgumentException.class,
        () -> geocoder.resolve("text", Arrays.asList(new Span(0, 1), null)));
    assertTrue(nullElement.getMessage().startsWith("LocationMentions must not contain a null"),
        nullElement.getMessage());

    final IllegalArgumentException outOfBounds = assertThrows(IllegalArgumentException.class,
        () -> geocoder.resolve("abc", List.of(new Span(0, 4))));
    assertTrue(outOfBounds.getMessage().contains("outside the text"), outOfBounds.getMessage());
  }
}
