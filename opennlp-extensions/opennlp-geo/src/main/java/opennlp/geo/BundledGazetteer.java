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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.geo.AttributeValue;
import opennlp.tools.geo.Gazetteer;
import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.geo.GeoPoint;
import opennlp.tools.util.normalizer.Term;
import opennlp.tools.util.normalizer.TermAnalyzer;

/**
 * The bundled {@link Gazetteer}: a public-domain populated-places table derived from Natural
 * Earth, shipped inside this jar so location lookup works with no download and no configuration.
 *
 * <p>The table ({@code naturalearth-populated-places.txt}) is a project-authored, semicolon
 * separated derivation of the Natural Earth "Populated Places" dataset (public domain); its full
 * derivation record is in the file header. It is parsed once, lazily, on first use
 * ({@link #getInstance()}), with fail-loud errors that name the resource and line for any
 * malformed row. After loading, an instance is immutable and thread-safe; one instance serves
 * concurrent lookups.</p>
 *
 * <p><b>Name matching.</b> Both indexed names and queries are folded through the same
 * normalization chain used for matching elsewhere in OpenNLP: the name is segmented with the
 * UAX&#160;#29 word tokenizer and each token is normalized with {@link TermAnalyzer} configured
 * as NFC, case fold, accent fold; the folded tokens joined by single spaces form the match key.
 * Folding queries and index keys identically makes matching robust to case ({@code ZURICH}),
 * accents (a query containing {@code u} with umlaut finds the ASCII row), and punctuation or
 * hyphenation variation ({@code Winston-Salem} and {@code Winston Salem} produce the same
 * key).</p>
 *
 * <p><b>ASCII-only v1 boundary.</b> The bundled table is deliberately pure ASCII: a place whose
 * primary name is not ASCII is carried as the transliterated ASCII name Natural Earth provides
 * ({@code NAMEASCII}), and the Unicode form is omitted in v1. Because queries are folded through
 * the chain above, accented queries still match the ASCII rows; native-script names (for example
 * CJK or Cyrillic forms) are not matchable against this table and belong to a later, richer data
 * tier behind the same {@link Gazetteer} interface.</p>
 *
 * <p><b>Ranking.</b> {@link #lookup(CharSequence)} returns candidates ordered by population
 * descending, then by feature class ({@code CITY} before {@code ADMIN} before {@code POI} before
 * anything else), then by source and record id for a deterministic total order.
 * {@link #byRegion(String)} returns the most populous bundled entry for the region under the
 * same order; with this dataset that is a city-level record (very often the capital or primate
 * city), a documented v1 approximation of a country-level record.</p>
 */
public final class BundledGazetteer implements Gazetteer {

  private static final String RESOURCE = "naturalearth-populated-places.txt";

  // Population descending, then feature-class prior, then source and record id: a deterministic
  // total order over any candidate list.
  private static final Comparator<GazetteerEntry> RANKING =
      Comparator.comparingLong(GazetteerEntry::population).reversed()
          .thenComparingInt(entry -> featureClassRank(entry.featureClass()))
          .thenComparing(GazetteerEntry::source)
          .thenComparing(GazetteerEntry::recordId);

  // The character-level matching chain; stateless and thread-safe, shared by index and queries.
  private static final TermAnalyzer FOLD =
      TermAnalyzer.builder().nfc().caseFold().accentFold().build();

  private static volatile BundledGazetteer instance;

  private final Map<IdKey, GazetteerEntry> idIndex;
  private final Map<String, List<GazetteerEntry>> nameIndex;
  private final Map<String, GazetteerEntry> regionIndex;
  private final Set<String> sources;

