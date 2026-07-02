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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WordTypeTest {

  private static WordType of(String token) {
    return WordType.of(token, 0, token.length());
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void lettersClassifyAlphanumeric() {
    assertEquals(WordType.ALPHANUMERIC, of("great"));
    assertEquals(WordType.ALPHANUMERIC, of("MiXeD"));
    assertEquals(WordType.ALPHANUMERIC, of("a1b2")); // a letter anywhere wins over digits
    assertEquals(WordType.ALPHANUMERIC, of("caf" + cp(0x00E9))); // non-ASCII Latin letter
  }

  @Test
  void digitsClassifyNumeric() {
    assertEquals(WordType.NUMERIC, of("12345"));
    assertEquals(WordType.NUMERIC, of(cp(0x0661) + cp(0x0662))); // Arabic-Indic digits
  }

  @Test
  void nonWordRangesClassifyNull() {
    assertNull(of("..."));
    assertNull(of("   "));
    assertNull(of("!?"));
    assertNull(of(""));
  }

  @Test
  void emojiAndRegionalIndicatorsClassifyEmoji() {
    assertEquals(WordType.EMOJI, of(cp(0x1F600))); // grinning face, supplementary
    assertEquals(WordType.EMOJI, of(cp(0x1F1E9) + cp(0x1F1EA))); // regional-indicator flag pair
    // Extended_Pictographic symbols such as the copyright sign classify EMOJI by design (they are
    // kept rather than dropped as punctuation); documented in WordTokenizer.
    assertEquals(WordType.EMOJI, of(cp(0x00A9)));
  }

  @Test
  void emojiWinsOverLettersAndScripts() {
    assertEquals(WordType.EMOJI, of("a" + cp(0x1F600)));
    assertEquals(WordType.EMOJI, of(cp(0x3042) + cp(0x1F600)));
  }

  @Test
  void scriptTokensClassifyTheirScript() {
    assertEquals(WordType.IDEOGRAPHIC, of(cp(0x6C34))); // Han
    assertEquals(WordType.HIRAGANA, of(cp(0x3042)));
    assertEquals(WordType.KATAKANA, of(cp(0x30AB)));
    assertEquals(WordType.HANGUL, of(cp(0xAC00)));
    assertEquals(WordType.SOUTHEAST_ASIAN, of(cp(0x0E01))); // Thai
    assertEquals(WordType.SOUTHEAST_ASIAN, of(cp(0x0E81))); // Lao
  }

  @Test
  void leadingScriptWinsForAMixedScriptRange() {
    // Documented behavior: the script category comes from the first script code point in the
    // range, since UAX #29 word segments are single-script in practice.
    assertEquals(WordType.HIRAGANA, of(cp(0x3042) + cp(0x6C34)));
  }

  @Test
  void subrangeOffsetsAreRespected() {
    assertEquals(WordType.NUMERIC, WordType.of("ab12cd", 2, 4));
    assertEquals(WordType.ALPHANUMERIC, WordType.of("12ab34", 2, 4));
  }
}
