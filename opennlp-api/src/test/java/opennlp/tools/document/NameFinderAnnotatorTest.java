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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link NameFinderAnnotator} maps token-index mentions to character spans on
 * the original text and clears the finder's adaptive data per document.
 */
public class NameFinderAnnotatorTest {

  @Test
  void testTokenIndexSpansBecomeCharacterSpans() {
    final AtomicInteger cleared = new AtomicInteger();
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        // "New York" as a two-token person-free location mention
        return new Span[] {new Span(1, 3, "location")};
      }

      @Override
      public void clearAdaptiveData() {
        cleared.incrementAndGet();
      }
    };

    final Document document = Document.of("in New York today")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 17), "in New York today")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "in"),
            new Annotation<>(new Span(3, 6), "New"),
            new Annotation<>(new Span(7, 11), "York"),
            new Annotation<>(new Span(12, 17), "today")));

    final Document annotated = new NameFinderAnnotator(finder).annotate(document);
    final List<Annotation<String>> entities = annotated.get(Layers.ENTITIES);
    assertEquals(1, entities.size());
    assertEquals(new Span(3, 11, "location"), entities.get(0).span());
    assertEquals("location", entities.get(0).value());
    assertEquals("New York",
        entities.get(0).span().getCoveredText(annotated.text()).toString());
    assertEquals(1, cleared.get());
  }

  /**
   * Verifies that a document carrying sentences but no token layer is rejected with a
   * message naming the token layer, so the token check is exercised on its own rather
   * than being shadowed by the sentence check.
   */
  @Test
  void testMissingTokenLayerThrows() {
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    final Document document = Document.of("no tokens")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 9), "no tokens")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new NameFinderAnnotator(finder).annotate(document));
    assertEquals("document lacks the required layer tokens<String>", e.getMessage());
  }

  /**
   * Verifies that a mention the finder returns without a type is recorded with the
   * {@link NameFinderAnnotator#UNTYPED} label, and that the label is the name-sample
   * default type, so downstream consumers can rely on the two being interchangeable.
   */
  @Test
  void testUntypedMentionRecordedAsUntyped() {
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[] {new Span(0, 1)};
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    final Document document = Document.of("Ana runs.")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 9), "Ana runs.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 9), "runs.")));

    final Document annotated = new NameFinderAnnotator(finder).annotate(document);
    final List<Annotation<String>> entities = annotated.get(Layers.ENTITIES);
    assertEquals(1, entities.size());
    assertEquals(NameSample.DEFAULT_TYPE, NameFinderAnnotator.UNTYPED);
    assertEquals(NameFinderAnnotator.UNTYPED, entities.get(0).value());
    assertEquals(new Span(0, 3, NameFinderAnnotator.UNTYPED), entities.get(0).span());
    assertEquals(NameFinderAnnotator.UNTYPED, entities.get(0).span().getType());
  }

  /**
   * Verifies that a mention whose token indices reach beyond its sentence's tokens is
   * rejected loudly instead of silently taking its character span from the following
   * sentence's tokens, and that the adaptive data is still cleared on that failure.
   */
  @Test
  void testMentionOutsideSentenceTokensFailsLoud() {
    final AtomicInteger cleared = new AtomicInteger();
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        // two tokens in the sentence, but the mention claims three
        return new Span[] {new Span(0, 3, "person")};
      }

      @Override
      public void clearAdaptiveData() {
        cleared.incrementAndGet();
      }
    };
    final Document document = Document.of("Ana runs. Bob sits.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 9), "Ana runs."),
            new Annotation<>(new Span(10, 19), "Bob sits.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 9), "runs."),
            new Annotation<>(new Span(10, 13), "Bob"),
            new Annotation<>(new Span(14, 19), "sits.")));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new NameFinderAnnotator(finder).annotate(document));
    assertEquals("finder returned mention [0..3) person outside the sentence's 2 tokens",
        e.getMessage());
    assertEquals(1, cleared.get());
  }

  /**
   * Verifies that a token lying outside every sentence is rejected loudly and that the
   * adaptive data is still cleared on that failure, so a rejected document cannot leak
   * finder state into the next one.
   */
  @Test
  void testTokenOutsideEverySentenceThrowsAndStillClears() {
    final AtomicInteger cleared = new AtomicInteger();
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
        cleared.incrementAndGet();
      }
    };
    final Document document = Document.of("Ana runs. Bob")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 9), "Ana runs.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 9), "runs."),
            new Annotation<>(new Span(10, 13), "Bob")));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new NameFinderAnnotator(finder).annotate(document));
    assertEquals("token at [10..13) lies outside every sentence", e.getMessage());
    assertEquals(1, cleared.get());
  }

  /**
   * Verifies that the finder is invoked once per sentence with exactly that sentence's
   * tokens, that sentence-local mention indices are mapped through the sentence's first
   * token position into document character spans, and that the adaptive data is cleared
   * exactly once after the whole document.
   */
  @Test
  void testFindsPerSentenceAndMapsSentenceLocalIndices() {
    final List<List<String>> calls = new ArrayList<>();
    final AtomicInteger cleared = new AtomicInteger();
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        calls.add(List.of(tokens));
        // the first token of every sentence is a person mention, in sentence-local indices
        return new Span[] {new Span(0, 1, "person")};
      }

      @Override
      public void clearAdaptiveData() {
        cleared.incrementAndGet();
      }
    };

    final Document document = Document.of("Ana runs. Bob sits.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 9), "Ana runs."),
            new Annotation<>(new Span(10, 19), "Bob sits.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 9), "runs."),
            new Annotation<>(new Span(10, 13), "Bob"),
            new Annotation<>(new Span(14, 19), "sits.")));

    final Document annotated = new NameFinderAnnotator(finder).annotate(document);

    assertEquals(List.of(
        List.of("Ana", "runs."),
        List.of("Bob", "sits.")), calls);

    final List<Annotation<String>> entities = annotated.get(Layers.ENTITIES);
    assertEquals(2, entities.size());
    assertEquals(new Span(0, 3, "person"), entities.get(0).span());
    assertEquals(new Span(10, 13, "person"), entities.get(1).span());
    assertEquals("Bob",
        entities.get(1).span().getCoveredText(annotated.text()).toString());
    assertEquals(1, cleared.get());
  }

  /**
   * Verifies that the annotator declares both the sentence layer and the token layer as
   * required, so a pipeline without a sentence step fails at build time.
   */
  @Test
  void testRequiresSentencesAndTokens() {
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS),
        new NameFinderAnnotator(finder).requires());
  }

  /**
   * Verifies that present-but-empty sentence and token layers yield a present-but-empty
   * entity layer without invoking the finder, rather than an exception.
   */
  @Test
  void testEmptyPresentLayersYieldEmptyEntityLayer() {
    final AtomicInteger found = new AtomicInteger();
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        found.incrementAndGet();
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    final Document document = Document.of("")
        .with(Layers.SENTENCES, List.of())
        .with(Layers.TOKENS, List.of());

    final Document annotated = new NameFinderAnnotator(finder).annotate(document);

    assertTrue(annotated.layers().contains(Layers.ENTITIES));
    assertTrue(annotated.get(Layers.ENTITIES).isEmpty());
    assertEquals(0, found.get());
  }

  /**
   * Verifies that a document without a sentence layer is rejected with a message naming
   * the missing layer.
   */
  @Test
  void testAbsentSentenceLayerThrowsWithExactMessage() {
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    final Document document = Document.of("Ana")
        .with(Layers.TOKENS, List.of(new Annotation<>(new Span(0, 3), "Ana")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new NameFinderAnnotator(finder).annotate(document));
    assertEquals("document lacks the required layer sentences<String>", e.getMessage());
  }
}