  /**
   * Indexes the given entries. Package-private so tests can build a gazetteer from crafted rows;
   * production use goes through {@link #getInstance()}.
   *
   * @throws IllegalArgumentException Thrown if {@code entries} is {@code null}, contains a
   *     {@code null} element, or contains two entries with the same (source, recordId).
   */
  BundledGazetteer(List<GazetteerEntry> entries) {
    if (entries == null) {
      throw new IllegalArgumentException("Entries must not be null");
    }
    final Map<IdKey, GazetteerEntry> byId = new HashMap<>(entries.size() * 2);
    final Map<String, List<GazetteerEntry>> byName = new HashMap<>(entries.size() * 2);
    final Map<String, GazetteerEntry> byRegion = new HashMap<>();
    final Set<String> sourceIds = new HashSet<>();
    for (final GazetteerEntry entry : entries) {
      if (entry == null) {
        throw new IllegalArgumentException("Entries must not contain a null element");
      }
      final IdKey key = new IdKey(entry.source(), entry.recordId());
      if (byId.putIfAbsent(key, entry) != null) {
        throw new IllegalArgumentException("Duplicate gazetteer record for source "
            + entry.source() + " and recordId " + entry.recordId());
      }
      sourceIds.add(entry.source());
      indexName(byName, entry.name(), entry);
      for (final String alternateName : entry.alternateNames()) {
        indexName(byName, alternateName, entry);
      }
      if (entry.countryCode() != null) {
        byRegion.merge(entry.countryCode(), entry,
            (existing, candidate) -> RANKING.compare(candidate, existing) < 0 ? candidate : existing);
      }
    }
    for (final Map.Entry<String, List<GazetteerEntry>> indexed : byName.entrySet()) {
      final List<GazetteerEntry> ranked = new ArrayList<>(indexed.getValue());
      ranked.sort(RANKING);
      indexed.setValue(List.copyOf(ranked));
    }
    this.idIndex = byId;
    this.nameIndex = byName;
    this.regionIndex = byRegion;
    this.sources = Set.copyOf(sourceIds);
  }

  /**
   * {@return the shared instance backed by the bundled table} The table is loaded and indexed
   * once, on the first call; later calls return the same immutable instance.
   *
   * @throws IllegalStateException Thrown if the bundled data resource is missing.
   * @throws IllegalArgumentException Thrown if the bundled data is malformed; the message names
   *     the resource and line.
   */
  public static BundledGazetteer getInstance() {
    BundledGazetteer result = instance;
    if (result == null) {
      synchronized (BundledGazetteer.class) {
        result = instance;
        if (result == null) {
          result = new BundledGazetteer(load());
          instance = result;
        }
      }
    }
    return result;
  }

  @Override
  public List<GazetteerEntry> lookup(CharSequence name) {
    if (name == null) {
      throw new IllegalArgumentException("Name must not be null");
    }
    final String key = foldKey(name);
    if (key.isEmpty()) {
      return List.of();
    }
    final List<GazetteerEntry> entries = nameIndex.get(key);
    return entries == null ? List.of() : entries;
  }

  @Override
  public Optional<GazetteerEntry> byId(String source, String recordId) {
    if (source == null) {
      throw new IllegalArgumentException("Source must not be null");
    }
    if (recordId == null) {
      throw new IllegalArgumentException("RecordId must not be null");
    }
    return Optional.ofNullable(idIndex.get(new IdKey(source, recordId)));
  }

