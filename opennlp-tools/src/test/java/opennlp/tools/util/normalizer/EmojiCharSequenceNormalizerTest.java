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

import java.nio.CharBuffer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation")
public class EmojiCharSequenceNormalizerTest {

  private static final EmojiCharSequenceNormalizer NORMALIZER =
      EmojiCharSequenceNormalizer.getInstance();

  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  @Test
  void normalizeEmoji() {
    String s = "Any funny text goes here " + cp(0x1F606, 0x1F606, 0x1F606) + " " + cp(0x1F61B);
    Assertions.assertEquals("Any funny text goes here    ", NORMALIZER.normalize(s));
  }

  @Test
  void normalizeNullThrows() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> NORMALIZER.normalize(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "plain text", "a-b", "-", "well-known", "\t\n",
      "\u00E9\u00C9\u00DF", "\u0661\u0662", "\u65E5\u672C\u8A9E", "\u0630\u0647\u0628"})
  void normalizeLeavesTextWithoutSupplementaryCodePoints(String text) {
    Assertions.assertEquals(text, NORMALIZER.normalize(text));
  }

  /*
   * Every code point of the supplementary planes is replaced, whether it is an emoji, a
   * letter, a symbol, or private use, and a run collapses to one space.
   */
  private static Stream<Arguments> supplementaryRuns() {
    return Stream.of(
        Arguments.of(cp(0x1F600), " "),
        Arguments.of("a" + cp(0x1F600) + "b", "a b"),
        Arguments.of("a" + cp(0x1F600, 0x1F601, 0x1F602) + "b", "a b"),
        // runs separated by a BMP character are replaced one each, the space between stays
        Arguments.of("a" + cp(0x1F600) + " " + cp(0x1F601) + "b", "a   b"),
        // the lowest and the highest supplementary code point
        Arguments.of("a" + cp(0x10000) + "b", "a b"),
        Arguments.of("a" + cp(0x10FFFF) + "b", "a b"),
        // plane 16 private use is replaced too
        Arguments.of("a" + cp(0x10FC01) + "b", "a b"),
        // Deseret letters, mathematical bold A, and a Tangut ideograph
        Arguments.of("a" + cp(0x10412, 0x1043A) + "b", "a b"),
        Arguments.of("a" + cp(0x1D400) + "b", "a b"),
        Arguments.of("a" + cp(0x17000) + "b", "a b"),
        // flags are two regional indicators, one run
        Arguments.of(cp(0x1F1E9, 0x1F1EA), " "),
        // skin tone modifier after a pictograph, one run
        Arguments.of(cp(0x1F44D, 0x1F3FD), " "),
        Arguments.of(cp(0x1F600) + cp(0x1F600), " "),
        Arguments.of(cp(0x1F600) + "x" + cp(0x1F600), " x "));
  }

  @ParameterizedTest
  @MethodSource("supplementaryRuns")
  void normalizeReplacesSupplementaryRuns(String text, String expected) {
    Assertions.assertEquals(expected, NORMALIZER.normalize(text));
  }

  /*
   * BMP characters near the top of the plane are kept: fullwidth Latin, halfwidth katakana, a CJK
   * compatibility ideograph, an Arabic presentation form, private use, the replacement
   * character, and U+FFFF.
   */
  @ParameterizedTest
  @ValueSource(strings = {"\uFF21\uFF22", "\uFF76\uFF80", "\uFA11", "\uFB50", "\uE000",
      "\uFFFD", "\uFFFF", "\uD7FF"})
  void normalizeKeepsBasicMultilingualPlane(String text) {
    Assertions.assertEquals("a" + text + "b", NORMALIZER.normalize("a" + text + "b"));
  }

  @Test
  void normalizeKeepsBmpPartsOfEmojiSequences() {
    // the zero width joiner and the variation selector are BMP characters and stay, the
    // pictographs around them are replaced one run each
    String family = cp(0x1F468) + "\u200D" + cp(0x1F469);
    Assertions.assertEquals(" \u200D ", NORMALIZER.normalize(family));
    Assertions.assertEquals(" \uFE0F", NORMALIZER.normalize(cp(0x1F495) + "\uFE0F"));
    // a BMP emoji such as the heavy black heart is not supplementary and stays
    Assertions.assertEquals("\u2764\uFE0F", NORMALIZER.normalize("\u2764\uFE0F"));
  }

  @Test
  void normalizeKeepsUnpairedSurrogates() {
    // an unpaired surrogate is not a supplementary code point, so it is kept as it is
    String s = "a" + '\uD83C' + "b" + '\uDC00' + "c";
    Assertions.assertEquals(s, NORMALIZER.normalize(s));

    // a lone high surrogate directly before a pair belongs to no code point and stays,
    // the pair is replaced
    String t = "x" + '\uD83C' + cp(0x1F600) + "y";
    Assertions.assertEquals("x\uD83C y", NORMALIZER.normalize(t));

    // a lone low surrogate after a pair likewise
    String u = "x" + cp(0x1F600) + '\uDC00' + "y";
    Assertions.assertEquals("x \uDC00y", NORMALIZER.normalize(u));
  }

  @Test
  void normalizeAcceptsAnyCharSequence() {
    String text = "a" + cp(0x1F600) + "b";
    Assertions.assertEquals("a b", NORMALIZER.normalize(new StringBuilder(text)));
    Assertions.assertEquals("a b", NORMALIZER.normalize(CharBuffer.wrap(text.toCharArray())));
  }

  @Test
  void normalizeIsIdempotent() {
    String text = "a" + cp(0x1F600, 0x1F601) + "b" + cp(0x10412) + "c";
    CharSequence once = NORMALIZER.normalize(text);
    Assertions.assertEquals(once.toString(), NORMALIZER.normalize(once).toString());
  }
}
