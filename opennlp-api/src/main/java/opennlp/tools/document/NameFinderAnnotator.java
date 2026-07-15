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

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

/**
 * Adapts a {@link TokenNameFinder} to the document pipeline: reads {@link Layers#TOKENS},
 * maps the finder's token-index spans to character spans on the original text, and
 * provides {@link Layers#ENTITIES} carrying the entity type.
 *
 * <p>The finder's adaptive data is cleared after each document, so document order does
 * not leak between pipeline runs.</p>
 *
 * @since 3.0.0
 */
public class NameFinderAnnotator implements DocumentAnnotator {

  private final TokenNameFinder finder;

  /**
   * Initializes the adapter.
   *
   * @param finder The name finder to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code finder} is {@code null}.
   */
  public NameFinderAnnotator(TokenNameFinder finder) {
    if (finder == null) {
      throw new IllegalArgumentException("finder must not be null");
    }
    this.finder = finder;
  }

  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    if (tokens.isEmpty()) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.TOKENS);
    }
    final String[] words = new String[tokens.size()];
    for (int i = 0; i < words.length; i++) {
      words[i] = tokens.get(i).value();
    }
    final List<Annotation<String>> entities = new ArrayList<>();
    for (final Span mention : finder.find(words)) {
      final int start = tokens.get(mention.getStart()).span().getStart();
      final int end = tokens.get(mention.getEnd() - 1).span().getEnd();
      final String type = mention.getType() == null ? "default" : mention.getType();
      entities.add(new Annotation<>(new Span(start, end, type), type));
    }
    finder.clearAdaptiveData();
    return document.with(Layers.ENTITIES, entities);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.TOKENS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(Layers.ENTITIES);
  }
}
