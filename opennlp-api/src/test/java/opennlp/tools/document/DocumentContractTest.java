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

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the observable contract of the document container at its edges: key
 * equality, layer ordering, span boundary cases, list immutability, the exact rejection
 * messages, and the analyzer's build-time validation. Every expected value in this class
 * is asserted exactly so any behavioral drift is caught, not just gross breakage.
 */
public class DocumentContractTest {

  private static final LayerKey<String> WORDS = LayerKey.of("words", String.class);

  /**
   * Verifies that two independently created keys with the same id and the same type are
   * equal, hash alike, and therefore address the same layer, while remaining distinct
   * instances. This is what lets separately compiled producers agree on a layer without
   * sharing a constant.
   */
  @Test
  void testKeysWithSameIdAndTypeAddressTheSameLayer() {
    final LayerKey<String> first = LayerKey.of("words", String.class);
    final LayerKey<String> second = LayerKey.of("words", String.class);
    assertNotSame(first, second);
    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertEquals("words<String>", first.toString());

    final Document document = Document.of("the")
        .with(first, List.of(new Annotation<>(new Span(0, 3), "the")));
    // Reading through the other, equal key yields the very layer added above.
    assertEquals(1, document.get(second).size());
    assertEquals("the", document.get(second).get(0).value());
    // A duplicate add through the equal key is rejected like any duplicate.
    assertThrows(IllegalArgumentException.class, () -> document.with(second, List.of()));
  }

  /**
   * Verifies that two keys sharing an id but differing in value type are unequal and
   * denote two independent layers that can coexist on one document.
   */
  @Test
  void testKeysWithSameIdButDifferentTypesAreDifferentLayers() {
    final LayerKey<String> asString = LayerKey.of("marks", String.class);
    final LayerKey<Integer> asInteger = LayerKey.of("marks", Integer.class);
    assertNotEquals(asString, asInteger);

    final Document document = Document.of("ab")
        .with(asString, List.of(new Annotation<>(new Span(0, 1), "a")))
        .with(asInteger, List.of(new Annotation<>(new Span(1, 2), 7)));
    assertEquals(Set.of(asString, asInteger), document.layers());
    assertEquals("a", document.get(asString).get(0).value());
    assertEquals(7, document.get(asInteger).get(0).value());
  }

  /**
   * Verifies the per-key scope contract: a document-scoped layer carries span-less
   * values and round-trips them, a spanned annotation under a document-scoped key is
   * rejected with a message naming the layer, a span-less annotation under a
   * positional key is rejected likewise, and two keys differing only in scope are
   * unequal and never address the same layer.
   */
  @Test
  void testDocumentScopedLayersCarrySpanlessValues() {
    final LayerKey<String> language = LayerKey.document("language", String.class);
    assertEquals(LayerKey.Scope.DOCUMENT, language.scope());
    assertEquals(LayerKey.Scope.POSITIONAL, WORDS.scope());

    final Document document = Document.of("the dog")
        .with(language, List.of(Annotation.of("eng")));
    assertEquals(1, document.get(language).size());
    assertEquals("eng", document.get(language).get(0).value());
    assertNull(document.get(language).get(0).span());

    final IllegalArgumentException spanned = assertThrows(IllegalArgumentException.class,
        () -> Document.of("the").with(language,
            List.of(new Annotation<>(new Span(0, 3), "eng"))));
    assertEquals("document-scoped layer language<String> must not carry spans",
        spanned.getMessage());

    final IllegalArgumentException spanless = assertThrows(IllegalArgumentException.class,
        () -> Document.of("the").with(WORDS, List.of(Annotation.of("the"))));
    assertEquals("positional layer words<String> requires a span on every annotation",
        spanless.getMessage());

    final LayerKey<String> positionalTwin = LayerKey.of("language", String.class);
    assertNotEquals(language, positionalTwin);
    final Document both = Document.of("the")
        .with(language, List.of(Annotation.of("eng")))
        .with(positionalTwin, List.of(new Annotation<>(new Span(0, 3), "the")));
    assertEquals(2, both.layers().size());
  }

