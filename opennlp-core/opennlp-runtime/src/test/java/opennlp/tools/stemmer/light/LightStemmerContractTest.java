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

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.stemmer.Stemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The shared API contract of the light and minimal stemmers: fail-loud null handling, identity
 * on empty input, and single-element {@code stemAll}.
 */
class LightStemmerContractTest {

  static Stream<Stemmer> stemmers() {
    return Stream.of(new GermanLightStemmer(), new GermanMinimalStemmer(),
        new EnglishMinimalStemmer(), new SpanishLightStemmer(), new SpanishMinimalStemmer(),
        new FinnishLightStemmer(), new FrenchLightStemmer(), new FrenchMinimalStemmer(),
        new HungarianLightStemmer(), new ItalianLightStemmer(),
        new NorwegianLightStemmer(NorwegianVariety.BOKMAAL, NorwegianVariety.NYNORSK),
        new NorwegianMinimalStemmer(NorwegianVariety.BOKMAAL),
        new PortugueseLightStemmer(), new RussianLightStemmer(),
        new SwedishLightStemmer(), new SwedishMinimalStemmer());
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testNullFailsLoudly(Stemmer stemmer) {
    assertThrows(IllegalArgumentException.class, () -> stemmer.stem(null));
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testEmptyInputStaysEmpty(Stemmer stemmer) {
    assertEquals("", stemmer.stem("").toString());
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testStemAllReturnsTheSingleStem(Stemmer stemmer) {
    final List<CharSequence> all = stemmer.stemAll("running");
    assertEquals(1, all.size());
    assertEquals(stemmer.stem("running").toString(), all.get(0).toString());
  }

  @Test
  void testNorwegianVarietyValidation() {
    assertThrows(IllegalArgumentException.class, () -> new NorwegianLightStemmer(null));
    assertThrows(IllegalArgumentException.class,
        () -> new NorwegianMinimalStemmer(NorwegianVariety.BOKMAAL, (NorwegianVariety) null));
  }
}
