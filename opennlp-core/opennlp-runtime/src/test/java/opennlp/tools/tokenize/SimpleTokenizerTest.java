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

package opennlp.tools.tokenize;

import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ResetWhitespaceMode;
import opennlp.tools.util.WhitespaceMode;

/**
 * Tests for the {@link SimpleTokenizer} class.
 */
@ResetWhitespaceMode
public class SimpleTokenizerTest {

  private final SimpleTokenizer mTokenizer = SimpleTokenizer.newInstance(false);

  /**
   * Checks that independently configured tokenizers leave other instances unchanged.
   *
   * @param keep Whether the first tokenizer retains line breaks.
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void testIndependentInstances(boolean keep) {
    SimpleTokenizer first = SimpleTokenizer.newInstance(keep);
    SimpleTokenizer second = SimpleTokenizer.newInstance(keep);
    String input = "a\nb";
    String[] expected = keep ? new String[] {"a", "\n", "b"} : new String[] {"a", "b"};
    String[] sharedBefore = SimpleTokenizer.INSTANCE.tokenize(input);

    Assertions.assertNotSame(SimpleTokenizer.INSTANCE, first);
    Assertions.assertNotSame(SimpleTokenizer.INSTANCE, second);
    Assertions.assertNotSame(first, second);
    Assertions.assertArrayEquals(expected, first.tokenize(input));
    Assertions.assertArrayEquals(expected, second.tokenize(input));

    first.setKeepNewLines(!keep);
    Assertions.assertArrayEquals(keep ? new String[] {"a", "b"} : new String[] {"a", "\n", "b"},
        first.tokenize(input));
    Assertions.assertArrayEquals(expected, second.tokenize(input));
    Assertions.assertArrayEquals(sharedBefore, SimpleTokenizer.INSTANCE.tokenize(input));
  }

  /**
   * Tests if it can tokenize whitespace separated tokens.
   */
  @Test
  void testWhitespaceTokenization() {

    String text = "a b c  d     e                f    ";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("a", tokenizedText[0]);
    Assertions.assertEquals("b", tokenizedText[1]);
    Assertions.assertEquals("c", tokenizedText[2]);
    Assertions.assertEquals("d", tokenizedText[3]);
    Assertions.assertEquals("e", tokenizedText[4]);
    Assertions.assertEquals("f", tokenizedText[5]);

    Assertions.assertEquals(6, tokenizedText.length);
  }

  /**
   * Tests if it can tokenize a word and a dot.
   */
  @Test
  void testWordDotTokenization() {
    String text = "a.";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("a", tokenizedText[0]);
    Assertions.assertEquals(".", tokenizedText[1]);
    Assertions.assertEquals(2, tokenizedText.length);
  }

  /**
   * Tests if it can tokenize a word and numeric.
   */
  @Test
  void testWordNumericTokeniztation() {
    String text = "305KW";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("305", tokenizedText[0]);
    Assertions.assertEquals("KW", tokenizedText[1]);
    Assertions.assertEquals(2, tokenizedText.length);
  }

  @Test
  void testWordWithOtherTokenization() {
    String text = "rebecca.sleep()";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("rebecca", tokenizedText[0]);
    Assertions.assertEquals(".", tokenizedText[1]);
    Assertions.assertEquals("sleep", tokenizedText[2]);
    Assertions.assertEquals("(", tokenizedText[3]);
    Assertions.assertEquals(")", tokenizedText[4]);
    Assertions.assertEquals(5, tokenizedText.length);
  }

