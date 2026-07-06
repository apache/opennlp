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

import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

/**
 * Per-language normalization settings, mirroring how OpenNLP already selects a Snowball stemmer by
 * language. A profile pairs a language with its Snowball {@link SnowballStemmer.ALGORITHM} and the
 * diacritic fold appropriate for that language (if any).
 *
 * <p>The {@code accentFold} normalizer is the language's diacritic transform for a matching form, or
 * {@code null} when folding is not appropriate. It is the generic
 * {@link AccentFoldCharSequenceNormalizer} for English and the major Romance languages (where
 * accented letters are matching variants of their base letter), the German-specific
 * {@link GermanUmlautCharSequenceNormalizer} (a-umlaut to {@code ae}, eszett to {@code ss}, ...) for
 * German, and {@code null} where diacritics mark distinct letters (the Nordic languages and the
 * non-Latin scripts), because folding there is language-wrong. This is a search-recall choice, not a
 * statement of linguistic correctness; callers can build a {@link TermAnalyzer} directly to
 * override it.</p>
 *
 * @param language         The language, as an ISO 639-3 code (for example {@code "eng"}). Must
 *                         not be {@code null} or blank.
 * @param stemmerAlgorithm The Snowball algorithm for the language. Must not be {@code null}.
 * @param accentFold       The diacritic fold for the language, or {@code null} for none.
 */
public record NormalizationProfile(String language, SnowballStemmer.ALGORITHM stemmerAlgorithm,
    CharSequenceNormalizer accentFold) {

  /**
   * Validates the components.
   *
   * @throws IllegalArgumentException if {@code language} or {@code stemmerAlgorithm} is
   *     {@code null}, or if {@code language} is blank.
   */
  public NormalizationProfile {
    if (language == null) {
      throw new IllegalArgumentException("language must not be null");
    }
    if (stemmerAlgorithm == null) {
      throw new IllegalArgumentException("stemmerAlgorithm must not be null");
    }
    if (language.isBlank()) {
      throw new IllegalArgumentException("language must not be blank");
    }
  }

  /**
   * {@return a new {@link Stemmer} for this language} A fresh instance is returned on each call
   * because the Snowball stemmers are stateful and not thread-safe.
   */
  public Stemmer newStemmer() {
    return new SnowballStemmer(stemmerAlgorithm);
  }

  /**
   * Returns a matching analyzer for this language: NFC, case folding, the language's
   * {@linkplain #accentFold() diacritic fold} when it has one, then stemming. Each call builds an
   * independent analyzer with its own stemmer, so use one per thread when stemming.
   *
   * @return the analyzer.
   */
  public TermAnalyzer matchingAnalyzer() {
    final TermAnalyzer.Builder builder = TermAnalyzer.builder().nfc().caseFold();
    if (accentFold != null) {
      builder.transform(Dimension.ACCENT_FOLD, accentFold);
    }
    return builder.stem(newStemmer()).build();
  }
}
