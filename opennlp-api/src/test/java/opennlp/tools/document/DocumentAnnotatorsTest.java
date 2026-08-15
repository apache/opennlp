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

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link DocumentAnnotators} support methods directly: the required-layer
 * check's exact rejection messages and the per-sentence walk's slicing, skipping, and
 * loud rejection of a token outside every sentence. The adapter tests exercise the same
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
}
