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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks representative selection using the candidate order shared by the local indexes.
 */
public class GazetteerRegionRankingTest {

  private static final String NAME = "Region Candidate";
  private static final String COUNTRY = "US";
  private static final String USER_SOURCE = "user-ranking";
  private static final String MEMORY_SOURCE = "memory-ranking";

  /** Supported construction paths in the shared ordering matrix. */
  private enum Format {
    USER,
    GEONAMES,
    OVERTURE,
    IN_MEMORY
  }

  /** Minimal input used to create equivalent records in each format. */
  private record FixtureEntry(String recordId, long population, String featureClass) {
  }

  /**
   * Confirms that lookup and region selection use the documented candidate order, independent of
   * input order and region-code case.
   *
   * @param format The construction path.
   * @param label The case label.
   * @param input The records in source order.
   * @param expectedLookupIds The expected lookup record identifiers.
   * @param expectedRegionId The expected region record identifier.
   * @param regionCode The region-code spelling.
   * @throws IOException Thrown if loading or lookup fails.
   */
  @ParameterizedTest(name = "{0} {1} {5}")
  @MethodSource({"rankingCases", "fallbackFeatureCases"})
  void testSharedRegionRanking(Format format, String label, List<FixtureEntry> input,
      List<String> expectedLookupIds, String expectedRegionId, String regionCode)
      throws IOException {
    final Gazetteer gazetteer = load(format, input);

    assertEquals(expectedLookupIds, recordIds(gazetteer.lookup(NAME)));
    assertEquals(expectedRegionId, gazetteer.byRegion(regionCode).orElseThrow().recordId());
  }

  /**
   * Confirms that in-memory records use lexical source order before their record identifiers.
   *
   * @param label The case label.
   * @param input The records in source order.
   * @throws IOException Thrown if lookup fails.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("sourceCases")
  void testInMemorySourceRanking(String label, List<GazetteerEntry> input) throws IOException {
    final Gazetteer gazetteer = InMemoryGazetteer.fromEntries(input);

    assertEquals(List.of("a-source", "z-source"),
        sources(gazetteer.lookup(NAME)));
    assertEquals("a-source", gazetteer.byRegion("us").orElseThrow().source());
  }

  /**
   * Provides a copyable UserGazetteer example for equal populations and lexical record
   * identifiers.
   *
   * @throws IOException Thrown if loading or lookup fails.
   */
  @Test
  void testUserGazetteerEqualPopulationExample() throws IOException {
    final String input =
        "2\tRegion Candidate\t\t1\t2\tUS\tCITY\t500\n"
            + "10\tRegion Candidate\t\t3\t4\tUS\tCITY\t500\n";
    final Gazetteer gazetteer = UserGazetteer.load(utf8(input), "customer");

    assertEquals(List.of("10", "2"), recordIds(gazetteer.lookup("Region Candidate")));
    assertEquals("10", gazetteer.byRegion("us").orElseThrow().recordId());
  }

  /**
   * Confirms that a GeoNames identifier remains directly available through source-scoped lookup.
   *
   * @throws IOException Thrown if loading or lookup fails.
   */
  @Test
  void testGeoNamesRecordIdIsSourceScoped() throws IOException {
    final String input = String.join("\t", "6252001", "Direct Place", "Direct Place", "",
        "1", "2", "P", "XXX", "US", "", "", "", "", "", "7", "", "",
        "Etc/UTC", "2026-01-01") + "\n";
    final Gazetteer gazetteer = GeoNamesGazetteer.load(utf8(input));

    assertEquals("6252001",
        gazetteer.byId(GeoNamesGazetteer.SOURCE, "6252001").orElseThrow().recordId());
    assertTrue(gazetteer.byId(USER_SOURCE, "6252001").isEmpty());
  }

  /**
   * Confirms that additions retain overlay precedence when populations are equal, even when the
   * base feature has the preferred rank.
   *
   * @throws IOException Thrown if loading or lookup fails.
   */
  @Test
  void testOverlayRetainsAdditionPrecedence() throws IOException {
    final Gazetteer base = UserGazetteer.load(utf8(
        "shared\tRegion Candidate\t\t1\t2\tUS\tCITY\t500\n"), "base-source");
    final Gazetteer additions = UserGazetteer.load(utf8(
        "shared\tRegion Candidate\t\t3\t4\tUS\tPOI\t500\n"), "added-source");
    final Gazetteer overlay = new OverlayGazetteer(base, additions, List.of());
    final GazetteerEntry baseRecord = base.byId("base-source", "shared").orElseThrow();
    final GazetteerEntry addedRecord = additions.byId("added-source", "shared").orElseThrow();

    assertEquals(List.of(addedRecord, baseRecord), overlay.lookup(NAME));
    assertEquals("added-source", overlay.byRegion("US").orElseThrow().source());
    assertSame(baseRecord, overlay.byId("base-source", "shared").orElseThrow());
    assertSame(addedRecord, overlay.byId("added-source", "shared").orElseThrow());
  }

