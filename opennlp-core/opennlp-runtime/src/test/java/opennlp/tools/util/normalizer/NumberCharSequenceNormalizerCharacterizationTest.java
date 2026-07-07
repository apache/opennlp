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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization tests for {@link NumberCharSequenceNormalizer}.
 *
 * <p>The fixed expectations below were probed against the regex implementation
 * ({@code "\\d+"} replaced by a single space) and pin its output byte for byte: every maximal run
 * of ASCII digits collapses to one space, and nothing else changes. In particular, non-ASCII
 * digits are left alone, because the regex {@code \d} class (without
 * {@code UNICODE_CHARACTER_CLASS}) only ever matched {@code [0-9]}.</p>
 */
public class NumberCharSequenceNormalizerCharacterizationTest {

  private static final NumberCharSequenceNormalizer NORMALIZER =
      NumberCharSequenceNormalizer.getInstance();

  private static void check(String input, String expected) {
    assertEquals(expected, NORMALIZER.normalize(input).toString());
  }

  @Test
  void asciiDigitRunsCollapseToASingleSpace() {
    check("", "");
    check("abc", "abc");
    check("123", " ");
    check("absc 123,0123 abcd", "absc  ,  abcd");
    check("a1b22c333d", "a b c d");
    check(" 12 34 ", "     ");
    check("0", " ");
    check("12.34", " . ");
    check("-5", "- ");
    check("12ab34", " ab ");
    check("x0123456789y", "x y");
    check("1", " ");
    check("a1", "a ");
    check("1a", " a");
    check("  123  ", "     ");
    check("phone: +1-800-555-0199", "phone: + - - - ");
  }

  @Test
  void nonAsciiDigitsAreNotTouched() {
    // Arabic-Indic digits, a Bengali digit, a circled digit, and the mathematical bold digit one
    // (a supplementary code point) are all outside the ASCII \d class.
    check("\u0661\u0662\u0663", "\u0661\u0662\u0663");
    check("\u09E9", "\u09E9");
    check("\u2460", "\u2460");
    check("\uD835\uDFCF", "\uD835\uDFCF");
    check("\uD83D\uDE001\uD83D\uDE00", "\uD83D\uDE00 \uD83D\uDE00");
    check("1\uD835\uDFCF2", " \uD835\uDFCF ");
  }

  @Test
  void nullTextIsRejected() {
    // Not characterization: the cursor refactor deliberately rejects null with an
    // IllegalArgumentException, where the regex version threw an undocumented
    // NullPointerException from Matcher.
    assertThrows(IllegalArgumentException.class, () -> NORMALIZER.normalize(null));
  }

  @Test
  void matchesTheFormerRegexOnRandomizedInputs() {
    final Pattern formerRegex = Pattern.compile("\\d+");
    final String[] pool = {"a", "Z", " ", "0", "1", "23", "007", ".", ",", "-", "+",
        "\u0661", "\u2460", "\uD835\uDFCF", "\uD83D\uDE00", "\uD83D", "\uDE00"};
    final Random random = new Random(42);
    for (int i = 0; i < 5000; i++) {
      final String input = randomInput(random, pool);
      assertEquals(formerRegex.matcher(input).replaceAll(" "),
          NORMALIZER.normalize(input).toString(), () -> "Input: " + escape(input));
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
