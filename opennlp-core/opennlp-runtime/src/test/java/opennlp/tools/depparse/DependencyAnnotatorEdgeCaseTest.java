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

import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnalyzer;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins down the boundary behavior of {@link DependencyAnnotator}: the exact exception and
 * message for empty and misaligned input layers, the immutability rule that a second
 * annotation pass is rejected, and the exact {@code requires()} and {@code provides()}
 * declarations the pipeline validation relies on.
 */
public class DependencyAnnotatorEdgeCaseTest {

  /**
   * A parser stub that returns a fixed two-token graph regardless of its input, so the
   * assertions in this class exercise only the annotator's own layer handling.
   */
  private static final DependencyParser FIXED = (tokens, tags) ->
      DependencyGraph.of(new int[] {DependencyArc.ROOT_HEAD, 0},
          new String[] {"root", "obj"});

  /**
   * Builds a document over the text {@code "ab cd"} carrying aligned two-entry token and
   * tag layers, mirroring what the upstream tokenizer and tagger annotators would produce.
   *
   * @return A document ready for dependency annotation. Never {@code null}.
   */
  private static Document twoTokens() {
    return Document.of("ab cd")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB"),
            new Annotation<>(new Span(3, 5), "NN")));
  }

  @Test
  void testEmptyTokenAndTagLayersAreRejected() {
    // a document with zero sentences has zero tokens; the annotator refuses to parse it
    final Document empty = Document.of("")
        .with(Layers.TOKENS, List.of())
        .with(Layers.POS_TAGS, List.of());
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(FIXED).annotate(empty));
    assertEquals("document needs aligned tokens<String> and pos<String> layers",
        e.getMessage());
  }

  @Test
  void testMisalignedTagLayerIsRejected() {
    // two tokens but only one tag: the layers are present yet not aligned by position
    final Document misaligned = Document.of("ab cd")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "ab"),
            new Annotation<>(new Span(3, 5), "cd")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 2), "VB")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new DependencyAnnotator(FIXED).annotate(misaligned));
    assertEquals("document needs aligned tokens<String> and pos<String> layers",
        e.getMessage());
  }

  @Test
  void testAnnotatingTwiceIsRejected() {
    final DependencyAnnotator annotator = new DependencyAnnotator(FIXED);
    final Document once = annotator.annotate(twoTokens());
    assertEquals(2, once.get(DependencyAnnotator.DEPENDENCIES).size());

    // documents are immutable and layers are add-once: a second pass must not overwrite
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(once));
    assertEquals("layer is already present: dependencies<DependencyArc>", e.getMessage());
  }

  @Test
  void testRequiresAndProvidesDeclarationsAreExact() {
    final DependencyAnnotator annotator = new DependencyAnnotator(FIXED);
    assertEquals(Set.of(Layers.TOKENS, Layers.POS_TAGS), annotator.requires());
    assertEquals(Set.of(DependencyAnnotator.DEPENDENCIES), annotator.provides());
  }

  @Test
  void testPipelineWithoutUpstreamAnnotatorsFailsAtBuildTime() {
    // requires() feeds the analyzer's validation: no tokenizer or tagger, no pipeline
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder()
        .add(new DependencyAnnotator(FIXED));
    assertThrows(IllegalArgumentException.class, builder::build);
  }
}
