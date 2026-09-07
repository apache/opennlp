/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the {@link JsonScan} class.
 */
public class JsonScanTest {

  // -------------------------------------------------------------------------
  // closingQuote
  // -------------------------------------------------------------------------

  static Stream<Arguments> closingQuotes() {
    return Stream.of(
        Arguments.of("\"a\"", 0, 2),
        Arguments.of("\"\"", 0, 1),
        Arguments.of("x\"abc\": 1", 1, 5),
        Arguments.of("\"a\\\"b\"", 0, 5),
        Arguments.of("\"a\\\\\"", 0, 4),
        Arguments.of("\"\\u0120\"", 0, 7),
        Arguments.of("\"a\nb\"", 0, 4),
        Arguments.of("\"\uD83D\uDE00\"", 0, 3),
        Arguments.of("\"\\\uD83D\uDE00\"", 0, 4),
        Arguments.of("\"a", 0, -1),
        Arguments.of("\"", 0, -1),
        Arguments.of("\"a\\", 0, -1),
        Arguments.of("\"a\\\"", 0, -1),
        Arguments.of("\"a\\\nb\"", 0, -1),
        Arguments.of("\"a\\\rb\"", 0, -1),
        Arguments.of("\"a\\\u0085b\"", 0, -1),
        Arguments.of("\"a\\\u2028b\"", 0, -1),
        Arguments.of("\"a\\\u2029b\"", 0, -1),
        Arguments.of("\"a\\\tb\"", 0, 5));
  }

  @ParameterizedTest
  @MethodSource("closingQuotes")
  void testClosingQuote(String text, int openQuote, int expected) {
    Assertions.assertEquals(expected, JsonScan.closingQuote(text, openQuote));
  }

  // -------------------------------------------------------------------------
  // closingQuoteOnLine
  // -------------------------------------------------------------------------

  static Stream<Arguments> closingQuotesOnLine() {
    return Stream.of(
        Arguments.of("\"a\"", 0, 2),
        Arguments.of("\"\"", 0, 1),
        Arguments.of("x\"a b\"c\"", 1, 5),
        Arguments.of("\"a\\\"b\"", 0, 3),
        Arguments.of("\"a\tb\"", 0, 4),
        Arguments.of("\"a\u00A0\u3000b\"", 0, 5),
        Arguments.of("\"\uD83D\uDE00\"", 0, 3),
        Arguments.of("\"a", 0, -1),
        Arguments.of("\"", 0, -1),
        Arguments.of("\"a\nb\"", 0, -1),
        Arguments.of("\"a\rb\"", 0, -1),
        Arguments.of("\"a\u0085b\"", 0, -1),
        Arguments.of("\"a\u2028b\"", 0, -1),
        Arguments.of("\"a\u2029b\"", 0, -1));
  }

  @ParameterizedTest
  @MethodSource("closingQuotesOnLine")
  void testClosingQuoteOnLine(String text, int openQuote, int expected) {
    Assertions.assertEquals(expected, JsonScan.closingQuoteOnLine(text, openQuote));
  }

  // -------------------------------------------------------------------------
  // afterColon
  // -------------------------------------------------------------------------

  static Stream<Arguments> colons() {
    return Stream.of(
        Arguments.of(":", 0, 1),
        Arguments.of(":1", 0, 1),
        Arguments.of(" : 1", 0, 3),
        Arguments.of("\t\n\r\u000B\f:\t\n\r\u000B\f1", 0, 11),
        Arguments.of("x: 1", 1, 3),
        Arguments.of(": ", 0, 2),
        Arguments.of("", 0, -1),
        Arguments.of(" ", 0, -1),
        Arguments.of("1", 0, -1),
        Arguments.of("x:", 0, -1),
        Arguments.of("::", 1, 2),
        Arguments.of("\u00A0:", 0, -1),
        Arguments.of("\u2003:", 0, -1),
        Arguments.of(":\u00A01", 0, 1));
  }

  @ParameterizedTest
  @MethodSource("colons")
  void testAfterColon(String text, int from, int expected) {
    Assertions.assertEquals(expected, JsonScan.afterColon(text, from));
  }

  // -------------------------------------------------------------------------
  // skipWhitespace
  // -------------------------------------------------------------------------

  static Stream<Arguments> whitespaceRuns() {
    return Stream.of(
        Arguments.of("", 0, 0),
        Arguments.of("a", 0, 0),
        Arguments.of(" a", 0, 1),
        Arguments.of(" \t\n\u000B\f\ra", 0, 6),
        Arguments.of("   ", 0, 3),
        Arguments.of("a  b", 1, 3),
        Arguments.of("a  b", 3, 3),
        Arguments.of("\u00A0a", 0, 0),
        Arguments.of("\u0085a", 0, 0),
        Arguments.of("\u2003a", 0, 0),
        Arguments.of("\u3000a", 0, 0),
        Arguments.of("\u001Ca", 0, 0));
  }

  @ParameterizedTest
  @MethodSource("whitespaceRuns")
  void testSkipWhitespace(String text, int from, int expected) {
    Assertions.assertEquals(expected, JsonScan.skipWhitespace(text, from));
  }

  // -------------------------------------------------------------------------
  // endOfDigits
  // -------------------------------------------------------------------------

  static Stream<Arguments> digitRuns() {
    return Stream.of(
        Arguments.of("", 0, 0),
        Arguments.of("0", 0, 1),
        Arguments.of("0123456789", 0, 10),
        Arguments.of("12abc", 0, 2),
        Arguments.of("a12", 0, 0),
        Arguments.of("a12", 1, 3),
        Arguments.of("-1", 0, 0),
        Arguments.of("1.5", 0, 1),
        Arguments.of("\u0661\u0662", 0, 0),
        Arguments.of("\uFF11", 0, 0),
        Arguments.of("\uD835\uDFCE", 0, 0),
        Arguments.of("1\u0661", 0, 1));
  }

  @ParameterizedTest
  @MethodSource("digitRuns")
  void testEndOfDigits(String text, int from, int expected) {
    Assertions.assertEquals(expected, JsonScan.endOfDigits(text, from));
  }
}
