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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization tests for {@link ShrinkCharSequenceNormalizer}.
 *
 * <p>The fixed expectations below were probed against the regex implementation
 * ({@code "\\s{2,}"} replaced by one space, then {@code "(.)\\1{2,}"} with
 * {@code CASE_INSENSITIVE} replaced by {@code "$1$1"}, then {@link String#trim()}) and pin its
 * output byte for byte. The behavior has several sharp edges worth keeping visible:</p>
 * <ul>
 *   <li>{@code \s} is the ASCII class, so only runs of the six ASCII whitespace characters
 *       collapse; a single tab survives, and NBSP or NEL never collapse as whitespace.</li>
 *   <li>The repeated-character pass is ASCII case-insensitive ({@code "aAAb"} shrinks to
 *       {@code "aab"}, keeping the first character's case twice), but it never starts a run on a
 *       regex line terminator because {@code .} does not match one.</li>
 *   <li>Supplementary-plane repeats shrink as whole code points, and lone surrogates follow the
 *       regex engine's code-point reading of the input.</li>
 * </ul>
 */
public class ShrinkCharSequenceNormalizerCharacterizationTest {

  private static final ShrinkCharSequenceNormalizer NORMALIZER =
      ShrinkCharSequenceNormalizer.getInstance();

  private static void check(String input, String expected) {
    assertEquals(expected, NORMALIZER.normalize(input).toString());
  }

  @Test
  void whitespaceRunsOfTwoOrMoreCollapseToOneSpace() {
    check("", "");
    check("   ", "");
    check("a text    extra space", "a text extra space");
    check("a  b", "a b");
    check("a \t b", "a b");
    check("a\tb", "a\tb");
    check("a\t\tb", "a b");
    check("\tab", "ab");
    check("ab\t", "ab");
    check("a\n\n\nb", "a b");
    check("a\nb", "a\nb");
    check("a\r\n\r\nb", "a b");
    check(" \t\n ", "");
    check("x X x X", "x X x X");
  }

  @Test
  void repeatedCharacterRunsShrinkToTwoAsciiCaseInsensitively() {
    check("Helllllloooooo", "Helloo");
    check("Hello", "Hello");
    check("HHello", "HHello");
    check("aaab", "aab");
    check("aaaa", "aa");
    check("aAAb", "aab");
    check("aAaB", "aaB");
    check("aAa", "aa");
    check("aA", "aA");
    check("heyyy", "heyy");
    check("cooool!!!!", "cool!!");
    check("....", "..");
    check("----", "--");
    check("aa", "aa");
    check("SSSs", "SS");
    check("111222333", "112233");
    check("ababab", "ababab");
    check("xxxXXX", "xx");
    check("aaabbb ccc", "aabb cc");
    check("aaa\n\n\nbbb", "aa bb");
    check("a   bbb  c", "a bb c");
  }

  @Test
  void edgesAreTrimmedLikeStringTrim() {
    check("  aaa  ", "aa");
    check("a", "a");
    check("a  ", "a");
    // String.trim() drops every char up to U+0020, not only whitespace.
    check("\u0001a\u0001", "a");
  }

  @Test
  void regexLineTerminatorsNeverStartARepeatRun() {
    // NEL, LINE SEPARATOR, and PARAGRAPH SEPARATOR are not in the ASCII \s class, and the
    // regex "." refused to match them, so their runs survived both passes (and trim keeps
    // them, as they are above U+0020).
    check("\u0085\u0085\u0085", "\u0085\u0085\u0085");
    check("\u2028\u2028\u2028", "\u2028\u2028\u2028");
    check("\u2029\u2029\u2029", "\u2029\u2029\u2029");
    // NBSP is matched by "." and shrinks like any repeated character.
    check("\u00A0\u00A0\u00A0", "\u00A0\u00A0");
  }

  @Test
  void nonAsciiRepeatsCompareByExactCodePoint() {
    // ASCII-only case folding: e-acute repeats shrink, but E-acute does not fold to e-acute.
    check("\u00E9\u00E9\u00E9\u00E9", "\u00E9\u00E9");
    check("\u00C9\u00C9\u00E9\u00E9", "\u00C9\u00C9\u00E9\u00E9");
  }

  @Test
  void supplementaryAndLoneSurrogateRunsFollowTheRegexReading() {
    check("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE00");
    check("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00", "\uD83D\uDE00\uD83D\uDE00");
    // Three lone high surrogates before a low one do not shrink: at the third one the engine
    // reads a full surrogate pair, which no longer equals the one-char group.
    check("\uD83D\uD83D\uD83D\uDE00", "\uD83D\uD83D\uD83D\uDE00");
    // A pair followed by two lone low surrogates shrinks the three-low-surrogate run found
    // when scanning resumes inside the pair.
    check("\uD83D\uDE00\uDE00\uDE00", "\uD83D\uDE00\uDE00");
  }

  @Test
  void nullTextIsRejected() {
    // Not characterization: the refactor deliberately rejects null with IllegalArgumentException.
    assertThrows(IllegalArgumentException.class, () -> NORMALIZER.normalize(null));
  }

  @Test
  void matchesTheFormerRegexOnRandomizedInputs() {
    final Pattern spaceRegex = Pattern.compile("\\s{2,}", Pattern.CASE_INSENSITIVE);
    final Pattern repeatedCharRegex = Pattern.compile("(.)\\1{2,}", Pattern.CASE_INSENSITIVE);
    final String[] pool = {"a", "aa", "aaa", "A", "b", "Z", "!", "!!", ".", "-", " ", "  ",
        "\t", "\n", "\r", "\u0001", "\u0085", "\u2028", "\u00A0", "\u00E9", "\u00C9",
        "\uD83D\uDE00", "\uD83D", "\uDE00"};
    final Random random = new Random(42);
    for (int i = 0; i < 5000; i++) {
      final String input = CharacterizationInputs.randomInput(random, pool);
      final String expected = repeatedCharRegex
          .matcher(spaceRegex.matcher(input).replaceAll(" "))
          .replaceAll("$1$1").trim();
      assertEquals(expected, NORMALIZER.normalize(input).toString(),
          () -> "Input: " + CharacterizationInputs.escape(input));
    }
  }
  @Test
  void noMatchInputIsReturnedUncopied() {
    // Single spaces, no repeat runs, nothing to trim: no pass may allocate.
    final String plain = "single spaced words only";
    Assertions.assertSame(plain, NORMALIZER.normalize(plain));
  }
}
