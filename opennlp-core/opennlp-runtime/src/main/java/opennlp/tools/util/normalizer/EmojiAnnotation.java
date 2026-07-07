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

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The annotation record of one emoji: an extensible per-symbol store of attribute values, each
 * carrying its own provenance. A record is assembled from up to three strictly separated layers:
 * bundled facts from {@code emoji-annotations.txt} (see {@link EmojiAnnotations}), derived facts
 * computed from the code point sequence itself, and joined facts resolved at run time against
 * user-installed data. The typed accessors ({@link #name()}, {@link #sentiment()},
 * {@link #entityType()}, {@link #category()}) are projections of the {@link #attributes()} map,
 * so an attribute added later is new rows plus an accessor, not a shape change.
 *
 * <p>Every attribute is optional: a consumer that only wants one dimension pays nothing for the
 * rest, and a record never fabricates a value it has no source for. Instances are immutable and
 * thread-safe.</p>
 *
 * <p>These annotations are per-symbol <em>metadata</em>, not text transforms, which is why they
 * are not {@link Dimension} constants; see {@link EmojiAnnotations} for the design note.</p>
 *
 * @param symbol     The annotated code point sequence, without the U+FE0F presentation selector.
 * @param attributes The attribute values keyed by attribute name ({@link #NAME},
 *                   {@link #SENTIMENT}, {@link #ENTITY_TYPE}, {@link #CATEGORY}).
 */
public record EmojiAnnotation(String symbol, Map<String, Value> attributes) {

  /** The attribute key of the human-readable name (the CLDR short name). */
  public static final String NAME = "name";

  /** The attribute key of the project-authored coarse sentiment score, an integer in -2..2. */
  public static final String SENTIMENT = "sentiment";

  /** The attribute key of the coarse {@link EmojiEntityType entity type}. */
  public static final String ENTITY_TYPE = "entityType";

  /** The attribute key of the {@link EmojiCategory document-category hint}. */
  public static final String CATEGORY = "category";

  /**
   * The attribute key of the ISO 3166 region code of a flag emoji. Never a bundled row: the value
   * is decoded from the code point sequence by the derived layer (see {@code EmojiFlags}).
   */
  public static final String ISO_REGION = "isoRegion";

  /**
   * One attribute value with its provenance, mirroring one row of the bundled data file.
   *
   * @param value  The attribute value. Must not be {@code null} or empty.
   * @param source The provenance tag, for example {@code CLDR:annotation}, {@code UCD:emoji-test},
   *               or {@code UNSPECIFIED} (the explicit marker for a project judgment). Must not be
   *               {@code null} or empty.
   * @param notes  Free-text notes, for example the upstream subgroup a value was derived from.
   *               Must not be {@code null}; may be empty.
   */
  public record Value(String value, String source, String notes) {

    public Value {
      if (value == null || value.isEmpty()) {
        throw new IllegalArgumentException("Value must not be null or empty");
      }
      if (source == null || source.isEmpty()) {
        throw new IllegalArgumentException("Source must not be null or empty");
      }
      if (notes == null) {
        throw new IllegalArgumentException("Notes must not be null");
      }
    }
  }

  /**
   * Creates a record; normally records come from {@link EmojiAnnotations#lookup(CharSequence)}.
   *
   * @param symbol     The annotated code point sequence. Must not be {@code null} or empty.
   * @param attributes The attribute values keyed by attribute name. Must not be {@code null} or
   *                   contain {@code null} keys or values; it is defensively copied.
   * @throws IllegalArgumentException if {@code symbol} is {@code null} or empty, or if
   *     {@code attributes} is {@code null} or contains a {@code null} key or value.
   */
  public EmojiAnnotation {
    if (symbol == null || symbol.isEmpty()) {
      throw new IllegalArgumentException("Symbol must not be null or empty");
    }
    if (attributes == null) {
      throw new IllegalArgumentException("Attributes must not be null");
    }
    for (final Map.Entry<String, Value> entry : attributes.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new IllegalArgumentException(
            "Attributes must not contain a null key or value, got: " + attributes);
      }
    }
    attributes = Map.copyOf(attributes);
  }

  /**
   * Returns the value of one attribute with its provenance.
   *
   * @param name The attribute name, for example {@link #NAME}. Must not be {@code null}.
   * @return The value, or empty when this record does not carry the attribute.
   * @throws IllegalArgumentException if {@code name} is {@code null}.
   */
  public Optional<Value> attribute(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Name must not be null");
    }
    return Optional.ofNullable(attributes.get(name));
  }

  /**
   * {@return the human-readable name (the CLDR short name), or empty when not annotated}
   */
  public Optional<String> name() {
    final Value value = attributes.get(NAME);
    return value == null ? Optional.empty() : Optional.of(value.value());
  }

  /**
   * {@return the ISO 3166 code of a flag emoji ({@code DE}, {@code GB-ENG}), or empty when this
   * record is not a flag} Populated by the derived layer through {@code EmojiAnnotator}.
   */
  public Optional<String> isoRegion() {
    final Value value = attributes.get(ISO_REGION);
    return value == null ? Optional.empty() : Optional.of(value.value());
  }

  /**
   * {@return the project-authored coarse sentiment score in -2..2, or empty when not annotated}
   *
   * @throws IllegalStateException if the stored value is not an integer, which cannot happen for
   *     records loaded from the bundled data (the loader validates it).
   */
  public OptionalInt sentiment() {
    final Value value = attributes.get(SENTIMENT);
    if (value == null) {
      return OptionalInt.empty();
    }
    try {
      return OptionalInt.of(Integer.parseInt(value.value()));
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Sentiment value '" + value.value() + "' of symbol '"
          + symbol + "' is not an integer", e);
    }
  }

  /**
   * {@return the coarse entity type, or empty when not annotated}
   *
   * @throws IllegalStateException if the stored value is not an {@link EmojiEntityType} constant,
   *     which cannot happen for records loaded from the bundled data (the loader validates it).
   */
  public Optional<EmojiEntityType> entityType() {
    final Value value = attributes.get(ENTITY_TYPE);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(EmojiEntityType.valueOf(value.value()));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Entity type value '" + value.value() + "' of symbol '"
          + symbol + "' is not an EmojiEntityType constant", e);
    }
  }

  /**
   * {@return the document-category hint, or empty when not annotated}
   *
   * @throws IllegalStateException if the stored value is not an {@link EmojiCategory} constant,
   *     which cannot happen for records loaded from the bundled data (the loader validates it).
   */
  public Optional<EmojiCategory> category() {
    final Value value = attributes.get(CATEGORY);
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(EmojiCategory.valueOf(value.value()));
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Category value '" + value.value() + "' of symbol '"
          + symbol + "' is not an EmojiCategory constant", e);
    }
  }
}
