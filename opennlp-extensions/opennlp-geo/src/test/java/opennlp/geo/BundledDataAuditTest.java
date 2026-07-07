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
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.geo.GazetteerEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audits the bundled naturalearth-populated-places.txt table: the claims made in the data file
 * header and the class javadoc are enforced here, row by row, so a regeneration of the table
 * cannot silently drift from them.
 */
public class BundledDataAuditTest {

  private static final String RESOURCE = "naturalearth-populated-places.txt";

  /** Attribute keys the derivation may emit; a new key must be added here deliberately. */
  private static final Set<String> ALLOWED_ATTRIBUTE_KEYS =
      Set.of("wikidata", "geonames", "whosonfirst");

  private static List<GazetteerEntry> entries;

  @BeforeAll
  static void parseBundledTable() throws IOException {
    try (InputStream in = BundledGazetteer.class.getResourceAsStream(RESOURCE)) {
      assertNotNull(in, "Missing bundled data resource: " + RESOURCE);
      // Every row parses: parse() fails loud on the first malformed one.
      entries = BundledGazetteer.parse(in, RESOURCE);
    }
  }

  @Test
  void testRowCountFloor() {
    // The full Natural Earth populated-places set; a truncated regeneration fails here.
    assertTrue(entries.size() >= 7000, "Expected at least 7000 rows, got " + entries.size());
  }

  @Test
  void testResourceIsPureAscii() throws IOException {
    try (InputStream in = BundledGazetteer.class.getResourceAsStream(RESOURCE)) {
      assertNotNull(in);
      final byte[] bytes = in.readAllBytes();
      for (int i = 0; i < bytes.length; i++) {
        final int unsigned = bytes[i] & 0xFF;
        assertTrue(unsigned < 0x80, "Non-ASCII byte 0x" + Integer.toHexString(unsigned)
            + " at offset " + i);
      }
    }
  }

  @Test
  void testNoDuplicateSourceAndRecordId() {
    final Set<String> seen = new HashSet<>(entries.size() * 2);
    for (final GazetteerEntry entry : entries) {
      assertTrue(seen.add(entry.source() + ";" + entry.recordId()),
          "Duplicate record: " + entry.source() + ";" + entry.recordId());
    }
  }

  @Test
  void testEveryRowKeepsTheDocumentedShape() {
    for (final GazetteerEntry entry : entries) {
      // Source, feature class, and containment shape are fixed for this table.
      assertEquals("naturalearth", entry.source(), entry.recordId());
      assertEquals("CITY", entry.featureClass(), entry.recordId());
      assertTrue(entry.containment().size() <= 1,
          "v1 carries at most the admin-1 containment element: " + entry.recordId());
      // GazetteerEntry and GeoPoint validate these at construction; asserted here so the audit
      // stands on its own if those validations ever loosen.
      assertTrue(entry.population() >= 0, entry.recordId());
      final double latitude = entry.location().latitude();
      final double longitude = entry.location().longitude();
      assertTrue(latitude >= -90.0 && latitude <= 90.0, entry.recordId());
      assertTrue(longitude >= -180.0 && longitude <= 180.0, entry.recordId());
      final String countryCode = entry.countryCode();
      if (countryCode != null) {
        assertTrue(countryCode.length() == 2
                && countryCode.charAt(0) >= 'A' && countryCode.charAt(0) <= 'Z'
                && countryCode.charAt(1) >= 'A' && countryCode.charAt(1) <= 'Z',
            "Malformed country code " + countryCode + " on " + entry.recordId());
      }
      for (final String key : entry.attributes().keySet()) {
        assertTrue(ALLOWED_ATTRIBUTE_KEYS.contains(key),
            "Unexpected attribute key " + key + " on " + entry.recordId());
      }
    }
  }

  @Test
  void testMostRowsCarryACountryCode() {
    // Only the upstream -99 sentinel rows (disputed territories) may lack a code.
    final long withoutCode = entries.stream().filter(e -> e.countryCode() == null).count();
    assertTrue(withoutCode <= 20, "Too many rows without a country code: " + withoutCode);
  }

  @Test
  void testBundledInstanceLoadsAndIndexesTheTable() {
    final BundledGazetteer gazetteer = BundledGazetteer.getInstance();
    assertEquals(Set.of("naturalearth"), gazetteer.sources());
    final List<GazetteerEntry> tokyo = gazetteer.lookup("Tokyo");
    assertFalse(tokyo.isEmpty());
    assertEquals("JP", tokyo.get(0).countryCode());
    assertEquals("1159151609", tokyo.get(0).recordId());
  }

  @Test
  void testAccentedQueryFindsAsciiRow() {
    // Z + u-with-diaeresis + rich against the ASCII "Zurich" row.
    final String query = "Z" + new String(Character.toChars(0x00FC)) + "rich";
    final List<GazetteerEntry> result = BundledGazetteer.getInstance().lookup(query);
    assertFalse(result.isEmpty(), "Accented query should find the ASCII row");
    assertEquals("CH", result.get(0).countryCode());
    assertEquals("Zurich", result.get(0).name());
  }

  @Test
  void testByRegionServesTheEmojiJoinKeyShape() {
    // The same ISO 3166-1 alpha-2 strings the emoji annotation layer decodes from flag emoji
    // (EmojiAnnotationJoin keys on them); asserted against the bundled data with no dependency
    // on that layer. byRegion must answer for them, and the returned entry must carry the very
    // code it was asked for.
    final BundledGazetteer gazetteer = BundledGazetteer.getInstance();
    for (final String code : new String[] {"US", "DE", "FR", "JP", "GB", "BR", "IN", "CN"}) {
      final GazetteerEntry entry = gazetteer.byRegion(code).orElseThrow(
          () -> new AssertionError("No entry for region " + code));
      assertEquals(code, entry.countryCode());
    }
  }

  @Test
  void testByRegionReturnsTheMostPopulousEntry() {
    // Documented representative choice: for JP that is Tokyo, for US New York.
    final BundledGazetteer gazetteer = BundledGazetteer.getInstance();
    assertEquals("Tokyo", gazetteer.byRegion("JP").orElseThrow().name());
    assertEquals("New York", gazetteer.byRegion("US").orElseThrow().name());
  }

  @Test
  void testLookupIsDeterministicAcrossCalls() {
    final BundledGazetteer gazetteer = BundledGazetteer.getInstance();
    assertEquals(gazetteer.lookup("Springfield"), gazetteer.lookup("Springfield"));
    assertEquals(gazetteer.lookup("San Jose"), gazetteer.lookup("San Jose"));
  }
}
