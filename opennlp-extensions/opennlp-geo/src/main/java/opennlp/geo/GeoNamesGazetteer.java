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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;

/**
 * A {@link Gazetteer} over a user-supplied file in the GeoNames main table format: one
 * tab-separated row per place with name, ASCII name, comma-separated alternate names,
 * coordinates, feature class, country code, and population.
 *
 * <p>The file is downloaded by the caller; nothing is bundled, and complying with the
 * publisher's license terms, including attribution, is the caller's responsibility.
 * The whole table is indexed in memory, so this loader is meant for the filtered city
 * extracts rather than the full multi-gigabyte dump; memory grows with row and
 * alternate-name count.</p>
 *
 * <p>Lookup matches the canonical name, the ASCII name, and every alternate name,
 * case-insensitively and without further folding; the ASCII name column is what makes
 * accent-free queries hit accented places. Candidates are returned ranked by population
 * descending. Feature classes map coarsely: {@code P} rows become
 * {@link GazetteerEntry#FEATURE_CLASS_CITY}, {@code A} rows
 * {@link GazetteerEntry#FEATURE_CLASS_ADMIN}, everything else
 * {@link GazetteerEntry#FEATURE_CLASS_POI}.</p>
 *
 * <p>Instances are immutable after loading and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public final class GeoNamesGazetteer implements Gazetteer {

  /** The dataset identifier this gazetteer scopes its record ids to. */
  public static final String SOURCE = "geonames";

  private static final int COLUMNS = 19;

  private final Map<String, List<GazetteerEntry>> byName;
  private final Map<String, GazetteerEntry> byId;
  private final Map<String, GazetteerEntry> byCountry;

  private GeoNamesGazetteer(Map<String, List<GazetteerEntry>> byName,
      Map<String, GazetteerEntry> byId, Map<String, GazetteerEntry> byCountry) {
    this.byName = byName;
    this.byId = byId;
    this.byCountry = byCountry;
  }

  /**
   * Loads a GeoNames main-format table from a file.
   *
   * @param table The tab-separated table. Must not be {@code null}.
   * @return A loaded {@link GeoNamesGazetteer}. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws IllegalArgumentException Thrown if {@code table} is {@code null} or a row
   *         is not in the expected format.
   */
  public static GeoNamesGazetteer load(Path table) throws IOException {
    if (table == null) {
      throw new IllegalArgumentException("table must not be null");
    }
    try (InputStream in = Files.newInputStream(table)) {
      return load(in);
    }
  }

  /**
   * Loads a GeoNames main-format table from a stream.
   *
   * @param in The tab-separated content. Must not be {@code null}. The stream is read
   *           fully but not closed.
   * @return A loaded {@link GeoNamesGazetteer}. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null}, the content
   *         is empty, or a row is not in the expected format.
   */
  public static GeoNamesGazetteer load(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final Map<String, List<GazetteerEntry>> byName = new HashMap<>();
    final Map<String, GazetteerEntry> byId = new HashMap<>();
    final Map<String, GazetteerEntry> byCountry = new HashMap<>();
    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      if (line.isBlank()) {
        continue;
      }
      final GazetteerEntry entry = parseRow(line, lineNumber);
      index(byName, entry.name(), entry);
      for (final String alternate : entry.alternateNames()) {
        index(byName, alternate, entry);
      }
      byId.put(entry.recordId(), entry);
      if (entry.countryCode() != null) {
        byCountry.merge(entry.countryCode(), entry,
            (a, b) -> a.population() >= b.population() ? a : b);
      }
    }
    if (byId.isEmpty()) {
      throw new IllegalArgumentException("the table contains no rows");
    }
    for (final List<GazetteerEntry> candidates : byName.values()) {
      candidates.sort(CandidateRanking.BY_PRIOR);
    }
    return new GeoNamesGazetteer(byName, byId, byCountry);
  }

  @Override
  public List<GazetteerEntry> lookup(CharSequence name) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    final List<GazetteerEntry> found =
        byName.get(name.toString().toLowerCase(Locale.ROOT));
    return found == null ? List.of() : Collections.unmodifiableList(found);
  }

  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) {
    if (source == null || recordId == null) {
      throw new IllegalArgumentException("source and recordId must not be null");
    }
    return SOURCE.equals(source)
        ? Optional.ofNullable(byId.get(recordId)) : Optional.empty();
  }

  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    if (isoCountryCode == null) {
      throw new IllegalArgumentException("isoCountryCode must not be null");
    }
    return Optional.ofNullable(byCountry.get(isoCountryCode.toUpperCase(Locale.ROOT)));
  }

  @Override
  public Set<String> sources() {
    return Set.of(SOURCE);
  }

  private static void index(Map<String, List<GazetteerEntry>> byName, String name,
      GazetteerEntry entry) {
    final List<GazetteerEntry> entries =
        byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>(2));
    if (!entries.contains(entry)) {
      entries.add(entry);
    }
  }

  /** Parses one main-format row into an entry, failing loud with the line number. */
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
      final String ascii = fields[2].trim();
      if (!ascii.isEmpty() && !ascii.equals(name)) {
        alternates.add(ascii);
      }
      for (final String alternate : fields[3].split(",")) {
        final String trimmed = alternate.trim();
        if (!trimmed.isEmpty() && !trimmed.equals(name)) {
          alternates.add(trimmed);
        }
      }
      final GeoPoint location = new GeoPoint(
          Double.parseDouble(fields[4].trim()), Double.parseDouble(fields[5].trim()));
      final String countryCode = fields[8].trim().isEmpty() ? null : fields[8].trim();
      final String population = fields[14].trim();
      return new GazetteerEntry(SOURCE, id, name, List.copyOf(alternates), location,
          countryCode, List.of(), population.isEmpty() ? 0L : Long.parseLong(population),
          featureClass(fields[6].trim()), Map.of());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "line " + lineNumber + " is not a GeoNames row: " + e.getMessage(), e);
    }
  }

  /** Maps the one-letter GeoNames feature class onto the coarse conventional classes. */
  private static String featureClass(String geoNamesClass) {
    return switch (geoNamesClass) {
      case "P" -> GazetteerEntry.FEATURE_CLASS_CITY;
      case "A" -> GazetteerEntry.FEATURE_CLASS_ADMIN;
      default -> GazetteerEntry.FEATURE_CLASS_POI;
    };
  }
}
