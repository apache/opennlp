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

package opennlp.tools.depparse;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnalyzer;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins down the boundary behavior of {@link DependencyAnnotator}: the exact exception and
 * message for absent, empty, and misaligned input layers, the immutability rule that a
 * second annotation pass is rejected, and the exact {@code requires()} and
 * {@code provides()} declarations the pipeline validation relies on.
 */
public class DependencyAnnotatorEdgeCaseTest {

  /** The rejection message for a token that no sentence encloses. */
  private static final String STRAY_TOKEN = "token at [3..5) lies outside every sentence";

  /** The rejection message for a tag layer that does not have one tag per token. */
  private static final String MISALIGNED =
      "document needs aligned opennlp:tokens<String> and opennlp:pos<String> layers";

  /**
   * A parser stub that returns a fixed two-token graph regardless of its input, so the
   * assertions in this class exercise only the annotator's own layer handling.
   */
  private static final DependencyParser FIXED = (tokens, tags) ->
      DependencyGraph.of(new int[] {DependencyArc.ROOT_HEAD, 0},
          new String[] {"root", "obj"});

  /**
   * A parser stub that accepts only one-token sentences and returns their single root
   * arc, so a sentence slice of any other length fails the test loudly.
   */
  private static final DependencyParser ONE_TOKEN_ROOT = (tokens, tags) -> {
    if (tokens.length != 1) {
      throw new IllegalStateException("expected one-token sentences, got " + tokens.length);
    }
    return DependencyGraph.of(new int[] {DependencyArc.ROOT_HEAD},
        new String[] {"root"});
  };

  /**
   * A parser stub that returns a flat tree of the requested size, for assertions that
   * must get past the annotator's graph-size validation with sentences of any length.
   */
  private static final DependencyParser SIZE_MATCHING = (tokens, tags) -> {
    final int[] heads = new int[tokens.length];
    final String[] relations = new String[tokens.length];
    heads[0] = DependencyArc.ROOT_HEAD;
    relations[0] = "root";
    for (int i = 1; i < heads.length; i++) {
      heads[i] = 0;
      relations[i] = "dep";
    }
    return DependencyGraph.of(heads, relations);
  };