  @Override
  public Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    if (isoCountryCode == null) {
      throw new IllegalArgumentException("IsoCountryCode must not be null");
    }
    if (isoCountryCode.length() != 2
        || !isAsciiLetter(isoCountryCode.charAt(0)) || !isAsciiLetter(isoCountryCode.charAt(1))) {
      throw new IllegalArgumentException(
          "IsoCountryCode must be an ISO 3166-1 alpha-2 code (two ASCII letters), got: "
              + isoCountryCode);
    }
    final String key = new String(new char[] {upperAscii(isoCountryCode.charAt(0)),
        upperAscii(isoCountryCode.charAt(1))});
    return Optional.ofNullable(regionIndex.get(key));
  }

  @Override
  public Set<String> sources() {
    return sources;
  }

  // Folds one name to its match key: UAX #29 word tokens, each NFC + case fold + accent fold,
  // joined by single spaces. Empty when the name contains no word token.
  static String foldKey(CharSequence name) {
    final List<Term> terms = FOLD.analyze(name);
    if (terms.isEmpty()) {
      return "";
    }
    final StringBuilder key = new StringBuilder(name.length());
    for (final Term term : terms) {
      if (key.length() > 0) {
        key.append(' ');
      }
      key.append(term.normalized());
    }
    return key.toString();
  }

  // CITY before ADMIN before POI before anything else (including unknown); used on ties only.
  static int featureClassRank(String featureClass) {
    if ("CITY".equals(featureClass)) {
      return 0;
    }
    if ("ADMIN".equals(featureClass)) {
      return 1;
    }
    if ("POI".equals(featureClass)) {
      return 2;
    }
    return 3;
  }

  /**
   * Parses gazetteer rows from a stream of the bundled table format. Package-private so the
   * fail-loud handling can be exercised without the bundled resource.
   *
   * <p>Row format, eleven semicolon separated fields (documented in the data file header):
   * {@code source;recordId;name;altNames;lat;lon;iso2;containment;population;featureClass;
   * attributes} where altNames and containment are pipe separated lists (possibly empty), iso2
   * and featureClass may be empty for unknown, and attributes is a pipe separated list of
   * {@code key=value} pairs (possibly empty) whose provenance is the row's source. A line
   * starting with {@code #} is a comment; blank lines are skipped; data rows carry no inline
   * comments.</p>
   *
   * @throws IllegalArgumentException Thrown for any malformed row; the message names
   *     {@code resourceName} and the line number.
   */
  static List<GazetteerEntry> parse(InputStream in, String resourceName) throws IOException {
    final List<GazetteerEntry> entries = new ArrayList<>();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        entries.add(parseRow(line, resourceName, lineNumber));
      }
    }
    return entries;
  }

  private static GazetteerEntry parseRow(String line, String resourceName, int lineNumber) {
    // Cursor scan into exactly 11 fields; no regular expression, no String.split.
    final String[] fields = new String[11];
    int fieldCount = 0;
    int start = 0;
    while (fieldCount < 10) {
      final int semicolon = line.indexOf(';', start);
      if (semicolon < 0) {
        throw malformed(resourceName, lineNumber, line, null);
      }
      fields[fieldCount++] = line.substring(start, semicolon);
      start = semicolon + 1;
    }
    if (line.indexOf(';', start) >= 0) {
      throw malformed(resourceName, lineNumber, line, null);
    }
    fields[10] = line.substring(start);
    try {
      final double latitude = Double.parseDouble(fields[4]);
      final double longitude = Double.parseDouble(fields[5]);
      final long population = Long.parseLong(fields[8]);
      return new GazetteerEntry(
          fields[0],
          fields[1],
          fields[2],
          splitList(fields[3]),
          new GeoPoint(latitude, longitude),
          fields[6].isEmpty() ? null : fields[6],
          splitList(fields[7]),
          population,
          fields[9].isEmpty() ? null : fields[9],
          parseAttributes(fields[10], fields[0]));
    } catch (IllegalArgumentException e) {
      // Includes NumberFormatException from the numeric fields and the record validations.
      throw malformed(resourceName, lineNumber, line, e);
    }
  }

  // Splits a pipe separated list field; an empty field is an empty list, and empty elements
  // (from a leading, trailing, or doubled pipe) surface through GazetteerEntry's validation.
  private static List<String> splitList(String field) {
    if (field.isEmpty()) {
      return List.of();
    }
    final List<String> values = new ArrayList<>();
    int start = 0;
    while (true) {
      final int pipe = field.indexOf('|', start);
      if (pipe < 0) {
        values.add(field.substring(start));
        return values;
      }
      values.add(field.substring(start, pipe));
      start = pipe + 1;
    }
  }

  // Parses the attributes field: pipe separated key=value pairs, provenance is the row's source.
  private static Map<String, AttributeValue> parseAttributes(String field, String source) {
    if (field.isEmpty()) {
      return Map.of();
    }
    final Map<String, AttributeValue> attributes = new LinkedHashMap<>();
    for (final String pair : splitList(field)) {
      final int equals = pair.indexOf('=');
      if (equals <= 0 || equals == pair.length() - 1) {
        throw new IllegalArgumentException("Attribute pair must have the form key=value, got: "
            + pair);
      }
      final String key = pair.substring(0, equals);
      if (attributes.putIfAbsent(key,
          new AttributeValue(pair.substring(equals + 1), source, "")) != null) {
        throw new IllegalArgumentException("Duplicate attribute key: " + key);
      }
    }
    return attributes;
  }

  private static IllegalArgumentException malformed(String resourceName, int lineNumber,
                                                    String line, Throwable cause) {
    final String message = "Malformed gazetteer data in " + resourceName + " at line "
        + lineNumber + ": " + line;
    return cause == null
        ? new IllegalArgumentException(message) : new IllegalArgumentException(message, cause);
  }

  private static List<GazetteerEntry> load() {
    try (InputStream in = BundledGazetteer.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing gazetteer data resource: " + RESOURCE);
      }
      return parse(in, RESOURCE);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read gazetteer data resource " + RESOURCE, e);
    }
  }

  private static void indexName(Map<String, List<GazetteerEntry>> byName, String name,
                                GazetteerEntry entry) {
    final String key = foldKey(name);
    if (key.isEmpty()) {
      return;
    }
    final List<GazetteerEntry> entries =
        byName.computeIfAbsent(key, unused -> new ArrayList<>(2));
    // An entry indexed under the same key by several of its names (or by two alternate names
    // that fold together) is listed once.
    if (!entries.contains(entry)) {
      entries.add(entry);
    }
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  private static char upperAscii(char c) {
    return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
  }

  // The composite identifier of one record; only (source, recordId) together are unique.
  private record IdKey(String source, String recordId) {
  }
}
