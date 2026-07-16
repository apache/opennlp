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

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for composing normalization steps into a single {@link CharSequenceNormalizer}.
 *
 * <p>Use {@link #builder()} to assemble a chain, or {@link #defaultChain()} for a conservative,
 * ready-made chain. Steps run in the order they are added. Each step is a shared, stateless
 * normalizer; the built result is an {@link AggregateCharSequenceNormalizer} that applies them
 * in sequence.</p>
 *
 * <pre>{@code
 * CharSequenceNormalizer n = TextNormalizer.builder()
 *     .nfc().caseFold().accentFold()
 *     .build();
 * }</pre>
 */
public final class TextNormalizer {

  /** Prevents instantiation; use the static factory methods. */
  private TextNormalizer() {
  }

  /** {@return a new, empty {@link Builder}} */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@return a conservative, ready-made normalization chain}
   *
   * <p>The chain strips invisible controls, applies NFC, collapses whitespace, folds quotes and
   * dashes, case folds, and finally applies script-gated diacritic folding.</p>
   */
  public static CharSequenceNormalizer defaultChain() {
    return builder()
        .stripInvisible()
        .nfc()
        .whitespace()
        .quotes()
        .dashes()
        .caseFold()
        .accentFold()
        .build();
  }

  /**
   * A fluent builder that appends normalization steps in order and composes them into one
   * {@link CharSequenceNormalizer} via {@link #build()}.
   */
  public static final class Builder {

    private final List<CharSequenceNormalizer> steps = new ArrayList<>();

    /**
     * Use {@link TextNormalizer#builder()} to obtain an instance.
     */
    private Builder() {
    }

    /** {@return this builder with NFC canonical composition appended} */
    public Builder nfc() {
      return add(Dimension.NFC.defaultNormalizer());
    }

    /** {@return this builder with NFKC compatibility composition appended} */
    public Builder nfkc() {
      return add(Dimension.NFKC.defaultNormalizer());
    }

    /** {@return this builder with invisible/bidi control stripping appended} */
    public Builder stripInvisible() {
      return add(InvisibleCharSequenceNormalizer.getInstance());
    }

    /** {@return this builder with Unicode whitespace collapsing appended} */
    public Builder whitespace() {
      return add(Dimension.WHITESPACE.defaultNormalizer());
    }

    /**
     * {@return this builder with whitespace collapsing that preserves line and paragraph breaks
     * appended} Horizontal runs collapse to a single space, but a run containing a line break
     * collapses to a single newline, so paragraph structure survives.
     */
    public Builder whitespacePreservingLineBreaks() {
      return add(LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance());
    }

    /** {@return this builder with quotation-mark folding appended} */
    public Builder quotes() {
      return add(QuoteCharSequenceNormalizer.getInstance());
    }

    /** {@return this builder with dash folding appended} */
    public Builder dashes() {
      return add(Dimension.DASH.defaultNormalizer());
    }

    /** {@return this builder with decimal-digit folding appended} */
    public Builder digits() {
      return add(DigitCharSequenceNormalizer.getInstance());
    }

    /** {@return this builder with ellipsis expansion appended} */
    public Builder ellipsis() {
      return add(EllipsisCharSequenceNormalizer.getInstance());
    }

    /** {@return this builder with list-bullet replacement appended} */
    public Builder bullets() {
      return add(BulletCharSequenceNormalizer.getInstance());
    }

    /** {@return this builder with case folding appended} */
    public Builder caseFold() {
      return add(Dimension.CASE_FOLD.defaultNormalizer());
    }

    /**
     * {@return this builder with Unicode full case folding appended}
     *
     * <p>Applies the full case foldings of {@code CaseFolding.txt} including the expanding folds
     * (sharp s to {@code ss}, the ligatures). It expects composed (NFC) input, since it maps per
     * code point. Prefer {@link #caseFold()} when the plain lower-case fold is sufficient.</p>
     */
    public Builder fullCaseFold() {
      return add(Dimension.FULL_CASE_FOLD.defaultNormalizer());
    }

    /** {@return this builder with script-gated diacritic folding appended} */
    public Builder accentFold() {
      return add(Dimension.ACCENT_FOLD.defaultNormalizer());
    }

    /**
     * Appends a custom normalizer.
     *
     * @param custom The normalizer to append. Must not be {@code null}.
     * @return This builder.
     * @throws IllegalArgumentException if {@code custom} is {@code null}.
     */
    public Builder with(CharSequenceNormalizer custom) {
      if (custom == null) {
        throw new IllegalArgumentException("custom must not be null");
      }
      return add(custom);
    }

    /** {@return the composed normalizer for the steps added so far} */
    public CharSequenceNormalizer build() {
      return new AggregateCharSequenceNormalizer(steps.toArray(new CharSequenceNormalizer[0]));
    }

    /**
     * {@return an offset-aware composition of the steps added so far}
     *
     * <p>Every step must be an {@link OffsetAwareNormalizer}. NFC, NFKC, accent folding, confusable
     * folding, and case folding are rejected because they delegate to {@link java.text.Normalizer}
     * or JDK case mapping and cannot report their per-character edits. The returned normalizer's
     * {@link OffsetAwareNormalizer#normalizeAligned(CharSequence)} maps a span found in the fully
     * normalized text back to the original input through every stage.</p>
     *
     * @throws IllegalStateException if any step cannot report an alignment; the message names the
     *     offending step
     */
    public OffsetAwareNormalizer buildAligned() {
      final OffsetAwareNormalizer[] aligned = new OffsetAwareNormalizer[steps.size()];
      for (int i = 0; i < steps.size(); i++) {
        final CharSequenceNormalizer step = steps.get(i);
        if (!(step instanceof OffsetAwareNormalizer)) {
          throw new IllegalStateException("step at 0-based index " + i + " (" + step.getClass().getName()
              + ") is not offset-aware and cannot be composed into an aligned pipeline; the "
              + "per-code-point folds report an alignment, while folds that delegate to "
              + "java.text.Normalizer or JDK case mapping (such as NFC, NFKC, accent, confusable, "
              + "or case folding) do not");
        }
        aligned[i] = (OffsetAwareNormalizer) step;
      }
      return new AlignedAggregateCharSequenceNormalizer(aligned);
    }

    /** Appends a normalization step and returns this builder. */
    private Builder add(CharSequenceNormalizer normalizer) {
      steps.add(normalizer);
      return this;
    }
  }
}
