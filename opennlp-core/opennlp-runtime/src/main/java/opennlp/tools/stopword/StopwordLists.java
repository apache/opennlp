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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
   * Caches the immutable, thread-safe filters loaded from the bundled
   * resources, keyed by normalized ISO 639-1 code, so that repeated
   * {@link #forLanguage(String)} calls do not re-read and re-parse the same
   * classpath resource.
   */
  private static final Map<String, StopwordFilter> BUNDLED_CACHE =
      new ConcurrentHashMap<>();

  static {
    final Set<String> langs = new LinkedHashSet<>();
    Collections.addAll(langs,
        "bg", "da", "de", "en", "es", "fi", "fr", "it", "nl", "pt", "ru");
    SUPPORTED_LANGUAGES = Collections.unmodifiableSet(langs);
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
   * @return A {@link StopwordFilter} backed by the bundled resource. The
   *     returned instance is immutable, thread-safe and cached, so repeated
   *     calls for the same language return the same shared filter.
   * @throws IllegalArgumentException if {@code iso639Code} is {@code null},
   *     is not a valid ISO 639 code, or has no bundled list for this language.
   * @throws UncheckedIOException if reading the bundled resource fails.
   */
  public static StopwordFilter forLanguage(final String iso639Code) {
    if (iso639Code == null) {
      throw new IllegalArgumentException("iso639Code must not be null");
    }
    LanguageCodeValidator.validateLanguageCode(iso639Code);

    final String normalized = LanguageCodeValidator.toIso6391(iso639Code);

    if (!SUPPORTED_LANGUAGES.contains(normalized)) {
      throw new IllegalArgumentException(
          "No bundled stopword list for language '" + iso639Code
              + "'. Supported languages: " + SUPPORTED_LANGUAGES);
    }

    return BUNDLED_CACHE.computeIfAbsent(normalized, StopwordLists::loadBundled);
  }

  private static StopwordFilter loadBundled(final String normalized) {
    final String resource = RESOURCE_PATH_PREFIX + normalized + ".txt";
    final InputStream in = StopwordLists.class.getResourceAsStream(resource);
    if (in == null) {
      throw new IllegalArgumentException(
          "Bundled stopword resource '" + resource + "' not found on the"
              + " classpath. Supported languages: " + SUPPORTED_LANGUAGES);
    }
    try (InputStream stream = in) {
      return new DictionaryStopwordFilter(stream, StandardCharsets.UTF_8, false);
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
}
