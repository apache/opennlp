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

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;
import opennlp.tools.util.StringUtil;

/**
 * A {@link Gazetteer} over a user-supplied file in the GeoNames main table format: one
 * tab-separated row per place with name, ASCII name, comma-separated alternate names,
 * coordinates, feature class, country code, and population.
 *
 * <p>The file is downloaded by the caller; nothing is bundled, and the publisher's
 * license terms, including attribution, stay with the downloaded file.
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
 */
@ThreadSafe
public final class GeoNamesGazetteer implements Gazetteer {

  /** The dataset identifier this gazetteer scopes its record ids to. */
  public static final String SOURCE = "geonames";

  private static final int COLUMNS = 19;

  /** The separator between the fields of one row. */
  private static final String FIELD_SEPARATOR = "\t";

  /** The separator between the elements of the alternate-names field. */
  private static final String LIST_SEPARATOR = ",";

  private final GazetteerIndex index;

  private GeoNamesGazetteer(GazetteerIndex index) {
    this.index = index;
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
    final GazetteerIndex index = new GazetteerIndex();
    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      if (StringUtil.isBlank(line)) {
        continue;
      }
      index.add(parseRow(line, lineNumber));
    }
    if (index.isEmpty()) {
      throw new IllegalArgumentException("the table contains no rows");
    }
    index.freeze();
    return new GeoNamesGazetteer(index);
  }

  /** {@inheritDoc} */
  @Override
  public List<GazetteerEntry> lookup(CharSequence name) {
    if (name == null) {
      throw new IllegalArgumentException("name must not be null");
    }
    return index.lookup(name);
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) {
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    if (recordId == null) {
      throw new IllegalArgumentException("recordId must not be null");
    }
    return SOURCE.equals(source) ? index.byId(recordId) : Optional.empty();
  }

  /** {@inheritDoc} */
  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    return index.byRegion(isoCountryCode);
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> sources() {
    return Set.of(SOURCE);
  }

  /** Parses one main-format row into an entry, failing loud with the line number. */
  private static GazetteerEntry parseRow(String line, int lineNumber) {
    final String[] fields = line.split(FIELD_SEPARATOR, -1);
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
      for (final String alternate : fields[3].split(LIST_SEPARATOR)) {
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
