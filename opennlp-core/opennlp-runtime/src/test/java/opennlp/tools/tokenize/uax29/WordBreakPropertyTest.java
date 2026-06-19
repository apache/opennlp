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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class WordBreakPropertyTest {

  @Test
  void testAsciiLettersAndDigits() {
    assertSame(WordBreak.ALETTER, WordBreakProperty.of('a'));
    assertSame(WordBreak.ALETTER, WordBreakProperty.of('Z'));
    assertSame(WordBreak.NUMERIC, WordBreakProperty.of('0'));
    assertSame(WordBreak.NUMERIC, WordBreakProperty.of('9'));
  }

  @Test
  void testWhitespaceAndLineBreaks() {
    assertSame(WordBreak.WSEG_SPACE, WordBreakProperty.of(0x0020)); // space
    assertSame(WordBreak.CR, WordBreakProperty.of(0x000D));
    assertSame(WordBreak.LF, WordBreakProperty.of(0x000A));
    assertSame(WordBreak.NEWLINE, WordBreakProperty.of(0x000B));   // vertical tab
  }

  @Test
  void testMidAndExtendClasses() {
    assertSame(WordBreak.MID_NUM, WordBreakProperty.of(0x002C));      // comma
    assertSame(WordBreak.MID_NUM_LET, WordBreakProperty.of(0x002E));  // full stop
    assertSame(WordBreak.MID_LETTER, WordBreakProperty.of(0x003A));   // colon
    assertSame(WordBreak.EXTEND_NUM_LET, WordBreakProperty.of(0x005F)); // low line
    assertSame(WordBreak.EXTEND, WordBreakProperty.of(0x0301));       // combining acute
  }

  @Test
  void testQuotesJoinerAndScriptLetters() {
    assertSame(WordBreak.SINGLE_QUOTE, WordBreakProperty.of(0x0027));
    assertSame(WordBreak.DOUBLE_QUOTE, WordBreakProperty.of(0x0022));
    assertSame(WordBreak.ZWJ, WordBreakProperty.of(0x200D));
    assertSame(WordBreak.HEBREW_LETTER, WordBreakProperty.of(0x05D0));
    assertSame(WordBreak.KATAKANA, WordBreakProperty.of(0x30A1));
  }

  @Test
  void testSupplementaryCodePointsUseTheRangeTable() {
    assertSame(WordBreak.REGIONAL_INDICATOR, WordBreakProperty.of(0x1F1E6)); // regional indicator A
    assertSame(WordBreak.ALETTER, WordBreakProperty.of(0x1D400));            // math bold A
    assertSame(WordBreak.OTHER, WordBreakProperty.of(0x1F600));              // grinning face
  }

  @ParameterizedTest
  @ValueSource(ints = {0x0021, 0x0040, 0x2014})
  void testUnassignedCodePointsAreOther(int codePoint) {
    assertSame(WordBreak.OTHER, WordBreakProperty.of(codePoint));
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, Integer.MIN_VALUE, Character.MAX_CODE_POINT + 1, Integer.MAX_VALUE})
  void testOutOfRangeIsOtherAndSafe(int codePoint) {
    assertSame(WordBreak.OTHER, WordBreakProperty.of(codePoint));
  }

  @Test
  void testFromPropertyNameRejectsUnknown() {
    assertEquals(WordBreak.ALETTER, WordBreak.fromPropertyName("ALetter"));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> WordBreak.fromPropertyName("NotAValue"));
  }
}