  @Test
  void testTokenizationOfStringWithUnixNewLineTokens() {
    SimpleTokenizer tokenizer = SimpleTokenizer.newInstance(true);

    Assertions.assertEquals(2, tokenizer.tokenize("a\n").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n"}, tokenizer.tokenize("a\n"));

    Assertions.assertEquals(3, tokenizer.tokenize("a\nb").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, tokenizer.tokenize("a\nb"));

    Assertions.assertEquals(4, tokenizer.tokenize("a\n\n b").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "\n", "b"}, tokenizer.tokenize("a\n\n b"));

    Assertions.assertEquals(7, tokenizer.tokenize("a\n\n b\n\n c").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "\n", "b", "\n", "\n", "c"},
        tokenizer.tokenize("a\n\n b\n\n c"));
  }

  @Test
  void testTokenizationOfStringWithWindowsNewLineTokens() {
    SimpleTokenizer tokenizer = SimpleTokenizer.newInstance(true);

    Assertions.assertEquals(3, tokenizer.tokenize("a\r\n").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n"}, tokenizer.tokenize("a\r\n"));

    Assertions.assertEquals(4, tokenizer.tokenize("a\r\nb").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "b"}, tokenizer.tokenize("a\r\nb"));

    Assertions.assertEquals(6, tokenizer.tokenize("a\r\n\r\n b").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b"}, tokenizer
        .tokenize("a\r\n\r\n b"));

    Assertions.assertEquals(11, tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b", "\r", "\n", "\r", "\n", "c"},
        tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c"));
  }

  /**
   * Tests if it can tokenize a word containing a non-spacing character
   * like Arabic Damma Unicode Character “◌ُ” (U+064F)
   */
  @Test
  void testNonSpacingLetters() {
    String text = "تمّ طُوّر المشروع بنجاح."; //In Arabic: "The project was developed successfully."

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals(5, tokenizedText.length);
    Assertions.assertEquals("تمّ", tokenizedText[0]);
    Assertions.assertEquals("طُوّر", tokenizedText[1]);
    Assertions.assertEquals("المشروع", tokenizedText[2]);
    Assertions.assertEquals("بنجاح", tokenizedText[3]);
    Assertions.assertEquals(".", tokenizedText[4]);
  }

  /**
   * Under the default {@link WhitespaceMode#UNICODE}, the next line control ({@code U+0085})
   * separates tokens like whitespace, and the information separators ({@code U+001C..U+001F})
   * do not.
   */
  @Test
  void testNextLineControlIsWhitespaceByDefault() {
    char nextLine = (char) 0x0085;
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        mTokenizer.tokenize("a" + nextLine + "b"));

    char infoSeparator = (char) 0x001C;
    Assertions.assertArrayEquals(new String[] {"a", String.valueOf(infoSeparator), "b"},
        mTokenizer.tokenize("a" + infoSeparator + "b"));
  }

  /**
   * Under {@link WhitespaceMode#LEGACY}, the information separators ({@code U+001C..U+001F})
   * separate tokens like whitespace, and the next line control ({@code U+0085}) does not,
   * matching OpenNLP 1.x/2.x tokenizer models.
   */
  @Test
  void testInformationSeparatorsAreWhitespaceUnderLegacyMode() {
    WhitespaceMode.setActive(WhitespaceMode.LEGACY);

    char infoSeparator = (char) 0x001C;
    Assertions.assertArrayEquals(new String[] {"a", "b"},
        mTokenizer.tokenize("a" + infoSeparator + "b"));

    char nextLine = (char) 0x0085;
    Assertions.assertArrayEquals(new String[] {"a", String.valueOf(nextLine), "b"},
        mTokenizer.tokenize("a" + nextLine + "b"));
  }

  static Stream<Arguments> lineBreaks() {
    return Stream.of(
        Arguments.of('\n', new String[] {"a", "b"}, new String[] {"a", "\n", "b"}),
        Arguments.of('\u000B', new String[] {"a", "b"}, new String[] {"a", "b"}),
        Arguments.of('\f', new String[] {"a", "b"}, new String[] {"a", "b"}),
        Arguments.of('\r', new String[] {"a", "b"}, new String[] {"a", "\r", "b"}),
        Arguments.of('\u0085', new String[] {"a", "\u0085", "b"}, new String[] {"a", "\u0085", "b"}),
        Arguments.of('\u2028', new String[] {"a", "b"}, new String[] {"a", "b"}),
        Arguments.of('\u2029', new String[] {"a", "b"}, new String[] {"a", "b"}));
  }

  @ParameterizedTest
  @MethodSource("lineBreaks")
  void testLineBreaksInUnicodeAndLegacyModes(
      char lineBreak, String[] legacyDropping, String[] legacyKeeping) {
    String sep = String.valueOf(lineBreak);
    String input = "a" + sep + "b";

    mTokenizer.setKeepNewLines(false);
    Assertions.assertArrayEquals(new String[] {"a", "b"}, mTokenizer.tokenize(input));
    mTokenizer.setKeepNewLines(true);
    Assertions.assertArrayEquals(new String[] {"a", sep, "b"}, mTokenizer.tokenize(input));

    WhitespaceMode.setActive(WhitespaceMode.LEGACY);
    mTokenizer.setKeepNewLines(false);
    Assertions.assertArrayEquals(legacyDropping, mTokenizer.tokenize(input));
    mTokenizer.setKeepNewLines(true);
    Assertions.assertArrayEquals(legacyKeeping, mTokenizer.tokenize(input));
  }
}