  /**
   * Verifies the gold-layer convention: a hand-annotated version of a layer lives
   * under the {@code gold:} prefixed key beside the produced layer, both are readable
   * independently, and the once-only rule keeps either from replacing the other.
   */
  @Test
  void testGoldLayerLivesBesideThePredictedLayer() {
    final LayerKey<String> gold = LayerKey.of("gold:" + Layers.TOKENS.id(), String.class);
    final Document document = Document.of("the dog")
        .with(Layers.TOKENS, List.of(new Annotation<>(new Span(0, 3), "the")))
        .with(gold, List.of(
            new Annotation<>(new Span(0, 3), "the"),
            new Annotation<>(new Span(4, 7), "dog")));
    assertEquals("gold:opennlp:tokens", gold.id());
    assertEquals(1, document.get(Layers.TOKENS).size());
    assertEquals(2, document.get(gold).size());
    assertThrows(IllegalArgumentException.class,
        () -> document.with(gold, List.of()));
  }

  /**
   * Verifies that a layer preserves the insertion order of its annotations: the
   * container does not sort by span, so a producer that wants span order must supply
   * span order.
   */
  @Test
  void testLayerPreservesInsertionOrder() {
    final Document document = Document.of("the dog")
        .with(WORDS, List.of(
            new Annotation<>(new Span(4, 7), "dog"),
            new Annotation<>(new Span(0, 3), "the")));
    final List<Annotation<String>> words = document.get(WORDS);
    assertEquals(2, words.size());
    assertEquals(new Span(4, 7), words.get(0).span());
    assertEquals("dog", words.get(0).value());
    assertEquals(new Span(0, 3), words.get(1).span());
    assertEquals("the", words.get(1).value());
  }

  /**
   * Verifies that several annotations may share one span within a layer, for example
   * alternative readings of the same region, and all of them are retained in order.
   */
  @Test
  void testAnnotationsWithIdenticalSpansAreAllRetained() {
    final Document document = Document.of("bank")
        .with(WORDS, List.of(
            new Annotation<>(new Span(0, 4), "institution"),
            new Annotation<>(new Span(0, 4), "riverside")));
    final List<Annotation<String>> words = document.get(WORDS);
    assertEquals(2, words.size());
    assertEquals("institution", words.get(0).value());
    assertEquals("riverside", words.get(1).value());
    assertEquals(words.get(0).span(), words.get(1).span());
  }

  /**
   * Verifies that zero-length spans are accepted anywhere within the bounds, including
   * at the very end of the text where start and end equal the text length.
   */
  @Test
  void testZeroLengthSpansAreAccepted() {
    final Document document = Document.of("ab")
        .with(WORDS, List.of(
            new Annotation<>(new Span(1, 1), "between"),
            new Annotation<>(new Span(2, 2), "at the end")));
    final List<Annotation<String>> words = document.get(WORDS);
    assertEquals(new Span(1, 1), words.get(0).span());
    assertEquals(new Span(2, 2), words.get(1).span());
    assertEquals(0, words.get(0).span().length());
  }

