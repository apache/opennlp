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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import opennlp.tools.geo.GazetteerEntry;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringUtil;

/**
 * The shared in-memory index behind the file-loading gazetteers: entries keyed by
 * every name variant (case-insensitively), by record id, and by country, where the
 * country representative is the most populous entry.
 *
 * <p>Mutable while loading; {@link #freeze()} ranks every candidate list by the
 * population prior and must be called once before queries.
 * {@link #load(InputStream, boolean, RowParser)} is the shared read loop of the file
 * loaders and returns a frozen index.</p>
 */
final class GazetteerIndex {

  /** Parses one data line of a gazetteer table into an entry. */
  @FunctionalInterface
  interface RowParser {

    /**
     * Parses one data line.
     *
     * @param line       The data line; never blank and never a skipped comment line.
     * @param lineNumber The one-based line number, for fail-loud messages.
     * @return The parsed entry. Never {@code null}.
     * @throws InvalidFormatException Thrown if the line is not a valid row.
     */
    GazetteerEntry parse(String line, int lineNumber) throws InvalidFormatException;
  }

  private final Map<String, List<GazetteerEntry>> byName = new HashMap<>();
  private final Map<String, GazetteerEntry> byId = new HashMap<>();
  private final Map<String, GazetteerEntry> byCountry = new HashMap<>();

  /**
   * Indexes one entry under its canonical and alternate names, its record id, and, when it
   * carries a country code, as that country's candidate representative.
   *
   * @param entry The entry to index.
   */
  void add(GazetteerEntry entry) {
    index(entry.name(), entry);
    for (final String alternate : entry.alternateNames()) {
      index(alternate, entry);
    }
    byId.put(entry.recordId(), entry);
    if (entry.countryCode() != null) {
      byCountry.merge(entry.countryCode(), entry,
          (a, b) -> a.population() >= b.population() ? a : b);
    }
  }

  /**
   * Reads a table into a frozen index: every line is handed to {@code parser} with its
   * one-based line number, except blank lines and, when {@code skipComments} is set,
   * lines starting with {@code #}.
   *
   * @param in           The table content, read fully as UTF-8 but not closed.
   * @param skipComments Whether lines starting with {@code #} are skipped.
   * @param parser       The row parser of the caller's table format.
   * @return The frozen index over the parsed entries.
   * @throws IOException Thrown if reading fails.
   * @throws InvalidFormatException Thrown if the content has no data rows, or from
   *     {@code parser} for a malformed row.
   */
  static GazetteerIndex load(InputStream in, boolean skipComments, RowParser parser)
      throws IOException {
    final GazetteerIndex index = new GazetteerIndex();
    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      if (StringUtil.isUnicodeBlank(line) || (skipComments && line.charAt(0) == '#')) {
        continue;
      }
      index.add(parser.parse(line, lineNumber));
    }
    if (index.isEmpty()) {
      throw new InvalidFormatException("the table contains no rows");
    }
    index.freeze();
    return index;
  }

  /** Ranks every candidate list by the population prior; call once after loading. */
  void freeze() {
    for (final List<GazetteerEntry> candidates : byName.values()) {
      candidates.sort(CandidateRanking.BY_PRIOR);
    }
  }

  /** {@return {@code true} if nothing was indexed} */
  boolean isEmpty() {
    return byId.isEmpty();
  }

  /**
   * Finds the candidates indexed under a name.
   *
   * @param name The name to look up, matched after lower-casing. Must not be {@code null}.
   * @return The candidates in {@link #freeze()} order; empty when nothing matches.
   */
  List<GazetteerEntry> lookup(CharSequence name) {
    final List<GazetteerEntry> found = byName.get(name.toString().toLowerCase(Locale.ROOT));
    return found == null ? List.of() : Collections.unmodifiableList(found);
  }

  /**
   * Finds the entry with a record id.
   *
   * @param recordId The record id to look up.
   * @return The entry, or empty when no entry carries that id.
   */
  Optional<GazetteerEntry> byId(String recordId) {
    return Optional.ofNullable(byId.get(recordId));
  }

  /**
   * Finds the most populous entry of a country.
   *
   * @param isoCountryCode The <a href="https://www.iso.org/iso-3166-country-codes.html">ISO
   *                       3166-1</a> alpha-2 code, two ASCII letters of either case. Must
   *                       not be {@code null}.
   * @return The most populous entry, or empty when the code is well-formed but unknown.
   * @throws IllegalArgumentException Thrown if {@code isoCountryCode} is {@code null} or is not
   *     two ASCII letters.
   */
  Optional<GazetteerEntry> byRegion(String isoCountryCode) {
    return Optional.ofNullable(byCountry.get(normalizeRegionCode(isoCountryCode)));
  }

  /**
   * Validates an ISO 3166-1 alpha-2 region code and folds it to its canonical uppercase form.
   *
   * @param isoCountryCode The code to validate. Must not be {@code null}.
   * @return The code with both letters upper-cased.
   * @throws IllegalArgumentException Thrown if {@code isoCountryCode} is {@code null} or is not
   *     two ASCII letters.
   */
  static String normalizeRegionCode(String isoCountryCode) {
    if (isoCountryCode == null) {
      throw new IllegalArgumentException("isoCountryCode must not be null");
    }
    if (isoCountryCode.length() != 2
        || !isAsciiLetter(isoCountryCode.charAt(0)) || !isAsciiLetter(isoCountryCode.charAt(1))) {
      throw new IllegalArgumentException(
          "isoCountryCode must be an ISO 3166-1 alpha-2 code (two ASCII letters), got: "
              + isoCountryCode);
    }
    return new String(new char[] {upperAscii(isoCountryCode.charAt(0)),
        upperAscii(isoCountryCode.charAt(1))});
  }

  /**
   * Indexes {@code entry} under the lower-cased form of {@code name}, listing it once even when
   * several of its names fold to the same key.
   *
   * @param name  The name variant to index under.
   * @param entry The entry to list under it.
   */
  private void index(String name, GazetteerEntry entry) {
    final List<GazetteerEntry> entries =
        byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>(2));
    if (!entries.contains(entry)) {
      entries.add(entry);
    }
  }

  /** {@return {@code true} if {@code c} is an ASCII letter} */
  private static boolean isAsciiLetter(char c) {
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
  }

  /** {@return {@code c} upper-cased if it is an ASCII lowercase letter, otherwise unchanged} */
  private static char upperAscii(char c) {
    return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
  }
}
