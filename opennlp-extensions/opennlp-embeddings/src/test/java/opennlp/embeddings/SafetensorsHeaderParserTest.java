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
package opennlp.embeddings;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@link SafetensorsHeaderParser}, complementing the indirect coverage in
 * {@link SafetensorsFileTest}: the file-level tests exercise headers as whole files, these pin
 * the parser's own contract, its error offsets, and every malformed-input branch.
 */
class SafetensorsHeaderParserTest {

  @Test
  void testParsesTensorsInHeaderOrder() {
    final SafetensorsHeaderParser.Result result = SafetensorsHeaderParser.parse(
        "{\"beta\":{\"dtype\":\"F32\",\"shape\":[2,3],\"data_offsets\":[0,24]},"
            + "\"alpha\":{\"dtype\":\"I64\",\"shape\":[],\"data_offsets\":[24,32]}}");

    assertEquals(2, result.tensors().size());
    final TensorInfo beta = result.tensors().get(0);
    assertEquals("beta", beta.name());
    assertEquals("F32", beta.dtype());
    assertArrayEquals(new int[] {2, 3}, beta.shape());
    assertEquals(0, beta.dataOffsetBegin());
    assertEquals(24, beta.dataOffsetEnd());
    final TensorInfo alpha = result.tensors().get(1);
    assertEquals("alpha", alpha.name());
    assertArrayEquals(new int[0], alpha.shape());
    assertEquals(1, alpha.elementCount());
    assertTrue(result.metadata().isEmpty());
  }

  @Test
  void testParsesAnEmptyHeader() {
    final SafetensorsHeaderParser.Result result = SafetensorsHeaderParser.parse("{}");

    assertTrue(result.tensors().isEmpty());
    assertTrue(result.metadata().isEmpty());
  }

  @Test
  void testParsesAMetadataOnlyHeader() {
    final SafetensorsHeaderParser.Result result =
        SafetensorsHeaderParser.parse("{\"__metadata__\":{\"format\":\"pt\"}}");

    assertTrue(result.tensors().isEmpty());
    assertEquals("pt", result.metadata().get("format"));
  }

  @Test
  void testDecodesEveryEscapeSequence() {
    final SafetensorsHeaderParser.Result result = SafetensorsHeaderParser.parse(
        "{\"__metadata__\":{\"note\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\"}}");

    assertEquals("\"\\/\b\f\n\r\tA", result.metadata().get("note"));
  }

  @Test
  void testSkipsUnknownFieldsOfEveryValueType() {
    // Fields safetensors may add over time must not break the reader: nested objects, arrays,
    // floating-point numbers, booleans, null, and strings are all skipped structurally.
    final SafetensorsHeaderParser.Result result = SafetensorsHeaderParser.parse(
        "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4],"
            + "\"future\":{\"nested\":[1,-2.5e3,true,false,null,\"s\",{\"deep\":[]}]}}}");

    assertEquals(List.of("w"), result.tensors().stream().map(TensorInfo::name).toList());
  }

  @Test
  void testToleratesTrailingWhitespacePadding() {
    // Writers space-pad the header so the data section starts aligned; padding is part of the
    // declared header length and must parse cleanly.
    final SafetensorsHeaderParser.Result result = SafetensorsHeaderParser.parse(
        "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}}      ");

    assertEquals(1, result.tensors().size());
  }

  @Test
  void testRejectsTrailingGarbage() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SafetensorsHeaderParser.parse("{} x"));
    assertTrue(e.getMessage().contains("Trailing content"));
  }

  @Test
  void testRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> SafetensorsHeaderParser.parse(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      // unterminated string
      "{\"w",
      // unknown escape
      "{\"a\\x\":{}}",
      // truncated \_u escape (split so the Java lexer does not see a \_u sequence)
      "{\"a\\" + "u00",
      // malformed \_u escape
      "{\"a\\" + "uZZZZ\":{}}",
      // missing colon
      "{\"w\" 1}",
      // empty tensor object
      "{\"w\":{}}",
      // missing dtype
      "{\"w\":{\"shape\":[1],\"data_offsets\":[0,4]}}",
      // missing shape
      "{\"w\":{\"dtype\":\"F32\",\"data_offsets\":[0,4]}}",
      // missing data_offsets
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[1]}}",
      // data_offsets arity 1
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0]}}",
      // data_offsets arity 3
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4,8]}}",
      // negative shape dimension
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[-1],\"data_offsets\":[0,4]}}",
      // shape dimension over int range
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[4294967296],\"data_offsets\":[0,4]}}",
      // non-numeric array element
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[\"x\"],\"data_offsets\":[0,4]}}",
      // number too large for long
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[99999999999999999999],\"data_offsets\":[0,4]}}",
      // bare value instead of an object
      "42",
      // truncated after a tensor entry
      "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}"
  })
  void testRejectsMalformedHeaders(String header) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> SafetensorsHeaderParser.parse(header));
    assertTrue(e.getMessage().contains("Malformed safetensors header at offset"),
        () -> "Message should carry the offset, got: " + e.getMessage());
  }
  @Test
  void testSignedUnicodeEscapeFailsLoudly() {
    // Integer.parseInt would accept "-0FF" and decode the wrong character; the parser must not.
    final String header = "{\"__metadata__\":{\"note\":\"a\\u-0FFb\"},"
        + "\"w\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}}";
    assertThrows(IllegalArgumentException.class, () -> SafetensorsHeaderParser.parse(header));
  }

  @Test
  void testMalformedNumberInSkippedFieldFailsLoudly() {
    // Skipped unknown fields still hold values to the JSON grammar; "1e++--..5" is not a number.
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],"
        + "\"data_offsets\":[0,4],\"unknown\":1e++--..5}}";
    assertThrows(IllegalArgumentException.class, () -> SafetensorsHeaderParser.parse(header));
  }

  @Test
  void testWellFormedNumbersInSkippedFieldsAreAccepted() {
    final String header = "{\"w\":{\"dtype\":\"F32\",\"shape\":[1],"
        + "\"data_offsets\":[0,4],\"a\":-1.5e+10,\"b\":0.25,\"c\":3}}";
    assertEquals(1, SafetensorsHeaderParser.parse(header).tensors().size());
  }
}
