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

package opennlp.tools.stopword;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import opennlp.tools.util.LanguageCodeValidator;

/**
 * Static factory for {@link StopwordFilter} instances backed by bundled
 * language-specific stopword resources or caller-supplied input streams.
 * <p>
 * Bundled lists ship for the eleven languages enumerated in
 * <a href="https://issues.apache.org/jira/browse/OPENNLP-660">OPENNLP-660</a>:
 * Bulgarian (bg), Danish (da), German (de), English (en), Spanish (es),
 * Finnish (fi), French (fr), Italian (it), Dutch (nl), Portuguese (pt),
 * Russian (ru). Each list is keyed by its ISO 639-1 two-letter code.
 */
public final class StopwordLists {

  private static final String RESOURCE_PATH_PREFIX = "/opennlp/tools/stopword/";

  private static final Set<String> SUPPORTED_LANGUAGES;

  /**
   * ISO 639-2 bibliographic codes that {@link Locale#getISO3Language()} does
   * not produce (the JDK returns only the terminologic forms), but which
   * {@link LanguageCodeValidator} accepts. Mapped to their ISO 639-1
   * equivalents here so that callers can use either form.
   */
  private static final Map<String, String> BIBLIOGRAPHIC_TO_ISO6391;

  static {
    final Set<String> langs = new LinkedHashSet<>();
    Collections.addAll(langs,
        "bg", "da", "de", "en", "es", "fi", "fr", "it", "nl", "pt", "ru");
    SUPPORTED_LANGUAGES = Collections.unmodifiableSet(langs);

    final Map<String, String> biblio = new HashMap<>();
    biblio.put("dut", "nl"); // Dutch
    biblio.put("fre", "fr"); // French
    biblio.put("ger", "de"); // German
    BIBLIOGRAPHIC_TO_ISO6391 = Collections.unmodifiableMap(biblio);
  }

  private StopwordLists() {
    // utility class
  }

  /**
   * Returns a case-insensitive {@link StopwordFilter} for the given ISO 639
   * language code. Three-letter codes are normalized to their two-letter
   * equivalent when a bundled list exists for the latter.
   *
   * @param iso639Code The ISO 639-1 or ISO 639-2/3 language code.
   *     Must not be {@code null}.
   * @return A {@link StopwordFilter} backed by the bundled resource.
   * @throws IllegalArgumentException if {@code iso639Code} is {@code null},
   *     is not a valid ISO 639 code, or has no bundled list for this language.
   * @throws UncheckedIOException if reading the bundled resource fails.
   */
  public static StopwordFilter forLanguage(final String iso639Code) {
    if (iso639Code == null) {
      throw new IllegalArgumentException("iso639Code must not be null");
    }
    LanguageCodeValidator.validateLanguageCode(iso639Code);

    final String normalized = normalizeToIso6391(iso639Code);

    if (!SUPPORTED_LANGUAGES.contains(normalized)) {
      throw new IllegalArgumentException(
          "No bundled stopword list for language '" + iso639Code
              + "'. Supported languages: " + SUPPORTED_LANGUAGES);
    }

    final String resource = RESOURCE_PATH_PREFIX + normalized + ".txt";
    final InputStream in = StopwordLists.class.getResourceAsStream(resource);
    if (in == null) {
      throw new IllegalArgumentException(
          "Bundled stopword resource '" + resource + "' not found on the"
              + " classpath. Supported languages: " + SUPPORTED_LANGUAGES);
    }
    try (InputStream stream = in) {
      return new DictionaryStopwordFilter(stream, Charset.forName("UTF-8"), false);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to load bundled stopword list for '" + normalized + "'", e);
    }
  }

  /**
   * @return An unmodifiable view of the bundled ISO 639-1 codes for which
   *     stopword lists are shipped.
   */
  public static Set<String> supportedLanguages() {
    return SUPPORTED_LANGUAGES;
  }

  /**
   * Loads a stopword filter from a caller-supplied input stream.
   *
   * @param in The input stream. Must not be {@code null}.
   * @param cs The {@link Charset} to decode with. Must not be {@code null}.
   * @param caseSensitive Whether the resulting filter matches case-sensitively.
   * @return A {@link StopwordFilter} populated from {@code in}.
   * @throws IllegalArgumentException if {@code in} or {@code cs} is
   *     {@code null}.
   * @throws IOException Thrown if an IO error occurs while reading.
   */
  public static StopwordFilter load(final InputStream in, final Charset cs,
                                    final boolean caseSensitive) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    if (cs == null) {
      throw new IllegalArgumentException("cs must not be null");
    }
    return new DictionaryStopwordFilter(in, cs, caseSensitive);
  }

  /**
   * Normalizes an ISO 639-2/3 three-letter code to its ISO 639-1 two-letter
   * equivalent when one is available, otherwise returns the (lower-cased)
   * input unchanged. The caller is responsible for validating the code first
   * via {@link LanguageCodeValidator#validateLanguageCode(String)}.
   * <p>
   * Two-letter inputs are simply lower-cased and returned. For three-letter
   * inputs, ISO 639-2 bibliographic codes ({@code dut}, {@code fre},
   * {@code ger}) are mapped explicitly because {@link Locale#getISO3Language()}
   * returns only the terminologic forms ({@code nld}, {@code fra},
   * {@code deu}). Other three-letter codes are resolved by enumerating
   * {@link Locale#getAvailableLocales()} and matching against
   * {@link Locale#getISO3Language()}.
   */
  private static String normalizeToIso6391(final String code) {
    final String lower = code.toLowerCase(Locale.ROOT);
    if (lower.length() == 2) {
      return lower;
    }
    final String mapped = BIBLIOGRAPHIC_TO_ISO6391.get(lower);
    if (mapped != null) {
      return mapped;
    }
    for (final Locale locale : Locale.getAvailableLocales()) {
      final String lang = locale.getLanguage();
      if (lang.length() != 2) {
        continue;
      }
      try {
        if (lower.equals(locale.getISO3Language())) {
          return lang;
        }
      } catch (final Exception ignored) {
        // skip locales without a 3-letter form
      }
    }
    return lower;
  }
}
