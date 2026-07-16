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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.geo.AttributeValue;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BundledGazetteerTest {

  // Code point helpers: source files are pure ASCII, non-ASCII test input is built from escapes.
  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  private static final String U_UMLAUT = cp(0x00FC);           // u with diaeresis, composed
  private static final String COMBINING_DIAERESIS = cp(0x0308);
  private static final String A_TILDE = cp(0x00E3);            // a with tilde, composed
  private static final String E_ACUTE = cp(0x00E9);            // e with acute, composed

  private static final String[] ROWS = {
      "naturalearth;1;Zurich;Zuerich;47.36667;8.55;CH;Zurich;1108000;CITY;wikidata=Q72",
      "naturalearth;2;Sao Paulo;;-23.55;-46.63333;BR;Sao Paulo;18845000;CITY;",
      "naturalearth;3;Springfield;;39.79968;-89.64399;US;Illinois;750000;CITY;",
      "naturalearth;4;Springfield;;37.18095;-93.29229;US;Missouri;450000;CITY;",
      "naturalearth;5;Montreal;;45.5;-73.58333;CA;Quebec;3678000;CITY;geonames=6077243",
      "test2;6;Springfield;;42.10148;-72.58982;US;Massachusetts;680000;ADMIN;",
      "naturalearth;7;Tietown;;10.0;10.0;;;5000;ADMIN;",
      "naturalearth;8;Tietown;;11.0;11.0;;;5000;CITY;",
      "naturalearth;9;Winston-Salem;;36.09986;-80.24422;US;North Carolina;615000;CITY;",
  };

  private static BundledGazetteer gazetteer(String... rows) {
    final StringBuilder data = new StringBuilder("# comment line\n\n");
    for (final String row : rows) {
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

  private static BundledGazetteer fixture() {
    return gazetteer(ROWS);
  }

  @Test
  void testParsesAllFields() {
    final GazetteerEntry zurich = fixture().byId("naturalearth", "1").orElseThrow();
    assertEquals("naturalearth", zurich.source());
    assertEquals("1", zurich.recordId());
    assertEquals("Zurich", zurich.name());
    assertEquals(List.of("Zuerich"), zurich.alternateNames());
    assertEquals(47.36667, zurich.location().latitude());
    assertEquals(8.55, zurich.location().longitude());
    assertEquals("CH", zurich.countryCode());
    assertEquals(List.of("Zurich"), zurich.containment());
    assertEquals(1108000L, zurich.population());
    assertEquals("CITY", zurich.featureClass());
    final AttributeValue wikidata = zurich.attributes().get("wikidata");
    assertEquals("Q72", wikidata.value());
    assertEquals("naturalearth", wikidata.source());
    assertEquals("", wikidata.notes());
  }

  @Test
  void testParsesEmptyOptionalFields() {
    final GazetteerEntry tietown = fixture().byId("naturalearth", "7").orElseThrow();
    assertTrue(tietown.alternateNames().isEmpty());
    assertNull(tietown.countryCode());
    assertTrue(tietown.containment().isEmpty());
    assertTrue(tietown.attributes().isEmpty());
  }

  @Test
  void testLookupExact() {
    assertEquals("1", singleResultId(fixture(), "Zurich"));
  }

  @Test
  void testLookupCaseFolded() {
    assertEquals("1", singleResultId(fixture(), "ZURICH"));
    assertEquals("2", singleResultId(fixture(), "SAO PAULO"));
  }

  @Test
  void testLookupAccentFoldedComposedFindsAsciiRow() {
    // Z + u-with-diaeresis + rich, S + a-with-tilde + o Paulo, Montr + e-with-acute + al: an
    // accented query finds the ASCII row through the shared folding chain.
    assertEquals("1", singleResultId(fixture(), "Z" + U_UMLAUT + "rich"));
    assertEquals("2", singleResultId(fixture(), "S" + A_TILDE + "o Paulo"));
    assertEquals("5", singleResultId(fixture(), "Montr" + E_ACUTE + "al"));
  }

  @Test
  void testLookupAccentFoldedDecomposedFindsAsciiRow() {
    // Zu + combining diaeresis + rich: NFC composes first, then the accent fold strips the mark.
    assertEquals("1", singleResultId(fixture(), "Zu" + COMBINING_DIAERESIS + "rich"));
  }

  @Test
  void testLookupByAlternateName() {
    assertEquals("1", singleResultId(fixture(), "Zuerich"));
  }

  @Test
  void testLookupHyphenationVariants() {
    final BundledGazetteer gazetteer = fixture();
    assertEquals("9", singleResultId(gazetteer, "Winston-Salem"));
    assertEquals("9", singleResultId(gazetteer, "Winston Salem"));
    assertEquals("9", singleResultId(gazetteer, "winston salem"));
  }

  @Test
  void testLookupRanksByPopulationDescending() {
    final List<GazetteerEntry> springfields = fixture().lookup("Springfield");
    assertEquals(3, springfields.size());
    assertEquals("3", springfields.get(0).recordId()); // 750000
    assertEquals("6", springfields.get(1).recordId()); // 680000
    assertEquals("4", springfields.get(2).recordId()); // 450000
  }

  @Test
  void testLookupBreaksPopulationTiesByFeatureClass() {
    final List<GazetteerEntry> tietowns = fixture().lookup("Tietown");
    assertEquals(2, tietowns.size());
    assertEquals("8", tietowns.get(0).recordId()); // CITY before ADMIN on equal population
    assertEquals("7", tietowns.get(1).recordId());
  }

  @Test
  void testLookupUnknownNameReturnsEmpty() {
    assertTrue(fixture().lookup("Atlantis").isEmpty());
  }

  @Test
  void testLookupNameWithoutWordTokensReturnsEmpty() {
    assertTrue(fixture().lookup("...").isEmpty());
  }

  @Test
  void testLookupResultIsImmutable() {
    final List<GazetteerEntry> result = fixture().lookup("Zurich");
    assertThrows(UnsupportedOperationException.class, () -> result.remove(0));
  }

  @Test
  void testLookupNullFailsLoud() {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> fixture().lookup(null));
    assertTrue(e.getMessage().startsWith("Name must not be null"), e.getMessage());
  }

  @Test
  void testByIdAbsent() {
    final BundledGazetteer gazetteer = fixture();
    assertEquals(Optional.empty(), gazetteer.byId("naturalearth", "999"));
    assertEquals(Optional.empty(), gazetteer.byId("unknownsource", "1"));
  }

  @Test
  void testByIdNullFailsLoud() {
    final BundledGazetteer gazetteer = fixture();
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byId(null, "1"));
    assertThrows(IllegalArgumentException.class, () -> gazetteer.byId("naturalearth", null));
  }

  @Test
  void testByRegionReturnsMostPopulousEntry() {
    final BundledGazetteer gazetteer = fixture();
    assertEquals("1", gazetteer.byRegion("CH").orElseThrow().recordId());
    assertEquals("3", gazetteer.byRegion("US").orElseThrow().recordId()); // most populous US row
  }

  @Test
  void testByRegionAcceptsLowerCase() {
    assertEquals("1", fixture().byRegion("ch").orElseThrow().recordId());
  }

  @Test
  void testByRegionUnknownReturnsEmpty() {
    assertEquals(Optional.empty(), fixture().byRegion("XX"));
  }

  @Test
  void testByRegionNullFailsLoud() {
    assertThrows(IllegalArgumentException.class, () -> fixture().byRegion(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "C", "CHE", "C1"})
  void testByRegionMalformedCodeFailsLoud(String malformed) {
    final BundledGazetteer gazetteer = fixture();
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> gazetteer.byRegion(malformed));
    assertTrue(e.getMessage().startsWith("IsoCountryCode must be an ISO 3166-1 alpha-2 code"),
        e.getMessage());
  }

  @Test
  void testSources() {
    assertEquals(Set.of("naturalearth", "test2"), fixture().sources());
  }

  @Test
  void testParseRejectsTooFewFields() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY");
  }

  @Test
  void testParseRejectsTooManyFields() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY;;extra");
  }

  @Test
  void testParseRejectsNonNumericLatitude() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;north;8.5;CH;;1108000;CITY;");
  }

  @Test
  void testParseRejectsOutOfRangeLatitude() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;95.0;8.5;CH;;1108000;CITY;");
  }

  @Test
  void testParseRejectsNonNumericPopulation() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;many;CITY;");
  }

  @Test
  void testParseRejectsNegativePopulation() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;-1;CITY;");
  }

  @Test
  void testParseRejectsMalformedCountryCode() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CHE;;1108000;CITY;");
  }

  @Test
  void testParseRejectsEmptyName() {
    assertMalformedAt(3, "naturalearth;1;;;47.4;8.5;CH;;1108000;CITY;");
  }

  @Test
  void testParseRejectsAttributePairWithoutValue() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY;wikidata");
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY;wikidata=");
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY;=Q72");
  }

  @Test
  void testParseReportsTheFailingLineNotTheFirst() {
    // Two good rows before the bad one: the reported line number is the bad row's.
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> gazetteer(ROWS[0], ROWS[1], "naturalearth;99;Bad;;95.0;8.5;CH;;1;CITY;"));
    assertTrue(e.getMessage().contains("test-gazetteer.txt"), e.getMessage());
    assertTrue(e.getMessage().contains("at line 5"), e.getMessage()); // header comment + blank
  }

  @Test
  void testDuplicateRecordIdFailsLoud() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> gazetteer(ROWS[0], "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY;"));
    assertTrue(e.getMessage().startsWith("Duplicate gazetteer record"), e.getMessage());
  }

  @Test
  void testParseRejectsOutOfRangeLongitude() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;190.0;CH;;1108000;CITY;");
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;-190.0;CH;;1108000;CITY;");
  }

  @Test
  void testParseRejectsDuplicateAttributeKey() {
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;;1108000;CITY;wikidata=Q72|wikidata=Q73");
  }

  @Test
  void testParseRejectsEmptyPipeListElements() {
    // Leading, trailing, and doubled pipes in the altNames field.
    assertMalformedAt(3, "naturalearth;1;Zurich;|Zuerich;47.4;8.5;CH;;1108000;CITY;");
    assertMalformedAt(3, "naturalearth;1;Zurich;Zuerich|;47.4;8.5;CH;;1108000;CITY;");
    assertMalformedAt(3, "naturalearth;1;Zurich;Zuerich||Turicum;47.4;8.5;CH;;1108000;CITY;");
    // The same shapes in the containment field.
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;|Zurich;1108000;CITY;");
    assertMalformedAt(3, "naturalearth;1;Zurich;;47.4;8.5;CH;Zurich|;1108000;CITY;");
  }

  @Test
  void testNameWithoutWordTokensFailsLoudAtLoadTime() {
    // A row that parses but whose name folds to an empty match key would be unreachable by
    // lookup; the loader rejects it instead of silently skipping the index entry.
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> gazetteer("naturalearth;1;...;;47.4;8.5;CH;;1108000;CITY;"));
    assertTrue(e.getMessage().contains("folds to an empty match key"), e.getMessage());
    assertTrue(e.getMessage().contains("naturalearth;1"), e.getMessage());
  }

  @Test
  void testAlternateNameWithoutWordTokensFailsLoudAtLoadTime() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> gazetteer("naturalearth;1;Zurich;...;47.4;8.5;CH;;1108000;CITY;"));
    assertTrue(e.getMessage().contains("folds to an empty match key"), e.getMessage());
  }

  @Test
  void testFromEntriesBuildsACustomGazetteer() {
    // The documented third-party path: build a gazetteer over caller-supplied entries without
    // touching the bundled table, and get the same folding and ranking behavior.
    final GazetteerEntry smallville = new GazetteerEntry("customsource", "sv-1", "Smallville",
        List.of("Small Ville"), new GeoPoint(38.0, -97.0), "US",
        List.of("Kansas"), 45001L, GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
    final GazetteerEntry bigtown = new GazetteerEntry("customsource", "bt-1", "Bigtown",
        List.of(), new GeoPoint(40.0, -75.0), "US",
        List.of(), 250000L, GazetteerEntry.FEATURE_CLASS_CITY, Map.of());
    final BundledGazetteer custom = BundledGazetteer.fromEntries(List.of(smallville, bigtown));
    assertEquals(Set.of("customsource"), custom.sources());
    assertEquals("sv-1", custom.lookup("smallville").get(0).recordId()); // case folded
    assertEquals("sv-1", custom.lookup("Small Ville").get(0).recordId()); // alternate name
    assertEquals("bt-1", custom.byRegion("US").orElseThrow().recordId()); // most populous
    assertEquals(smallville, custom.byId("customsource", "sv-1").orElseThrow());
  }

  @Test
  void testFromEntriesValidatesItsInput() {
    assertThrows(IllegalArgumentException.class, () -> BundledGazetteer.fromEntries(null));
    assertThrows(IllegalArgumentException.class,
        () -> BundledGazetteer.fromEntries(Arrays.asList((GazetteerEntry) null)));
  }

  private static void assertMalformedAt(int line, String row) {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> gazetteer(row));
    assertTrue(e.getMessage().startsWith(
            "Malformed gazetteer data in test-gazetteer.txt at line " + line + ":"),
        e.getMessage());
  }

  private static String singleResultId(BundledGazetteer gazetteer, String query) {
    final List<GazetteerEntry> result = gazetteer.lookup(query);
    assertEquals(1, result.size(), () -> "expected one result for " + query + ", got " + result);
    return result.get(0).recordId();
  }
}
