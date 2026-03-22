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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LanguageCodeValidatorTest {

  @Test
  void testValidIso639_1Codes() {
    Assertions.assertTrue(LanguageCodeValidator.isValid("en"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("de"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("fr"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("es"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("pt"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("it"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("nl"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("th"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("ja"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("pl"));
  }

  @Test
  void testValidIso639_3TerminologicalCodes() {
    Assertions.assertTrue(LanguageCodeValidator.isValid("eng"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("deu"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("fra"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("spa"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("por"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("ita"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("nld"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("tha"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("jpn"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("pol"));
  }

  @Test
  void testValidIso639_2BibliographicCodes() {
    Assertions.assertTrue(LanguageCodeValidator.isValid("dut"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("fre"));
    Assertions.assertTrue(LanguageCodeValidator.isValid("ger"));
  }

  @Test
  void testUndeterminedCode() {
    Assertions.assertTrue(LanguageCodeValidator.isValid("und"));
  }

  @Test
  void testSpecialCodes() {
    Assertions.assertTrue(LanguageCodeValidator.isValid("x-unspecified"));
  }

  @Test
  void testInvalidCodes() {
    Assertions.assertFalse(LanguageCodeValidator.isValid(""));
    Assertions.assertFalse(LanguageCodeValidator.isValid("xyz123"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("invalid"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("EN"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("ENG"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("123"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("e"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("en-US"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("abcd"));
  }

  @Test
  void testInvalidTwoLetterCode() {
    Assertions.assertFalse(LanguageCodeValidator.isValid("xx"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("zz"));
  }

  @Test
  void testInvalidThreeLetterCode() {
    Assertions.assertFalse(LanguageCodeValidator.isValid("xyz"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("abc"));
    Assertions.assertFalse(LanguageCodeValidator.isValid("zzz"));
  }

  @Test
  void testNullCode() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> LanguageCodeValidator.isValid(null));
  }

  @Test
  void testValidateThrowsForInvalidCode() {
    IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
        () -> LanguageCodeValidator.validateLanguageCode("invalid_code"));
    Assertions.assertTrue(ex.getMessage().contains("invalid_code"));
  }

  @Test
  void testValidatePassesForValidCode() {
    Assertions.assertDoesNotThrow(
        () -> LanguageCodeValidator.validateLanguageCode("en"));
    Assertions.assertDoesNotThrow(
        () -> LanguageCodeValidator.validateLanguageCode("eng"));
    Assertions.assertDoesNotThrow(
        () -> LanguageCodeValidator.validateLanguageCode("dut"));
    Assertions.assertDoesNotThrow(
        () -> LanguageCodeValidator.validateLanguageCode("und"));
    Assertions.assertDoesNotThrow(
        () -> LanguageCodeValidator.validateLanguageCode("x-unspecified"));
  }
}
