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

import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.CompatibilityMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization tests for the output of {@link EmojiCharSequenceNormalizer} under
 * {@link CompatibilityMode#LEGACY}.
 *
 * <p>The fixed expectations below were probed against the regex implementation
 * ({@code "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"} to a space) and pin its output byte for byte.
 * Java reads the adjacent escapes {@code \\uDBFF\\uDC00} inside the class as the one code point
 * U+10FC00, so the class is {@code [U+D83C..U+10FC00]} plus a literal {@code -} plus U+DFFF.
 * Sharp edges kept visible:</p>
 * <ul>
 *   <li>every ASCII hyphen is removed, so {@code "well-known"} becomes {@code "well known"};</li>
 *   <li>everything from U+E000 to U+FFFF is removed: private use characters, CJK compatibility
 *       ideographs, Arabic and Hebrew presentation forms, fullwidth and halfwidth forms, and the
 *       variation selector U+FE0F on its own;</li>
 *   <li>every supplementary character up to U+10FC00 is removed, emoji or not, while
 *       U+10FC01..U+10FFFF are kept;</li>
 *   <li>a BMP emoji such as U+231A is kept, because it is below U+D83C;</li>
 *   <li>an unpaired surrogate from U+D83C on is removed, one below it is kept.</li>
 * </ul>
 */
public class EmojiCharSequenceNormalizerCharacterizationTest {

  private static final EmojiCharSequenceNormalizer NORMALIZER =
      EmojiCharSequenceNormalizer.getInstance();

  /** Former emoji regex used for differential characterization. */
  private static final Pattern FORMER_EMOJI_REGEX =
      Pattern.compile("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+");

  @BeforeEach
  void selectLegacyMode() {
    CompatibilityMode.setActive(CompatibilityMode.LEGACY);
  }

  @AfterEach
  void resetMode() {
    CompatibilityMode.reset();
  }

  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  private static void check(String input, String expected) {
    assertEquals(expected, NORMALIZER.normalize(input).toString());
  }

  @Test
  void emojiRunsBecomeASingleSpace() {
    check("", "");
    check("Any funny text goes here " + cp(0x1F606, 0x1F606, 0x1F606) + " " + cp(0x1F61B),
        "Any funny text goes here    ");
    check("a" + cp(0x1F600) + "b", "a b");
    check("a" + cp(0x1F600) + " " + cp(0x1F603) + "b", "a   b");
    check(cp(0x1F468) + "‍" + cp(0x1F469), " ‍ ");
    check(cp(0x1F1E9, 0x1F1EA), " ");
  }

  @Test
  void hyphensAreRemoved() {
    check("well-known", "well known");
    check("a-b", "a b");
    check("--", " ");
    check("-" + cp(0x1F600) + "-", " ");
  }

  @Test
  void everythingFromPrivateUseToTheEndOfTheBasicPlaneIsRemoved() {
    check("xy", "x y");
    check("xy", "x y");
    check("x豈y", "x y");
    check("xﬁy", "x y");
    check("xﭐy", "x y");
    check("xﹰy", "x y");
    check("x！y", "x y");
    check("xＡＢy", "x y");
    check("xｱy", "x y");
    check("x�y", "x y");
    check("x￿y", "x y");
    check("x❤️y", "x❤ y");
  }

  @Test
  void supplementaryCharactersAreRemovedUpToTheRegexBoundary() {
    check("a𐐒b", "a b");
    check("a𠀀b", "a b");
    check("a" + cp(0x10FC00) + "b", "a b");
    check("a" + cp(0x10FC01) + "b", "a" + cp(0x10FC01) + "b");
    check("a" + cp(0x10FFFF) + "b", "a" + cp(0x10FFFF) + "b");
  }

  @Test
  void charactersBelowTheRegexBoundaryAreKept() {
    check("a⌚b", "a⌚b");
    check("a❤b", "a❤b");
    check("x\uDFFEy", "x y");
    check("x\uDFFFy", "x y");
    check("x\uD83Bx", "x\uD83Bx");
    check("x\uD83Cx", "x x");
    check("x\uD800y", "x\uD800y");
    check("x\uDC00y", "x y");
    check("123 #*", "123 #*");
  }

  @Test
  void nullTextIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> NORMALIZER.normalize(null));
  }

  @Test
  void noMatchInputIsReturnedUncopied() {
    final String plain = "no emoji in this sentence at all";
    Assertions.assertSame(plain, NORMALIZER.normalize(plain));
  }

  @Test
  void matchesTheFormerRegexOnRandomizedInputs() {
    final String[] pool = {"a", "Z", "1", " ", "-", "#", "*", "é", "م", "⌚",
        "❤", "︎", "️", "‍", "⃣", "퟿", "", "", "豈",
        "ﭐ", "ﹰ", "Ａ", "ｱ", "�", "￿", "\uD800", "\uD83B", "\uD83C",
        "\uDBFF", "\uDC00", "\uDFFF", "😀", "🇩", "𐐒",
        "𠀀", "󰀀", cp(0x10FC00), cp(0x10FC01), cp(0x10FFFF)};
    final Random random = new Random(42);
    for (int i = 0; i < 5000; i++) {
      final String input = CharacterizationInputs.randomInput(random, pool);
      final String expected = FORMER_EMOJI_REGEX.matcher(input).replaceAll(" ");
      assertEquals(expected, NORMALIZER.normalize(input).toString(),
          () -> "Input: " + CharacterizationInputs.escape(input));
    }
  }

  static Stream<String> mixedScripts() {
    return Stream.of(
        "well-known e-mail re-entry",
        "ＡＢＣ fullwidth and ｱｲ halfwidth",
        "ﭐﭑ Arabic and יִ Hebrew presentation forms",
        "豈更 compatibility ideographs",
        " private use",
        "go" + cp(0x1F600) + "❤️now",
        cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F),
        "7️⃣",
        "a\uD83Cb\uDC00c\uD83C" + cp(0x1F600) + "\uDC00d",
        cp(0x20000, 0x1D400, 0x10330));
  }

  @ParameterizedTest
  @MethodSource("mixedScripts")
  void mixedScriptsMatchTheFormerRegexExactly(String input) {
    assertEquals(FORMER_EMOJI_REGEX.matcher(input).replaceAll(" "),
        NORMALIZER.normalize(input).toString(), CharacterizationInputs.escape(input));
  }
}
