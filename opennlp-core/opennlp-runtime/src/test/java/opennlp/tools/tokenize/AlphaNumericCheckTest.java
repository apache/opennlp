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

package opennlp.tools.tokenize;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.tokenize.lang.Factory;

public class AlphaNumericCheckTest {

  private static final List<String> LANGUAGES =
      List.of("en", "es", "it", "pt", "ca", "pl", "de", "fr", "nl", "xx");

  private static final List<String> TOKENS = List.of(
      "", "a", "Z", "0", "abc123", "Straße", "Café", "señor", "łódź", "ĳs", "Ÿ", "-", "a-b",
      "a b", "a\nb", "\n", "abc\n", "ñ", "Ç", "ß", "é", " ", "١٢٣", "Ａ", "𐐒", "😀a",
      "aé", "AB.", "x_y", "[", "]", "\\", "^", "&", "$");

  private static Stream<Arguments> builtInPatternsAndTokens() {
    return LANGUAGES.stream().flatMap(language -> {
      Pattern pattern = new Factory().getAlphanumeric(language);
      return TOKENS.stream().map(token -> Arguments.of(language, pattern, token));
    });
  }

  @ParameterizedTest(name = "{0}: \"{2}\"")
  @MethodSource("builtInPatternsAndTokens")
  void testBuiltInPatternsAgreeWithRegex(String language, Pattern pattern, String token) {
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertTrue(check.isCharacterSet(), language + " default runs as a set lookup");
    Assertions.assertEquals(pattern.matcher(token).matches(), check.test(token));
  }

  private static Stream<Arguments> customPatternsAndTokens() {
    List<String> patterns = List.of(
        "^[a-z-]+$", "^[-a-z]+$", "^[a-c1-3]+$", "^[a]+$", "^[a-zA-Z0-9_]+$",
        "^[\\p{L}]+$", "^[^a-z]+$", "^[a-z&&[^b]]+$", "^[\\d]+$", "^[a-z]+$|^[0-9]+$",
        "^[a-z]*$", "[a-z]+", "^(?i)[a-z]+$", "^[a-z]+\\d$", "^[ab\\]]+$", "^[a-]+$",
        "^[😀]+$");
    return patterns.stream().flatMap(regex -> {
      Pattern pattern = Pattern.compile(regex);
      return TOKENS.stream().map(token -> Arguments.of(regex, pattern, token));
    });
  }

  @ParameterizedTest(name = "{0}: \"{2}\"")
  @MethodSource("customPatternsAndTokens")
  void testCustomPatternsAgreeWithRegex(String regex, Pattern pattern, String token) {
    Assertions.assertEquals(pattern.matcher(token).matches(), AlphaNumericCheck.of(pattern).test(token));
  }

  @ParameterizedTest
  @ValueSource(strings = {"^[A-Za-z0-9]+$", "^[a-z-]+$", "^[-a-z]+$", "^[a-]+$", "^[a-c1-3]+$",
      "^[a]+$", "^[a-zA-Z0-9_]+$", "^[0-9a-záãâàéêíóõôúüçA-ZÁÃÂÀÉÊÍÓÕÔÚÜÇ]+$"})
  void testEligiblePatternsRunAsSetLookup(String regex) {
    Assertions.assertTrue(AlphaNumericCheck.of(Pattern.compile(regex)).isCharacterSet());
  }

  @ParameterizedTest
  @ValueSource(strings = {"^[\\p{L}]+$", "^[^a-z]+$", "^[a-z&&[^b]]+$", "^[\\d]+$",
      "^[a-z]+$|^[0-9]+$", "^[a-z]*$", "[a-z]+", "^(?i)[a-z]+$", "^[a-z]+\\d$", "^[ab\\]]+$",
      "^[😀]+$"})
  void testOtherPatternsFallBackToRegex(String regex) {
    Assertions.assertFalse(AlphaNumericCheck.of(Pattern.compile(regex)).isCharacterSet());
  }

  @Test
  void testFlagsForceRegex() {
    Pattern pattern = Pattern.compile("^[a-z]+$", Pattern.CASE_INSENSITIVE);
    AlphaNumericCheck check = AlphaNumericCheck.of(pattern);
    Assertions.assertFalse(check.isCharacterSet());
    Assertions.assertTrue(check.test("ABC"));
  }

  @Test
  void testNullPatternIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> AlphaNumericCheck.of(null));
  }
}
