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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

/**
 * A {@link Gazetteer} over a division table derived from Overture Maps data with the
 * {@code dev/derive-overture-divisions.py} script in this module: one tab-separated row
 * per division with id, primary name, comma-separated alternate names, coordinates,
 * country code, Overture subtype, and population.
 *
 * <p>The upstream divisions theme is published under the Open Database License, whose
 * attribution and database share-alike terms follow the derived table; it is
 * distributed as partitioned Parquet, which this module deliberately does not parse.
 * The derivation script flattens the division features into this plain table, and the
 * script's output header carries the derivation record. Because divisions include countries and
 * regions, not only settlements, a derived table also resolves mentions like
 * {@code Australia} or {@code Bavaria} that place-only gazetteers miss.</p>
 *
 * <p>Lookup matches the primary and every alternate name case-insensitively;
 * candidates are ranked by population descending. Subtypes map coarsely:
 * {@code locality} rows become {@link GazetteerEntry#FEATURE_CLASS_CITY}; the
 * sub-locality subtypes ({@code borough}, {@code macrohood}, {@code neighborhood},
 * {@code microhood}) become {@link GazetteerEntry#FEATURE_CLASS_POI}; every other
 * subtype, countries through local administrative areas, becomes
 * {@link GazetteerEntry#FEATURE_CLASS_ADMIN}.</p>
 *
 * <p>Instances are immutable after loading and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public final class OvertureGazetteer implements Gazetteer {

  /** The dataset identifier this gazetteer scopes its record ids to. */
  public static final String SOURCE = "overture";

  private static final int COLUMNS = 8;

  private static final Set<String> SUB_LOCALITY_SUBTYPES =
      Set.of("borough", "macrohood", "neighborhood", "microhood");

  private final GazetteerIndex index;

  private OvertureGazetteer(GazetteerIndex index) {
    this.index = index;
  }

  /**
   * Loads a derived division table from a file.
   *
   * @param table The tab-separated table. Must not be {@code null}.
   * @return A loaded {@link OvertureGazetteer}. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws IllegalArgumentException Thrown if {@code table} is {@code null} or a row
   *         is not in the expected format.
   */
  public static OvertureGazetteer load(Path table) throws IOException {
    if (table == null) {
      throw new IllegalArgumentException("table must not be null");
    }
    try (InputStream in = Files.newInputStream(table)) {
      return load(in);
    }
  }

  /**
   * Loads a derived division table from a stream.
   *
   * @param in The tab-separated content. Must not be {@code null}. The stream is read
   *           fully but not closed. Lines starting with {@code #} carry the derivation
   *           record and are skipped.
   * @return A loaded {@link OvertureGazetteer}. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null}, the content
   *         has no rows, or a row is not in the expected format.
   */
  public static OvertureGazetteer load(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final GazetteerIndex index = new GazetteerIndex();
    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      if (line.isBlank() || line.charAt(0) == '#') {
        continue;
      }
      index.add(parseRow(line, lineNumber));
    }
    if (index.isEmpty()) {
      throw new IllegalArgumentException("the table contains no rows");
    }
    index.freeze();
    return new OvertureGazetteer(index);
  }

  @Override
  public List<GazetteerEntry> lookup(CharSequence name) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    return index.lookup(name);
  }

  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) {
    if (source == null || recordId == null) {
      throw new IllegalArgumentException("source and recordId must not be null");
    }
    return SOURCE.equals(source) ? index.byId(recordId) : Optional.empty();
  }

  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    if (isoCountryCode == null) {
      throw new IllegalArgumentException("isoCountryCode must not be null");
    }
    return index.byRegion(isoCountryCode);
  }

  @Override
  public Set<String> sources() {
    return Set.of(SOURCE);
  }

  /** Parses one derived row into an entry, failing loud with the line number. */
  private static GazetteerEntry parseRow(String line, int lineNumber) {
    final String[] fields = line.split("\t", -1);
    if (fields.length < COLUMNS) {
      throw new IllegalArgumentException("line " + lineNumber + " has " + fields.length
          + " columns, expected " + COLUMNS);
    }
    try {
      final String id = fields[0].trim();
      final String name = fields[1].trim();
      final Set<String> alternates = new LinkedHashSet<>();
      for (final String alternate : fields[2].split(",")) {
        final String trimmed = alternate.trim();
        if (!trimmed.isEmpty() && !trimmed.equals(name)) {
          alternates.add(trimmed);
        }
      }
      final GeoPoint location = new GeoPoint(
          Double.parseDouble(fields[3].trim()), Double.parseDouble(fields[4].trim()));
      final String countryCode = fields[5].trim().isEmpty() ? null : fields[5].trim();
      final String population = fields[7].trim();
      return new GazetteerEntry(SOURCE, id, name, List.copyOf(alternates), location,
          countryCode, List.of(), population.isEmpty() ? 0L : Long.parseLong(population),
          featureClass(fields[6].trim()), Map.of());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "line " + lineNumber + " is not a derived division row: " + e.getMessage(), e);
    }
  }

  /** Maps the Overture division subtype onto the coarse conventional classes. */
  private static String featureClass(String subtype) {
    if ("locality".equals(subtype)) {
      return GazetteerEntry.FEATURE_CLASS_CITY;
    }
    return SUB_LOCALITY_SUBTYPES.contains(subtype)
        ? GazetteerEntry.FEATURE_CLASS_POI : GazetteerEntry.FEATURE_CLASS_ADMIN;
  }
}