  /** Supplies the common ranking matrix with forward and reverse input orders. */
  private static Stream<Arguments> rankingCases() {
    final List<Arguments> cases = new ArrayList<>();
    for (final Format format : Format.values()) {
      addPermutations(cases, format, "city-before-admin",
          List.of(
              new FixtureEntry("admin", 100, GazetteerEntry.FEATURE_CLASS_ADMIN),
              new FixtureEntry("city", 100, GazetteerEntry.FEATURE_CLASS_CITY)),
          List.of("city", "admin"), "city");
      addPermutations(cases, format, "admin-before-poi",
          List.of(
              new FixtureEntry("poi", 100, GazetteerEntry.FEATURE_CLASS_POI),
              new FixtureEntry("admin", 100, GazetteerEntry.FEATURE_CLASS_ADMIN)),
          List.of("admin", "poi"), "admin");
      addPermutations(cases, format, "city-before-poi",
          List.of(
              new FixtureEntry("poi", 100, GazetteerEntry.FEATURE_CLASS_POI),
              new FixtureEntry("city", 100, GazetteerEntry.FEATURE_CLASS_CITY)),
          List.of("city", "poi"), "city");
      addPermutations(cases, format, "lexical-record-id",
          List.of(
              new FixtureEntry("2", 100, GazetteerEntry.FEATURE_CLASS_CITY),
              new FixtureEntry("10", 100, GazetteerEntry.FEATURE_CLASS_CITY)),
          List.of("10", "2"), "10");
      addPermutations(cases, format, "zero-population",
          List.of(
              new FixtureEntry("2", 0, GazetteerEntry.FEATURE_CLASS_ADMIN),
              new FixtureEntry("10", 0, GazetteerEntry.FEATURE_CLASS_ADMIN)),
          List.of("10", "2"), "10");
      addPermutations(cases, format, "maximum-population",
          List.of(
              new FixtureEntry("city", Long.MAX_VALUE - 1,
                  GazetteerEntry.FEATURE_CLASS_CITY),
              new FixtureEntry("poi", Long.MAX_VALUE, GazetteerEntry.FEATURE_CLASS_POI)),
          List.of("poi", "city"), "poi");
    }
    return cases.stream();
  }

  /** Supplies fallback-feature cases for formats that retain an arbitrary feature value. */
  private static Stream<Arguments> fallbackFeatureCases() {
    final List<Arguments> cases = new ArrayList<>();
    final List<FixtureEntry> input = List.of(
        new FixtureEntry("2", 0, "CUSTOM"),
        new FixtureEntry("10", 0, null),
        new FixtureEntry("poi", 0, GazetteerEntry.FEATURE_CLASS_POI));
    addPermutations(cases, Format.USER, "user-fallback-feature", input,
        List.of("poi", "10", "2"), "poi");
    addPermutations(cases, Format.IN_MEMORY, "memory-fallback-feature", input,
        List.of("poi", "10", "2"), "poi");
    return cases.stream();
  }

  /** Supplies source-order cases in forward and reverse input orders. */
  private static Stream<Arguments> sourceCases() {
    final GazetteerEntry fromZ = memoryEntry("z-source", "shared", 100,
        GazetteerEntry.FEATURE_CLASS_CITY);
    final GazetteerEntry fromA = memoryEntry("a-source", "shared", 100,
        GazetteerEntry.FEATURE_CLASS_CITY);
    return Stream.of(
        Arguments.of("source-forward", List.of(fromZ, fromA)),
        Arguments.of("source-reverse", List.of(fromA, fromZ)));
  }

  /**
   * Adds forward and reverse variants with explicit expected results.
   *
   * @param cases The destination cases.
   * @param format The construction path.
   * @param label The case label.
   * @param input The forward input order.
   * @param expectedLookupIds The expected lookup record identifiers.
   * @param expectedRegionId The expected region record identifier.
   */
  private static void addPermutations(List<Arguments> cases, Format format, String label,
      List<FixtureEntry> input, List<String> expectedLookupIds, String expectedRegionId) {
    cases.add(Arguments.of(format, label + "-forward", input, expectedLookupIds,
        expectedRegionId, "US"));
    final List<FixtureEntry> reversed = new ArrayList<>(input);
    Collections.reverse(reversed);
    cases.add(Arguments.of(format, label + "-reverse", List.copyOf(reversed), expectedLookupIds,
        expectedRegionId, "us"));
  }

  /**
   * Loads records through the requested public construction path.
   *
   * @param format The construction path.
   * @param entries The records to load.
   * @return The loaded gazetteer.
   * @throws IOException Thrown if loading fails.
   */
  private Gazetteer load(Format format, List<FixtureEntry> entries) throws IOException {
    return switch (format) {
      case USER -> UserGazetteer.load(utf8(userTable(entries)), USER_SOURCE);
      case GEONAMES -> GeoNamesGazetteer.load(utf8(geoNamesTable(entries)));
      case OVERTURE -> OvertureGazetteer.load(utf8(overtureTable(entries)));
      case IN_MEMORY -> InMemoryGazetteer.fromEntries(memoryEntries(entries));
    };
  }