  /**
   * Builds a document over the text {@code "ab cd"} carrying aligned two-entry token and
   * tag layers, mirroring what the upstream tokenizer and tagger annotators would produce.
   *
   * @return A document ready for dependency annotation. Never {@code null}.
   */
  private static Document twoTokens() {
    return Document.of("ab cd")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 5), "ab cd")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(3, 5), "NN")));
  }

  /**
   * Supplies one document per required layer, each missing exactly that layer, together
   * with the key the rejection message must name.
   *
   * @return The documents and the expected layer key. Never {@code null}.
   */
  private static Stream<Arguments> documentsMissingOneLayer() {
    final List<Annotation<String>> sentence =
        List.of(new Annotation<>(new Span(0, 5), "ab cd"));
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 2), "ab"),
        new Annotation<>(new Span(3, 5), "cd"));
    final List<Annotation<String>> tags = List.of(
        new Annotation<>(new Span(0, 2), "VB"),
        new Annotation<>(new Span(3, 5), "NN"));
    return Stream.of(
        Arguments.of(Document.of("ab cd")
            .with(Layers.TOKENS, tokens).with(Layers.POS_TAGS, tags), Layers.SENTENCES),
        Arguments.of(Document.of("ab cd")
            .with(Layers.SENTENCES, sentence).with(Layers.POS_TAGS, tags), Layers.TOKENS),
        Arguments.of(Document.of("ab cd")
            .with(Layers.SENTENCES, sentence).with(Layers.TOKENS, tokens), Layers.POS_TAGS));
  }

  @ParameterizedTest
  @MethodSource("documentsMissingOneLayer")
  void testAbsentRequiredLayerIsNamed(Document document, LayerKey<String> missing) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(FIXED).annotate(document));
    assertEquals("document lacks the required layer " + missing, e.getMessage());
  }

  @Test
  void testNullDocumentIsRejected() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(FIXED).annotate(null));
    assertEquals("document must not be null", e.getMessage());
  }

  /**
   * Verifies the empty-versus-absent distinction of the annotator contract: present but
   * empty required layers are valid input and yield a present-but-empty arc layer, so a
   * pipeline does not fail on a document without content.
   */
  @Test
  void testEmptyRequiredLayersYieldAnEmptyArcLayer() {
    final Document empty = Document.of("")
        .with(Layers.SENTENCES, List.of())
        .with(Layers.TOKENS, List.of())
        .with(Layers.POS_TAGS, List.of());
    final Document annotated = new DependencyAnnotator(FIXED).annotate(empty);
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS,
        DependencyAnnotator.DEPENDENCIES), annotated.layers());
    assertEquals(List.of(), annotated.get(DependencyAnnotator.DEPENDENCIES));
  }

  @Test
  void testMisalignedTagLayerIsRejected() {
    // two tokens but only one tag: the layers are present yet not aligned by position
    final Document misaligned = Document.of("ab cd")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 5), "ab cd")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(FIXED).annotate(misaligned));
    assertEquals(MISALIGNED, e.getMessage());
  }

  @Test
  void testAnnotatingTwiceIsRejected() {
    final DependencyAnnotator annotator = new DependencyAnnotator(FIXED);
    final Document once = annotator.annotate(twoTokens());
    assertEquals(2, once.get(DependencyAnnotator.DEPENDENCIES).size());

    // documents are immutable and layers are add-once: a second pass must not overwrite
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(once));
    assertEquals("layer is already present: opennlp:dependencies<DependencyArc>", e.getMessage());
  }

  @Test
  void testRequiresAndProvidesDeclarationsAreExact() {
    final DependencyAnnotator annotator = new DependencyAnnotator(FIXED);
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS),
        annotator.requires());
    assertEquals(Set.of(DependencyAnnotator.DEPENDENCIES), annotator.provides());
  }

  /**
   * Verifies the per-sentence contract: two one-token sentences are parsed as two
   * separate calls, each yielding its own root arc, and the dependents come back as
   * document-wide token indices.
   */
  @Test
  void testEachSentenceGetsItsOwnTree() {
    final Document document = Document.of("ab. cd.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 3), "ab."),
            new Annotation<>(new Span(4, 7), "cd.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(4, 6), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(4, 6), "VB")));

    final List<Annotation<DependencyArc>> arcs =
        new DependencyAnnotator(ONE_TOKEN_ROOT).annotate(document)
            .get(DependencyAnnotator.DEPENDENCIES);
    assertEquals(2, arcs.size());
    assertEquals(new DependencyArc(DependencyArc.ROOT_HEAD, 0, "root"),
        arcs.get(0).value());
    assertEquals(new DependencyArc(DependencyArc.ROOT_HEAD, 1, "root"),
        arcs.get(1).value());
    assertEquals(new Span(0, 2), arcs.get(0).span());
    assertEquals(new Span(4, 6), arcs.get(1).span());
  }

  /**
   * Verifies a token that no sentence encloses is reported instead of being parsed
   * outside of any sentence.
   */
  @Test
  void testTokenOutsideEverySentenceIsRejected() {
    final Document strayToken = Document.of("ab cd")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 2), "ab")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(3, 5), "NN")));
    final IllegalArgumentException stray = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(SIZE_MATCHING).annotate(strayToken));
    assertEquals(STRAY_TOKEN, stray.getMessage());
  }

  @Test
  void testPipelineWithoutUpstreamAnnotatorsFailsAtBuildTime() {
    // requires() feeds the analyzer's validation: no tokenizer or tagger, no pipeline
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder()
        .add(new DependencyAnnotator(FIXED));
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  /**
   * Verifies the javadoc-promised behavior for a sentence containing no tokens: it
   * contributes no arcs and no parser call, and the token indices of the sentence
   * after it still shift by the correct first-token position rather than by a count
   * that includes the empty sentence.
   */
  @Test
  void testEmptySentenceContributesNoArcsAndKeepsTheIndexShift() {
    final Document document = Document.of("ab. ??? cd.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 3), "ab."),
            new Annotation<>(new Span(4, 7), "???"),
            new Annotation<>(new Span(8, 11), "cd.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(8, 10), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(8, 10), "VB")));

    final List<Annotation<DependencyArc>> arcs =
        new DependencyAnnotator(ONE_TOKEN_ROOT).annotate(document)
            .get(DependencyAnnotator.DEPENDENCIES);
    assertEquals(2, arcs.size());
    assertEquals(new DependencyArc(DependencyArc.ROOT_HEAD, 1, "root"),
        arcs.get(1).value());
    assertEquals(new Span(8, 10), arcs.get(1).span());
  }

  /**
   * Verifies the text-order walk on a token straddling two sentence spans: the token
   * belongs to neither sentence under the enclosure rule, the scan sticks at it, and
   * the annotator reports it as lying outside every sentence instead of silently
   * attaching it to one of its neighbors.
   */
  @Test
  void testTokenStraddlingTwoSentencesIsRejected() {
    final Document document = Document.of("ab cd ef")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 4), "ab c"),
            new Annotation<>(new Span(4, 8), "d ef")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd"),
            new Annotation<>(new Span(6, 8), "ef")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(3, 5), "NN"),
            new Annotation<>(new Span(6, 8), "NN")));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(SIZE_MATCHING).annotate(document));
    assertEquals(STRAY_TOKEN, e.getMessage());
  }

  /**
   * Verifies the stuck-scan path: a gap token between sentences stops the walk, and
   * the token-bearing sentence after the gap does not pull the scan forward past the
   * stray token, which is still reported rather than skipped.
   */
  @Test
  void testGapTokenBeforeATokenBearingSentenceIsStillRejected() {
    final Document document = Document.of("ab cd ef.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(6, 9), "ef.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd"),
            new Annotation<>(new Span(6, 8), "ef")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(3, 5), "NN"),
            new Annotation<>(new Span(6, 8), "NN")));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(SIZE_MATCHING).annotate(document));
    assertEquals(STRAY_TOKEN, e.getMessage());
  }

  /**
   * Verifies that a parser returning a wrong-size graph is rejected loudly instead of
   * silently misaligning the dependency layer with the token layer: the fixed
   * two-token stub meets a three-token sentence and the annotator names both counts.
   */
  @Test
  void testWrongSizeGraphFailsLoud() {
    final Document document = Document.of("ab cd ef")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 8), "ab cd ef")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd"),
            new Annotation<>(new Span(6, 8), "ef")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(3, 5), "NN"),
            new Annotation<>(new Span(6, 8), "NN")));

    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(FIXED).annotate(document));
    assertEquals("parser returned a graph over 2 tokens for a sentence of 3",
        e.getMessage());
  }
}
