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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The bundled-facts layer of the emoji annotation record store: license-clean, provenance-tagged
 * attributes intrinsic to a pictograph (name, coarse sentiment, entity type, document category),
 * loaded once from the project-authored {@code emoji-annotations.txt} resource. Each row of that
 * file is one attribute of one symbol ({@code codepoints ; attribute ; value ; source ; notes}),
 * so every value carries its own provenance and adding an attribute later is new rows plus loader
 * support instead of a file-format break. The loader fails loud on an unknown attribute or a
 * malformed row: the data and the code move together, so an unrecognized attribute is corruption,
 * not extensibility.
 *
 * <p>Data licensing: the name values are the CLDR short names and the entity-type/category values
 * are derived from the group and subgroup headers of the upstream {@code emoji-test.txt}
 * (UTS&#160;#51, Unicode License V3, see the NOTICE file); the sentiment scores are original
 * project judgments tagged {@code UNSPECIFIED}. No third-party sentiment data set is copied; in
 * particular the Emoji Sentiment Ranking (CC&#160;BY-SA) is not used in any form.</p>
 *
 * <p>Design note: these annotations are surfaced beside the {@link Term} model rather than as new
 * {@link Dimension} constants. A {@code Dimension} is a character-level text <em>transform</em>
 * whose result is another string layer of the token (each layer feeds the next, and
 * {@link Term#peel()}/{@link Term#normalized()} walk that stack), while an annotation is typed,
 * per-symbol <em>metadata</em> about the original pictograph: it has no place in the transform
 * pipeline, would not compose with the layers below it, and would force typed facts through a
 * stringly {@code Map<Dimension, String>}. The parallel accessor surface is
 * {@code EmojiAnnotator}, which consumes a {@code Term} and returns the record.</p>
 *
 * <p>Lookups strip U+FE0F VARIATION SELECTOR-16 (the emoji presentation selector) from the queried
 * sequence, because presentation selection does not change a symbol's identity; the bundled rows
 * are keyed without it. Flag emoji intentionally have no bundled rows: their region decodes from
 * the code point sequence itself (the derived-facts layer), and gazetteer identifiers are never
 * baked into bundled data.</p>
 */
public final class EmojiAnnotations {

  private static final String RESOURCE = "emoji-annotations.txt";

  // The records keyed by code point sequence, loaded lazily on first use and cached. Volatile so
  // the fully built, immutable map is safely published to every thread that observes it non-null.
  private static volatile Map<String, EmojiAnnotation> annotations;

  private EmojiAnnotations() {
  }

  /**
   * Returns the bundled annotation record of one emoji.
   *
   * @param symbol The code point sequence of one symbol, for example one {@link Term#original()}
   *               token. U+FE0F presentation selectors are ignored. Must not be {@code null}.
   * @return The record, or empty when the bundled data does not annotate the symbol.
   * @throws IllegalArgumentException if {@code symbol} is {@code null}.
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
    return Optional.ofNullable(annotations().get(key));
  }

  // Removes every U+FE0F VARIATION SELECTOR-16; allocation-free when none is present.
  // Package-visible so EmojiAnnotator keys derived-only records the same way.
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

  private static Map<String, EmojiAnnotation> annotations() {
    Map<String, EmojiAnnotation> map = annotations;
    if (map == null) {
      synchronized (EmojiAnnotations.class) {
        map = annotations;
        if (map == null) {
          map = load();
          annotations = map;
        }
      }
    }
    return map;
  }

  private static Map<String, EmojiAnnotation> load() {
    try (InputStream in = EmojiAnnotations.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing emoji annotation data resource: " + RESOURCE);
      }
      return parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read emoji annotation data resource " + RESOURCE, e);
    }
  }

  // Package-private so the malformed-data handling can be exercised without the bundled resource.
  // Parses rows of "codepoints ; attribute ; value ; source ; notes" with space-separated
  // hexadecimal code points; '#' starts a comment line. The notes column is the fifth and final
  // field, so it may contain ';'.
  static Map<String, EmojiAnnotation> parse(InputStream in) throws IOException {
    final Map<String, Map<String, EmojiAnnotation.Value>> rows = new HashMap<>();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        final String content = line.strip();
        if (content.isEmpty() || content.startsWith("#")) {
          continue;
        }
        // Bounded split: only the first four separators are structural.
        final String[] fields = content.split(";", 5);
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
      }
    }
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
      final StringBuilder decoded = new StringBuilder();
      for (final String hex : stripped.split(" ")) {
        decoded.appendCodePoint(Integer.parseInt(hex, 16));
      }
      return decoded.toString();
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Malformed emoji annotation data in " + RESOURCE
          + " at line " + lineNumber + ": " + content, e);
    }
  }
}
