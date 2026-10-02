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

package opennlp.tools.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;

/**
 * Validates language codes against ISO 639 standards.
 * <p>
 * Accepts:
 * <ul>
 *   <li>ISO 639-1 two-letter language codes
 *       (e.g., {@code "en"}, {@code "de"})</li>
 *   <li>ISO 639-2/3 three-letter language codes
 *       (e.g., {@code "eng"}, {@code "deu"})</li>
 *   <li>The special code {@code "x-unspecified"}
 *       used internally by OpenNLP</li>
 * </ul>
 * <p>
 * Valid codes are derived from {@link Locale#availableLocales()}
 * plus additional ISO 639-2 bibliographic codes and
 * {@code "und"} (undetermined).
 * <p>
 * Also converts between ISO 639-1 two-letter codes and ISO 639-2/3
 * three-letter codes, see {@link #toIso6391(String)} and
 * {@link #toIso6393(String)}.
 *
 * @see <a href="https://iso639-3.sil.org/">ISO 639-3</a>
 */
public final class LanguageCodeValidator {

  private static final String X_UNSPECIFIED = "x-unspecified";

  private static final int ISO_639_1_LENGTH = 2;

  /**
   * ISO 639-2 bibliographic codes that {@link Locale#getISO3Language()} never
   * returns, mapped to their ISO 639-2/T (terminologic) equivalent.
   */
  private static final Map<String, String> BIBLIOGRAPHIC_TO_TERMINOLOGIC = Map.of(
      "dut", "nld", // Dutch
      "fre", "fra", // French
      "ger", "deu"); // German

  private static final Set<String> VALID_CODES;

  /**
   * Maps three-letter ISO 639-2/3 codes, terminologic and bibliographic, to
   * their ISO 639-1 two-letter equivalent.
   */
  private static final Map<String, String> ISO_639_3_TO_ISO_639_1;

  static {
    final Set<String> validCodes = new HashSet<>();
    final Map<String, String> toIso6391 = new HashMap<>();
    for (final Locale locale : Locale.getAvailableLocales()) {
      final String lang = locale.getLanguage();
      if (!lang.isEmpty()) {
        validCodes.add(lang);
      }
      try {
        final String iso3 = locale.getISO3Language();
        if (!iso3.isEmpty()) {
          validCodes.add(iso3);
          if (lang.length() == ISO_639_1_LENGTH) {
            toIso6391.putIfAbsent(iso3, lang);
          }
        }
      } catch (MissingResourceException ignored) {
        // locale has no three-letter form; skip it
      }
    }
    VALID_CODES = validCodes;

    // ISO 639-2 bibliographic codes not returned by Locale
    VALID_CODES.addAll(BIBLIOGRAPHIC_TO_TERMINOLOGIC.keySet());

    // ISO 639-3 special code for undetermined language
    VALID_CODES.add("und");

    // OpenNLP-specific special code
    VALID_CODES.add(X_UNSPECIFIED);

    for (final Map.Entry<String, String> e : BIBLIOGRAPHIC_TO_TERMINOLOGIC.entrySet()) {
      final String iso1 = toIso6391.get(e.getValue());
      if (iso1 != null) {
        toIso6391.put(e.getKey(), iso1);
      }
    }
    ISO_639_3_TO_ISO_639_1 = Map.copyOf(toIso6391);
  }

  private LanguageCodeValidator() {
    // utility class, not intended to be instantiated
  }

  /**
   * Checks whether the given language code is a valid ISO 639 code.
   *
   * @param languageCode The language code to check.
   *     Must not be {@code null}.
   * @return {@code true} if the code is valid,
   *     {@code false} otherwise.
   * @throws IllegalArgumentException Thrown if {@code languageCode}
   *     is {@code null}.
   */
  public static boolean isValid(String languageCode) {
    if (languageCode == null) {
      throw new IllegalArgumentException(
          "languageCode must not be null");
    }
    return VALID_CODES.contains(languageCode);
  }

  /**
   * Validates the given language code and throws an
   * {@link IllegalArgumentException} if it is not a recognized
   * ISO 639 language code.
   *
   * @param languageCode The language code to validate.
   *     Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if the code is not a valid
   *     ISO 639 language code or is {@code null}.
   */
  public static void validateLanguageCode(String languageCode) {
    if (!isValid(languageCode)) {
      throw new IllegalArgumentException(
          "Unknown language code '" + languageCode
              + "', must be a valid ISO 639 code!");
    }
  }

  /**
   * Converts a three-letter ISO 639-2/3 code, terminologic ({@code "nld"}) or
   * bibliographic ({@code "dut"}), to its ISO 639-1 two-letter equivalent
   * ({@code "nl"}). The code is lower-cased with
   * {@link StringUtil#toLowerCase(CharSequence)} first. Two-letter codes and
   * codes without a two-letter equivalent are returned lower-cased and
   * otherwise unchanged. The code is not validated; use
   * {@link #validateLanguageCode(String)} for that.
   *
   * @param languageCode The language code to convert. Must not be {@code null}.
   * @return The ISO 639-1 code, or the lower-cased input if there is none.
   * @throws IllegalArgumentException Thrown if {@code languageCode} is {@code null}.
   */
  public static String toIso6391(String languageCode) {
    if (languageCode == null) {
      throw new IllegalArgumentException("languageCode must not be null");
    }
    final String lower = StringUtil.toLowerCase(languageCode);
    if (lower.length() == ISO_639_1_LENGTH) {
      return lower;
    }
    return ISO_639_3_TO_ISO_639_1.getOrDefault(lower, lower);
  }

  /**
   * Converts an ISO 639-1 two-letter code ({@code "de"}) or an ISO 639-2
   * bibliographic code ({@code "ger"}) to its ISO 639-2/3 three-letter
   * equivalent ({@code "deu"}). The code is lower-cased with
   * {@link StringUtil#toLowerCase(CharSequence)} first. Other codes, and
   * two-letter codes without a three-letter equivalent, are returned
   * lower-cased and otherwise unchanged. The code is not validated; use
   * {@link #validateLanguageCode(String)} for that.
   *
   * @param languageCode The language code to convert. Must not be {@code null}.
   * @return The ISO 639-2/3 code, or the lower-cased input if there is none.
   * @throws IllegalArgumentException Thrown if {@code languageCode} is {@code null}.
   */
  public static String toIso6393(String languageCode) {
    if (languageCode == null) {
      throw new IllegalArgumentException("languageCode must not be null");
    }
    final String lower = StringUtil.toLowerCase(languageCode);
    if (lower.length() == ISO_639_1_LENGTH) {
      try {
        final String iso3 = Locale.of(lower).getISO3Language();
        return iso3.isEmpty() ? lower : iso3;
      } catch (MissingResourceException ignored) {
        return lower;
      }
    }
    return BIBLIOGRAPHIC_TO_TERMINOLOGIC.getOrDefault(lower, lower);
  }
}
