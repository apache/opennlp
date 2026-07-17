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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the overlay semantics over two small user-format gazetteers: additions rank first,
 * suppressions hide base entries from every query but never hide additions, and the region
 * representative is resolved across both sides.
 */
public class OverlayGazetteerTest {

  private static final String BASE = String.join("\n",
      "mobile-city\tMobile\t\t30.69\t-88.04\tUS\tCITY\t187000",
      "mobile-bay\tMobile Bay\t\t30.44\t-88.01\tUS\tPOI\t0",
      "paris-fr\tParis\t\t48.85\t2.35\tFR\tCITY\t2100000",
      "paris-us\tParis\t\t33.66\t-95.56\tUS\tCITY\t25000",
      "") + "\n";

  private static final String ADDITIONS = String.join("\n",
      "plant-3\tAcme Plant 3\tParis\t33.75\t-84.39\tUS\tPOI\t0",
      "") + "\n";

  private static Gazetteer base() throws IOException {
    return UserGazetteer.load(
        new ByteArrayInputStream(BASE.getBytes(StandardCharsets.UTF_8)), "test");
  }

  private static Gazetteer additions() throws IOException {
    return UserGazetteer.load(
        new ByteArrayInputStream(ADDITIONS.getBytes(StandardCharsets.UTF_8)), "customer");
  }

  @Test
  void testAdditionsRankBeforeBaseCandidates() throws IOException {
    final OverlayGazetteer overlay = new OverlayGazetteer(base(), additions(), List.of());
    final List<GazetteerEntry> candidates = overlay.lookup("Paris");
    assertEquals(3, candidates.size());
    assertEquals("plant-3", candidates.get(0).recordId(),
        "the user's alternate name outranks the millions of Paris, France");
    assertEquals("paris-fr", candidates.get(1).recordId());
    assertEquals("paris-us", candidates.get(2).recordId());
  }

  @Test
  void testSuppressionHidesTheBaseEntryFromEveryQuery() throws IOException {
    final OverlayGazetteer overlay = new OverlayGazetteer(base(), null,
        List.of(new Suppression("Mobile", "US", GazetteerEntry.FEATURE_CLASS_CITY)));
    assertTrue(overlay.lookup("Mobile").isEmpty());
    assertEquals(Optional.empty(), overlay.byId("test", "mobile-city"));
    assertEquals("mobile-bay", overlay.lookup("Mobile Bay").get(0).recordId(),
        "the feature-class filter leaves the same-prefixed POI alone");
    assertEquals(Optional.empty(), overlay.byRegion("US"),
        "a suppressed representative removes the base's region candidate entirely");
  }

  /** Suppressing a name and adding a replacement is the override pattern: only the user's wins. */
  @Test
  void testSuppressionNeverHidesAdditions() throws IOException {
    final String replacement = "mobile-office\tMobile\t\t30.70\t-88.05\tUS\tPOI\t0\n";
    final Gazetteer additions = UserGazetteer.load(
        new ByteArrayInputStream(replacement.getBytes(StandardCharsets.UTF_8)), "customer");
    final OverlayGazetteer overlay =
        new OverlayGazetteer(base(), additions, List.of(new Suppression("Mobile")));
    final List<GazetteerEntry> candidates = overlay.lookup("Mobile");
    assertEquals(1, candidates.size());
    assertEquals("mobile-office", candidates.get(0).recordId());
  }

  @Test
  void testByIdConsultsAdditionsFirstAndKeepsSourcesApart() throws IOException {
    final OverlayGazetteer overlay = new OverlayGazetteer(base(), additions(), List.of());
    assertEquals("Acme Plant 3", overlay.byId("customer", "plant-3").orElseThrow().name());
    assertEquals("Mobile", overlay.byId("test", "mobile-city").orElseThrow().name());
    assertEquals(Optional.empty(), overlay.byId("customer", "mobile-city"));
    assertEquals(Set.of("customer", "test"), overlay.sources());
  }

  @Test
  void testByRegionPicksTheMorePopulousSideAndAdditionsWinTies() throws IOException {
    final OverlayGazetteer overlay = new OverlayGazetteer(base(), additions(), List.of());
    assertEquals("mobile-city", overlay.byRegion("US").orElseThrow().recordId(),
        "the base representative is more populous");
    final String populous = "megacity\tMegacity\t\t10.0\t10.0\tUS\tCITY\t99000000\n";
    final Gazetteer bigger = UserGazetteer.load(
        new ByteArrayInputStream(populous.getBytes(StandardCharsets.UTF_8)), "customer");
    assertEquals("megacity",
        new OverlayGazetteer(base(), bigger, List.of()).byRegion("US").orElseThrow().recordId());
    final String peer = "peer\tPeer\t\t10.0\t10.0\tUS\tCITY\t187000\n";
    final Gazetteer tied = UserGazetteer.load(
        new ByteArrayInputStream(peer.getBytes(StandardCharsets.UTF_8)), "customer");
    assertEquals("peer",
        new OverlayGazetteer(base(), tied, List.of()).byRegion("US").orElseThrow().recordId(),
        "additions win population ties");
  }

  /**
   * When every base candidate of a region is suppressed the base contributes nothing, because
   * the seam offers no second choice; the additions' representative stands alone.
   */
  @Test
  void testSuppressedRegionRepresentativeFallsBackToAdditions() throws IOException {
    final OverlayGazetteer overlay = new OverlayGazetteer(base(), additions(),
        List.of(new Suppression("Mobile"), new Suppression("Mobile Bay"),
            new Suppression("Paris", "US", null)));
    assertEquals("plant-3", overlay.byRegion("US").orElseThrow().recordId());
  }

  @Test
  void testRejectsInvalidArguments() throws IOException {
    final Gazetteer base = base();
    final Gazetteer additions = additions();
    assertThrows(IllegalArgumentException.class,
        () -> new OverlayGazetteer(null, additions, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new OverlayGazetteer(base, additions, null));
    final List<Suppression> holey = new ArrayList<>();
    holey.add(null);
    assertThrows(IllegalArgumentException.class,
        () -> new OverlayGazetteer(base, additions, holey));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new OverlayGazetteer(base, null, List.of()));
    assertTrue(e.getMessage().contains("changes nothing"), e.getMessage());
    final OverlayGazetteer overlay = new OverlayGazetteer(base, additions, List.of());
    assertThrows(IllegalArgumentException.class, () -> overlay.lookup(null));
    assertThrows(IllegalArgumentException.class, () -> overlay.byId(null, "x"));
    assertThrows(IllegalArgumentException.class, () -> overlay.byId("test", null));
  }
}
