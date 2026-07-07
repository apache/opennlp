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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for {@link TwitterCharSequenceNormalizer}.
 *
 * <p>The fixed expectations below were probed against the regex implementation (four passes:
 * {@code "[#@]\\S+"} to a space, {@code "\\b(rt[ :])+"} case-insensitive to a space,
 * {@code "[:;x]-?[()dop]"} case-insensitive to a space, and the laugh pattern
 * {@code "([hj])+([aieou])+(\\1+\\2+)+"} case-insensitive to {@code "$1$2$1$2"}) and pin the
 * output byte for byte. Notable sharp edges kept visible here:</p>
 * <ul>
 *   <li>{@code \S} is the complement of the six ASCII whitespace characters, so a hashtag or
 *       handle swallows NBSP, emoji, and any other non-ASCII-space characters.</li>
 *   <li>The word boundary before {@code rt} follows the JDK engine: ASCII word characters block
 *       it, and so does a non-spacing mark that has a base character, scanning backwards char by
 *       char (which makes a supplementary-plane mark behave as if it had no base).</li>
 *   <li>The emoticon pass happily fires inside words ({@code "expo"} contains {@code "xp"}).</li>
 *   <li>The laugh pass keeps the case of the last consonant and last vowel of the two leading
 *       runs ({@code "hAHa"} becomes {@code "hAhA"}, {@code "hjaja"} becomes {@code "jaja"}).</li>
 * </ul>
 */
public class TwitterCharSequenceNormalizerCharacterizationTest {

  private static final TwitterCharSequenceNormalizer NORMALIZER =
      TwitterCharSequenceNormalizer.getInstance();

  private static void check(String input, String expected) {
    assertEquals(expected, NORMALIZER.normalize(input).toString());
  }

  @Test
  void hashtagsAndHandlesBecomeASingleSpace() {
    check("", "");
    check("asdf #hasdk23 2nnfdf", "asdf   2nnfdf");
    check("asdf @hasdk23 2nnfdf", "asdf   2nnfdf");
    check("#", "#");
    check("##", " ");
    check("# #", "# #");
    check("#a#b", " ");
    check("abc#def", "abc ");
    check("@user hi", "  hi");
    check("@ home", "@ home");
    check("#\uD83D\uDE00", " ");
    check("#\u00A0x", " ");
    check("email me @home now", "email me   now");
    check("\uD83D\uDE00 #yes \uD83D\uDE00", "\uD83D\uDE00   \uD83D\uDE00");
  }

  @Test
  void retweetMarkersCollapseBehindAJdkStyleWordBoundary() {
    check("RT RT RT 2nnfdf", " 2nnfdf");
    check("rt this", " this");
    check("rt rt rt", " rt");
    check("RT: hello", "  hello");
    check("rt:rt:go", " go");
    check("RT @user: hello", "   hello");
    check("art here", "art here");
    check("smart tv", "smart tv");
    check("cart rt cart", "cart  cart");
    check("hrt x", "hrt x");
    check("1rt x", "1rt x");
    check("_rt x", "_rt x");
    check("-rt x", "- x");
  }

  @Test
  void wordBoundaryTreatsNonAsciiLettersAsNonWordButHonorsCombiningMarks() {
    // e-acute is not an ASCII word char, so "rt" after it sits on a boundary and matches.
    check("\u00E9rt x", "\u00E9 x");
    // A combining mark with a base character blocks the boundary, like the JDK engine.
    check("e\u0301rt x", "e\u0301rt x");
    // A combining mark with no base character does not block it.
    check("\u0301rt x", "\u0301 x");
    // A supplementary-plane mark: the engine's backwards scan reads a lone low surrogate and
    // stops, so the mark does not block the boundary despite its base character.
    check("a\uD800\uDDFDrt x", "a\uD800\uDDFD x");
  }

