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

import java.util.Objects;

import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;
import opennlp.tools.stemmer.snowball.SnowballStemmer;
import opennlp.tools.stemmer.snowball.SnowballStemmerFactory;
import opennlp.tools.util.ParamChecks;

/**
 * Per-language normalization settings, mirroring how OpenNLP already selects a Snowball stemmer by
 * language. A profile pairs a language with its Snowball {@link SnowballStemmer.ALGORITHM} and the
 * diacritic fold appropriate for that language (if any).
 *
 * <p>The {@code accentFold} normalizer is the language's diacritic transform for a matching form, or
 * {@code null} when folding is not appropriate. In the profiles of {@link NormalizationProfiles} it
 * is the generic {@link AccentFoldCharSequenceNormalizer} for English, Catalan, French, Italian,
 * Portuguese and Spanish (where accented letters are matching variants of their base letter), the
 * German-specific {@link GermanUmlautCharSequenceNormalizer} (a-umlaut to {@code ae}, eszett to
 * {@code ss}, ...) for German, and {@code null} for every other language, including those where
 * diacritics mark distinct letters (such as the Nordic languages) and the non-Latin scripts. This
 * is a search-recall choice, not a statement of linguistic correctness; callers can build a
 * {@link TermAnalyzer} directly to override it.</p>
 *
 * <p>A profile is immutable and thread-safe. Two profiles are equal when their language, stemmer
 * algorithm and diacritic fold are equal.</p>
 */
public final class NormalizationProfile {

  private final String language;
  private final SnowballStemmer.ALGORITHM stemmerAlgorithm;
  private final CharSequenceNormalizer accentFold;

  /** The matching analyzer, built on the first call to {@link #matchingAnalyzer()}. */
  private volatile TermAnalyzer matchingAnalyzer;

  /**
   * Creates a profile.
   *
   * @param language         The language, as an ISO 639-3 code (for example {@code "eng"}). Must
   *                         not be {@code null} or blank.
   * @param stemmerAlgorithm The Snowball algorithm for the language. Must not be {@code null}.
   * @param accentFold       The diacritic fold for the language, or {@code null} for none.
   * @throws IllegalArgumentException if {@code language} or {@code stemmerAlgorithm} is
   *     {@code null}, or if {@code language} is blank.
   */
  public NormalizationProfile(String language, SnowballStemmer.ALGORITHM stemmerAlgorithm,
      CharSequenceNormalizer accentFold) {
    ParamChecks.requireNonNullArg(language, "language");
    ParamChecks.requireNonNullArg(stemmerAlgorithm, "stemmerAlgorithm");
    if (language.isBlank()) {
      throw new IllegalArgumentException("language must not be blank");
    }
    this.language = language;
    this.stemmerAlgorithm = stemmerAlgorithm;
    this.accentFold = accentFold;
  }

  /**
   * {@return the language, as an ISO 639-3 code}
   */
  public String language() {
    return language;
  }

  /**
   * {@return the Snowball algorithm for the language}
   */
  public SnowballStemmer.ALGORITHM stemmerAlgorithm() {
    return stemmerAlgorithm;
  }

  /**
   * {@return the diacritic fold for the language, or {@code null} for none}
   */
  public CharSequenceNormalizer accentFold() {
    return accentFold;
  }

  /**
   * {@return a thread-safe factory for this language's Snowball stemmer} The factory may be
   * shared; each {@linkplain StemmerFactory#newStemmer() minted stemmer} is confined to the
   * calling thread.
   */
  public StemmerFactory stemmerFactory() {
    return new SnowballStemmerFactory(stemmerAlgorithm);
  }

  /**
   * {@return a new {@link Stemmer} for this language} The returned {@link SnowballStemmer} is
   * thread-safe and may be shared across threads.
   */
  public Stemmer newStemmer() {
    return new SnowballStemmer(stemmerAlgorithm);
  }

  /**
   * Returns the matching analyzer for this language: NFC, case folding, the language's
   * {@linkplain #accentFold() diacritic fold} when it has one, then stemming. The analyzer is
   * built on the first call, and every call on this profile returns the same instance. It is
   * thread-safe, and repeated words resolve from a bounded per-thread stem cache instead of being
   * re-stemmed; the cache pays off on threads that live across many calls, such as a fixed
   * platform-thread pool. Call {@link TermAnalyzer#clearThreadLocalState()} when a pooled thread
   * no longer uses the analyzer.
   *
   * @return the analyzer.
   */
  public TermAnalyzer matchingAnalyzer() {
    TermAnalyzer analyzer = matchingAnalyzer;
    if (analyzer == null) {
      synchronized (this) {
        analyzer = matchingAnalyzer;
        if (analyzer == null) {
          final TermAnalyzer.Builder builder = TermAnalyzer.builder().nfc().caseFold();
          if (accentFold != null) {
            builder.transform(Dimension.ACCENT_FOLD, accentFold);
          }
          analyzer = builder.stem(stemmerFactory()).build();
          matchingAnalyzer = analyzer;
        }
      }
    }
    return analyzer;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof NormalizationProfile other
        && language.equals(other.language)
        && stemmerAlgorithm == other.stemmerAlgorithm
        && Objects.equals(accentFold, other.accentFold);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return Objects.hash(language, stemmerAlgorithm, accentFold);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    return "NormalizationProfile[language=" + language + ", stemmerAlgorithm=" + stemmerAlgorithm
        + ", accentFold=" + accentFold + "]";
  }
}
