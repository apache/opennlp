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

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.JsonScan.Member;

/**
 * Tests for the {@link JsonScan} class.
 */
public class JsonScanTest {

  // -------------------------------------------------------------------------
  // document, members
  // -------------------------------------------------------------------------

  static Stream<Arguments> documents() {
    return Stream.of(
        Arguments.of("{}", List.of()),
        Arguments.of(" \t\r\n{ \t\r\n} \t\r\n", List.of()),
        Arguments.of("{\"a\":1}", List.of("a=1")),
        Arguments.of("{\"a\": 1, \"b\": \"x\"}", List.of("a=1", "b=\"x\"")),
        Arguments.of("{ \"a\"\n:\r\n\t1 ,\n\"b\" : 2 }", List.of("a=1", "b=2")),
        // each value kind is skipped in full
        Arguments.of("{\"o\": {\"n\": {\"m\": [1, {\"k\": \"}\"}]}}, \"a\": [[], {}, \"]\"],"
            + " \"t\": true, \"f\": false, \"z\": null, \"n\": -1.5e+10, \"e\": 0}",
            List.of("o={\"n\": {\"m\": [1, {\"k\": \"}\"}]}}", "a=[[], {}, \"]\"]", "t=true",
                "f=false", "z=null", "n=-1.5e+10", "e=0")),
        // keys are unescaped, values are reported as written
        Arguments.of("{\"a\\\"b\": \"c\\\"d\", \"\\u00e9\": \"\\u00e9\"}",
            List.of("a\"b=\"c\\\"d\"", "\u00E9=\"\\u00e9\"")),
        // a control character inside a string is content
        Arguments.of("{\"a\nb\": \"c\nd\"}", List.of("a\nb=\"c\nd\"")),
        // an empty key and duplicate keys are members like any other
        Arguments.of("{\"\": 1, \"a\": 1, \"a\": 2}", List.of("=1", "a=1", "a=2")),
        // non-ASCII and supplementary-plane text in keys and values
        Arguments.of("{\"\uD83D\uDE00\": \"\uD801\uDC12\", \"\u00E9\": \"\u3000x\"}",
            List.of("\uD83D\uDE00=\"\uD801\uDC12\"", "\u00E9=\"\u3000x\"")));
  }

  @ParameterizedTest
  @MethodSource("documents")
  void testDocument(String text, List<String> expected) {
    Assertions.assertEquals(expected, render(text, JsonScan.document(text)));
  }