  /**
   * Creates UserGazetteer input from the supplied records.
   *
   * @param entries The records to encode.
   * @return The UTF-8 table content.
   */
  private String userTable(List<FixtureEntry> entries) {
    final StringBuilder table = new StringBuilder();
    for (final FixtureEntry entry : entries) {
      table.append(entry.recordId()).append('\t')
          .append(NAME).append('\t')
          .append('\t')
          .append("1\t2\t")
          .append(COUNTRY).append('\t')
          .append(entry.featureClass() == null ? "" : entry.featureClass()).append('\t')
          .append(entry.population()).append('\n');
    }
    return table.toString();
  }

  /**
   * Creates GeoNames input from the supplied records.
   *
   * @param entries The records to encode.
   * @return The UTF-8 table content.
   */
  private String geoNamesTable(List<FixtureEntry> entries) {
    final StringBuilder table = new StringBuilder();
    for (final FixtureEntry entry : entries) {
      table.append(String.join("\t", entry.recordId(), NAME, NAME, "", "1", "2",
          geoNamesClass(entry.featureClass()), "XXX", COUNTRY, "", "", "", "", "",
          Long.toString(entry.population()), "", "", "Etc/UTC", "2026-01-01"));
      table.append('\n');
    }
    return table.toString();
  }

  /**
   * Creates Overture input from the supplied records.
   *
   * @param entries The records to encode.
   * @return The UTF-8 table content.
   */
  private String overtureTable(List<FixtureEntry> entries) {
    final StringBuilder table = new StringBuilder();
    for (final FixtureEntry entry : entries) {
      table.append(String.join("\t", entry.recordId(), NAME, "", "1", "2", COUNTRY,
          overtureSubtype(entry.featureClass()), Long.toString(entry.population())));
      table.append('\n');
    }
    return table.toString();
  }

  /**
   * Creates in-memory records from the supplied fixture values.
   *
   * @param entries The fixture values.
   * @return The records.
   */
  private List<GazetteerEntry> memoryEntries(List<FixtureEntry> entries) {
    final List<GazetteerEntry> records = new ArrayList<>();
    for (final FixtureEntry entry : entries) {
      records.add(memoryEntry(MEMORY_SOURCE, entry.recordId(), entry.population(),
          entry.featureClass()));
    }
    return List.copyOf(records);
  }

  /**
   * Creates one in-memory record.
   *
   * @param source The source identifier.
   * @param recordId The record identifier.
   * @param population The population.
   * @param featureClass The feature class, or null.
   * @return The record.
   */
  private static GazetteerEntry memoryEntry(String source, String recordId, long population,
      String featureClass) {
    return new GazetteerEntry(source, recordId, NAME, List.of(), new GeoPoint(1, 2), COUNTRY,
        List.of(), population, featureClass, Map.of());
  }

  /**
   * Maps a common feature class to the GeoNames main-format class.
   *
   * @param featureClass The common feature class.
   * @return The GeoNames class.
   */
  private String geoNamesClass(String featureClass) {
    if (GazetteerEntry.FEATURE_CLASS_CITY.equals(featureClass)) {
      return "P";
    }
    if (GazetteerEntry.FEATURE_CLASS_ADMIN.equals(featureClass)) {
      return "A";
    }
    return "S";
  }

  /**
   * Maps a common feature class to the Overture place subtype.
   *
   * @param featureClass The common feature class.
   * @return The Overture subtype.
   */
  private String overtureSubtype(String featureClass) {
    if (GazetteerEntry.FEATURE_CLASS_CITY.equals(featureClass)) {
      return "locality";
    }
    if (GazetteerEntry.FEATURE_CLASS_ADMIN.equals(featureClass)) {
      return "region";
    }
    return "neighborhood";
  }

  /**
   * Creates an input stream over the supplied UTF-8 text.
   *
   * @param value The input text.
   * @return The input stream.
   */
  private InputStream utf8(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Collects record identifiers in candidate order.
   *
   * @param entries The candidate records.
   * @return The ordered record identifiers.
   */
  private List<String> recordIds(List<GazetteerEntry> entries) {
    final List<String> result = new ArrayList<>();
    for (final GazetteerEntry entry : entries) {
      result.add(entry.recordId());
    }
    return List.copyOf(result);
  }

  /**
   * Collects source identifiers in candidate order.
   *
   * @param entries The candidate records.
   * @return The ordered source identifiers.
   */
  private List<String> sources(List<GazetteerEntry> entries) {
    final List<String> result = new ArrayList<>();
    for (final GazetteerEntry entry : entries) {
      result.add(entry.source());
    }
    return List.copyOf(result);
  }
}
