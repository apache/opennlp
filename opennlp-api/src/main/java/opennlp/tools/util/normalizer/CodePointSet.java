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
package opennlp.tools.util.normalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * An immutable set of Unicode code points with O(1) membership.
 *
 * <p>Backed by a {@link BitSet} keyed directly by code point, so {@link #contains(int)} is a
 * single array-word read with no boxing, hashing, or branching beyond a range check. Memory is
 * bounded by the largest member code point (the whole of Unicode would cost about {@code 136 KiB},
 * and the standard whitespace and dash sets are entirely or almost entirely in the Basic
 * Multilingual Plane, so a few kilobytes in practice).</p>
 *
 * <p>This type carries no opinion about what the code points mean. It is the explicit,
 * standards-sourced data layer that {@link CharClass} and the reference tables
 * ({@link UnicodeWhitespace}, {@link UnicodeDash}) are built from, and that users extend or
 * override through {@link #fromFile(Path, String)}.</p>
 */
public final class CodePointSet {

  private final BitSet members;

  private CodePointSet(BitSet members) {
    this.members = members;
  }

  /**
   * Creates a set from explicit code points.
   *
   * @param codePoints The code points to include.
   * @return The set.
   * @throws IllegalArgumentException Thrown if any value is not a valid Unicode code point
   *     (outside {@code [0, U+10FFFF]}).
   */
  public static CodePointSet of(int... codePoints) {
    final BitSet members = new BitSet();
    for (final int codePoint : codePoints) {
      requireValid(codePoint);
      members.set(codePoint);
    }
    return new CodePointSet(members);
  }

  /**
   * Creates a set covering an inclusive code point range.
   *
   * @param firstInclusive The first code point in the range.
   * @param lastInclusive The last code point in the range.
   * @return The set.
   * @throws IllegalArgumentException Thrown if either bound is invalid or {@code firstInclusive}
   *     is greater than {@code lastInclusive}.
   */
  public static CodePointSet ofRange(int firstInclusive, int lastInclusive) {
    requireValid(firstInclusive);
    requireValid(lastInclusive);
    if (firstInclusive > lastInclusive) {
      throw new IllegalArgumentException("Range start " + firstInclusive
          + " must not exceed range end " + lastInclusive + ".");
    }
    final BitSet members = new BitSet();
    members.set(firstInclusive, lastInclusive + 1);
    return new CodePointSet(members);
  }

  /**
   * Loads the code points declared under one section of a user definitions file.
   *
   * <p>The format is line oriented and parsed with simple cursor scanning, not a regular
   * expression: a {@code [name]} line opens a section; a {@code #} begins a comment that runs to
   * end of line; each remaining line is a single hex code point ({@code U+00A0}, {@code 0x00A0},
   * or {@code 00A0}) or an inclusive range ({@code U+2000-U+200A}). Section names match case
   * insensitively. Only entries under the requested section are returned, so one file can carry,
   * for example, both {@code [whitespace]} and {@code [dash]} sections.</p>
   *
   * @param definitions The file to read (UTF-8).
   * @param section The section whose entries should be loaded.
   * @return The code points declared under {@code section}, or an empty set if the section is
   *     absent.
   * @throws IOException Thrown if the file cannot be read.
   * @throws IllegalArgumentException Thrown if a line is malformed, naming the offending line.
   */
  public static CodePointSet fromFile(Path definitions, String section) throws IOException {
    Objects.requireNonNull(definitions, "definitions");
    return parse(Files.readAllLines(definitions, StandardCharsets.UTF_8), section);
  }

  // Package visible so the parser can be exercised directly, without a temporary file.
  static CodePointSet parse(List<String> lines, String section) {
    Objects.requireNonNull(section, "section");
    final String wanted = section.trim().toLowerCase(Locale.ROOT);
    final BitSet members = new BitSet();
    String current = null;

    for (int i = 0; i < lines.size(); i++) {
      final String raw = lines.get(i);
      final int lineNumber = i + 1;
      final String line = stripComment(raw).strip();
      if (line.isEmpty()) {
        continue;
      }
      if (line.charAt(0) == '[') {
        if (line.length() < 3 || line.charAt(line.length() - 1) != ']') {
          throw malformed("section header", lineNumber, raw);
        }
        current = line.substring(1, line.length() - 1).strip().toLowerCase(Locale.ROOT);
        continue;
      }
      if (current == null) {
        throw new IllegalArgumentException("Code point entry before any [section] header on line "
            + lineNumber + ": " + raw);
      }
      if (wanted.equals(current)) {
        addEntry(members, line, lineNumber, raw);
      }
    }

    return new CodePointSet(members);
  }

  private static void addEntry(BitSet members, String line, int lineNumber, String raw) {
    final int separator = line.indexOf('-');
    if (separator < 0) {
      members.set(parseCodePoint(line, lineNumber, raw));
      return;
    }
    final int low = parseCodePoint(line.substring(0, separator).strip(), lineNumber, raw);
    final int high = parseCodePoint(line.substring(separator + 1).strip(), lineNumber, raw);
    if (low > high) {
      throw new IllegalArgumentException("Descending code point range on line "
          + lineNumber + ": " + raw);
    }
    members.set(low, high + 1);
  }

  private static int parseCodePoint(String token, int lineNumber, String raw) {
    String hex = token;
    if (hex.length() >= 2) {
      final String prefix = hex.substring(0, 2).toLowerCase(Locale.ROOT);
      if (prefix.equals("u+") || prefix.equals("0x")) {
        hex = hex.substring(2);
      }
    }
    if (hex.isEmpty()) {
      throw malformed("code point", lineNumber, raw);
    }
    final int codePoint;
    try {
      codePoint = Integer.parseInt(hex, 16);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid hex code point '" + token + "' on line "
          + lineNumber + ": " + raw, e);
    }
    if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
      throw new IllegalArgumentException("Code point out of range on line "
          + lineNumber + ": " + raw);
    }
    return codePoint;
  }

  private static String stripComment(String raw) {
    final int hash = raw.indexOf('#');
    return hash < 0 ? raw : raw.substring(0, hash);
  }

  private static IllegalArgumentException malformed(String what, int lineNumber, String raw) {
    return new IllegalArgumentException("Malformed " + what + " on line " + lineNumber + ": " + raw);
  }

  private static void requireValid(int codePoint) {
    if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
      throw new IllegalArgumentException("Not a Unicode code point: " + codePoint);
    }
  }

  /**
   * Tests membership.
   *
   * @param codePoint The code point to test. Out-of-range values return {@code false}.
   * @return {@code true} if the code point is in this set.
   */
  public boolean contains(int codePoint) {
    return codePoint >= 0 && codePoint <= Character.MAX_CODE_POINT && members.get(codePoint);
  }

  /**
   * Returns a new set containing every code point in this set or {@code other}.
   *
   * @param other The set to union with.
   * @return The union, a new set; neither input is modified.
   */
  public CodePointSet union(CodePointSet other) {
    Objects.requireNonNull(other, "other");
    final BitSet merged = (BitSet) members.clone();
    merged.or(other.members);
    return new CodePointSet(merged);
  }

  /** {@return the number of code points in this set} */
  public int size() {
    return members.cardinality();
  }

  /** {@return whether this set is empty} */
  public boolean isEmpty() {
    return members.isEmpty();
  }

  /** {@return the member code points, in ascending order} */
  public int[] toArray() {
    return members.stream().toArray();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CodePointSet other && members.equals(other.members);
  }

  @Override
  public int hashCode() {
    return members.hashCode();
  }
}
