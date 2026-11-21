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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.ResetWhitespaceMode;
import opennlp.tools.util.Span;
import opennlp.tools.util.WhitespaceMode;

import static opennlp.tools.util.WhitespaceMode.LEGACY;
import static opennlp.tools.util.WhitespaceMode.UNICODE;

/**
 * Checks complete token spans at line-break boundaries in both whitespace modes.
 */
@ResetWhitespaceMode
class TokenizerLineBreakBoundaryTest {

  /**
   * Checks offsets and token text against explicit expectations for each tokenizer.
   *
   * @param mode The whitespace mode.
   * @param keep Whether to retain line breaks.
   * @param input The text to tokenize.
   * @param simpleSpans Expected spans for the character-class tokenizer.
   * @param whitespaceSpans Expected spans for the whitespace tokenizer.
   */
  @ParameterizedTest(name = "{index}: {0}, keep={1}")
  @MethodSource({"unicodeBoundaries", "legacyBoundaries"})
  void testLineBreakBoundaries(WhitespaceMode mode, boolean keep, String input,
                               Span[] simpleSpans, Span[] whitespaceSpans) {
    WhitespaceMode.setActive(mode);
    SimpleTokenizer simple = SimpleTokenizer.newInstance(keep);
    WhitespaceTokenizer whitespace = WhitespaceTokenizer.newInstance(keep);

    Assertions.assertAll(
        () -> Assertions.assertArrayEquals(simpleSpans, simple.tokenizePos(input)),
        () -> Assertions.assertArrayEquals(Span.spansToStrings(simpleSpans, input),
            simple.tokenize(input)),
        () -> Assertions.assertArrayEquals(whitespaceSpans, whitespace.tokenizePos(input)),
        () -> Assertions.assertArrayEquals(Span.spansToStrings(whitespaceSpans, input),
            whitespace.tokenize(input)));
  }

  /**
   * Supplies explicit UNICODE expectations, retaining all seven line-break characters.
   *
   * @return Inputs, settings and expected spans.
   */
  static Stream<Arguments> unicodeBoundaries() {
    return Stream.of(
        boundary(UNICODE, true, "\nab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\nab", 1, 3),
        boundary(UNICODE, true, "\rab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\rab", 1, 3),
        boundary(UNICODE, true, "\u000Bab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\u000Bab", 1, 3),
        boundary(UNICODE, true, "\fab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\fab", 1, 3),
        boundary(UNICODE, true, "\u0085ab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\u0085ab", 1, 3),
        boundary(UNICODE, true, "\u2028ab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\u2028ab", 1, 3),
        boundary(UNICODE, true, "\u2029ab", 0, 1, 1, 3),
        boundary(UNICODE, false, "\u2029ab", 1, 3),
        boundary(UNICODE, true, "ab \n cd", 0, 2, 3, 4, 5, 7),
        boundary(UNICODE, false, "ab \n cd", 0, 2, 5, 7),
        boundary(UNICODE, true, "ab \t\r\n cd", 0, 2, 4, 5, 5, 6, 7, 9),
        boundary(UNICODE, false, "ab \t\r\n cd", 0, 2, 7, 9),
        boundary(UNICODE, true, "ab \u000B cd", 0, 2, 3, 4, 5, 7),
        boundary(UNICODE, true, "ab \f cd", 0, 2, 3, 4, 5, 7),
        boundary(UNICODE, true, "ab \u0085 cd", 0, 2, 3, 4, 5, 7),
        boundary(UNICODE, true, "ab \u2028 cd", 0, 2, 3, 4, 5, 7),
        boundary(UNICODE, true, "ab \u2029 cd", 0, 2, 3, 4, 5, 7),
        boundary(UNICODE, true, "\r\n東京", 0, 1, 1, 2, 2, 4),
        boundary(UNICODE, false, "\r\n東京", 2, 4),
        boundary(UNICODE, true, "café\r\n", 0, 4, 4, 5, 5, 6),
        boundary(UNICODE, false, "café\r\n", 0, 4),
        boundary(UNICODE, true, "é \r\n中", 0, 1, 2, 3, 3, 4, 4, 5),
        boundary(UNICODE, false, "é \r\n中", 0, 1, 4, 5),
        boundary(UNICODE, true, "ab\n  \ncd", 0, 2, 2, 3, 5, 6, 6, 8),
        boundary(UNICODE, false, "ab\n  \ncd", 0, 2, 6, 8),
        boundary(UNICODE, true, "\n\u000B\f\r\u0085\u2028\u2029",
            0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7),
        boundary(UNICODE, false, "\n\u000B\f\r\u0085\u2028\u2029"),
        boundary(UNICODE, true, " \t "),
        boundary(UNICODE, false, " \t "),
        boundary(UNICODE, true, ""),
        boundary(UNICODE, false, ""),
        Arguments.of(UNICODE, true, "\uD83D\uDE00 \r\n猫",
            spans(0, 1, 1, 2, 3, 4, 4, 5, 5, 6), spans(0, 2, 3, 4, 4, 5, 5, 6)),
        Arguments.of(UNICODE, false, "\uD83D\uDE00 \r\n猫",
            spans(0, 1, 1, 2, 5, 6), spans(0, 2, 5, 6)));
  }

