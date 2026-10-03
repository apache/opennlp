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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import opennlp.tools.util.StringUtil;

/**
 * The bundled-facts layer of the emoji annotation record store: license-clean, provenance-tagged
 * attributes intrinsic to a pictograph (name, coarse sentiment, entity type, document category),
 * loaded on first use from the project-authored {@code emoji-annotations.txt} resource. Each row
 * of that file is one attribute of one symbol
 * ({@code codepoints ; attribute ; value ; source ; notes}), so every value carries its own
 * provenance and adding an attribute later is new rows plus loader support instead of a
 * file-format break. The loader fails loud on an unknown attribute or a
 * malformed row: the data and the code move together, so an unrecognized attribute is corruption,
 * not extensibility.
 *
 * <p>Data licensing: the name values are the CLDR short names and the entity-type/category values
 * are derived from the group and subgroup headers of the upstream {@code emoji-test.txt}
 * (UTS&#160;#51, Unicode License V3, see the NOTICE file); the sentiment scores are original
 * project judgments tagged {@code UNSPECIFIED}. No third-party sentiment data set is copied; in
 * particular the Emoji Sentiment Ranking (CC&#160;BY-SA) is not used in any form.</p>
 *
 * <p>{@link EmojiAnnotator} is the accessor. Lookups strip U+FE0F (emoji presentation selector)
 * because it does not change identity; bundled rows are keyed without it. Flag emoji have no
 * bundled rows: region is derived, and gazetteer ids are never baked in.</p>
 */
public final class EmojiAnnotations {

  private static final String RESOURCE = "emoji-annotations.txt";

  /** Starts a comment line in {@code emoji-annotations.txt}. */
  private static final String COMMENT_PREFIX = "#";

  /**
   * Field separator in {@code emoji-annotations.txt}
   * ({@code codepoints ; attribute ; value ; source ; notes}).
   */
  private static final char FIELD_SEPARATOR = ';';

  // The records keyed by code point sequence, loaded on the first lookup.
  private static final BundledUnicodeData.Lazy<Map<String, EmojiAnnotation>> ANNOTATIONS =
      new BundledUnicodeData.Lazy<>(EmojiAnnotations::load);

  private EmojiAnnotations() {
  }

  /**
   * Returns the bundled annotation record of one emoji. The bundled data is loaded on the first
   * call; a failed load is not cached, so the next call loads again.
   *
   * @param symbol The code point sequence of one symbol, for example one {@link Term#original()}
   *               token. U+FE0F presentation selectors are ignored. Must not be {@code null}.
   * @return The record, or empty when the bundled data does not annotate the symbol.
   * @throws IllegalArgumentException if {@code symbol} is {@code null}, or if the bundled data
   *     is malformed.
   * @throws IllegalStateException if the bundled data resource is missing.
   * @throws UncheckedIOException if the bundled data resource cannot be read.
   */
  public static Optional<EmojiAnnotation> lookup(CharSequence symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Symbol must not be null");
    }
    final String key = stripPresentationSelector(symbol);
    if (key.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(ANNOTATIONS.get().get(key));
  }

  /**
   * Removes every U+FE0F VARIATION SELECTOR-16 from {@code symbol}. When none is present, a
   * {@link String} argument is returned as is and any other {@link CharSequence} is copied once
   * by {@link CharSequence#toString()}. Package-visible so {@link EmojiAnnotator} keys
   * derived-only records the same way.
   *
   * @param symbol The code point sequence to strip.
   * @return The sequence without presentation selectors; {@code symbol}'s own text when it
   *     contains none.
   */
  static String stripPresentationSelector(CharSequence symbol) {
    final int length = symbol.length();
    int i = 0;
    while (i < length && symbol.charAt(i) != 0xFE0F) {
      i++;
    }
    if (i == length) {
      return symbol.toString();
    }
    final StringBuilder stripped = new StringBuilder(length - 1);
    stripped.append(symbol, 0, i);
    for (int k = i + 1; k < length; k++) {
      final char c = symbol.charAt(k);
      if (c != 0xFE0F) {
        stripped.append(c);
      }
    }
    return stripped.toString();
  }

