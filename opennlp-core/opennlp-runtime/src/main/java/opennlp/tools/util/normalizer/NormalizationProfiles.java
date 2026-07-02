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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

/**
 * A registry of {@link NormalizationProfile}s by language, with detection-based fallback. This is
 * the language dispatch the design note calls for: pick the profile for a requested language, or
 * detect the language with a {@link LanguageDetector} when it is unspecified. The covered languages
 * are the Snowball stemmer algorithms that name a natural language -- every
 * {@link SnowballStemmer.ALGORITHM} except {@code PORTER}, which is an English-only algorithm
 * variant rather than a distinct language. Several codes can map to one algorithm (the three
 * Norwegian written standards all use {@code NORWEGIAN}).
 *
 * <p>Profiles are keyed by ISO 639-3 code (what {@link LanguageDetector} produces);
 * {@link #forLanguage(String)} also accepts ISO 639-1 two-letter codes.</p>
 */
public final class NormalizationProfiles {

  private static final Map<String, NormalizationProfile> BY_LANGUAGE = build();

  private NormalizationProfiles() {
  }

  private static Map<String, NormalizationProfile> build() {
    final Map<String, NormalizationProfile> map = new HashMap<>();
    // The generic accent fold is used for English and the major Romance languages, German uses its
    // own ae/oe/ue/ss fold, and folding is disabled elsewhere (Nordic, non-Latin) where diacritics
    // mark distinct letters.
    final CharSequenceNormalizer latin = AccentFoldCharSequenceNormalizer.getInstance();
    final CharSequenceNormalizer german = GermanUmlautCharSequenceNormalizer.getInstance();
    add(map, "ara", SnowballStemmer.ALGORITHM.ARABIC, null);
    add(map, "cat", SnowballStemmer.ALGORITHM.CATALAN, latin);
    add(map, "dan", SnowballStemmer.ALGORITHM.DANISH, null);
    add(map, "deu", SnowballStemmer.ALGORITHM.GERMAN, german);
    add(map, "ell", SnowballStemmer.ALGORITHM.GREEK, null);
    add(map, "eng", SnowballStemmer.ALGORITHM.ENGLISH, latin);
    add(map, "fin", SnowballStemmer.ALGORITHM.FINNISH, null);
    add(map, "fra", SnowballStemmer.ALGORITHM.FRENCH, latin);
    add(map, "gle", SnowballStemmer.ALGORITHM.IRISH, null);
    add(map, "hun", SnowballStemmer.ALGORITHM.HUNGARIAN, null);
    add(map, "ind", SnowballStemmer.ALGORITHM.INDONESIAN, null);
    add(map, "ita", SnowballStemmer.ALGORITHM.ITALIAN, latin);
    add(map, "nld", SnowballStemmer.ALGORITHM.DUTCH, null);
    add(map, "nob", SnowballStemmer.ALGORITHM.NORWEGIAN, null); // Bokmal (nb), the dominant standard
    add(map, "nno", SnowballStemmer.ALGORITHM.NORWEGIAN, null); // Nynorsk (nn)
    add(map, "nor", SnowballStemmer.ALGORITHM.NORWEGIAN, null); // macrolanguage / 639-1 "no"
    add(map, "por", SnowballStemmer.ALGORITHM.PORTUGUESE, latin);
    add(map, "ron", SnowballStemmer.ALGORITHM.ROMANIAN, null);
    add(map, "rus", SnowballStemmer.ALGORITHM.RUSSIAN, null);
    add(map, "spa", SnowballStemmer.ALGORITHM.SPANISH, latin);
    add(map, "swe", SnowballStemmer.ALGORITHM.SWEDISH, null);
    // Turkish diacritics are distinct letters, so there is no accent fold. The matching analyzer's
    // case fold stays locale-generic: the Turkish dotted/dotless-i pair folds by the Unicode default
    // rather than Turkish rules -- a deliberate recall choice, not Turkish-correct casing.
    add(map, "tur", SnowballStemmer.ALGORITHM.TURKISH, null);
    return Map.copyOf(map);
  }

  private static void add(Map<String, NormalizationProfile> map, String language,
      SnowballStemmer.ALGORITHM algorithm, CharSequenceNormalizer accentFold) {
    map.put(language, new NormalizationProfile(language, algorithm, accentFold));
  }

  /**
   * Returns the profile for a language.
   *
   * @param language An ISO 639-3 or ISO 639-1 language code; case-insensitive.
   * @return The profile, or empty if the language has no Snowball stemmer.
   */
  public static Optional<NormalizationProfile> forLanguage(String language) {
    Objects.requireNonNull(language, "language");
    String code = language.strip().toLowerCase(Locale.ROOT);
    if (code.length() == 2) {
      try {
        final String iso3 = Locale.of(code).getISO3Language();
        if (!iso3.isEmpty()) {
          code = iso3;
        }
      } catch (MissingResourceException ignored) {
        // No ISO 639-3 code for this two-letter code; fall through and look up as given.
      }
    }
    return Optional.ofNullable(BY_LANGUAGE.get(code));
  }

  /**
   * Detects the language of {@code text} and returns its profile.
   *
   * @param text     The text to detect.
   * @param detector The language detector to use.
   * @return The profile for the detected language, or empty if it has no Snowball stemmer.
   */
  public static Optional<NormalizationProfile> detect(CharSequence text,
      LanguageDetector detector) {
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(detector, "detector");
    return forLanguage(detector.predictLanguage(text).getLang());
  }

  /**
   * {@return the ISO 639-3 codes of the supported languages}
   */
  public static Set<String> supportedLanguages() {
    return BY_LANGUAGE.keySet();
  }
}
