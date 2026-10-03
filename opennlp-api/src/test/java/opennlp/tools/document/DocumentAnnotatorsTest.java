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

package opennlp.tools.document;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link DocumentAnnotators} support methods directly: the required-layer
 * and alignment checks' exact rejection messages, the per-sentence walk's slicing,
 * skipping, and loud rejection of a token outside every sentence, the value gather,
 * and the token-to-character span mapping. The adapter tests exercise the same
 * behavior through the annotators; this class pins the helpers as public API on their
 * own.
 */
public class DocumentAnnotatorsTest {

  @Test
  void testRequireLayersAcceptsPresentLayers() {
    final Document document = Document.of("the")
        .with(Layers.SENTENCES, List.of())
        .with(Layers.TOKENS, List.of());
    DocumentAnnotators.requireLayers(document, Layers.SENTENCES, Layers.TOKENS);
    DocumentAnnotators.requireLayers(document);
  }

  @Test
  void testRequireLayersRejectsNullDocument() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.requireLayers(null, Layers.TOKENS));
    assertEquals("document must not be null", e.getMessage());
  }

  /**
   * Verifies that an absent layer is rejected with the shared message naming the first
   * absent layer in the order the caller listed them.
   */
  @Test
  void testRequireLayersNamesTheFirstAbsentLayer() {
    final Document document = Document.of("the").with(Layers.TOKENS, List.of());
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.requireLayers(document,
            Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS));
    assertEquals("document lacks the required layer opennlp:sentences<String>",
        e.getMessage());
  }

  /**
   * Verifies the walk contract: each sentence receives the contiguous run of tokens its
   * span encloses with the run's first token layer position, and a sentence without
   * tokens is skipped rather than reported as an empty run.
   */
  @Test
  void testForEachSentenceSlicesContiguousRuns() {
    final List<Annotation<String>> sentences = List.of(
        new Annotation<>(new Span(0, 9), "Ana runs."),
        new Annotation<>(new Span(10, 11), "!"),
        new Annotation<>(new Span(12, 21), "Bob sits."));
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 3), "Ana"),
        new Annotation<>(new Span(4, 9), "runs."),
        new Annotation<>(new Span(12, 15), "Bob"),
        new Annotation<>(new Span(16, 21), "sits."));

    final List<Integer> firsts = new ArrayList<>();
    final List<List<String>> runs = new ArrayList<>();
    DocumentAnnotators.forEachSentence(sentences, tokens, (first, words) -> {
      firsts.add(first);
      runs.add(List.of(words));
    });

    assertEquals(List.of(0, 2), firsts);
    assertEquals(List.of(
        List.of("Ana", "runs."),
        List.of("Bob", "sits.")), runs);
  }

  @Test
  void testForEachSentenceRejectsTokenOutsideEverySentence() {
    final List<Annotation<String>> sentences = List.of(
        new Annotation<>(new Span(0, 9), "Ana runs."));
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 3), "Ana"),
        new Annotation<>(new Span(4, 9), "runs."),
        new Annotation<>(new Span(10, 13), "Bob"));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.forEachSentence(sentences, tokens, (first, words) -> {
        }));
    assertEquals("token at [10..13) lies outside every sentence", e.getMessage());
  }

  @Test
  void testForEachSentenceOverEmptyLayersConsumesNothing() {
    final List<List<String>> runs = new ArrayList<>();
    DocumentAnnotators.forEachSentence(List.of(), List.of(),
        (first, words) -> runs.add(List.of(words)));
    assertTrue(runs.isEmpty());
  }

  @Test
  void testRequireAlignedAcceptsEqualSizes() {
    final Document document = Document.of("Ana runs")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 8), "runs")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 3), "NNP"),
            new Annotation<>(new Span(4, 8), "VBZ")));
    DocumentAnnotators.requireAligned(document, Layers.TOKENS, Layers.POS_TAGS);
  }

  @Test
  void testRequireAlignedAcceptsEmptyLayers() {
    final Document document = Document.of("")
        .with(Layers.TOKENS, List.of())
        .with(Layers.POS_TAGS, List.of());
    DocumentAnnotators.requireAligned(document, Layers.TOKENS, Layers.POS_TAGS);
  }

  @Test
  void testRequireAlignedRejectsDifferentSizes() {
    final Document document = Document.of("Ana runs")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 8), "runs")))
        .with(Layers.POS_TAGS, List.of(new Annotation<>(new Span(0, 3), "NNP")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.requireAligned(document, Layers.TOKENS, Layers.POS_TAGS));
    assertEquals("document needs aligned " + Layers.TOKENS + " and " + Layers.POS_TAGS
        + " layers", e.getMessage());
  }

  /**
   * Verifies that an absent layer is reported as absent, not as misaligned, and that two
   * absent layers do not pass as aligned.
   */
  @Test
  void testRequireAlignedRejectsAbsentLayers() {
    final Document oneLayer = Document.of("Ana runs")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 8), "runs")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.requireAligned(oneLayer, Layers.TOKENS, Layers.POS_TAGS));
    assertEquals("document lacks the required layer " + Layers.POS_TAGS, e.getMessage());

    final Document noLayers = Document.of("Ana runs");
    assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.requireAligned(noLayers, Layers.TOKENS, Layers.POS_TAGS));
  }

  @Test
  void testRequireAlignedRejectsNullDocument() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.requireAligned(null, Layers.TOKENS, Layers.POS_TAGS));
    assertEquals("document must not be null", e.getMessage());
  }

  /**
   * Verifies that the gather returns the run's values in layer order, including values
   * outside the Basic Multilingual Plane unchanged, and an empty array for an empty run.
   */
  @Test
  void testValuesGathersTheRun() {
    final List<Annotation<String>> layer = List.of(
        new Annotation<>(new Span(0, 1), "a"),
        new Annotation<>(new Span(2, 4), "\uD83D\uDE00"),
        new Annotation<>(new Span(5, 6), "c"));
    assertArrayEquals(new String[] {"\uD83D\uDE00", "c"},
        DocumentAnnotators.values(layer, 1, 2));
    assertArrayEquals(new String[] {"a", "\uD83D\uDE00", "c"},
        DocumentAnnotators.values(layer, 0, 3));
    assertArrayEquals(new String[0], DocumentAnnotators.values(layer, 3, 0));
  }

  @ParameterizedTest(name = "run of {1} at {0}")
  @CsvSource({"-1, 1", "0, -1", "2, 2", "4, 0", "1, " + Integer.MAX_VALUE})
  void testValuesRejectsRunOutsideTheLayer(int first, int count) {
    final List<Annotation<String>> layer = List.of(
        new Annotation<>(new Span(0, 1), "a"),
        new Annotation<>(new Span(2, 3), "b"),
        new Annotation<>(new Span(4, 5), "c"));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.values(layer, first, count));
    assertEquals("run of " + count + " annotations at " + first
        + " lies outside the layer's 3 annotations", e.getMessage());
  }

  @Test
  void testValuesRejectsNullLayer() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.values(null, 0, 0));
    assertEquals("layer must not be null", e.getMessage());
  }

  /**
   * Verifies that a sentence-relative span is shifted by the sentence's first token and
   * resolved to the characters from its first token's start to its last token's end.
   */
  @ParameterizedTest(name = "span [{0}..{1}) maps to [{2}..{3})")
  @CsvSource({"0, 1, 4, 7", "1, 3, 8, 17", "0, 3, 4, 17", "2, 3, 13, 17"})
  void testToCharacterSpanMapsTokenRun(int start, int end, int charStart, int charEnd) {
    final Span covered = DocumentAnnotators.toCharacterSpan(sentenceTokens(), 1, 3,
        new Span(start, end, "type"), "finder returned mention");
    assertEquals(new Span(charStart, charEnd), covered);
  }

  @ParameterizedTest(name = "span [{0}..{1})")
  @CsvSource({"0, 4", "3, 4", "2, 2", "3, 3"})
  void testToCharacterSpanRejectsSpanOutsideSentence(int start, int end) {
    final Span span = new Span(start, end, "NP");
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.toCharacterSpan(sentenceTokens(), 1, 3, span,
            "chunker returned chunk"));
    assertEquals("chunker returned chunk " + span + " outside the sentence's 3 tokens",
        e.getMessage());
  }

  @Test
  void testToCharacterSpanRejectsEmptySpanWithExactMessage() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.toCharacterSpan(sentenceTokens(), 1, 3, new Span(0, 0),
            "finder returned mention"));
    assertEquals("finder returned mention [0..0) outside the sentence's 3 tokens",
        e.getMessage());
  }

  @ParameterizedTest(name = "sentence of {1} at {0}")
  @CsvSource({"-1, 1", "2, 3", "0, -1", "1, " + Integer.MAX_VALUE})
  void testToCharacterSpanRejectsSentenceOutsideTokens(int first, int count) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.toCharacterSpan(sentenceTokens(), first, count,
            new Span(0, 1), "finder returned mention"));
    assertEquals("sentence of " + count + " tokens at " + first
        + " lies outside the token layer's 4 tokens", e.getMessage());
  }

  @Test
  void testToCharacterSpanRejectsNullArguments() {
    final List<Annotation<String>> tokens = sentenceTokens();
    final Span span = new Span(0, 1);
    assertEquals("tokens must not be null", assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.toCharacterSpan(null, 0, 1, span, "x")).getMessage());
    assertEquals("span must not be null", assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.toCharacterSpan(tokens, 0, 1, null, "x")).getMessage());
    assertEquals("source must not be null", assertThrows(IllegalArgumentException.class,
        () -> DocumentAnnotators.toCharacterSpan(tokens, 0, 1, span, null)).getMessage());
  }

  /**
   * {@return the token layer of "Hi. Ana sees Bob!": a one-token sentence followed by
   * a three-token sentence starting at position 1}
   */
  private static List<Annotation<String>> sentenceTokens() {
    return List.of(
        new Annotation<>(new Span(0, 3), "Hi."),
        new Annotation<>(new Span(4, 7), "Ana"),
        new Annotation<>(new Span(8, 12), "sees"),
        new Annotation<>(new Span(13, 17), "Bob!"));
  }
}
