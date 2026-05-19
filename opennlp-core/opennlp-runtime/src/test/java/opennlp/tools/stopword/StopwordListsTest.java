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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class StopwordListsTest {

  /**
   * Returns one well-known stopword for each bundled language. The chosen
   * tokens are common function words (typically the conjunction "and" or a
   * very common preposition/article) verified to be present in the bundled
   * Lucene snowball lists shipped with OpenNLP.
   */
  static Stream<Arguments> bundledLanguages() {
    return Stream.of(
        Arguments.of("bg", "и"),     // Bulgarian: and
        Arguments.of("da", "og"),    // Danish: and
        Arguments.of("de", "und"),   // German: and
        Arguments.of("en", "the"),   // English: the
        Arguments.of("es", "y"),     // Spanish: and
        Arguments.of("fi", "ja"),    // Finnish: and
        Arguments.of("fr", "et"),    // French: and
        Arguments.of("it", "e"),     // Italian: and
        Arguments.of("nl", "de"),    // Dutch: the
        Arguments.of("pt", "e"),     // Portuguese: and
        Arguments.of("ru", "и")      // Russian: and
    );
  }

  @ParameterizedTest(name = "forLanguage(\"{0}\") contains stopword \"{1}\"")
  @MethodSource("bundledLanguages")
  void testBundledLanguageContainsKnownStopword(final String code, final String stopword) {
    final StopwordFilter filter = StopwordLists.forLanguage(code);
    Assertions.assertNotNull(filter, "filter for '" + code + "' must not be null");
    Assertions.assertFalse(filter.isCaseSensitive(),
        "bundled filter for '" + code + "' should be case-insensitive");
    Assertions.assertTrue(filter.isStopword(stopword),
        "expected '" + stopword + "' to be a stopword in '" + code + "'");
  }

  @ParameterizedTest(name = "forLanguage(\"{0}\") loads a non-empty list")
  @ValueSource(strings = {"bg", "da", "de", "en", "es", "fi", "fr", "it", "nl", "pt", "ru"})
  void testBundledLanguageLoadsNonEmptyList(final String code) {
    final StopwordFilter filter = StopwordLists.forLanguage(code);
    Assertions.assertFalse(filter.stopwords().isEmpty(),
        "bundled stopword list for '" + code + "' must not be empty");
  }

  @ParameterizedTest(name = "forLanguage(\"{0}\") is case-insensitive: uppercase matches lowercase")
  @MethodSource("bundledLanguages")
  void testBundledLanguageIsCaseInsensitive(final String code, final String stopword) {
    final StopwordFilter filter = StopwordLists.forLanguage(code);
    final String upper = stopword.toUpperCase(java.util.Locale.ROOT);
    Assertions.assertTrue(filter.isStopword(upper),
        "expected uppercase '" + upper + "' to match in '" + code + "' (case-insensitive)");
  }

  @Test
  void testEnglishContainsCommonStopwords() {
    final StopwordFilter en = StopwordLists.forLanguage("en");
    Assertions.assertTrue(en.isStopword("the"));
    Assertions.assertTrue(en.isStopword("and"));
    Assertions.assertTrue(en.isStopword("of"));
  }

  @Test
  void testEnglishRejectsContentWord() {
    final StopwordFilter en = StopwordLists.forLanguage("en");
    Assertions.assertFalse(en.isStopword("dog"));
  }

  @Test
  void testUnsupportedLanguageThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StopwordLists.forLanguage("xx"));
  }

  @Test
  void testInvalidLanguageCodeThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> StopwordLists.forLanguage("not-a-language"));
  }

  @Test
  void testThreeLetterCodeMapsToTwoLetterEquivalent() {
    final StopwordFilter en2 = StopwordLists.forLanguage("en");
    final StopwordFilter en3 = StopwordLists.forLanguage("eng");

    // Both lists should contain the same set of common stopwords.
    Assertions.assertEquals(en2.stopwords(), en3.stopwords());

    // Spot-check.
    Assertions.assertTrue(en3.isStopword("the"));
    Assertions.assertTrue(en3.isStopword("and"));
  }

  @Test
  void testSupportedLanguagesReturnsElevenCodes() {
    final Set<String> supported = StopwordLists.supportedLanguages();
    Assertions.assertEquals(11, supported.size());

    final Set<String> expected = new HashSet<>();
    expected.add("bg");
    expected.add("da");
    expected.add("de");
    expected.add("en");
    expected.add("es");
    expected.add("fi");
    expected.add("fr");
    expected.add("it");
    expected.add("nl");
    expected.add("pt");
    expected.add("ru");
    Assertions.assertEquals(expected, new HashSet<>(supported));
  }
}
