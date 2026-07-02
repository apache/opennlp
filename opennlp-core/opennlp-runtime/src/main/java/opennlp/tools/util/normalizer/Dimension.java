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

import java.util.function.Supplier;

/**
 * A layer of the {@link Term} normalization stack, in increasing order of aggressiveness. A
 * {@link TermAnalyzer} applies a configured prefix of these to each token; the declaration order is
 * the canonical pipeline order, because the transforms do not commute (case folding then accent
 * folding differs from the reverse for Turkish dotted/dotless i and the German eszett).
 *
 * <p>This enum is the single definition of the character-level steps: each one carries its default
 * {@link CharSequenceNormalizer}, which both {@link TermAnalyzer} and {@link TextNormalizer} read
 * from rather than re-listing. The default is resolved lazily, so loading this enum does not eagerly
 * initialize heavy data such as the confusables table.</p>
 *
 * <p>{@link #ORIGINAL} is the source token and is always present. {@link #STEM} and {@link #LEMMA}
 * are token-level and have no default normalizer; they require a
 * {@link opennlp.tools.stemmer.Stemmer} or {@link opennlp.tools.lemmatizer.Lemmatizer} on the
 * analyzer ({@link #LEMMA} also a part-of-speech tag).</p>
 */
public enum Dimension {

  /** The original token text, the canonical source of truth. */
  ORIGINAL(null),

  /** Unicode canonical composition (NFC); lossless under canonical equivalence. */
  NFC(NfcCharSequenceNormalizer::getInstance),

  /** Unicode compatibility composition (NFKC); lossy (for example superscripts to digits). */
  NFKC(NfkcCharSequenceNormalizer::getInstance),

  /** Unicode whitespace folded to ASCII spaces. */
  WHITESPACE(WhitespaceCharSequenceNormalizer::getInstance),

  /** Unicode dashes folded to the ASCII hyphen-minus. */
  DASH(DashCharSequenceNormalizer::getInstance),

  /** Case folding; lossy and locale sensitive. */
  CASE_FOLD(CaseFoldCharSequenceNormalizer::getInstance),

  /** Diacritic and accent folding; lossy, script gated, and language-wrong for some languages. */
  ACCENT_FOLD(AccentFoldCharSequenceNormalizer::getInstance),

  /** Confusable (homoglyph) skeleton folding per UTS #39; lossy, for matching only. */
  CONFUSABLE_FOLD(ConfusableSkeletonCharSequenceNormalizer::getInstance),

  /** Stemming through a configured {@link opennlp.tools.stemmer.Stemmer}. */
  STEM(null),

  /** Lemmatization through a configured {@link opennlp.tools.lemmatizer.Lemmatizer}. */
  LEMMA(null);

  private final Supplier<CharSequenceNormalizer> defaultNormalizer;

  Dimension(Supplier<CharSequenceNormalizer> defaultNormalizer) {
    this.defaultNormalizer = defaultNormalizer;
  }

  /**
   * {@return the default character-level normalizer for this dimension, or {@code null} for
   * {@link #ORIGINAL}, {@link #STEM}, and {@link #LEMMA}} The normalizer is resolved lazily, so it
   * is not initialized until first requested.
   */
  public CharSequenceNormalizer defaultNormalizer() {
    return defaultNormalizer == null ? null : defaultNormalizer.get();
  }
}
