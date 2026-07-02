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
package opennlp.tools.sentdetect;

import java.io.IOException;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.util.Span;

/**
 * Characterization tests for the end-of-sentence position to {@link Span} mapping of
 * {@link SentenceDetectorME#sentPosDetect(CharSequence)} (OPENNLP-205). These pin the mapping
 * behavior, including edge cases, so the mapping can be refactored without silently changing
 * results; every expectation was captured from the behavior of the pre-refactoring implementation.
 */
public class SentenceDetectorMESpanMappingTest extends AbstractSentenceDetectorTest {

  private static final String SECOND = "There are many tests, this is the second.";

  private static SentenceDetectorME tokenEnd;
  private static SentenceDetectorME noTokenEnd;

  @BeforeAll
  static void prepareResources() throws IOException {
    Dictionary abb = loadAbbDictionary(Locale.ENGLISH);
    tokenEnd = new SentenceDetectorME(
        train(new SentenceDetectorFactory("eng", true, abb, null), Locale.ENGLISH));
    noTokenEnd = new SentenceDetectorME(
        train(new SentenceDetectorFactory("eng", false, abb, null), Locale.ENGLISH));
  }

  private static void assertSpans(SentenceDetectorME sd, String input, Span... expected) {
    Span[] actual = sd.sentPosDetect(input);
    Assertions.assertEquals(expected.length, actual.length,
        () -> "span count for: " + input);
    for (int i = 0; i < expected.length; i++) {
      Assertions.assertEquals(expected[i].getStart(), actual[i].getStart(), "start of span " + i);
      Assertions.assertEquals(expected[i].getEnd(), actual[i].getEnd(), "end of span " + i);
    }
    // The probability list must stay aligned with the returned spans, whatever the mapping drops.
    Assertions.assertEquals(actual.length, sd.probs().length,
        () -> "probs alignment for: " + input);
  }

  @Test
  void twoSentencesMapToExactSpans() {
    String input = "This is a test. " + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 15), new Span(16, 57));
    assertSpans(noTokenEnd, input, new Span(0, 15), new Span(16, 57));
  }

  @Test
  void leadingAndTrailingWhitespaceIsExcludedFromSpans() {
    String input = "   This is a test.   " + SECOND + "   ";
    assertSpans(tokenEnd, input, new Span(3, 18), new Span(21, 62));
    assertSpans(noTokenEnd, input, new Span(3, 18), new Span(21, 62));
  }

  @Test
  void multiCharacterDelimiterRunStaysWithItsSentence() {
    String input = "This is great!!! " + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 16), new Span(17, 58));
    assertSpans(noTokenEnd, input, new Span(0, 16), new Span(17, 58));
  }

  @Test
  void noBreakSpaceBetweenSentencesSplitsAndTrims() {
    // NBSP is SPACE_SEPARATOR, recognized as whitespace by the mapping both before and after the
    // OPENNLP-205 refactoring; the second span must start at the word, not the NBSP.
    String input = "This is a test.\u00A0" + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 15), new Span(16, 57));
  }

  @Test
  void carriageReturnLineFeedBetweenSentences() {
    String input = "This is a test.\r\n" + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 15), new Span(17, 58));
  }

  @Test
  void whitespaceOnlyAndEmptyInputYieldNoSpans() {
    assertSpans(tokenEnd, "   ");
    assertSpans(tokenEnd, "");
  }

  @Test
  void inputWithoutEndOfSentenceCharactersIsOneTrimmedSpan() {
    assertSpans(tokenEnd, "  no end of sentence marker here  ", new Span(2, 32));
  }

  @Test
  void delimiterIslandsRemainSeparateSpans() {
    // Pinned, not endorsed: a free-standing delimiter between sentences is reported as its own
    // one-character span with its own probability. Changing this would change public output, so
    // the OPENNLP-205 mapping refactoring must preserve it.
    String input = "This is a test. . " + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 15), new Span(16, 17), new Span(18, 59));
    assertSpans(noTokenEnd, input, new Span(0, 15), new Span(16, 17), new Span(18, 59));
    String runs = "This is a test.  .  .  " + SECOND;
    assertSpans(tokenEnd, runs,
        new Span(0, 15), new Span(17, 18), new Span(20, 21), new Span(23, 64));
  }

  @Test
  void nextLineControlBetweenSpacedSentences() {
    // U+0085 NEL is Unicode White_Space. The pre-refactoring mapping did not treat it as
    // whitespace (StringUtil.isWhitespace misses it), so the second span starts at the NEL
    // and carries it as leading content instead of starting at the word. The OPENNLP-205
    // refactoring moves this span start onto the word.
    String input = "This is a test. \u0085 " + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 15), new Span(16, 59));
  }

  @Test
  void bareNextLineControlDoesNotSplit() {
    // With no ordinary space around the delimiter, the model's context (unchanged by
    // OPENNLP-205; feature generation must stay stable for existing models) does not produce a
    // split here, so the text stays one span covering both parts.
    String input = "This is a test.\u0085" + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 57));
  }
}
