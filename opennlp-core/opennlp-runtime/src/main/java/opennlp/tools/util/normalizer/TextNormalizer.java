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
 * A fluent builder that composes the normalization rungs into a single
 * {@link CharSequenceNormalizer}.
 *
 * <p>The rungs are applied in the order they are added, so the caller controls the chain. A
 * conservative, search-oriented chain is available through {@link #searchDefault()}. Each rung is
 * a shared, stateless normalizer; the built normalizer is an {@link AggregateCharSequenceNormalizer}
 * that applies them in sequence.</p>
 *
 * <pre>{@code
 * CharSequenceNormalizer n = TextNormalizer.builder()
 *     .nfc().caseFold().accentFold()
 *     .build();
 * }</pre>
 */
public final class TextNormalizer {

  private final List<CharSequenceNormalizer> steps = new ArrayList<>();

  private TextNormalizer() {
  }

  /** {@return a new, empty builder} */
  public static TextNormalizer builder() {
    return new TextNormalizer();
  }

  /** {@return this builder with NFC canonical composition appended} */
  public TextNormalizer nfc() {
    return add(NfcCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with NFKC compatibility composition appended} */
  public TextNormalizer nfkc() {
    return add(NfkcCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with invisible/bidi control stripping appended} */
  public TextNormalizer stripInvisible() {
    return add(InvisibleCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with Unicode whitespace collapsing appended} */
  public TextNormalizer whitespace() {
    return add(WhitespaceCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with quotation-mark folding appended} */
  public TextNormalizer quotes() {
    return add(QuoteCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with dash folding appended} */
  public TextNormalizer dashes() {
    return add(DashCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with decimal-digit folding appended} */
  public TextNormalizer digits() {
    return add(DigitCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with ellipsis expansion appended} */
  public TextNormalizer ellipsis() {
    return add(EllipsisCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with list-bullet replacement appended} */
  public TextNormalizer bullets() {
    return add(BulletCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with case folding appended} */
  public TextNormalizer caseFold() {
    return add(CaseFoldCharSequenceNormalizer.getInstance());
  }

  /** {@return this builder with script-gated diacritic folding appended} */
  public TextNormalizer accentFold() {
    return add(AccentFoldCharSequenceNormalizer.getInstance());
  }

  /**
   * Appends a custom normalizer.
   *
   * @param custom The normalizer to append.
   * @return This builder.
   */
  public TextNormalizer with(CharSequenceNormalizer custom) {
    return add(Objects.requireNonNull(custom, "custom"));
  }

  /** {@return the composed normalizer for the rungs added so far} */
  public CharSequenceNormalizer build() {
    return new AggregateCharSequenceNormalizer(steps.toArray(new CharSequenceNormalizer[0]));
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

  private TextNormalizer add(CharSequenceNormalizer normalizer) {
    steps.add(normalizer);
    return this;
  }
}
