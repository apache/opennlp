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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexCodePointsTest {

  @Test
  void parseCodePointReadsHexDigits() {
    assertEquals(0x41, HexCodePoints.parseCodePoint("41"));
    assertEquals(0x1F600, HexCodePoints.parseCodePoint("1F600"));
    assertEquals(0x1f600, HexCodePoints.parseCodePoint("1f600"));
    assertEquals(0x10FFFF, HexCodePoints.parseCodePoint("10FFFF"));
    assertEquals(0, HexCodePoints.parseCodePoint("0000"));
    assertEquals(0x200D, HexCodePoints.parseCodePoint("x 200D y", 2, 6));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "G", "1F60G", "110000", "-1", "U+1F600", " 41"})
  void parseCodePointRejectsMalformedDigits(String hex) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> HexCodePoints.parseCodePoint(hex));
    assertTrue(e.getMessage().contains(hex), e.getMessage());
  }

  @Test
  void decodeSequenceReadsSpaceSeparatedCodePoints() {
    assertEquals("A", HexCodePoints.decodeSequence("41"));
    assertEquals(new String(Character.toChars(0x1F600)), HexCodePoints.decodeSequence("1F600"));
    assertEquals(new String(new int[] {0x1F468, 0x200D, 0x1F469}, 0, 3),
        HexCodePoints.decodeSequence("1F468 200D 1F469"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "1F600 ", " 1F600", "1F600  200D", "1F600 ZZ"})
  void decodeSequenceRejectsMalformedSequences(String hex) {
    assertThrows(IllegalArgumentException.class, () -> HexCodePoints.decodeSequence(hex));
  }

  @Test
  void parseRangeReadsSingleCodePointsAndRanges() {
    assertArrayEquals(new int[] {0x23, 0x23}, HexCodePoints.parseRange("23"));
    assertArrayEquals(new int[] {0x1F3FB, 0x1F3FF}, HexCodePoints.parseRange("1F3FB..1F3FF"));
    assertArrayEquals(new int[] {0xE0020, 0xE007F}, HexCodePoints.parseRange("E0020..E007F"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "..", "1F3FB..", "..1F3FF", "1F3FF..1F3FB", "1F3FB.1F3FF",
      "1F3FB..1F3FF..1F3FF"})
  void parseRangeRejectsMalformedRanges(String hex) {
    assertThrows(IllegalArgumentException.class, () -> HexCodePoints.parseRange(hex));
  }
}
