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
 * The opt-in annotation surface of the emoji record store: assembles one {@link EmojiAnnotation}
 * per symbol from the three strictly separated layers, bundled facts
 * ({@link EmojiAnnotations}), derived facts ({@link EmojiFlags}), and joined facts (an optional
 * {@link EmojiAnnotationJoin}).
 *
 * <p><strong>Design note: a parallel accessor surface beside {@link Term}, not new
 * {@link Dimension} constants.</strong> A {@code Dimension} is a character-level text transform:
 * each constant produces another <em>string form</em> of the token, the constants form an ordered
 * pipeline in which every layer feeds the next, and {@link Term#normalized()},
 * {@link Term#at(Dimension)}, and {@link Term#peel()} all walk that stack of strings. An emoji
 * annotation is neither a string form nor a pipeline stage: it is typed, per-symbol
 * <em>metadata</em> (a score, two enums, a name, a region code, each with provenance) that
 * describes the original pictograph and composes with nothing downstream of it. Modeling it as
 * {@code Dimension} constants would force those typed facts through the stringly
 * {@code Map<Dimension, String>} layer cache, would give them a meaningless position in the
 * transform order, and would break the {@code Dimension} contract that every layer is applied on
 * top of the previous one. So annotations stay a parallel, opt-in lookup keyed by the token:
 * consumers that only want folded text never touch this class and pay nothing for it.</p>
 *
 * <p>{@link #annotate(Term)} reads {@link Term#original()}, the source of truth: the derived
 * layers exist for matching and may have folded the pictograph away entirely (for example
 * {@link Dimension#EMOJI_FOLD} rewrites it to an ASCII emoticon), while annotations describe the
 * symbol the author actually wrote.</p>
 *
 * <p>Bulk safety: annotation is total over arbitrary token text. A token that is not an annotated
 * symbol returns empty, and degenerate flag-shaped text (a lone regional indicator in damaged
 * input) returns no region rather than throwing; the strict, fail-loud decoding contract lives on
 * {@link EmojiFlags#isoRegion(CharSequence)} for callers that want it. An instance is immutable
 * and thread-safe when its join is.</p>
 */
public final class EmojiAnnotator {

  // Provenance tags of the derived facts; the mechanisms are defined by UTS #51.
  private static final String FLAG_SEQUENCE = "UTS51:flag-sequence";
  private static final String TAG_SEQUENCE = "UTS51:tag-sequence";
  private static final String DERIVED_NOTE = "decoded from the code point sequence";

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
    // The total predicate, not the strict decoder: degenerate flag-shaped tokens in real-world
    // text must yield no region, not an exception.
    final String isoRegion = EmojiFlags.isFlag(symbol)
        ? EmojiFlags.isoRegion(symbol).orElseThrow() : null;
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
}
