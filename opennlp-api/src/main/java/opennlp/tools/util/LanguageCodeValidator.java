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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * @see <a href="https://iso639-3.sil.org/">ISO 639-3</a>
 */
public final class LanguageCodeValidator {

  private static final String X_UNSPECIFIED = "x-unspecified";

  private static final Set<String> VALID_CODES;

  static {
    VALID_CODES = Locale.availableLocales()
        .flatMap(loc -> {
          final Set<String> codes = new HashSet<>();
          final String lang = loc.getLanguage();
          if (!lang.isEmpty()) {
            codes.add(lang);
          }
          try {
            final String iso3 = loc.getISO3Language();
            if (!iso3.isEmpty()) {
              codes.add(iso3);
            }
          } catch (Exception ignored) {
            // Some locales may not have a 3-letter equivalent
          }
          return codes.stream();
        })
        .collect(Collectors.toCollection(HashSet::new));

    // ISO 639-2 bibliographic codes not returned by Locale
    VALID_CODES.add("dut"); // Dutch (bibliographic)
    VALID_CODES.add("fre"); // French (bibliographic)
    VALID_CODES.add("ger"); // German (bibliographic)

    // ISO 639-3 special code for undetermined language
    VALID_CODES.add("und");

    // OpenNLP-specific special code
    VALID_CODES.add(X_UNSPECIFIED);
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
   * @throws IllegalArgumentException if {@code languageCode}
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
   * @throws IllegalArgumentException if the code is not a valid
   *     ISO 639 language code or is {@code null}.
   */
  public static void validateLanguageCode(String languageCode) {
    if (!isValid(languageCode)) {
      throw new IllegalArgumentException(
          "Unknown language code '" + languageCode
              + "', must be a valid ISO 639 code!");
    }
  }
}
