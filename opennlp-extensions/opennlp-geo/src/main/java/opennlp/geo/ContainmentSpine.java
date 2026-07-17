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
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.geo.PlaceAncestor;
import opennlp.tools.geo.PlaceHierarchy;
import opennlp.tools.util.StringUtil;

/**
 * An immutable, in-memory containment hierarchy over user-supplied place tables: each
 * place carries its parent, name, and type, and {@link #ancestors(String)} walks the
 * chain outward. No hierarchy data is bundled; the user supplies the tables and thereby
 * accepts their licenses, the pattern shared with the gazetteers and dictionaries.
 *
 * <p>Two table formats load through the builder. The neutral tab-separated format
 * carries {@code id}, {@code parent_id}, {@code name}, and {@code type} columns, one
 * place per line, empty parent for roots; a containment table derived from any source,
 * for example an administrative-territory query result, fits it. The Who's On First
 * meta CSV format is read directly by its header columns, so the published per-placetype
 * tables load without conversion; non-positive parent identifiers mean no usable
 * parent, as in the source data.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public final class ContainmentSpine implements PlaceHierarchy {

  /**
   * The hard cap on the number of ancestors {@link #ancestors(String)} collects. Cycles
   * are already stopped by the visited set, so this cap only bounds the walk over a
   * pathologically deep acyclic table; real administrative hierarchies stay far below it.
   */
  private static final int MAX_DEPTH = 64;

  private record Node(String parentId, String name, String type) {
  }

  private final Map<String, Node> places;

  private ContainmentSpine(Map<String, Node> places) {
    this.places = places;
  }

  /**
   * @return A builder collecting places from tables and direct additions. Never
   *         {@code null}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Walks the containment chain of a place from the nearest enclosing place outward.
   * The queried place itself is never part of its own chain.
   *
   * <p>The walk stops, without failing, at the first of: a root (a place with no
   * recorded parent), a parent identifier the spine does not know (the dangling
   * reference contributes no ancestor), an identifier the walk has already seen (a
   * cycle in the user-supplied table), or an internal depth cap guarding against
   * pathologically deep tables.</p>
   *
   * @param id The place identifier. Must not be {@code null}.
   * @return The enclosing places, nearest first, excluding the place itself. Never
   *         {@code null}; empty when the identifier is unknown, when the place is a
   *         root, or when its parent reference is not in the spine.
   * @throws IllegalArgumentException Thrown if {@code id} is {@code null}.
   */
  @Override
  public List<PlaceAncestor> ancestors(String id) {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    final List<PlaceAncestor> chain = new ArrayList<>();
    final Set<String> visited = new HashSet<>();
    visited.add(id);
    Node node = places.get(id);
    String parentId = node == null ? null : node.parentId();
    while (parentId != null && chain.size() < MAX_DEPTH && visited.add(parentId)) {
      final Node parent = places.get(parentId);
      if (parent == null) {
        break;
      }
      chain.add(new PlaceAncestor(parentId, parent.name(), parent.type()));
      parentId = parent.parentId();
    }
    return chain;
  }

  /**
   * Collects places for a {@link ContainmentSpine} from direct additions and table
   * loads, in any combination and order. A builder is a plain mutable collector and is
   * not safe for concurrent use; the spine it builds is.
   */
  public static final class Builder {

    private final Map<String, Node> places = new HashMap<>();

    private Builder() {
    }

    /**
     * Adds one place. Adding an identifier that is already present replaces the
     * earlier place, so the last addition wins.
     *
     * @param id The place identifier. Must not be {@code null} or blank.
     * @param parentId The parent identifier, or {@code null} for a root.
     * @param name The place name. Must not be {@code null} or blank.
     * @param type The place type. Must not be {@code null} or blank.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if a required value is {@code null} or
     *         blank.
     */
    public Builder add(String id, String parentId, String name, String type) {
      if (id == null || StringUtil.isBlank(id)) {
        throw new IllegalArgumentException("id must not be null or blank");
      }
      if (name == null || StringUtil.isBlank(name)) {
        throw new IllegalArgumentException("name must not be null or blank");
      }
      if (type == null || StringUtil.isBlank(type)) {
        throw new IllegalArgumentException("type must not be null or blank");
      }
      places.put(id, new Node(parentId, name, type));
      return this;
    }

    /**
     * Loads a neutral tab-separated containment table: {@code id}, {@code parent_id},
     * {@code name}, {@code type} per line, empty parent for roots, {@code #} comment
     * lines skipped.
     *
     * @param table The table file, UTF-8. Must not be {@code null}.
     * @return This builder.
     * @throws IOException Thrown if reading fails or a line is malformed.
     * @throws IllegalArgumentException Thrown if {@code table} is {@code null}.
     */
    public Builder addTable(Path table) throws IOException {
      if (table == null) {
        throw new IllegalArgumentException("table must not be null");
      }
      int lineNumber = 0;
      for (final String line : readLines(table)) {
        lineNumber++;
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        final List<String> fields = splitOn(line, '\t');
        if (fields.size() < 4) {
          throw new IOException("malformed containment line " + lineNumber
              + " in " + table);
        }
        final String parent = stripped(fields.get(1));
        add(stripped(fields.get(0)), parent.isEmpty() ? null : parent,
            stripped(fields.get(2)), stripped(fields.get(3)));
      }
      return this;
    }

    /**
     * Loads a Who's On First meta CSV by its header columns {@code id},
     * {@code parent_id}, {@code name}, and {@code placetype}. Rows with a non-positive
     * parent identifier become roots, matching the source convention for places
     * without a usable parent.
     *
     * <p>The file is read as RFC 4180 CSV: a field may be quoted, a quoted field may
     * carry commas, doubled quotes, and line breaks, and such a field stays part of the
     * single row it belongs to.</p>
     *
     * <p>Every row of the file must describe a usable place. A row that is too short to
     * reach a required column, a row whose name or placetype column is empty, and a
     * quoted field the file never closes each fail loud, naming the line the offending
     * row starts on; no row is dropped silently, because a dropped row would end every
     * containment chain running through that place at a dangling parent.</p>
     *
     * @param metaCsv The meta CSV file, UTF-8. Must not be {@code null}.
     * @return This builder.
     * @throws IOException Thrown if reading fails, the file is empty, a required column
     *         is missing, a quoted field is unterminated, or a row is short, has an
     *         empty name column, or has an empty placetype column.
     * @throws IllegalArgumentException Thrown if {@code metaCsv} is {@code null}.
     */
    public Builder addWofMeta(Path metaCsv) throws IOException {
      if (metaCsv == null) {
        throw new IllegalArgumentException("metaCsv must not be null");
      }
      final WofMetaRows consumer = new WofMetaRows(this, metaCsv);
      parseCsv(metaCsv, consumer);
      if (!consumer.sawHeader) {
        throw new IOException("empty meta CSV: " + metaCsv);
      }
      return this;
    }

    /**
     * Builds the spine.
     *
     * @return The immutable hierarchy. Never {@code null}.
     * @throws IllegalArgumentException Thrown if no place was added.
     */
    public ContainmentSpine build() {
      if (places.isEmpty()) {
        throw new IllegalArgumentException("no places were added");
      }
      return new ContainmentSpine(Map.copyOf(places));
    }

    private static String positiveOrNull(String parent) {
      if (parent.isEmpty() || parent.startsWith("-") || "0".equals(parent)) {
        return null;
      }
      return parent;
    }
  }

  private static List<String> readLines(Path file) throws IOException {
    final String content;
    try (InputStream in = Files.newInputStream(file)) {
      content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    final List<String> lines = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= content.length(); i++) {
      if (i == content.length() || content.charAt(i) == '\n') {
        int end = i;
        if (end > start && content.charAt(end - 1) == '\r') {
          end--;
        }
        lines.add(content.substring(start, end));
        start = i + 1;
      }
    }
    return lines;
  }

  private static List<String> splitOn(String line, char separator) {
    final List<String> fields = new ArrayList<>();
    int start = 0;
    for (int i = 0; i <= line.length(); i++) {
      if (i == line.length() || line.charAt(i) == separator) {
        fields.add(line.substring(start, i));
        start = i + 1;
      }
    }
    return fields;
  }

  /** Consumes CSV rows as the parser completes them, with the row's starting line. */
  private interface CsvRows {
    void row(int line, List<String> fields) throws IOException;
  }

  /**
   * Consumes meta CSV rows as the parser completes them, so the table is never
   * materialized as a whole: the first row binds the header columns, and every further
   * row is validated and added to the builder immediately.
   */
  private static final class WofMetaRows implements CsvRows {

    private final Builder builder;
    private final Path file;
    private boolean sawHeader;
    private int idColumn;
    private int parentColumn;
    private int nameColumn;
    private int typeColumn;
    private int lastColumn;

    private WofMetaRows(Builder builder, Path file) {
      this.builder = builder;
      this.file = file;
    }

    @Override
    public void row(int line, List<String> fields) throws IOException {
      if (!sawHeader) {
        sawHeader = true;
        idColumn = fields.indexOf("id");
        parentColumn = fields.indexOf("parent_id");
        nameColumn = fields.indexOf("name");
        typeColumn = fields.indexOf("placetype");
        if (idColumn < 0 || parentColumn < 0 || nameColumn < 0 || typeColumn < 0) {
          throw new IOException(
              "meta CSV lacks id, parent_id, name, or placetype columns: " + file);
        }
        lastColumn = Math.max(Math.max(idColumn, parentColumn),
            Math.max(nameColumn, typeColumn));
        return;
      }
      if (fields.size() <= lastColumn) {
        throw new IOException("short row at line " + line + " in " + file);
      }
      final String id = stripped(fields.get(idColumn));
      final String name = stripped(fields.get(nameColumn));
      final String type = stripped(fields.get(typeColumn));
      if (id.isEmpty()) {
        throw new IOException("empty id column at line " + line + " in " + file);
      }
      if (name.isEmpty()) {
        throw new IOException("empty name column at line " + line + " in "
            + file + ": id " + id);
      }
      if (type.isEmpty()) {
        throw new IOException("empty placetype column at line " + line + " in "
            + file + ": id " + id);
      }
      final String parent = stripped(fields.get(parentColumn));
      builder.add(id, Builder.positiveOrNull(parent), name, type);
    }
  }

  /**
   * Parses a CSV file row by row, with double-quote quoting, doubled-quote escapes,
   * and quoted fields that may span lines, so a row is only ended by a line break
   * outside a quoted field. Blank lines between rows are dropped. Both {@code LF} and
   * {@code CRLF} end a line, inside a quoted field as well as outside, and a quoted
   * line break is kept in the field as a single {@code LF}. A quote inside an unquoted
   * field and content after a field's closing quote both fail loud, because either
   * would otherwise splice neighboring fields or rows together silently.
   *
   * <p>The file is streamed, never materialized whole, so a table of any size parses
   * in memory proportional to its longest row.</p>
   *
   * @param file The CSV file, UTF-8.
   * @param consumer Receives each completed row with its starting line.
   * @throws IOException Thrown if reading fails, a quoted field is never closed, a
   *         quote appears inside an unquoted field, or content follows a closing quote.
   */
  private static void parseCsv(Path file, CsvRows consumer) throws IOException {
    // The file streams through a replacing UTF-8 decoder, so tables larger than any
    // in-memory buffer parse in constant memory and stray malformed bytes read as
    // replacement characters instead of aborting the load.
    try (Reader in = new BufferedReader(new InputStreamReader(Files.newInputStream(file),
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)))) {
      final StringBuilder field = new StringBuilder();
      List<String> fields = new ArrayList<>();
      boolean quoted = false;
      boolean closedQuote = false;
      int line = 1;
      int rowLine = 1;
      int quoteLine = 1;
      // One character of pushback covers the two lookahead cases, the CRLF pair and
      // the doubled quote; NONE marks an empty pushback slot.
      final int none = -2;
      int pending = none;
      while (true) {
        final int read = pending != none ? pending : in.read();
        pending = none;
        if (read < 0) {
          break;
        }
        final char c = (char) read;
        boolean lineBreak = c == '\n';
        if (c == '\r') {
          final int follow = in.read();
          if (follow == '\n') {
            lineBreak = true;
          } else {
            pending = follow;
          }
        }
        if (quoted) {
          if (c == '"') {
            final int follow = in.read();
            if (follow == '"') {
              field.append('"');
            } else {
              quoted = false;
              closedQuote = true;
              pending = follow;
            }
          } else if (lineBreak) {
            field.append('\n');
            line++;
          } else {
            field.append(c);
          }
        } else if (c == '"') {
          if (field.length() > 0 || closedQuote) {
            throw new IOException("stray quote in an unquoted field at line " + line
                + " in " + file);
          }
          quoted = true;
          quoteLine = line;
        } else if (c == ',') {
          fields.add(field.toString());
          field.setLength(0);
          closedQuote = false;
        } else if (lineBreak) {
          fields.add(field.toString());
          field.setLength(0);
          closedQuote = false;
          emitRow(consumer, rowLine, fields);
          fields = new ArrayList<>();
          line++;
          rowLine = line;
        } else {
          if (closedQuote) {
            throw new IOException("content after a closing quote at line " + line
                + " in " + file);
          }
          field.append(c);
        }
      }
      if (quoted) {
        throw new IOException("unterminated quoted field starting at line " + quoteLine
            + " in " + file);
      }
      if (field.length() > 0 || !fields.isEmpty() || closedQuote) {
        fields.add(field.toString());
        emitRow(consumer, rowLine, fields);
      }
    }
  }

  /** Hands the row to the consumer unless it is a blank line, which carries no fields. */
  private static void emitRow(CsvRows consumer, int line, List<String> fields)
      throws IOException {
    if (fields.size() == 1 && fields.get(0).isEmpty()) {
      return;
    }
    consumer.row(line, List.copyOf(fields));
  }


  /**
   * Strips leading and trailing whitespace under the project whitespace definition, so
   * cells padded with no-break spaces read like cells padded with spaces.
   */
  private static String stripped(String value) {
    int start = 0;
    int end = value.length();
    while (start < end) {
      final int cp = value.codePointAt(start);
      if (!StringUtil.isWhitespace(cp)) {
        break;
      }
      start += Character.charCount(cp);
    }
    while (end > start) {
      final int cp = value.codePointBefore(end);
      if (!StringUtil.isWhitespace(cp)) {
        break;
      }
      end -= Character.charCount(cp);
    }
    return value.substring(start, end);
  }
}
