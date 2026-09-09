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

package opennlp.embeddings.corpus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.InvalidFormatException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

  @ParameterizedTest
  @ValueSource(strings = {"01", "1.", ".1", "1e309"})
  void testRejectsInvalidNumbers(String text) {
    assertThrows(InvalidFormatException.class, () -> Json.parse(text, "test input"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"line\nbreak\"", "\"tab\tcharacter\""})
  void testRejectsUnescapedControlCharacters(String text) {
    assertThrows(InvalidFormatException.class, () -> Json.parse(text, "test input"));
  }

  @Test
  void testRejectsDuplicateObjectMembers() {
    assertThrows(InvalidFormatException.class,
        () -> Json.parse("{\"id\": \"first\", \"id\": \"second\"}", "test input"));
  }

  @Test
  void testRejectsWhitespaceOutsideJsonGrammar() {
    assertThrows(InvalidFormatException.class, () -> Json.parse("\u2003null", "test input"));
  }

  @Test
  void testAppendStringRejectsNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> Json.appendString(null, "value"));
    assertThrows(IllegalArgumentException.class,
        () -> Json.appendString(new StringBuilder(), null));
  }

  @Test
  void testAppendStringEscapesControlCharacters() {
    final String value = new StringBuilder()
        .append((char) 0).append('\b').append('\f').append('\t')
        .append('\n').append('\r').append((char) 0x1f).toString();
    final StringBuilder encoded = new StringBuilder();
    Json.appendString(encoded, value);
    final String expected = new StringBuilder("\"")
        .append('\\').append('u').append("0000")
        .append('\\').append('u').append("0008")
        .append('\\').append('u').append("000c")
        .append("\\t\\n\\r")
        .append('\\').append('u').append("001f\"").toString();
    assertEquals(expected, encoded.toString());
  }

  @Test
  void testAppendStringEscapesSurrogates() {
    final StringBuilder encoded = new StringBuilder();
    Json.appendString(encoded, String.valueOf(Character.MIN_HIGH_SURROGATE));
    final String expected = new StringBuilder("\"")
        .append('\\').append('u').append("d800\"").toString();
    assertEquals(expected, encoded.toString());
  }

  @Test
  void testAcceptsJsonNumbers() throws InvalidFormatException {
    assertEquals(0L, Json.parse("0", "test input"));
    assertEquals(0L, Json.parse("-0", "test input"));
    assertEquals(12L, Json.parse("12", "test input"));
    assertEquals(-1.25, Json.parse("-1.25", "test input"));
    assertEquals(1000.0, Json.parse("1e3", "test input"));
  }
}