  /**
   * Supplies explicit LEGACY expectations, retaining only CR/LF as line breaks.
   * NEL remains an ordinary character with tokenizer-specific token boundaries.
   *
   * @return Inputs, settings and expected spans.
   */
  static Stream<Arguments> legacyBoundaries() {
    return Stream.of(
        boundary(LEGACY, true, "\nab", 0, 1, 1, 3),
        boundary(LEGACY, false, "\nab", 1, 3),
        boundary(LEGACY, true, "\rab", 0, 1, 1, 3),
        boundary(LEGACY, false, "\rab", 1, 3),
        boundary(LEGACY, true, "\u000Bab", 1, 3),
        boundary(LEGACY, false, "\u000Bab", 1, 3),
        boundary(LEGACY, true, "\fab", 1, 3),
        boundary(LEGACY, false, "\fab", 1, 3),
        Arguments.of(LEGACY, true, "\u0085ab", spans(0, 1, 1, 3), spans(0, 3)),
        Arguments.of(LEGACY, false, "\u0085ab", spans(0, 1, 1, 3), spans(0, 3)),
        boundary(LEGACY, true, "\u2028ab", 1, 3),
        boundary(LEGACY, false, "\u2028ab", 1, 3),
        boundary(LEGACY, true, "\u2029ab", 1, 3),
        boundary(LEGACY, false, "\u2029ab", 1, 3),
        boundary(LEGACY, true, "ab \n cd", 0, 2, 3, 4, 5, 7),
        boundary(LEGACY, false, "ab \n cd", 0, 2, 5, 7),
        boundary(LEGACY, true, "ab \t\r\n cd", 0, 2, 4, 5, 5, 6, 7, 9),
        boundary(LEGACY, false, "ab \t\r\n cd", 0, 2, 7, 9),
        boundary(LEGACY, true, "ab \u000B cd", 0, 2, 5, 7),
        boundary(LEGACY, true, "ab \f cd", 0, 2, 5, 7),
        boundary(LEGACY, true, "ab \u0085 cd", 0, 2, 3, 4, 5, 7),
        boundary(LEGACY, true, "ab \u2028 cd", 0, 2, 5, 7),
        boundary(LEGACY, true, "ab \u2029 cd", 0, 2, 5, 7),
        boundary(LEGACY, true, "\r\n東京", 0, 1, 1, 2, 2, 4),
        boundary(LEGACY, false, "\r\n東京", 2, 4),
        boundary(LEGACY, true, "café\r\n", 0, 4, 4, 5, 5, 6),
        boundary(LEGACY, false, "café\r\n", 0, 4),
        boundary(LEGACY, true, "é \r\n中", 0, 1, 2, 3, 3, 4, 4, 5),
        boundary(LEGACY, false, "é \r\n中", 0, 1, 4, 5),
        boundary(LEGACY, true, "ab\n  \ncd", 0, 2, 2, 3, 5, 6, 6, 8),
        boundary(LEGACY, false, "ab\n  \ncd", 0, 2, 6, 8),
        boundary(LEGACY, true, "\r\n", 0, 1, 1, 2),
        boundary(LEGACY, false, "\r\n"),
        boundary(LEGACY, true, "\u000B\f\u2028\u2029"),
        boundary(LEGACY, false, "\u000B\f\u2028\u2029"),
        boundary(LEGACY, true, "\u0085\u0085", 0, 2),
        boundary(LEGACY, false, "\u0085\u0085", 0, 2),
        boundary(LEGACY, true, " \t "),
        boundary(LEGACY, false, " \t "),
        boundary(LEGACY, true, ""),
        boundary(LEGACY, false, ""),
        Arguments.of(LEGACY, true, "\uD83D\uDE00 \r\n猫",
            spans(0, 1, 1, 2, 3, 4, 4, 5, 5, 6), spans(0, 2, 3, 4, 4, 5, 5, 6)),
        Arguments.of(LEGACY, false, "\uD83D\uDE00 \r\n猫",
            spans(0, 1, 1, 2, 5, 6), spans(0, 2, 5, 6)));
  }

  /**
   * Supplies a table row when both tokenizers have the same expected spans.
   *
   * @param mode The whitespace mode.
   * @param keep Whether to retain line breaks.
   * @param input The text to tokenize.
   * @param offsets Explicit start and exclusive end pairs.
   * @return Arguments with the expected spans for both tokenizers.
   */
  private static Arguments boundary(WhitespaceMode mode, boolean keep, String input, int... offsets) {
    Span[] expected = spans(offsets);
    return Arguments.of(mode, keep, input, expected, expected);
  }

  /**
   * Converts explicit start and exclusive end pairs to spans.
   *
   * @param offsets Alternating start and exclusive end offsets.
   * @return The expected spans.
   */
  private static Span[] spans(int... offsets) {
    Span[] result = new Span[offsets.length / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = new Span(offsets[2 * i], offsets[2 * i + 1]);
    }
    return result;
  }
}
