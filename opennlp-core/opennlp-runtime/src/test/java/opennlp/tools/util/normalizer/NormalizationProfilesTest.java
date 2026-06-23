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

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.stemmer.snowball.SnowballStemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NormalizationProfilesTest {

  @Test
  void testEnglishUsesTheGenericAccentFold() {
    final NormalizationProfile profile = NormalizationProfiles.forLanguage("eng").orElseThrow();
    assertEquals(SnowballStemmer.ALGORITHM.ENGLISH, profile.stemmerAlgorithm());
    assertSame(AccentFoldCharSequenceNormalizer.getInstance(), profile.accentFold());
    assertEquals(List.of(Dimension.NFC, Dimension.CASE_FOLD, Dimension.ACCENT_FOLD, Dimension.STEM),
        profile.searchAnalyzer().dimensions());
  }

  @Test
  void testTwoLetterCodeResolvesToProfile() {
    assertEquals(SnowballStemmer.ALGORITHM.GERMAN,
        NormalizationProfiles.forLanguage("de").orElseThrow().stemmerAlgorithm());
  }

  @Test
  void testGermanUsesTheGermanSpecificFold() {
    final NormalizationProfile profile = NormalizationProfiles.forLanguage("deu").orElseThrow();
    assertSame(GermanUmlautCharSequenceNormalizer.getInstance(), profile.accentFold());
    assertEquals(List.of(Dimension.NFC, Dimension.CASE_FOLD, Dimension.ACCENT_FOLD, Dimension.STEM),
        profile.searchAnalyzer().dimensions());
  }

  @Test
  void testRomanceLanguagesUseTheGenericFold() {
    for (final String language : List.of("fra", "spa", "por", "ita", "cat")) {
      assertSame(AccentFoldCharSequenceNormalizer.getInstance(),
          NormalizationProfiles.forLanguage(language).orElseThrow().accentFold());
    }
  }

  @Test
  void testNordicLanguageHasNoFold() {
    final NormalizationProfile swedish = NormalizationProfiles.forLanguage("swe").orElseThrow();
    assertNull(swedish.accentFold());
    assertEquals(List.of(Dimension.NFC, Dimension.CASE_FOLD, Dimension.STEM),
        swedish.searchAnalyzer().dimensions());
  }

  @Test
  void testUnsupportedLanguageIsEmpty() {
    assertTrue(NormalizationProfiles.forLanguage("jpn").isEmpty());
    assertTrue(NormalizationProfiles.forLanguage("zzz").isEmpty());
  }

  @Test
  void testSearchAnalyzerStemsThroughTheChain() {
    final NormalizationProfile english = NormalizationProfiles.forLanguage("eng").orElseThrow();
    assertEquals("cat", english.searchAnalyzer().analyze("Cats").get(0).normalized());
  }

  @Test
  void testDetectDispatchesThroughTheDetector() {
    final LanguageDetector detector = new LanguageDetector() {
      @Override
      public Language[] predictLanguages(CharSequence content) {
        return new Language[] {new Language("deu")};
      }

      @Override
      public Language predictLanguage(CharSequence content) {
        return new Language("deu");
      }

      @Override
      public String[] getSupportedLanguages() {
        return new String[] {"deu"};
      }
    };
    final NormalizationProfile profile =
        NormalizationProfiles.detect("Guten Tag", detector).orElseThrow();
    assertEquals(SnowballStemmer.ALGORITHM.GERMAN, profile.stemmerAlgorithm());
  }

  @Test
  void testDetectUnsupportedLanguageIsEmpty() {
    final LanguageDetector detector = new LanguageDetector() {
      @Override
      public Language[] predictLanguages(CharSequence content) {
        return new Language[] {new Language("jpn")};
      }

      @Override
      public Language predictLanguage(CharSequence content) {
        return new Language("jpn");
      }

      @Override
      public String[] getSupportedLanguages() {
        return new String[] {"jpn"};
      }
    };
    assertTrue(NormalizationProfiles.detect("text", detector).isEmpty());
  }

  @Test
  void testSupportedLanguagesCoverTheSnowballSet() {
    assertEquals(19, NormalizationProfiles.supportedLanguages().size());
    assertTrue(NormalizationProfiles.supportedLanguages().containsAll(List.of("eng", "deu", "fra")));
  }

  @Test
  void testForLanguageRejectsNull() {
    assertThrows(NullPointerException.class, () -> NormalizationProfiles.forLanguage(null));
  }
}
