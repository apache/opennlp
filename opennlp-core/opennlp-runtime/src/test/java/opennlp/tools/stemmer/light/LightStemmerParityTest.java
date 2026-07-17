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
package opennlp.tools.stemmer.light;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.StemmerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts every stemmer against its bundled vocabulary fixture: word and expected stem pairs
 * sampled from the vocabulary data of the algorithms' original test suites, regenerated against
 * the source implementations (see the fixture README for regeneration).
 */
class LightStemmerParityTest {

  private static final Map<String, Stemmer> STEMMERS = Map.ofEntries(
      Map.entry("de-light", new GermanLightStemmer()),
      Map.entry("de-minimal", new GermanMinimalStemmer()),
      Map.entry("en-minimal", new EnglishMinimalStemmer()),
      Map.entry("es-light", new SpanishLightStemmer()),
      Map.entry("es-minimal", new SpanishMinimalStemmer()),
      Map.entry("fi-light", new FinnishLightStemmer()),
      Map.entry("fr-light", new FrenchLightStemmer()),
      Map.entry("fr-minimal", new FrenchMinimalStemmer()),
      Map.entry("hu-light", new HungarianLightStemmer()),
      Map.entry("it-light", new ItalianLightStemmer()),
      Map.entry("no-light-bokmaal", new NorwegianLightStemmer(NorwegianVariety.BOKMAAL)),
      Map.entry("no-light-nynorsk", new NorwegianLightStemmer(NorwegianVariety.NYNORSK)),
      Map.entry("no-minimal-bokmaal", new NorwegianMinimalStemmer(NorwegianVariety.BOKMAAL)),
      Map.entry("no-minimal-nynorsk", new NorwegianMinimalStemmer(NorwegianVariety.NYNORSK)),
      Map.entry("pt-light", new PortugueseLightStemmer()),
      Map.entry("ru-light", new RussianLightStemmer()),
      Map.entry("sv-light", new SwedishLightStemmer()),
      Map.entry("sv-minimal", new SwedishMinimalStemmer()));

  static Stream<Arguments> fixtures() {
    return STEMMERS.entrySet().stream().sorted(Map.Entry.comparingByKey())
        .map(e -> Arguments.of(e.getKey(), e.getValue()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void testVocabularyParity(String fixture, Stemmer stemmer) throws IOException {
    int pairs = 0;
    try (InputStream in = LightStemmerParityTest.class.getResourceAsStream(fixture + ".tsv")) {
      assertNotNull(in, "missing fixture " + fixture + ".tsv");
      final BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        pairs++;
        final int tab = line.indexOf('\t');
        final String word = line.substring(0, tab);
        final String expected = line.substring(tab + 1);
        assertEquals(expected, stemmer.stem(word).toString(),
            fixture + " must stem <" + word + ">");
      }
    }
    assertTrue(pairs >= 50, fixture + " fixture must not be empty or truncated");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void testStemmerIsItsOwnFactory(String fixture, Stemmer stemmer) {
    final StemmerFactory factory = (StemmerFactory) stemmer;
    assertEquals(stemmer, factory.newStemmer(),
        "the stemmer returns itself from newStemmer()");
  }
}
