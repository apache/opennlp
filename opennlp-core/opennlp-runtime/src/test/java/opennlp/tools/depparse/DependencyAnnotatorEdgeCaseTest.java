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

/** Tests layer validation, sentence slicing, and invalid parser output. */
public class DependencyAnnotatorEdgeCaseTest {

  private static final String STRAY_TOKEN = "token at [3..5) lies outside every sentence";

  private static final String MISALIGNED =
      "document needs aligned opennlp:tokens<String> and opennlp:pos<String> layers";

  private static final DependencyParser FIXED = (tokens, tags) ->
      DependencyGraph.of(new int[] {DependencyArc.ROOT_HEAD, 0},
          new String[] {"root", "obj"});

  private static final DependencyParser ONE_TOKEN_ROOT = (tokens, tags) -> {
    if (tokens.length != 1) {
      throw new IllegalStateException("expected one-token sentences, got " + tokens.length);
    }
    return DependencyGraph.of(new int[] {DependencyArc.ROOT_HEAD},
        new String[] {"root"});
  };

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
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder()
        .add(new DependencyAnnotator(FIXED));
    assertThrows(IllegalArgumentException.class, builder::build);
  }

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

  @Test
  void testWrongSizeGraphIsRejected() {
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

  @Test
  void testNullGraphIsRejected() {
    final DependencyParser noGraph = (tokens, tags) -> null;
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(noGraph).annotate(twoTokens()));
    assertEquals("parser returned no dependency graph", e.getMessage());
  }
}
