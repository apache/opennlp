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
import java.util.ArrayList;
import java.util.List;
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
  void nextLineControlBetweenSpacedSentencesIsTrimmed() {
    // U+0085 NEL is Unicode White_Space. The pre-refactoring mapping missed it
    // (StringUtil.isWhitespace does not cover it), so the second span used to start at the NEL
    // and carry it as leading content; with the mapping on the CharClass Unicode White_Space set
    // (OPENNLP-205), the span starts at the word.
    String input = "This is a test. \u0085 " + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 15), new Span(18, 59));
  }

  @Test
  void bareNextLineControlDoesNotSplit() {
    // With no ordinary space around the delimiter, the model's context (unchanged by
    // OPENNLP-205; feature generation must stay stable for existing models) does not produce a
    // split here, so the text stays one span covering both parts.
    String input = "This is a test.\u0085" + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 57));
  }

  @Test
  void mapPositionsToSpansHandlesNoPositions() {
    java.util.List<Double> probs = new java.util.ArrayList<>();
    Assertions.assertEquals(0,
        SentenceDetectorME.mapPositionsToSpans("  \t ", new int[0], probs).length);
    Assertions.assertTrue(probs.isEmpty());
    Span[] whole = SentenceDetectorME.mapPositionsToSpans("  some text  ", new int[0], probs);
    Assertions.assertEquals(1, whole.length);
    Assertions.assertEquals(new Span(2, 11).getStart(), whole[0].getStart());
    Assertions.assertEquals(new Span(2, 11).getEnd(), whole[0].getEnd());
    Assertions.assertEquals(java.util.List.of(1d), probs);
  }

  @Test
  void mapPositionsToSpansDropsAWhitespaceOnlyCandidateWithItsProbability() {
    // The whitespace-only-candidate branch is not reachable through sentPosDetect's position
    // invariants, but the mapping guarantees alignment structurally: the span and its
    // probability are dropped together. The old implementation removed the probability by index
    // from a pre-sized array, which could misalign the pairing.
    java.util.List<Double> probs = new java.util.ArrayList<>(java.util.List.of(0.9d, 0.8d));
    Span[] spans = SentenceDetectorME.mapPositionsToSpans("ab  cd", new int[] {4, 4}, probs);
    Assertions.assertEquals(2, spans.length);
    Assertions.assertEquals(0, spans[0].getStart());
    Assertions.assertEquals(2, spans[0].getEnd());
    Assertions.assertEquals(0.9d, spans[0].getProb());
    Assertions.assertEquals(4, spans[1].getStart());
    Assertions.assertEquals(6, spans[1].getEnd());
    Assertions.assertEquals(1d, spans[1].getProb());
    Assertions.assertEquals(java.util.List.of(0.9d, 1d), probs);
  }

  @Test
  void mapPositionsToSpansTrimsTheFullUnicodeWhitespaceSet() {
    java.util.List<Double> probs = new java.util.ArrayList<>(java.util.List.of(0.7d));
    Span[] spans = SentenceDetectorME.mapPositionsToSpans(
        "One.\u0085\u00A0\u2028Two", new int[] {7}, probs);
    Assertions.assertEquals(2, spans.length);
    Assertions.assertEquals(0, spans[0].getStart());
    Assertions.assertEquals(4, spans[0].getEnd());
    Assertions.assertEquals(7, spans[1].getStart());
    Assertions.assertEquals(10, spans[1].getEnd());
  }

  @Test
  void informationSeparatorsAreContentNotWhitespace() {
    // Deliberate delta from the old StringUtil-based mapping: the U+001C..U+001F information
    // separators are not Unicode White_Space, so they are no longer trimmed from span edges.
    java.util.List<Double> probs = new java.util.ArrayList<>(java.util.List.of(0.7d));
    Span[] spans = SentenceDetectorME.mapPositionsToSpans("A.\u001C B", new int[] {4}, probs);
    Assertions.assertEquals(2, spans.length);
    Assertions.assertEquals(0, spans[0].getStart());
    Assertions.assertEquals(3, spans[0].getEnd()); // includes the separator control
  }

  @Test
  void mapPositionsToSpansClearsStaleProbsInTheZeroPositionsBranch() {
    // The probs contract holds in every branch: with no positions, entries a caller left in the
    // list are cleared so the list mirrors the returned spans instead of keeping stale values.
    List<Double> stale = new ArrayList<>(List.of(0.4d, 0.3d));
    Span[] spans = SentenceDetectorME.mapPositionsToSpans("  some text  ", new int[0], stale);
    Assertions.assertEquals(1, spans.length);
    Assertions.assertEquals(List.of(1d), stale);

    // Blank input: no spans, and the stale entries are gone as well.
    List<Double> blankStale = new ArrayList<>(List.of(0.4d));
    Assertions.assertEquals(0,
        SentenceDetectorME.mapPositionsToSpans(" \t ", new int[0], blankStale).length);
    Assertions.assertTrue(blankStale.isEmpty());
  }

  @Test
  void mapPositionsToSpansAttachesProbabilityOneInTheZeroPositionsBranch() {
    // Every returned span carries its probability; the whole-text span of the zero-positions
    // branch is no exception (it used to report the Span default of 0.0 via getProb()).
    List<Double> probs = new ArrayList<>();
    Span[] spans = SentenceDetectorME.mapPositionsToSpans("no end of sentence", new int[0], probs);
    Assertions.assertEquals(1, spans.length);
    Assertions.assertEquals(1d, spans[0].getProb());
    Assertions.assertEquals(List.of(1d), probs);
  }

  @Test
  void controlOnlyInputIsOneSpanOfContent() {
    // Deliberate delta, the whole-text counterpart of
    // informationSeparatorsAreContentNotWhitespace: information separators are content, so
    // control-only input no longer trims to nothing. The old mapping returned no spans here.
    List<Double> probs = new ArrayList<>();
    Span[] spans = SentenceDetectorME.mapPositionsToSpans("\u001C\u001C", new int[0], probs);
    Assertions.assertEquals(1, spans.length);
    Assertions.assertEquals(0, spans[0].getStart());
    Assertions.assertEquals(2, spans[0].getEnd());
    Assertions.assertEquals(1d, spans[0].getProb());
    Assertions.assertEquals(List.of(1d), probs);

    // The same input through the public API: no end-of-sentence characters, so the detector
    // takes the zero-positions branch and reports the control characters as one sentence span.
    assertSpans(tokenEnd, "\u001C\u001C", new Span(0, 2));
    Assertions.assertArrayEquals(new double[] {1d}, tokenEnd.probs());
  }

  @Test
  void mapPositionsToSpansPreservesSupplementaryCharactersAtSpanEdges() {
    // The trimming cursors scan by code point (CharClass discipline). Surrogate pairs at span
    // edges must survive intact; U+1D518 and U+1D51E are supplementary-plane letters.
    String frakturU = "\uD835\uDD18"; // MATHEMATICAL FRAKTUR CAPITAL U
    String frakturA = "\uD835\uDD1E"; // MATHEMATICAL FRAKTUR SMALL A

    List<Double> probs = new ArrayList<>();
    String noPositions = "  " + frakturU + "no end" + frakturA + "  ";
    Span[] spans = SentenceDetectorME.mapPositionsToSpans(noPositions, new int[0], probs);
    Assertions.assertEquals(1, spans.length);
    Assertions.assertEquals(frakturU + "no end" + frakturA,
        spans[0].getCoveredText(noPositions).toString());

    // A position between two sentences that start and end with supplementary characters.
    probs = new ArrayList<>(List.of(0.9d));
    String twoParts = frakturU + "one. " + frakturA + "two";
    Span[] parts = SentenceDetectorME.mapPositionsToSpans(twoParts, new int[] {7}, probs);
    Assertions.assertEquals(2, parts.length);
    Assertions.assertEquals(frakturU + "one.", parts[0].getCoveredText(twoParts).toString());
    Assertions.assertEquals(frakturA + "two", parts[1].getCoveredText(twoParts).toString());
    Assertions.assertEquals(List.of(0.9d, 1d), probs);
  }

  @Test
  void supplementaryCharactersFlowThroughTheDetectorUnharmed() {
    // End-to-end through the trained model: the whitespace cursors of the detection loop also
    // scan by code point, so a surrogate pair adjacent to the sentence boundary stays intact.
    String fraktur = "\uD835\uDD18\uD835\uDD1E"; // two supplementary letters as a word
    String input = "This is a " + fraktur + " test. " + SECOND;
    Span[] spans = tokenEnd.sentPosDetect(input);
    Assertions.assertEquals(2, spans.length);
    Assertions.assertEquals("This is a " + fraktur + " test.",
        spans[0].getCoveredText(input).toString());
    Assertions.assertEquals(SECOND, spans[1].getCoveredText(input).toString());
  }

  @Test
  void informationSeparatorGluedToTheDelimiterExtendsTheTokenEndBoundary() {
    // Detection-loop delta of the Unicode White_Space set (OPENNLP-205), pinned deliberately:
    // U+001C directly after the delimiter is content now. With useTokenEnd the token containing
    // the end-of-sentence character runs through "\u001CThere" up to the next real whitespace,
    // and the second sentence starts after that token; without useTokenEnd the second sentence
    // starts at the separator and carries it as leading content. The old StringUtil-based
    // cursors treated U+001C as whitespace and started the second sentence directly behind it
    // in both configurations. Feature generation is untouched; only the placement of the
    // accepted position moved.
    String input = "This is a test.\u001C" + SECOND;
    assertSpans(tokenEnd, input, new Span(0, 21), new Span(22, 57));
    assertSpans(noTokenEnd, input, new Span(0, 15), new Span(15, 57));
  }

  @Test
  void informationSeparatorInsideAbbreviationLikeTokenChangesTheCandidateSkip() {
    // Detection-loop delta, pinned deliberately: in "z.\u001Cb. x" the first delimiter sits in
    // front of non-whitespace content (U+001C is content now), so the delimiter-run skip
    // heuristic merges it into the multi-period token and only the second delimiter is scored.
    // The old cursors saw U+001C as whitespace and scored the first delimiter as well. The
    // model splits at the second delimiter, so the multi-period token stays one sentence and
    // "x" becomes the next; the separator is covered as content inside the first span.
    String input = "z.\u001Cb. x";
    assertSpans(tokenEnd, input, new Span(0, 5), new Span(6, 7));
    assertSpans(noTokenEnd, input, new Span(0, 5), new Span(6, 7));
  }
}