  /**
   * Verifies that a span reaching past the end of the text is rejected on insertion
   * with a message naming the span, the text length, and the layer.
   */
  @Test
  void testSpanBeyondTextLengthIsRejectedWithExactMessage() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Document.of("the").with(WORDS,
            List.of(new Annotation<>(new Span(0, 4), "the?"))));
    assertEquals("span [0..4) exceeds the text length 3 in layer words<String>",
        e.getMessage());
  }

  /**
   * Verifies that adding a layer under a key that is already present is rejected with a
   * message naming the offending layer.
   */
  @Test
  void testDuplicateLayerIsRejectedWithExactMessage() {
    final Document document = Document.of("the").with(WORDS, List.of());
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> document.with(WORDS, List.of()));
    assertEquals("layer is already present: words<String>", e.getMessage());
  }

  /**
   * Verifies that reading an absent layer yields an empty, unmodifiable list rather
   * than {@code null}, so callers can iterate without a presence check.
   */
  @Test
  void testAbsentLayerReadsAsUnmodifiableEmptyList() {
    final List<Annotation<String>> absent = Document.of("the").get(WORDS);
    assertTrue(absent.isEmpty());
    assertThrows(UnsupportedOperationException.class,
        () -> absent.add(new Annotation<>(new Span(0, 3), "the")));
  }

  /**
   * Verifies that the list returned for a present layer is unmodifiable and detached
   * from the caller's input list: mutating the input after the add does not change the
   * document.
   */
  @Test
  void testPresentLayerListIsUnmodifiableAndDetachedFromInput() {
    final List<Annotation<String>> input = new ArrayList<>();
    input.add(new Annotation<>(new Span(0, 3), "the"));
    final Document document = Document.of("the").with(WORDS, input);

    final List<Annotation<String>> words = document.get(WORDS);
    assertThrows(UnsupportedOperationException.class, () -> words.remove(0));
    assertThrows(UnsupportedOperationException.class,
        () -> words.add(new Annotation<>(new Span(0, 3), "the")));

    input.clear();
    assertEquals(1, document.get(WORDS).size());
  }

  /**
   * Verifies that the layer key set exposed by a document cannot be mutated by callers.
   */
  @Test
  void testLayerKeySetIsUnmodifiable() {
    final Document document = Document.of("the").with(WORDS, List.of());
    assertThrows(UnsupportedOperationException.class, () -> document.layers().clear());
  }

  /**
   * Verifies that an analyzer whose annotator requires a layer no earlier annotator
   * provides fails at build time with a message naming the annotator and the missing
   * layer. The annotator under test overrides {@code toString()} so the whole message
   * can be asserted exactly.
   */
  @Test
  void testUnsatisfiedRequirementFailsAtBuildTimeWithExactMessage() {
    final DocumentAnnotator needsTags = new DocumentAnnotator() {

      @Override
      public Document annotate(Document document) {
        throw new IllegalStateException("must never run; the pipeline must not build");
      }

      @Override
      public Set<LayerKey<?>> requires() {
        return Set.of(Layers.POS_TAGS);
      }

      @Override
      public Set<LayerKey<?>> provides() {
        return Set.of(WORDS);
      }

      @Override
      public String toString() {
        return "tag-consumer";
      }
    };
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder().add(needsTags);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, builder::build);
    assertEquals("annotator tag-consumer requires layer opennlp:pos<String>,"
        + " which no earlier annotator provides", e.getMessage());
  }

  /**
   * Verifies that an analyzer whose annotators would provide the same layer twice fails
   * at build time with a message naming the layer and the positions of both providers,
   * instead of crashing midway through the first document.
   */
  @Test
  void testDuplicateProviderFailsAtBuildTimeWithExactMessage() {
    final DocumentAnnotator provider = new DocumentAnnotator() {

      @Override
      public Document annotate(Document document) {
        throw new IllegalStateException("must never run; the pipeline must not build");
      }

      @Override
      public Set<LayerKey<?>> provides() {
        return Set.of(WORDS);
      }
    };
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder()
        .add(provider).add(provider);
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, builder::build);
    assertEquals("annotators at positions 0 and 1 both provide layer words<String>",
        e.getMessage());
  }

  /**
   * Verifies that building an analyzer without any annotator fails with a message
   * stating that a pipeline needs at least one annotator.
   */
  @Test
  void testEmptyPipelineFailsWithExactMessage() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> DocumentAnalyzer.builder().build());
    assertEquals("a pipeline needs at least one annotator", e.getMessage());
  }

  /**
   * Verifies that the value type travels through {@link LayerKey}: a layer added under
   * an {@code Integer} key reads back as {@code Annotation<Integer>}, so its values
   * participate in arithmetic without a cast, and a mismatched value can never enter
   * the layer in the first place.
   */
  @Test
  void testValueTypeTravelsThroughTheKey() {
    final LayerKey<Integer> counts = LayerKey.of("counts", Integer.class);
    final Document document = Document.of("ab cd")
        .with(counts, List.of(
            new Annotation<>(new Span(0, 2), 2),
            new Annotation<>(new Span(3, 5), 40)));
    int sum = 0;
    for (final Annotation<Integer> count : document.get(counts)) {
      sum += count.value();
    }
    assertEquals(42, sum);

    // The insertion-time check backs the typed read: a raw-typed caller cannot place a
    // String under the Integer key.
    @SuppressWarnings({"unchecked", "rawtypes"})
    final LayerKey<Object> raw = (LayerKey) LayerKey.of("counts2", Integer.class);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Document.of("ab").with(raw,
            List.of(new Annotation<>(new Span(0, 2), "not a number"))));
    assertEquals("value of type java.lang.String does not match layer counts2<Integer>",
        e.getMessage());
  }
}
