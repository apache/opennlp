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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;

/**
 * Adapts a {@link DependencyParser} to the document pipeline: reads
 * {@link Layers#SENTENCES}, {@link Layers#TOKENS}, and {@link Layers#POS_TAGS} and
 * provides {@link #DEPENDENCIES}, one {@link DependencyArc} per token on the token's
 * span.
 *
 * <p>This is the first graph-shaped layer: an arc's {@link DependencyArc#head()} and
 * {@link DependencyArc#dependent()} are indices into the token layer, following the
 * container's rule that annotations reference each other by layer and index, never by
 * object identity.</p>
 *
 * <p>Each sentence is parsed separately, the way the parser is trained, so every
 * sentence gets its own tree and its own root arc. The sentence-local indices the
 * parser produces are shifted by the sentence's first token position, which keeps every
 * arc's head and dependent a position in the document-wide token layer. Because every
 * token span already refers to the original document text, anchoring an arc on its
 * dependent token's span puts the arc in document coordinates without further offset
 * arithmetic.</p>
 *
 * @since 3.0.0
 */
public class DependencyAnnotator implements DocumentAnnotator {

  /**
   * Dependency arcs; one annotation per token, aligned with {@link Layers#TOKENS} by
   * position, anchored on the dependent token's span.
   */
  public static final LayerKey<DependencyArc> DEPENDENCIES =
      Layers.key("dependencies", DependencyArc.class);

  private final DependencyParser parser;

  /**
   * Initializes the adapter.
   *
   * @param parser The dependency parser to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code parser} is {@code null}.
   */
  public DependencyAnnotator(DependencyParser parser) {
    if (parser == null) {
      throw new IllegalArgumentException("parser must not be null");
    }
    this.parser = parser;
  }

  /**
   * Parses the document sentence by sentence and adds the {@link #DEPENDENCIES} layer.
   *
   * <p>For every sentence, the tokens whose spans lie inside the sentence span are
   * passed to the parser with their tags as one sequence, and the resulting
   * sentence-local arcs are shifted by the sentence's first token position. Arcs are
   * emitted in token order, so the new layer is aligned with {@link Layers#TOKENS} by
   * position, and each arc annotation reuses the span of its dependent token. A
   * sentence containing no tokens contributes no arcs.</p>
   *
   * <p>The sentence and token layers must both be in text order: the walk assigns each
   * sentence the contiguous run of tokens its span encloses, so a token that appears
   * before its sentence in the layer, or a token overlapping a sentence boundary, is
   * reported as lying outside every sentence rather than being silently attached to a
   * neighboring sentence.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry a
   *                 non-empty {@link Layers#SENTENCES} layer, in text order, a
   *                 non-empty {@link Layers#TOKENS} layer, in text order, whose every
   *                 token lies inside a sentence, and a {@link Layers#POS_TAGS} layer
   *                 of equal size.
   * @return A new {@link Document} with the {@link #DEPENDENCIES} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, the
   *         token layer is absent or empty, the tag layer does not have exactly one
   *         tag per token, the sentence layer is absent or empty, a token lies
   *         outside every sentence under the text-order walk, or the parser returns a
   *         graph whose size differs from its sentence's token count.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> tags = document.get(Layers.POS_TAGS);
    if (tokens.isEmpty() || tags.size() != tokens.size()) {
      throw new IllegalArgumentException("document needs aligned "
          + Layers.TOKENS + " and " + Layers.POS_TAGS + " layers");
    }
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    if (sentences.isEmpty()) {
      throw new IllegalArgumentException(
          "document needs a non-empty " + Layers.SENTENCES + " layer");
    }
    final List<Annotation<DependencyArc>> arcs = new ArrayList<>(tokens.size());
    // Walk the token layer once: both layers are in text order, so each sentence
    // consumes the contiguous run of tokens whose spans it encloses.
    int next = 0;
    for (final Annotation<String> sentence : sentences) {
      final int first = next;
      while (next < tokens.size()
          && tokens.get(next).span().getStart() >= sentence.span().getStart()
          && tokens.get(next).span().getEnd() <= sentence.span().getEnd()) {
        next++;
      }
      final int count = next - first;
      if (count == 0) {
        continue;
      }
      // unwrap the sentence's slice into the parallel arrays the parser expects
      final String[] words = new String[count];
      final String[] posTags = new String[count];
      for (int i = 0; i < count; i++) {
        words[i] = tokens.get(first + i).value();
        posTags[i] = tags.get(first + i).value();
      }
      final DependencyGraph graph = parser.parse(words, posTags);
      if (graph.size() != count) {
        throw new IllegalArgumentException("parser returned a graph over " + graph.size()
            + " tokens for a sentence of " + count);
      }
      // The parser indexes within the sentence; shifting by the sentence's first token
      // position turns every head and dependent into a document-wide token index, and
      // anchoring each arc on its dependent token's span puts the arc in document
      // coordinates, since token spans refer to the original text.
      for (final DependencyArc arc : graph.arcs()) {
        final int head = arc.head() == DependencyArc.ROOT_HEAD
            ? DependencyArc.ROOT_HEAD : arc.head() + first;
        arcs.add(new Annotation<>(tokens.get(first + arc.dependent()).span(),
            new DependencyArc(head, arc.dependent() + first, arc.relation())));
      }
    }
    if (next != tokens.size()) {
      throw new IllegalArgumentException("token at " + tokens.get(next).span()
          + " lies outside every sentence");
    }
    return document.with(DEPENDENCIES, arcs);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(DEPENDENCIES);
  }
}
