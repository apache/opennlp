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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.stemmer.Stemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared API contract of the light and minimal stemmers: null rejection, identity on empty
 * input, and single-element {@code stemAll}.
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
  void testNullIsRejected(Stemmer stemmer) {
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

  @ParameterizedTest
  @MethodSource("stemmers")
  void testAcceptsNonStringCharSequences(Stemmer stemmer) {
    final String word = "h\u00E4usern";
    assertEquals(stemmer.stem(word).toString(), stemmer.stem(new StringBuilder(word)).toString());
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testDoesNotFoldUppercaseInput(Stemmer stemmer) {
    assertEquals("TESTS", stemmer.stem("TESTS").toString());
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testDoesNotNormalizeDecomposedInput(Stemmer stemmer) {
    final String decomposed = "x\u0301q";
    assertEquals(decomposed, stemmer.stem(decomposed).toString());
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testSupplementaryPrefixRemainsIntact(Stemmer stemmer) {
    final String result = stemmer.stem("\uD83D\uDE00tests").toString();
    assertTrue(result.length() >= 2);
    assertTrue(Character.isSurrogatePair(result.charAt(0), result.charAt(1)));
    assertEquals(0x1F600, result.codePointAt(0));
  }

  @ParameterizedTest
  @MethodSource("stemmers")
  void testConcurrentCallsMatchSerialResults(Stemmer stemmer) throws Exception {
    final List<String> words = List.of(
        "running", "h\u00E4usern", "maisons", "h\u00E1zak",
        "\u0434\u043e\u043c\u0430\u043c\u0438", "flickorna");
    final List<String> expected = words.stream()
        .map(word -> stemmer.stem(word).toString())
        .toList();
    final List<Callable<String>> calls = IntStream.range(0, 256)
        .mapToObj(index -> (Callable<String>) () -> stemmer.stem(
            words.get(index % words.size())).toString())
        .toList();

    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      final List<Future<String>> results = executor.invokeAll(calls);
      for (int index = 0; index < results.size(); index++) {
        assertEquals(expected.get(index % expected.size()), results.get(index).get());
      }
    }
  }

  @Test
  void testNorwegianVarietyValidation() {
    assertThrows(IllegalArgumentException.class, () -> new NorwegianLightStemmer(null));
    assertThrows(IllegalArgumentException.class,
        () -> new NorwegianLightStemmer(NorwegianVariety.BOKMAAL, (NorwegianVariety[]) null));
    assertThrows(IllegalArgumentException.class,
        () -> new NorwegianMinimalStemmer(NorwegianVariety.BOKMAAL, (NorwegianVariety) null));
  }
}
