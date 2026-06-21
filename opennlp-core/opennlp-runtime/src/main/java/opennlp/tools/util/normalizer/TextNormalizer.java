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
import java.util.Objects;

/**
 * Entry point for composing the normalization rungs into a single {@link CharSequenceNormalizer}.
 *
 * <p>Use {@link #builder()} to assemble a chain, or {@link #searchDefault()} for a conservative,
 * search-oriented chain. The rungs are applied in the order they are added, so the caller controls
 * the chain. Each rung is a shared, stateless normalizer; the built normalizer is an
 * {@link AggregateCharSequenceNormalizer} that applies them in sequence.</p>
 *
 * <pre>{@code
 * CharSequenceNormalizer n = TextNormalizer.builder()
 *     .nfc().caseFold().accentFold()
 *     .build();
 * }</pre>
 */
public final class TextNormalizer {

  private TextNormalizer() {
  }

  /** {@return a new, empty {@link Builder}} */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * {@return a conservative search/matching chain}
   *
   * <p>The chain strips invisible controls, applies NFC, collapses whitespace, folds quotes and
   * dashes, case folds, and finally applies script-gated diacritic folding.</p>
   */
  public static CharSequenceNormalizer searchDefault() {
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
   * A fluent builder that appends normalization rungs in order and composes them into one
   * {@link CharSequenceNormalizer} via {@link #build()}.
   */
  public static final class Builder {

    private final List<CharSequenceNormalizer> steps = new ArrayList<>();

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

    /** {@return this builder with script-gated diacritic folding appended} */
    public Builder accentFold() {
      return add(Dimension.ACCENT_FOLD.defaultNormalizer());
    }

    /**
     * Appends a custom normalizer.
     *
     * @param custom The normalizer to append.
     * @return This builder.
     */
    public Builder with(CharSequenceNormalizer custom) {
      return add(Objects.requireNonNull(custom, "custom"));
    }

    /** {@return the composed normalizer for the rungs added so far} */
    public CharSequenceNormalizer build() {
      return new AggregateCharSequenceNormalizer(steps.toArray(new CharSequenceNormalizer[0]));
    }

    private Builder add(CharSequenceNormalizer normalizer) {
      steps.add(normalizer);
      return this;
    }
  }
}
