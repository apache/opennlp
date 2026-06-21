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
package opennlp.tools.util.normalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CodePointSetTest {

  @Test
  void testOfContainsExactlyTheGivenCodePoints() {
    final CodePointSet set = CodePointSet.of(0x0041, 0x00A0, 0x1F600);
    assertTrue(set.contains(0x0041));
    assertTrue(set.contains(0x00A0));
    assertTrue(set.contains(0x1F600));
    assertFalse(set.contains(0x0042));
    assertEquals(3, set.size());
    assertFalse(set.isEmpty());
  }

  @Test
  void testToArrayIsAscending() {
    final CodePointSet set = CodePointSet.of(0x3000, 0x0009, 0x00A0);
    assertArrayEquals(new int[] {0x0009, 0x00A0, 0x3000}, set.toArray());
  }

  @Test
  void testOfRangeIsInclusive() {
    final CodePointSet set = CodePointSet.ofRange(0x2000, 0x200A);
    assertTrue(set.contains(0x2000));
    assertTrue(set.contains(0x2005));
    assertTrue(set.contains(0x200A));
    assertFalse(set.contains(0x1FFF));
    assertFalse(set.contains(0x200B));
    assertEquals(11, set.size());
  }

  @Test
  void testOfRangeRejectsDescending() {
    assertThrows(IllegalArgumentException.class, () -> CodePointSet.ofRange(0x200A, 0x2000));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE, Character.MAX_CODE_POINT + 1, Integer.MAX_VALUE})
  void testOfRejectsInvalidCodePoints(int codePoint) {
    assertThrows(IllegalArgumentException.class, () -> CodePointSet.of(codePoint));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE, Character.MAX_CODE_POINT + 1, Integer.MAX_VALUE})
  void testContainsIsRangeSafe(int codePoint) {
    assertFalse(CodePointSet.of(0x0020).contains(codePoint));
  }

  @Test
  void testUnionIsNonDestructive() {
    final CodePointSet a = CodePointSet.of(0x0041);
    final CodePointSet b = CodePointSet.of(0x0042);
    final CodePointSet union = a.union(b);

    assertTrue(union.contains(0x0041));
    assertTrue(union.contains(0x0042));
    assertEquals(2, union.size());
    assertFalse(a.contains(0x0042), "left operand must be unchanged");
    assertFalse(b.contains(0x0041), "right operand must be unchanged");
  }

  @Test
  void testEqualsAndHashCode() {
    assertEquals(CodePointSet.of(0x01, 0x02), CodePointSet.of(0x02, 0x01));
    assertEquals(CodePointSet.of(0x01, 0x02).hashCode(), CodePointSet.of(0x02, 0x01).hashCode());
    assertFalse(CodePointSet.of(0x01).equals(CodePointSet.of(0x02)));
  }

  @Test
  void testEqualsAgainstOtherTypesAndNull() {
    final CodePointSet set = CodePointSet.of(0x20);
    assertFalse(set.equals(null));
    assertFalse(set.equals("not a code point set"));
  }

  @Test
  void testParseAcceptsSingleHexDigit() {
    assertTrue(CodePointSet.parse(List.of("[s]", "9"), "s").contains(0x9));
  }

  @Test
  void testParseRejectsEmptyCodePointAfterPrefix() {
    assertThrows(IllegalArgumentException.class,
        () -> CodePointSet.parse(List.of("[s]", "U+"), "s"));
  }

  @Test
  void testParseRejectsTooShortSectionHeader() {
    assertThrows(IllegalArgumentException.class,
        () -> CodePointSet.parse(List.of("[]", "U+0041"), "s"));
  }

  @Test
  void testParseSingleCodePointsRangesCommentsAndBlankLines() {
    final List<String> lines = List.of(
        "# a whitespace overlay",
        "[whitespace]",
        "U+00A0          # no-break space",
        "0x2028",
        "2029",
        "",
        "U+2000-U+200A   # typographic spaces");

    final CodePointSet set = CodePointSet.parse(lines, "whitespace");

    assertTrue(set.contains(0x00A0));
    assertTrue(set.contains(0x2028));
    assertTrue(set.contains(0x2029));
    assertTrue(set.contains(0x2000));
    assertTrue(set.contains(0x2007));
    assertTrue(set.contains(0x200A));
    assertFalse(set.contains(0x200B));
    assertEquals(3 + 11, set.size());
  }

  @Test
  void testParseReturnsOnlyRequestedSection() {
    final List<String> lines = List.of(
        "[whitespace]",
        "U+00A0",
        "[dash]",
        "U+2212",
        "U+2014");

    final CodePointSet whitespace = CodePointSet.parse(lines, "whitespace");
    assertTrue(whitespace.contains(0x00A0));
    assertFalse(whitespace.contains(0x2212));
    assertFalse(whitespace.contains(0x2014));

    final CodePointSet dash = CodePointSet.parse(lines, "dash");
    assertTrue(dash.contains(0x2212));
    assertTrue(dash.contains(0x2014));
    assertFalse(dash.contains(0x00A0));
  }

  @Test
  void testParseSectionNameIsCaseInsensitive() {
    final List<String> lines = List.of("[WhiteSpace]", "U+00A0");
    assertTrue(CodePointSet.parse(lines, "whitespace").contains(0x00A0));
    assertTrue(CodePointSet.parse(lines, "WHITESPACE").contains(0x00A0));
  }

  @Test
  void testParseMissingSectionIsEmpty() {
    final List<String> lines = List.of("[whitespace]", "U+00A0");
    assertTrue(CodePointSet.parse(lines, "dash").isEmpty());
  }

  @Test
  void testParseRejectsMalformedSectionHeader() {
    final List<String> lines = List.of("[whitespace", "U+00A0");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> CodePointSet.parse(lines, "whitespace"));
    assertTrue(e.getMessage().contains("line 1"), e.getMessage());
  }

  @Test
  void testParseRejectsInvalidHex() {
    final List<String> lines = List.of("[whitespace]", "U+ZZZZ");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> CodePointSet.parse(lines, "whitespace"));
    assertTrue(e.getMessage().contains("line 2"), e.getMessage());
  }

  @Test
  void testParseRejectsDescendingRange() {
    final List<String> lines = List.of("[whitespace]", "U+200A-U+2000");
    assertThrows(IllegalArgumentException.class, () -> CodePointSet.parse(lines, "whitespace"));
  }

  @Test
  void testParseRejectsOutOfRangeCodePoint() {
    final List<String> lines = List.of("[whitespace]", "U+110000");
    assertThrows(IllegalArgumentException.class, () -> CodePointSet.parse(lines, "whitespace"));
  }

  @Test
  void testParseRejectsEntryBeforeAnySection() {
    final List<String> lines = List.of("U+00A0");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> CodePointSet.parse(lines, "whitespace"));
    assertTrue(e.getMessage().contains("before any [section]"), e.getMessage());
  }

  @Test
  void testParseAcceptsAllThreeHexPrefixes() {
    final List<String> lines = List.of("[s]", "U+0041", "0x0042", "0043");
    final CodePointSet set = CodePointSet.parse(lines, "s");
    assertTrue(set.contains(0x41));
    assertTrue(set.contains(0x42));
    assertTrue(set.contains(0x43));
  }

  @Test
  void testFromFileReadsTheNamedSection(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("delimiters.txt");
    Files.writeString(file, String.join("\n",
        "[whitespace]",
        "U+00A0",
        "[dash]",
        "U+2E5D"), StandardCharsets.UTF_8);

    assertTrue(CodePointSet.fromFile(file, "whitespace").contains(0x00A0));
    assertTrue(CodePointSet.fromFile(file, "dash").contains(0x2E5D));
    assertFalse(CodePointSet.fromFile(file, "dash").contains(0x00A0));
  }
}
