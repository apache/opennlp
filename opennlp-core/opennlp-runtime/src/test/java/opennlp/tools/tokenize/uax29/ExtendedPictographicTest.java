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
package opennlp.tools.tokenize.uax29;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtendedPictographicTest {

  @ParameterizedTest
  @ValueSource(ints = {0x00A9, 0x00AE, 0x203C, 0x2764, 0x1F600, 0x1F468})
  void testPictographicCodePoints(int codePoint) {
    assertTrue(ExtendedPictographic.is(codePoint),
        () -> String.format("U+%04X should be Extended_Pictographic", codePoint));
  }

  @ParameterizedTest
  @ValueSource(ints = {'a', '5', ' ', 0x0301, 0x05D0, 0x1F1E6})
  void testNonPictographicCodePoints(int codePoint) {
    // 0x1F1E6 (regional indicator A) is a supplementary code point that is NOT pictographic.
    assertFalse(ExtendedPictographic.is(codePoint));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE, Character.MAX_CODE_POINT + 1, Integer.MAX_VALUE})
  void testOutOfRangeIsFalseAndSafe(int codePoint) {
    assertFalse(ExtendedPictographic.is(codePoint));
  }

  @Test
  void parseFailsLoudOnMalformedHex() {
    // A structurally-malformed line (non-hex code points) fails loud naming the line, the same way
    // the sibling loaders do, rather than surfacing a raw NumberFormatException.
    final String data = "1F600\n"   // valid
        + "NOTHEX\n";                // malformed
    assertThrows(IllegalArgumentException.class, () -> ExtendedPictographic.parse(
        new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)), new BitSet()));
  }
}
