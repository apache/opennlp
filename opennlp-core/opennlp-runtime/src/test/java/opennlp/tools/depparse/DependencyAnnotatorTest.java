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

/** Tests dependency arcs, token references, and original-text spans. */
public class DependencyAnnotatorTest {

  private static final DependencyParser FIXED = (tokens, tags) ->
      DependencyGraph.of(new int[] {1, -1, 1, 4, 1},
          new String[] {"nsubj", "root", "iobj", "det", "obj"});

  private static Document tokenized() {
    return Document.of("Alice sent Bob a message.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 25), "Alice sent Bob a message.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 5), "Alice"),
            new Annotation<>(new Span(6, 10), "sent"),
            new Annotation<>(new Span(11, 14), "Bob"),
            new Annotation<>(new Span(15, 16), "a"),
            new Annotation<>(new Span(17, 24), "message")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 5), "NNP"),
            new Annotation<>(new Span(6, 10), "VBD"),
            new Annotation<>(new Span(11, 14), "NNP"),
            new Annotation<>(new Span(15, 16), "DT"),
            new Annotation<>(new Span(17, 24), "NN")));
  }

  @Test
  void testArcsResolveThroughTheTokenLayer() {
    final Document document = new DependencyAnnotator(FIXED).annotate(tokenized());
    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);
    assertEquals(5, arcs.size());

    final Annotation<DependencyArc> bob = arcs.get(2);
    assertEquals(new Span(11, 14), bob.span());
    assertEquals("iobj", bob.value().relation());

    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final Annotation<String> head = tokens.get(bob.value().head());
    assertEquals("sent", head.value());
    assertEquals("sent", head.span().getCoveredText(document.text()).toString());
    assertEquals("Bob", bob.span().getCoveredText(document.text()).toString());
  }

  @Test
  void testRootArcCarriesRootHead() {
    final Document document = new DependencyAnnotator(FIXED).annotate(tokenized());
    final DependencyArc root =
        document.get(DependencyAnnotator.DEPENDENCIES).get(1).value();
    assertEquals(DependencyArc.ROOT_HEAD, root.head());
    assertEquals("root", root.relation());
  }

  @Test
  void testMissingLayersThrow() {
    final DependencyAnnotator annotator = new DependencyAnnotator(FIXED);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(Document.of("no layers")));
    assertEquals("document lacks the required layer " + Layers.SENTENCES, e.getMessage());
  }

  @Test
  void testNullParserThrows() {
    assertThrows(IllegalArgumentException.class, () -> new DependencyAnnotator(null));
  }
}