  @Test
  void emoticonsBecomeASingleSpaceEvenInsideWords() {
    check("hello :-) hello", "hello   hello");
    check("hello ;) hello", "hello   hello");
    check(":) hello", "  hello");
    check("hello :P", "hello  ");
    check("x-d ok", "  ok");
    check(":-( sad", "  sad");
    check("::-)", ": ");
    check("expo", "e o");
    check("xoxo", "  ");
    check("taxi", "taxi");
    check(":-x", ":-x");
    check("X-P", " ");
    check(":o", " ");
    check(":d", " ");
    check("x-o", " ");
    check("xp", " ");
    check("max power", "max power");
  }

  @Test
  void laughterShrinksToTheLastConsonantVowelPairTwice() {
    check("ahahahah", "ahahah");
    check("hahha", "haha");
    check("hahaa", "haha");
    check("ahahahahhahahhahahaaaa", "ahaha");
    check("jajjajajaja", "jaja");
    check("hahaha", "haha");
    check("haha", "haha");
    check("HaHaHa", "HaHa");
    check("hAHa", "hAhA");
    check("HAHAHA", "HAHA");
    check("hhaahhaa", "haha");
    check("hjaja", "jaja");
    check("ha", "ha");
    check("haaaa", "haaaa");
    check("hahe", "hahe");
    check("hajaha", "hajaha");
    check("jejeje", "jeje");
    check("Bahahaha", "Bahaha");
    check("hihihi", "hihi");
    check("hohoho", "hoho");
    check("huhuhu", "huhu");
    check("hyhy", "hyhy");
    check("jujuju", "juju");
    check("hahaha!", "haha!");
    check("hjhj", "hjhj");
  }

  @Test
  void thePassesComposeInOrder() {
    check("RT @user: check #cool :-) hahaha", "   check     haha");
    check("x #tag rt go", "x    go");
  }

  @Test
  void matchesTheFormerRegexesOnRandomizedInputs() {
    final Pattern hashUserRegex = Pattern.compile("[#@]\\S+");
    final Pattern rtRegex = Pattern.compile("\\b(rt[ :])+", Pattern.CASE_INSENSITIVE);
    final Pattern faceRegex = Pattern.compile("[:;x]-?[()dop]", Pattern.CASE_INSENSITIVE);
    final Pattern laughRegex =
        Pattern.compile("([hj])+([aieou])+(\\1+\\2+)+", Pattern.CASE_INSENSITIVE);
    final String[] pool = {"a", "A", "e", "h", "H", "j", "J", "o", "u", "y", "x", "X", "d", "p",
        "P", ")", "(", "-", ":", ";", " ", "  ", "\t", "\n", "#", "@", "_", "1", "rt", "RT",
        "rt:", "Rt ", "t", "r", "ha", "Ha", "ah", "hh", "aa", "jj", "\u00E9", "\u0301", "\uD800\uDDFD",
        "\uD83D\uDE00", "\uD83D", "\uDE00", "\u00A0"};
    final Random random = new Random(42);
    for (int i = 0; i < 5000; i++) {
      final String input = randomInput(random, pool);
      String expected = hashUserRegex.matcher(input).replaceAll(" ");
      expected = rtRegex.matcher(expected).replaceAll(" ");
      expected = faceRegex.matcher(expected).replaceAll(" ");
      expected = laughRegex.matcher(expected).replaceAll("$1$2$1$2");
      assertEquals(expected, NORMALIZER.normalize(input).toString(),
          () -> "Input: " + escape(input));
    }
  }

  private static String randomInput(Random random, String[] pool) {
    final int pieces = random.nextInt(24);
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < pieces; i++) {
      b.append(pool[random.nextInt(pool.length)]);
    }
    return b.toString();
  }

  private static String escape(String s) {
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      final char c = s.charAt(i);
      if (c >= 0x20 && c <= 0x7E) {
        b.append(c);
      } else {
        b.append(String.format("\\u%04X", (int) c));
      }
    }
    return b.toString();
  }
}
