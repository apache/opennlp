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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WordBreakTest {

  @Test
  void everyPropertyValueNameMapsToItsConstant() {
    // The names as written in WordBreakProperty.txt, one per constant except the OTHER default.
    final Map<String, WordBreak> expected = Map.ofEntries(
        Map.entry("CR", WordBreak.CR),
        Map.entry("LF", WordBreak.LF),
        Map.entry("Newline", WordBreak.NEWLINE),
        Map.entry("Extend", WordBreak.EXTEND),
        Map.entry("ZWJ", WordBreak.ZWJ),
        Map.entry("Regional_Indicator", WordBreak.REGIONAL_INDICATOR),
        Map.entry("Format", WordBreak.FORMAT),
        Map.entry("Katakana", WordBreak.KATAKANA),
        Map.entry("Hebrew_Letter", WordBreak.HEBREW_LETTER),
        Map.entry("ALetter", WordBreak.ALETTER),
        Map.entry("Single_Quote", WordBreak.SINGLE_QUOTE),
        Map.entry("Double_Quote", WordBreak.DOUBLE_QUOTE),
        Map.entry("MidNumLet", WordBreak.MID_NUM_LET),
        Map.entry("MidLetter", WordBreak.MID_LETTER),
        Map.entry("MidNum", WordBreak.MID_NUM),
        Map.entry("Numeric", WordBreak.NUMERIC),
        Map.entry("ExtendNumLet", WordBreak.EXTEND_NUM_LET),
        Map.entry("WSegSpace", WordBreak.WSEG_SPACE));
    for (final Map.Entry<String, WordBreak> entry : expected.entrySet()) {
      assertSame(entry.getValue(), WordBreak.fromPropertyName(entry.getKey()), entry.getKey());
    }
    // Every constant except the OTHER default is reachable from a property value name.
    assertEquals(WordBreak.values().length - 1, expected.size());
  }

  @Test
  void unknownPropertyValueNameFailsLoud() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> WordBreak.fromPropertyName("NotAWordBreakValue"));
    assertTrue(e.getMessage().contains("NotAWordBreakValue"));
    // Names are matched exactly as written in the data file, not case-insensitively.
    assertThrows(IllegalArgumentException.class, () -> WordBreak.fromPropertyName("aletter"));
  }

  @Test
  void ordinalsFitTheByteBackedTables() {
    // WordBreakProperty stores ordinals in byte tables (masked unsigned on read), so the enum must
    // stay within a byte; this trips if a future Unicode version adds values past that bound.
    assertTrue(WordBreak.values().length <= 127,
        "WordBreak ordinals must fit the byte-backed lookup tables");
  }
}
