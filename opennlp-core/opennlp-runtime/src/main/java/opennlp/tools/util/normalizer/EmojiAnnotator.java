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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles one {@link EmojiAnnotation} per symbol from bundled facts
 * ({@link EmojiAnnotations}), derived facts ({@link EmojiFlags}), and optional joined facts
 * ({@link EmojiAnnotationJoin}). {@link #annotate(Term)} keys on {@link Term#original()};
 * non-emoji tokens and degenerate flag-shaped text return empty. Instances are immutable and
 * thread-safe when the join is.
 *
 * <p>Annotations are per-symbol metadata, not text transforms; a parallel surface beside
 * {@link Term} rather than {@link Dimension} constants. See OPENNLP-1870.</p>
 */
public final class EmojiAnnotator {

  /** Feature-name prefix for the coarse sentiment score, for example {@code emojiSentiment=2}. */
  public static final String FEATURE_SENTIMENT_PREFIX = "emojiSentiment=";

  /** Feature-name prefix for the coarse entity type, for example {@code emojiType=HEART}. */
  public static final String FEATURE_TYPE_PREFIX = "emojiType=";

  /**
   * Feature-name prefix for the document-category hint, for example
   * {@code emojiCategory=SMILEYS_AND_EMOTION}.
   */
  public static final String FEATURE_CATEGORY_PREFIX = "emojiCategory=";

  /** Feature-name prefix for a flag's ISO 3166 region, for example {@code emojiRegion=DE}. */
  public static final String FEATURE_REGION_PREFIX = "emojiRegion=";

  // Provenance tags of the derived facts; the mechanisms are defined by UTS #51.
  private static final String FLAG_SEQUENCE = "UTS51:flag-sequence";
  private static final String TAG_SEQUENCE = "UTS51:tag-sequence";
  private static final String DERIVED_NOTE = "decoded from the code point sequence";

  // The first non-ASCII code unit; every annotatable symbol starts at or beyond it.
  private static final char FIRST_NON_ASCII = 0x80;

  private final EmojiAnnotationJoin join;

  /**
   * Creates an annotator over the bundled and derived layers only.
   */
  public EmojiAnnotator() {
    this.join = null;
  }

  /**
   * Creates an annotator that also resolves joined facts through {@code join}.
   *
   * @param join The joined-facts hook, called while a record is assembled. Must not be
   *             {@code null}.
   * @throws IllegalArgumentException if {@code join} is {@code null}.
   */
  public EmojiAnnotator(EmojiAnnotationJoin join) {
    if (join == null) {
      throw new IllegalArgumentException("Join must not be null");
    }
    this.join = join;
  }

  /**
   * Annotates one term, keyed on its {@link Term#original() original} text (see the class note on
   * why the original layer is the one annotations describe).
   *
   * @param term The term to annotate. Must not be {@code null}.
   * @return The assembled record, or empty when the term is not an annotated symbol.
   * @throws IllegalArgumentException if {@code term} is {@code null}.
   * @throws IllegalStateException if the configured join violates its contract (returns
   *     {@code null} or a key colliding with an existing attribute).
   */
  public Optional<EmojiAnnotation> annotate(Term term) {
    if (term == null) {
      throw new IllegalArgumentException("Term must not be null");
    }
    return annotate(term.original());
  }

  /**
   * Annotates one symbol.
   *
   * @param symbol The code point sequence of one symbol (one token). U+FE0F presentation
   *               selectors are ignored. Must not be {@code null}.
   * @return The assembled record, or empty when {@code symbol} is not an annotated symbol.
   * @throws IllegalArgumentException if {@code symbol} is {@code null}.
   * @throws IllegalStateException if the configured join violates its contract (returns
   *     {@code null} or a key colliding with an existing attribute).
   */
  public Optional<EmojiAnnotation> annotate(CharSequence symbol) {
    if (symbol == null) {
      throw new IllegalArgumentException("Symbol must not be null");
    }
    final EmojiAnnotation bundled = EmojiAnnotations.lookup(symbol).orElse(null);
    // The lenient decode, not the strict one: degenerate flag-shaped tokens in real-world text
    // must yield no region, not an exception. Decoded once on this per-token hot path.
    final String isoRegion = EmojiFlags.isoRegionOrNull(symbol);
    if (bundled == null && isoRegion == null) {
      return Optional.empty(); // nothing bundled, nothing derived: the join never creates records
    }
    final String recordSymbol = bundled != null ? bundled.symbol()
        : EmojiAnnotations.stripPresentationSelector(symbol);
    final Map<String, EmojiAnnotation.Value> attributes = new LinkedHashMap<>();
    if (bundled != null) {
      attributes.putAll(bundled.attributes());
    }
    if (isoRegion != null) {
      final String source = isoRegion.indexOf('-') >= 0 ? TAG_SEQUENCE : FLAG_SEQUENCE;
      attributes.put(EmojiAnnotation.ISO_REGION,
          new EmojiAnnotation.Value(isoRegion, source, DERIVED_NOTE));
      // A flag is a FLAG in the FLAGS group by the sequence mechanics alone; bundled rows, if any
      // ever exist for a flag, would win.
      attributes.putIfAbsent(EmojiAnnotation.ENTITY_TYPE,
          new EmojiAnnotation.Value(EmojiEntityType.FLAG.name(), source, DERIVED_NOTE));
      attributes.putIfAbsent(EmojiAnnotation.CATEGORY,
          new EmojiAnnotation.Value(EmojiCategory.FLAGS.name(), source, DERIVED_NOTE));
    }
    if (join != null) {
      final Map<String, EmojiAnnotation.Value> joined = join.join(recordSymbol, isoRegion);
      if (joined == null) {
        throw new IllegalStateException(
            "The join returned null for symbol '" + recordSymbol + "'; return an empty map");
      }
      for (final Map.Entry<String, EmojiAnnotation.Value> entry : joined.entrySet()) {
        if (attributes.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
          throw new IllegalStateException("The join returned attribute '" + entry.getKey()
              + "' for symbol '" + recordSymbol + "', which collides with an existing attribute");
        }
      }
    }
    // A pure bundled hit needs no new record; return the cached one.
    if (bundled != null && attributes.size() == bundled.attributes().size()) {
      return Optional.of(bundled);
    }
    return Optional.of(new EmojiAnnotation(recordSymbol, attributes));
  }

  /**
   * {@return whether {@code token} could carry an emoji annotation and so is worth looking up}
   * Every annotatable symbol (a pictograph, a regional indicator, or the waving black flag) starts
   * beyond ASCII, audited in {@code EmojiAnnotationsTest}; feature generators call this to
   * fast-path ordinary tokens without touching the annotation layer.
   *
   * @param token The token to test. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code token} is {@code null}.
   */
  public static boolean isAnnotatableToken(CharSequence token) {
    if (token == null) {
      throw new IllegalArgumentException("Token must not be null");
    }
    return token.length() != 0 && token.charAt(0) >= FIRST_NON_ASCII;
  }
}