  private static List<String> render(String text, List<Member> members) {
    return members.stream()
        .map(m -> m.key() + "=" + text.substring(m.valueStart(), m.valueEnd()))
        .toList();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "", " ", "[]", "\"a\"", "1", "null",
      "{", "{\"a\"", "{\"a\":", "{\"a\":1", "{\"a\":1,", "{\"a\":1,}", "{,}", "{\"a\" 1}",
      "{\"a\":1 \"b\":2}", "{a:1}", "{'a':1}", "{\"a\":1}}", "{\"a\":1} x", "{}{}",
      // strings
      "{\"a\":\"x}", "{\"a\":\"x\\\"}", "{\"a\\", "{\"a\\q\":1}", "{\"\\u12\":1}",
      "{\"\\u+123\":1}", "{\"\\u-123\":1}", "{\"\\u12G4\":1}",
      // numbers
      "{\"a\":01}", "{\"a\":-}", "{\"a\":1.}", "{\"a\":.5}", "{\"a\":1e}", "{\"a\":1e+}",
      "{\"a\":+1}", "{\"a\":0x1}", "{\"a\":\u0661}",
      // literals and containers
      "{\"a\":tru}", "{\"a\":True}", "{\"a\":nul}", "{\"a\":[1,]}", "{\"a\":[1 2]}",
      "{\"a\":[}", "{\"a\":{]}",
      // whitespace outside strings is only the four RFC 8259 characters
      "{\"a\":\u00A01}", "{\"a\"\u3000:1}", "\u000B{}", "\f{}", "{\"a\":1\u0085}"})
  void testDocumentRejectsMalformedText(String text) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document(text));
  }

  @Test
  void testDocumentRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.members(null, 0));
  }

  @Test
  void testMalformedMessageNamesTheOffset() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document("{\"a\": 1, \"b\" 2}"));
    Assertions.assertTrue(e.getMessage().contains("offset 13"), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains("expected ':'"), e.getMessage());
    e = Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document("{\"a\": "));
    Assertions.assertTrue(e.getMessage().contains("end of text"), e.getMessage());
  }

  @Test
  void testMembersReadsANestedObject() {
    final String text = "{\"x\": {\"a\": 1, \"b\": {\"c\": 2}}, \"y\": 3}";
    final Member x = JsonScan.member(JsonScan.document(text), "x");
    Assertions.assertTrue(JsonScan.isObject(text, x));
    Assertions.assertEquals(List.of("a=1", "b={\"c\": 2}"),
        render(text, JsonScan.members(text, x.valueStart())));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.members(text, x.valueStart() + 1));
  }

  // -------------------------------------------------------------------------
  // member, isObject, stringValue, nonNegativeIntValue
  // -------------------------------------------------------------------------

  @Test
  void testMemberFindsTheLastWithAKey() {
    final String text = "{\"a\": 1, \"b\": 2, \"a\": 3}";
    final List<Member> members = JsonScan.document(text);
    Assertions.assertEquals(3, JsonScan.nonNegativeIntValue(text, JsonScan.member(members, "a")));
    Assertions.assertEquals(2, JsonScan.nonNegativeIntValue(text, JsonScan.member(members, "b")));
    Assertions.assertNull(JsonScan.member(members, "c"));
    Assertions.assertNull(JsonScan.member(List.of(), "a"));
  }

  static Stream<Arguments> stringValues() {
    return Stream.of(
        Arguments.of("{\"a\": \"\"}", ""),
        Arguments.of("{\"a\": \"x y\"}", "x y"),
        Arguments.of("{\"a\": \"say \\\"hi\\\"\"}", "say \"hi\""),
        Arguments.of("{\"a\": \"\\\\ \\/ \\b \\f \\n \\r \\t\"}", "\\ / \b \f \n \r \t"),
        Arguments.of("{\"a\": \"\\u0120\\u00E9\\u00e9\"}", "\u0120\u00E9\u00E9"),
        // a surrogate pair written as two escapes, and one written as text
        Arguments.of("{\"a\": \"\\uD83D\\uDE00\"}", "\uD83D\uDE00"),
        Arguments.of("{\"a\": \"\uD83D\uDE00\"}", "\uD83D\uDE00"),
        // an unpaired surrogate escape is decoded as written
        Arguments.of("{\"a\": \"\\uD83D\"}", "\uD83D"),
        Arguments.of("{\"a\": \"tab\there\"}", "tab\there"),
        Arguments.of("{\"a\": \"li\nne\"}", "li\nne"),
        Arguments.of("{\"a\": \"\u00A0\u3000\"}", "\u00A0\u3000"));
  }

  @ParameterizedTest
  @MethodSource("stringValues")
  void testStringValue(String text, String expected) {
    Assertions.assertEquals(expected,
        JsonScan.stringValue(text, JsonScan.document(text).get(0)));
  }

  @ParameterizedTest
  // other value kinds, and a string with invalid escapes: a value is checked when read
  @ValueSource(strings = {"{\"a\": 1}", "{\"a\": true}", "{\"a\": null}", "{\"a\": {}}",
      "{\"a\": [\"x\"]}", "{\"a\": \"\\x\"}", "{\"a\": \"\\u12\"}", "{\"a\": \"\\u12G4\"}"})
  void testStringValueRejectsOtherValues(String text) {
    final Member a = JsonScan.document(text).get(0);
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringValue(text, a));
  }

  static Stream<Arguments> intValues() {
    return Stream.of(
        Arguments.of("{\"a\": 0}", 0),
        Arguments.of("{\"a\": 7}", 7),
        Arguments.of("{\"a\":42}", 42),
        Arguments.of("{\"a\": 2147483647}", Integer.MAX_VALUE));
  }

  @ParameterizedTest
  @MethodSource("intValues")
  void testNonNegativeIntValue(String text, int expected) {
    Assertions.assertEquals(expected,
        JsonScan.nonNegativeIntValue(text, JsonScan.document(text).get(0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\": -1}", "{\"a\": 1.5}", "{\"a\": 1.0}", "{\"a\": 1e3}",
      "{\"a\": 2147483648}", "{\"a\": \"1\"}", "{\"a\": true}", "{\"a\": null}", "{\"a\": [1]}"})
  void testNonNegativeIntValueRejectsOtherValues(String text) {
    final Member a = JsonScan.document(text).get(0);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.nonNegativeIntValue(text, a));
  }

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
        Arguments.of("\"a\\\"", 0, -1));
  }

  @ParameterizedTest
  @MethodSource("closingQuotes")
  void testClosingQuote(String text, int openQuote, int expected) {
    Assertions.assertEquals(expected, JsonScan.closingQuote(text, openQuote));
  }

  // -------------------------------------------------------------------------
  // afterColon, skipWhitespace, endOfValue
  // -------------------------------------------------------------------------

  static Stream<Arguments> colons() {
    return Stream.of(
        Arguments.of(":", 0, 1),
        Arguments.of(":1", 0, 1),
        Arguments.of(" : 1", 0, 3),
        Arguments.of(":\t\n\r1", 0, 4),
        Arguments.of("\t\n\r\u000B\f:1", 0, -1),
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

  static Stream<Arguments> whitespaceRuns() {
    return Stream.of(
        Arguments.of("", 0, 0),
        Arguments.of("a", 0, 0),
        Arguments.of(" a", 0, 1),
        Arguments.of(" \t\n\ra", 0, 4),
        // vertical tab and form feed are not JSON whitespace
        Arguments.of("\u000Ba", 0, 0),
        Arguments.of("\fa", 0, 0),
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

  static Stream<Arguments> values() {
    return Stream.of(
        Arguments.of("\"\"", 2), Arguments.of("\"a\\\"b\" x", 6),
        Arguments.of("0", 1), Arguments.of("-0", 2), Arguments.of("10", 2), Arguments.of("-10.5", 5),
        Arguments.of("1e10", 4), Arguments.of("1E-10", 5), Arguments.of("1.5e+3,", 6),
        Arguments.of("0.5", 3), Arguments.of("01", 1), Arguments.of("1.5.2", 3),
        Arguments.of("true", 4), Arguments.of("false", 5), Arguments.of("null", 4),
        Arguments.of("truex", 4),
        Arguments.of("{}", 2), Arguments.of("{\"a\":[1,{\"b\":\"]\"}]}x", 19),
        Arguments.of("[]", 2), Arguments.of("[ 1 , [ 2 ] , \"[\" ]", 19));
  }

  @ParameterizedTest
  @MethodSource("values")
  void testEndOfValue(String text, int expected) {
    Assertions.assertEquals(expected, JsonScan.endOfValue(text, 0));
  }

  // -------------------------------------------------------------------------
  // byte order mark
  // -------------------------------------------------------------------------

  static Stream<Arguments> documentsWithAByteOrderMark() {
    return Stream.of(
        Arguments.of("﻿{\"a\":1}", List.of("a=1")),
        Arguments.of("﻿ \r\n{ \"a\" : 1 }\r\n", List.of("a=1")),
        Arguments.of("﻿{}", List.of()));
  }

  @ParameterizedTest
  @MethodSource("documentsWithAByteOrderMark")
  void testDocumentSkipsALeadingByteOrderMark(String text, List<String> expected) {
    Assertions.assertEquals(expected, render(text, JsonScan.document(text)));
  }

  static Stream<Arguments> byteOrderMarksElsewhere() {
    return Stream.of(
        Arguments.of("﻿﻿{}", 1),
        Arguments.of(" ﻿{}", 1),
        Arguments.of("{﻿}", 1),
        Arguments.of("{}﻿", 2),
        Arguments.of("{\"a\":﻿1}", 5),
        Arguments.of("﻿", 1));
  }

  @ParameterizedTest
  @MethodSource("byteOrderMarksElsewhere")
  void testDocumentRejectsAByteOrderMarkElsewhere(String text, int offset) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document(text));
    Assertions.assertTrue(e.getMessage().contains("offset " + offset + ","), e.getMessage());
  }
}
