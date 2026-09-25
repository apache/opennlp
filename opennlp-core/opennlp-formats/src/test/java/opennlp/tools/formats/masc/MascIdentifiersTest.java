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

package opennlp.tools.formats.masc;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class MascIdentifiersTest {

  @ParameterizedTest
  @CsvSource({"ne-n7, ne-n, 7", "penn-n12, penn-n, 12", "seg-r0, seg-r, 0", "seg-r00, seg-r, 0",
      "penn-n007, penn-n, 7", "ne-n2147483647, ne-n, 2147483647"})
  void testParseIdReadsTheNumberAfterThePrefix(String id, String prefix, int expected) {
    Assertions.assertEquals(expected, MascIdentifiers.parseId(id, prefix));
  }

  @ParameterizedTest
  // other or missing prefix, prefix later in the text, doubled prefix, no digits, sign,
  // digits of another script, trailing text, whitespace, and an overflowing number
  @ValueSource(strings = {"7", "xne-n7", "NE-N7", "ne\u2011n7", "ne-nne-n7", "ne-n", "ne-n-7",
      "ne-n+7", "ne-n\u0661", "ne-n\uFF17", "ne-n7\u0661", "ne-n\u06F7", "ne-n\u00B2", "ne-n\u2167",
      "ne-n\uD835\uDFCE", "ne-n7x", "ne-n7 ", " ne-n7", "ne-n7\n", "ne-n2147483648",
      "ne-n99999999999", ""})
  void testParseIdRejectsAnythingElse(String id) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MascIdentifiers.parseId(id, MascIdentifiers.NAMED_ENTITY_ID_PREFIX));
  }

  @Test
  void testParseIdRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MascIdentifiers.parseId(null, MascIdentifiers.NAMED_ENTITY_ID_PREFIX));
  }

  @ParameterizedTest
  @ValueSource(strings = {"seg-r1 seg-r2", "  seg-r1   seg-r2  ", "seg-r1 seg-r2 ",
      "seg-r1\tseg-r2", "\r\nseg-r1\r\nseg-r2\t"})
  void testParseIdsSplitsOnXmlWhitespace(String ids) {
    Assertions.assertArrayEquals(new int[] {1, 2},
        MascIdentifiers.parseIds(ids, MascIdentifiers.REGION_ID_PREFIX));
  }

  @ParameterizedTest
  // Unicode whitespace outside XML S is not a separator
  @ValueSource(strings = {"seg-r1\u00A0seg-r2", "seg-r1\u3000seg-r2", "seg-r1\u0085seg-r2",
      "seg-r1\u000Bseg-r2", "seg-r1\fseg-r2"})
  void testParseIdsRejectsOtherWhitespaceBetweenIdentifiers(String ids) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MascIdentifiers.parseIds(ids, MascIdentifiers.REGION_ID_PREFIX));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ne-n2147483648", "ne-n99999999999"})
  void testParseIdNamesAnOverflowingNumber(String id) {
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> MascIdentifiers.parseId(id, MascIdentifiers.NAMED_ENTITY_ID_PREFIX));
    Assertions.assertEquals("MASC identifier number does not fit an int: " + id, e.getMessage());
  }

  private static Stream<Arguments> spaceSplits() {
    return Stream.of(
        Arguments.of("", new String[0]),
        Arguments.of("   ", new String[0]),
        Arguments.of("a", new String[] {"a"}),
        Arguments.of(" a  b ", new String[] {"a", "b"}),
        Arguments.of("a\tb", new String[] {"a", "b"}),
        Arguments.of("a\u00A0b c", new String[] {"a\u00A0b", "c"}));
  }

  @ParameterizedTest
  @MethodSource("spaceSplits")
  void testSplitOnXmlWhitespaceIgnoresRepeatedSeparators(String value, String[] expected) {
    Assertions.assertArrayEquals(expected, MascIdentifiers.splitOnXmlWhitespace(value));
  }

  @Test
  void testSplitOnSpacesRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> MascIdentifiers.splitOnXmlWhitespace(null));
  }

  @Test
  void testParseIdsReadsASingleIdentifier() {
    Assertions.assertArrayEquals(new int[] {5},
        MascIdentifiers.parseIds("seg-r5", MascIdentifiers.REGION_ID_PREFIX));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t", "seg-r1 penn-n2", "seg-r1 seg-r", "seg-r1,seg-r2",
      "seg-r1 seg-r\u0661", "seg-r1 seg-r2 seg-r\uFF13", "seg-r1 seg-r2x"})
  void testParseIdsRejectsEmptyOrMalformedLists(String ids) {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MascIdentifiers.parseIds(ids, MascIdentifiers.REGION_ID_PREFIX));
  }

  @Test
  void testParseIdsRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> MascIdentifiers.parseIds(null, MascIdentifiers.REGION_ID_PREFIX));
  }
}
