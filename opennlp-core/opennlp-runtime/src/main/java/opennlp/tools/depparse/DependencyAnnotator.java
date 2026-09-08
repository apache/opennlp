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
import opennlp.tools.document.DocumentAnnotators;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;

/**
 * Adds dependency arcs to a document through a {@link DependencyParser}. The annotator
 * reads {@link Layers#SENTENCES}, {@link Layers#TOKENS}, and {@link Layers#POS_TAGS} and
 * provides {@link #DEPENDENCIES}.
 *
 * <p>Parsing runs once per sentence. Sentence-local head and dependent indices are
 * offset to positions in the document token layer. Each output annotation uses the
 * dependent token's original-text span.</p>
 *
 * <p>The adapter holds no per-call state; it is as thread-safe as the parser it
 * wraps.</p>
 *
 * @since 3.0.0
 */
public final class DependencyAnnotator implements DocumentAnnotator {

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
   * <p>Arcs are emitted in token order and reuse their dependent tokens' spans. The
   * required layers may be empty. An empty document receives an empty dependency
   * layer, and sentences without tokens are skipped.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#SENTENCES} and {@link Layers#TOKENS} layers, in
   *                 text order, and a {@link Layers#POS_TAGS} layer with exactly one
   *                 tag per token, with every token lying inside a sentence.
   * @return A new {@link Document} with the {@link #DEPENDENCIES} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, the
   *         sentence layer, the token layer, or the tag layer is absent, the tag layer
   *         does not have exactly one tag per token, a token lies outside every
   *         sentence under the text-order walk, or the parser returns {@code null} or
   *         a graph whose size differs from its sentence's token count.
   */
  @Override
  public Document annotate(Document document) {
    DocumentAnnotators.requireLayers(document, Layers.SENTENCES, Layers.TOKENS,
        Layers.POS_TAGS);
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> tags = document.get(Layers.POS_TAGS);
    if (tags.size() != tokens.size()) {
      throw new IllegalArgumentException("document needs aligned "
          + Layers.TOKENS + " and " + Layers.POS_TAGS + " layers");
    }
    final List<Annotation<DependencyArc>> arcs = new ArrayList<>(tokens.size());
    DocumentAnnotators.forEachSentence(sentences, tokens, (first, words) -> {
      final String[] posTags = new String[words.length];
      for (int i = 0; i < words.length; i++) {
        posTags[i] = tags.get(first + i).value();
      }
      final DependencyGraph graph = parser.parse(words, posTags);
      if (graph == null) {
        throw new IllegalArgumentException("parser returned no dependency graph");
      }
      if (graph.size() != words.length) {
        throw new IllegalArgumentException("parser returned a graph over " + graph.size()
            + " tokens for a sentence of " + words.length);
      }
      for (final DependencyArc arc : graph.arcs()) {
        final int head = arc.head() == DependencyArc.ROOT_HEAD
            ? DependencyArc.ROOT_HEAD : arc.head() + first;
        arcs.add(new Annotation<>(tokens.get(first + arc.dependent()).span(),
            new DependencyArc(head, arc.dependent() + first, arc.relation())));
      }
    });
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

  /**
   * {@return the adapter's simple class name, which names it in pipeline validation
   * messages}
   */
  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
