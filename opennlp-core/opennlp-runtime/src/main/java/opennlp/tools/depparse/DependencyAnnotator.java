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
 * {@link Layers#TOKENS} and {@link Layers#POS_TAGS} and provides
 * {@link #DEPENDENCIES}, one {@link DependencyArc} per token on the token's span.
 *
 * <p>This is the first graph-shaped layer: an arc's {@link DependencyArc#head()} and
 * {@link DependencyArc#dependent()} are indices into the token layer, following the
 * container's rule that annotations reference each other by layer and index, never by
 * object identity.</p>
 *
 * @since 3.0.0
 */
public class DependencyAnnotator implements DocumentAnnotator {

  /**
   * Dependency arcs; one annotation per token, aligned with {@link Layers#TOKENS} by
   * position, anchored on the dependent token's span.
   */
  public static final LayerKey<DependencyArc> DEPENDENCIES =
      LayerKey.of("dependencies", DependencyArc.class);

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
    final String[] words = new String[tokens.size()];
    final String[] posTags = new String[tokens.size()];
    for (int i = 0; i < words.length; i++) {
      words[i] = tokens.get(i).value();
      posTags[i] = tags.get(i).value();
    }
    final DependencyGraph graph = parser.parse(words, posTags);
    final List<Annotation<DependencyArc>> arcs = new ArrayList<>(graph.size());
    for (final DependencyArc arc : graph.arcs()) {
      arcs.add(new Annotation<>(tokens.get(arc.dependent()).span(), arc));
    }
    return document.with(DEPENDENCIES, arcs);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.TOKENS, Layers.POS_TAGS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(DEPENDENCIES);
  }
}