  /**
   * {@return the records parsed from the bundled {@code emoji-annotations.txt} resource}
   *
   * @throws IllegalStateException if the resource is missing.
   * @throws UncheckedIOException if the resource cannot be read.
   */
  private static Map<String, EmojiAnnotation> load() {
    return BundledUnicodeData.load(EmojiAnnotations.class, RESOURCE, "emoji annotation",
        EmojiAnnotations::parse);
  }

  /**
   * Parses annotation rows of the form {@code codepoints ; attribute ; value ; source ; notes}
   * with space-separated hexadecimal code points; {@code '#'} starts a comment line. The notes
   * column is the fifth and final field, so it may contain {@code ';'}. Package-private so the
   * malformed-data handling can be exercised without the bundled resource.
   *
   * @param in The stream to read, in UTF-8; consumed and closed by this method.
   * @return One immutable record per annotated symbol, keyed by the selector-stripped symbol.
   * @throws IOException Thrown if reading {@code in} fails.
   * @throws IllegalArgumentException Thrown if a row is malformed.
   */
  static Map<String, EmojiAnnotation> parse(InputStream in) throws IOException {
    final Map<String, Map<String, EmojiAnnotation.Value>> rows = new HashMap<>();
    BundledUnicodeData.forEachLine(in, StandardCharsets.UTF_8, (line, lineNumber) -> {
      final String content = line.strip();
      if (content.isEmpty() || content.startsWith(COMMENT_PREFIX)) {
        return;
      }
      // Bounded split: only the first four separators are structural.
      final String[] fields = StringUtil.split(content, FIELD_SEPARATOR, 5);
      if (fields.length != 5) {
        throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
            + " at line " + lineNumber + ": expected 5 fields, got " + fields.length
            + " in: " + content);
      }
      final String symbol = decode(fields[0], lineNumber, content);
      final String attribute = fields[1].strip();
      final String value = fields[2].strip();
      final String source = fields[3].strip();
      final String notes = fields[4].strip();
      validate(attribute, value, source, lineNumber, content);
      final Map<String, EmojiAnnotation.Value> record =
          rows.computeIfAbsent(symbol, k -> new HashMap<>());
      if (record.putIfAbsent(attribute, new EmojiAnnotation.Value(value, source, notes)) != null) {
        throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
            + " at line " + lineNumber + ": duplicate attribute '" + attribute
            + "' in: " + content);
      }
    });
    final Map<String, EmojiAnnotation> records = new HashMap<>(rows.size());
    for (final Map.Entry<String, Map<String, EmojiAnnotation.Value>> entry : rows.entrySet()) {
      records.put(entry.getKey(), new EmojiAnnotation(entry.getKey(), entry.getValue()));
    }
    return Map.copyOf(records);
  }

  // Fails loud on an unknown attribute and on a value the attribute's type does not admit, so a
  // corrupted or drifted data file cannot load quietly.
  private static void validate(String attribute, String value, String source,
                               int lineNumber, String content) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
          + " at line " + lineNumber + ": empty value in: " + content);
    }
    if (source.isEmpty()) {
      throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
          + " at line " + lineNumber + ": empty source in: " + content);
    }
    switch (attribute) {
      case EmojiAnnotation.NAME:
        break;
      case EmojiAnnotation.SENTIMENT:
        final int score;
        try {
          score = Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
              + " at line " + lineNumber + ": sentiment value '" + value
              + "' is not an integer in: " + content, e);
        }
        if (score < -2 || score > 2) {
          throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
              + " at line " + lineNumber + ": sentiment value " + score
              + " is outside -2..2 in: " + content);
        }
        break;
      case EmojiAnnotation.ENTITY_TYPE:
        try {
          EmojiEntityType.valueOf(value);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
              + " at line " + lineNumber + ": unrecognized entityType value '" + value
              + "' in: " + content, e);
        }
        break;
      case EmojiAnnotation.CATEGORY:
        try {
          EmojiCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
              + " at line " + lineNumber + ": unrecognized category value '" + value
              + "' in: " + content, e);
        }
        break;
      default:
        throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
            + " at line " + lineNumber + ": unknown attribute '" + attribute + "' in: " + content);
    }
  }

  private static String decode(String hexCodePoints, int lineNumber, String content) {
    final String stripped = hexCodePoints.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
          + " at line " + lineNumber + ": empty code point sequence in: " + content);
    }
    try {
      return HexCodePoints.decodeSequence(stripped);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
          + " at line " + lineNumber + ": " + content, e);
    }
  }
}
