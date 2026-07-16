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

import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DependencyAnnotator} as the container's first graph-shaped layer: arcs
 * reference tokens by layer index, and resolving an arc through the token layer lands on
 * the right span of the original text.
 */
public class DependencyAnnotatorTest {

  /**
   * A parser stub that always returns the gold graph of {@code "the dog barks"}, so the
   * assertions in this class depend only on the annotator's own layer handling and not on
   * any trained model.
   */
  private static final DependencyParser FIXED = (tokens, tags) ->
      DependencyGraph.of(new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"});

  /**
   * Builds a document over the text {@code "the dog barks"} carrying aligned token and tag
   * layers, mirroring what the upstream tokenizer and tagger annotators would produce.
   *
   * @return A document ready for dependency annotation. Never {@code null}.
   */
  private static Document tokenized() {
    return Document.of("the dog barks")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "the"),
            new Annotation<>(new Span(4, 7), "dog"),
            new Annotation<>(new Span(8, 13), "barks")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 3), "DT"),
            new Annotation<>(new Span(4, 7), "NN"),
            new Annotation<>(new Span(8, 13), "VBZ")));
  }

  @Test
  void testArcsResolveThroughTheTokenLayer() {
    final Document document = new DependencyAnnotator(FIXED).annotate(tokenized());
    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);
    assertEquals(3, arcs.size());

    // the arc of "dog" is anchored on the dependent token's span in the original text
    final Annotation<DependencyArc> dog = arcs.get(1);
    assertEquals(new Span(4, 7), dog.span());
    assertEquals("nsubj", dog.value().relation());

    // cross-layer reference: the arc stores its head as an index, and looking that index
    // up in the token layer lands on the head token and its span in the original text
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final Annotation<String> head = tokens.get(dog.value().head());
    assertEquals("barks", head.value());
    assertEquals("barks", head.span().getCoveredText(document.text()).toString());
  }

  @Test
  void testRootArcCarriesRootHead() {
    final Document document = new DependencyAnnotator(FIXED).annotate(tokenized());
    final DependencyArc root =
        document.get(DependencyAnnotator.DEPENDENCIES).get(2).value();
    assertEquals(DependencyArc.ROOT_HEAD, root.head());
    assertEquals("root", root.relation());
  }

  @Test
  void testMissingLayersThrow() {
    final DependencyAnnotator annotator = new DependencyAnnotator(FIXED);
    assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(Document.of("no layers")));
  }

  @Test
  void testNullParserThrows() {
    assertThrows(IllegalArgumentException.class, () -> new DependencyAnnotator(null));
  }
}
