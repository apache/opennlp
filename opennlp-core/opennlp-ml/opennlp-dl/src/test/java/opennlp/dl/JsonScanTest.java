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
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.dl.JsonScan.Member;

public class JsonScanTest {

  private static final int DEEP = 100_000;

  static Stream<Arguments> documents() {
    return Stream.of(
        Arguments.of("{}", List.of()),
        Arguments.of(" \t\r\n{ \t\r\n} \t\r\n", List.of()),
        Arguments.of("{\"a\":1}", List.of("a=1")),
        Arguments.of("{\"a\": 1, \"b\": \"x\"}", List.of("a=1", "b=\"x\"")),
        Arguments.of("{ \"a\"\n:\r\n\t1 ,\n\"b\" : 2 }", List.of("a=1", "b=2")),
        Arguments.of("{\"a\":1,\"b\":2}", List.of("a=1", "b=2")),
        Arguments.of("{\"a\" : 1 , \"b\" :\t2}", List.of("a=1", "b=2")),
        // each value kind is skipped in full
        Arguments.of("{\"o\": {\"n\": {\"m\": [1, {\"k\": \"}\"}]}}, \"a\": [[], {}, \"]\"],"
            + " \"t\": true, \"f\": false, \"z\": null, \"n\": -1.5e+10, \"e\": 0}",
            List.of("o={\"n\": {\"m\": [1, {\"k\": \"}\"}]}}", "a=[[], {}, \"]\"]", "t=true",
                "f=false", "z=null", "n=-1.5e+10", "e=0")),
        Arguments.of("{\"a\":{},\"b\":[],\"c\":{\"d\":{}},\"e\":[[]]}",
            List.of("a={}", "b=[]", "c={\"d\":{}}", "e=[[]]")),
        // numbers in the forms RFC 8259 allows
        Arguments.of("{\"a\": 0, \"b\": -0, \"c\": 10, \"d\": -10.5, \"e\": 1e10, \"f\": 1E-10,"
            + " \"g\": 1.5e+3, \"h\": 0.5}",
            List.of("a=0", "b=-0", "c=10", "d=-10.5", "e=1e10", "f=1E-10", "g=1.5e+3", "h=0.5")),
        // keys are unescaped, values are reported as written
        Arguments.of("{\"a\\\"b\": \"c\\\"d\", \"\\u00e9\": \"\\u00e9\"}",
            List.of("a\"b=\"c\\\"d\"", "\u00E9=\"\\u00e9\"")),
        Arguments.of("{\"a\\\\\": 1, \"\\\\\\\"\": 2, \"\\u0120\": 3}",
            List.of("a\\=1", "\\\"=2", "\u0120=3")),
        // a control character inside a string is content
        Arguments.of("{\"a\nb\": \"c\nd\"}", List.of("a\nb=\"c\nd\"")),
        Arguments.of("{\"a\u0001b\": 1}", List.of("a\u0001b=1")),
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
      "{\"a\":1 \"b\":2}", "{a:1}", "{'a':1}", "{\"a\":1}}", "{\"a\":1} x", "{}{}", "{\"a\":1}]",
      "{\"a\"::1}",
      // strings
      "{\"a\":\"x}", "{\"a\":\"x\\\"}", "{\"a\\", "{\"a\\q\":1}", "{\"\\u12\":1}",
      "{\"\\u+123\":1}", "{\"\\u-123\":1}", "{\"\\u12G4\":1}", "{\"a\":\"\\x\"}",
      // numbers
      "{\"a\":01}", "{\"a\":-01}", "{\"a\":-}", "{\"a\":1.}", "{\"a\":.5}", "{\"a\":1e}",
      "{\"a\":1e+}", "{\"a\":+1}", "{\"a\":0x1}", "{\"a\":\u0661}", "{\"a\":1.5.2}",
      // literals and containers
      "{\"a\":tru}", "{\"a\":True}", "{\"a\":nul}", "{\"a\":truex}", "{\"a\":[1,]}",
      "{\"a\":[1 2]}", "{\"a\":[}", "{\"a\":{]}", "{\"a\":[1}}", "{\"a\":{\"b\":1]}",
      // whitespace outside strings is only the four RFC 8259 characters
      "{\"a\":\u00A01}", "{\"a\"\u3000:1}", "\u000B{}", "\f{}", "{\"a\":1\u0085}",
      "{\"a\"\u2003:1}", "{\"a\":\t1\u000B}", "\u001C{}"})
  void testDocumentRejectsMalformedText(String text) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document(text));
  }

  @Test
  void testDocumentRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document(null));
  }

  @Test
  void testMembersRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.members(null, 0));
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
        Arguments.of("{\"a\": \"\\ud83d\\ude00\"}", "\uD83D\uDE00"),
        Arguments.of("{\"a\": \"\uD83D\uDE00\"}", "\uD83D\uDE00"),
        // an unpaired surrogate escape is decoded as written
        Arguments.of("{\"a\": \"\\uD83D\"}", "\uD83D"),
        Arguments.of("{\"a\": \"tab\there\"}", "tab\there"),
        Arguments.of("{\"a\": \"li\nne\"}", "li\nne"),
        Arguments.of("{\"a\": \"\u00A0\u3000\"}", "\u00A0\u3000"),
        Arguments.of("{\"a\": \"x}y\"}", "x}y"),
        Arguments.of("{\"a\": \"x\\\\\"}", "x\\"));
  }

  @ParameterizedTest
  @MethodSource("stringValues")
  void testStringValue(String text, String expected) {
    Assertions.assertEquals(expected,
        JsonScan.stringValue(text, JsonScan.document(text).get(0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\": 1}", "{\"a\": true}", "{\"a\": null}", "{\"a\": {}}",
      "{\"a\": [\"x\"]}", "{\"a\": NaN}"})
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
  @ValueSource(strings = {"{\"a\": -1}", "{\"a\": -0}", "{\"a\": 1.5}", "{\"a\": 1.0}",
      "{\"a\": 1e3}", "{\"a\": 2147483648}", "{\"a\": \"1\"}", "{\"a\": true}", "{\"a\": null}",
      "{\"a\": [1]}", "{\"a\": NaN}", "{\"a\": Infinity}", "{\"a\": -Infinity}"})
  void testNonNegativeIntValueRejectsOtherValues(String text) {
    final Member a = JsonScan.document(text).get(0);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.nonNegativeIntValue(text, a));
  }

  static Stream<Arguments> stringObjects() {
    return Stream.of(
        Arguments.of("{\"m\": {\"0\": \"x\"}}", Map.of("0", "x")),
        Arguments.of("{\"m\": {}}", Map.of()),
        Arguments.of("{\"a\": 1, \"m\": {\"0\": \"x\", \"1\": \"y\"}, \"z\": [{}]}",
            Map.of("0", "x", "1", "y")),
        Arguments.of("{\"m\": {\"0\": \"x\"}, \"m\": {\"0\": \"y\"}}", Map.of("0", "y")),
        Arguments.of("{\"m\": {\"0\": \"x\", \"0\": \"y\"}}", Map.of("0", "y")),
        Arguments.of("{\"\\u006d\": {\"\\u0030\": \"\\u0078\"}}", Map.of("0", "x")),
        // no such top-level member, or only a nested one
        Arguments.of("{}", Map.of()),
        Arguments.of("{\"n\": {\"0\": \"x\"}}", Map.of()),
        Arguments.of("{\"n\": {\"m\": {\"0\": \"x\"}}}", Map.of()),
        // blank text, with or without a byte order mark
        Arguments.of("", Map.of()),
        Arguments.of(" \r\n\t", Map.of()),
        Arguments.of("\uFEFF", Map.of()),
        Arguments.of("\uFEFF \n", Map.of()),
        Arguments.of("\uFEFF{\"m\": {\"0\": \"x\"}}", Map.of("0", "x")));
  }

  @ParameterizedTest
  @MethodSource("stringObjects")
  void testStringObject(String text, Map<String, String> expected) {
    Assertions.assertEquals(expected, JsonScan.stringObject(text, "m"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"m\": \"x\"}", "{\"m\": [\"x\"]}", "{\"m\": null}", "{\"m\": 1}",
      "{\"m\": {\"0\": 1}}", "{\"m\": {\"0\": null}}", "{\"m\": {\"0\": {}}}",
      "{\"m\": {\"0\": \"x\"}", "[]", "x", "\u00A0", "\u001C", "\uFEFF\uFEFF"})
  void testStringObjectRejectsOtherText(String text) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringObject(text, "m"));
  }

  @Test
  void testStringObjectRejectsNullArguments() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringObject(null, "m"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringObject("{}", null));
  }

  static Stream<Arguments> documentsWithAByteOrderMark() {
    return Stream.of(
        Arguments.of("\uFEFF{\"a\":1}", List.of("a=1")),
        Arguments.of("\uFEFF \r\n{ \"a\" : 1 }\r\n", List.of("a=1")),
        Arguments.of("\uFEFF{}", List.of()));
  }

  @ParameterizedTest
  @MethodSource("documentsWithAByteOrderMark")
  void testDocumentSkipsALeadingByteOrderMark(String text, List<String> expected) {
    Assertions.assertEquals(expected, render(text, JsonScan.document(text)));
  }

  static Stream<Arguments> byteOrderMarksElsewhere() {
    return Stream.of(
        Arguments.of("\uFEFF\uFEFF{}", 1),
        Arguments.of(" \uFEFF{}", 1),
        Arguments.of("{\uFEFF}", 1),
        Arguments.of("{}\uFEFF", 2),
        Arguments.of("{\"a\":\uFEFF1}", 5),
        Arguments.of("\uFEFF", 1));
  }

  @ParameterizedTest
  @MethodSource("byteOrderMarksElsewhere")
  void testDocumentRejectsAByteOrderMarkElsewhere(String text, int offset) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document(text));
    Assertions.assertTrue(e.getMessage().contains("offset " + offset + ","), e.getMessage());
  }

  static Stream<Arguments> malformedOffsets() {
    return Stream.of(
        // empty text, or whitespace only
        Arguments.of("", 0, "expected '{'"),
        Arguments.of("   ", 3, "expected '{'"),
        Arguments.of("\u000B{}", 0, "expected '{'"),
        // cut off after each token of a member
        Arguments.of("{", 1, "expected '\"'"),
        Arguments.of("{\"a", 1, "unterminated string"),
        Arguments.of("{\"a\\", 1, "unterminated string"),
        Arguments.of("{\"a\"", 4, "expected ':'"),
        Arguments.of("{\"a\":", 5, "expected a value"),
        Arguments.of("{\"a\": ", 6, "expected a value"),
        Arguments.of("{\"a\":1", 6, "expected ','"),
        Arguments.of("{\"a\":1,", 7, "expected '\"'"),
        Arguments.of("{\"a\":1,}", 7, "expected '\"'"),
        Arguments.of("{\"a\":\"x", 5, "unterminated string"),
        Arguments.of("{\"a\":\"x\\\"", 5, "unterminated string"),
        Arguments.of("{\"a\":\"x\\", 5, "unterminated string"),
        Arguments.of("{\"a\":[1", 7, "expected ','"),
        Arguments.of("{\"a\":[1,", 8, "expected a value"),
        Arguments.of("{\"a\":[", 6, "expected a value"),
        Arguments.of("{\"a\":[}", 6, "expected a value"),
        Arguments.of("{\"a\":{]}", 6, "expected '\"'"),
        Arguments.of("{\"a\":[1}", 7, "expected ','"),
        Arguments.of("{\"a\":{\"b\":1]}", 11, "expected ','"),
        Arguments.of("{\"a\":{\"b\":1}", 12, "expected ','"),
        Arguments.of("{\"a\":{\"b\":", 10, "expected a value"),
        Arguments.of("{\"a\":tr", 5, "expected a value"),
        Arguments.of("{\"a\":-", 6, "expected a digit"),
        Arguments.of("{\"a\":1.", 7, "expected a digit"),
        Arguments.of("{\"a\":1e", 7, "expected a digit"),
        Arguments.of("{\"a\":1e+", 8, "expected a digit"),
        Arguments.of("{\"a\":-01}", 7, "expected ','"),
        // content after the object
        Arguments.of("{\"a\":1}x", 7, "content after the object"),
        Arguments.of("{\"a\":1} \n{}", 9, "content after the object"),
        Arguments.of("{}}", 2, "content after the object"),
        Arguments.of("{}\u00A0", 2, "content after the object"),
        // wrong separators and whitespace that is not JSON whitespace
        Arguments.of("{\"a\" 1}", 5, "expected ':'"),
        Arguments.of("{\"a\"::1}", 5, "expected a value"),
        Arguments.of("{\"a\":1 \"b\":2}", 7, "expected ','"),
        Arguments.of("{\"a\":\u00A01}", 5, "expected a value"),
        Arguments.of("{\"a\"\u3000:1}", 4, "expected ':'"),
        Arguments.of("{\"a\"\u000B:1}", 4, "expected ':'"),
        Arguments.of("{\"a\"\u2003:1}", 4, "expected ':'"),
        // escapes are checked where the backslash is
        Arguments.of("{\"\\u12\":1}", 2, "four hexadecimal digits"),
        Arguments.of("{\"a\\q\":1}", 3, "unknown escape \\q"));
  }

  @ParameterizedTest
  @MethodSource("malformedOffsets")
  void testMalformedMessageNamesTheOffsetAndReason(String text, int offset, String reason) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document(text));
    Assertions.assertTrue(e.getMessage().contains("offset " + offset + ","), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains(reason), e.getMessage());
  }

  @Test
  void testMalformedMessageQuotesTheTextAtTheOffset() {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document("{\"a\": 1, \"b\" 2}"));
    Assertions.assertTrue(e.getMessage().contains("found '2}'"), e.getMessage());
    e = Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document("{\"a\": "));
    Assertions.assertTrue(e.getMessage().contains("end of text"), e.getMessage());
  }

  static Stream<Arguments> lineEndings() {
    return Stream.of(
        Arguments.of("{\r\n\t\"a\"\r\n\t:\r\n\t1,\r\n\t\"b\": \"x\"\r\n}\r\n",
            List.of("a=1", "b=\"x\"")),
        Arguments.of("{\r\"a\":\r1\r}\r", List.of("a=1")),
        Arguments.of("{\"a\":[\r\n1,\r\n2\r\n],\"o\":{\r\n}}",
            List.of("a=[\r\n1,\r\n2\r\n]", "o={\r\n}")),
        // a line break inside a string is content, so it stays in the key
        Arguments.of("{\"a\r\nb\":1}", List.of("a\r\nb=1")),
        // escaped quotes, backslashes, and solidus in keys
        Arguments.of("{\"\\u005B\": 1, \"a\\\\\": 2, \"\\/\": 3, \"\\\\\\\"\": 4}",
            List.of("[=1", "a\\=2", "/=3", "\\\"=4")));
  }

  @ParameterizedTest
  @MethodSource("lineEndings")
  void testLineEndingsAndEscapedKeys(String text, List<String> expected) {
    Assertions.assertEquals(expected, render(text, JsonScan.document(text)));
  }

  static Stream<Arguments> deepDocuments() {
    return Stream.of(
        Arguments.of("{\"a\":" + "[".repeat(DEEP) + "]".repeat(DEEP) + "}"),
        Arguments.of("{\"a\":" + "{\"a\":".repeat(DEEP) + "0" + "}".repeat(DEEP) + "}"),
        Arguments.of("{\"a\":" + "[{\"k\":".repeat(DEEP) + "0" + "}]".repeat(DEEP) + ",\"b\":1}"));
  }

  @ParameterizedTest(name = "deep document {index}")
  @MethodSource("deepDocuments")
  void testDeeplyNestedValuesAreSkippedWithoutRecursion(String text) {
    final List<Member> members = JsonScan.document(text);
    Assertions.assertEquals("a", members.get(0).key());
    Assertions.assertEquals(5, members.get(0).valueStart());
  }

  @ParameterizedTest(name = "deep document {index} cut off")
  @MethodSource("deepDocuments")
  void testDeeplyNestedValueCutOffIsRejectedWithoutRecursion(String text) {
    final String cut = text.substring(0, text.length() - 2);
    final IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document(cut));
    Assertions.assertTrue(e.getMessage().contains("offset " + cut.length() + ","), e.getMessage());
  }

  static Stream<Arguments> badEscapesInsideValues() {
    return Stream.of(
        Arguments.of("{\"a\":\"\\q\"}", 6, "unknown escape \\q"),
        Arguments.of("{\"a\":[\"\\q\"]}", 7, "unknown escape \\q"),
        Arguments.of("{\"a\":{\"\\q\":1}}", 7, "unknown escape \\q"),
        Arguments.of("{\"a\":[\"\\u12\"]}", 7, "four hexadecimal digits"),
        Arguments.of("{\"a\":{\"b\":\"\\u12G4\"}}", 11, "four hexadecimal digits"),
        Arguments.of("{\"a\":\"x\\", 5, "unterminated string"));
  }

  @ParameterizedTest
  @MethodSource("badEscapesInsideValues")
  void testDocumentRejectsABadEscapeInsideAnyString(String text, int offset, String reason) {
    final IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.document(text));
    Assertions.assertTrue(e.getMessage().contains("offset " + offset + ","), e.getMessage());
    Assertions.assertTrue(e.getMessage().contains(reason), e.getMessage());
  }

  static Stream<Arguments> nonFiniteNumbers() {
    return Stream.of(
        Arguments.of("{\"a\": NaN, \"b\": 1}", List.of("a=NaN", "b=1")),
        Arguments.of("{\"a\": Infinity}", List.of("a=Infinity")),
        Arguments.of("{\"a\": -Infinity}", List.of("a=-Infinity")),
        Arguments.of("{\"a\": [NaN, -Infinity, Infinity], \"b\": {\"c\": NaN}}",
            List.of("a=[NaN, -Infinity, Infinity]", "b={\"c\": NaN}")));
  }

  @ParameterizedTest
  @MethodSource("nonFiniteNumbers")
  void testDocumentSkipsTheNonFiniteNumbersPythonWrites(String text, List<String> expected) {
    Assertions.assertEquals(expected, render(text, JsonScan.document(text)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"a\": nan}", "{\"a\": NAN}", "{\"a\": -NaN}", "{\"a\": +Infinity}",
      "{\"a\": inf}", "{\"a\": Infinit}", "{\"a\": NaNx}"})
  void testDocumentRejectsOtherSpellingsOfNonFiniteNumbers(String text) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.document(text));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 2, 3, Integer.MAX_VALUE})
  void testMembersRejectsAnOffsetOutsideTheText(int brace) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.members("{}", brace));
  }

  @Test
  void testMemberRecordRejectsAnInvalidRange() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Member("k", 5, 3));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Member("k", -1, 3));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Member(null, 0, 1));
  }

  @Test
  void testValueReadersRejectAMemberOutsideTheText() {
    final Member outside = new Member("k", 5, 9);
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.isObject("{}", outside));
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringValue("{}", outside));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> JsonScan.nonNegativeIntValue("{}", outside));
  }

  @Test
  void testMemberRejectsNullArguments() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.member(null, "k"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.member(List.of(), null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringValue("{}", null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> JsonScan.stringValue(null,
        new Member("k", 0, 1)));
  }
}
