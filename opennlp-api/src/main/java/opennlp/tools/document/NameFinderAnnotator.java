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

import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

/**
 * Adapts a {@link TokenNameFinder} to the document pipeline: reads
 * {@link Layers#SENTENCES} and {@link Layers#TOKENS}, maps the finder's token-index
 * spans to character spans on the original text, and provides {@link Layers#ENTITIES}
 * carrying the entity type.
 *
 * <p>Each sentence's tokens are passed to {@link TokenNameFinder#find(String[])} as one
 * sequence, the way the finder contract expects its input, so no mention can straddle a
 * sentence boundary. The finder's adaptive data is cleared exactly once per call, as the
 * {@link TokenNameFinder#clearAdaptiveData()} contract asks, whether annotation succeeds
 * or fails, so no document can leak finder state into the next one.</p>
 *
 * <p>Spans the finder returns without a type are recorded with the {@link #UNTYPED}
 * entity type.</p>
 *
 * @since 3.0.0
 */
public class NameFinderAnnotator implements DocumentAnnotator {

  /**
   * The entity type recorded when the wrapped finder returns a span without a type. It
   * is {@link NameSample#DEFAULT_TYPE}. Type-aware consumers should treat this label as
   * an unknown type rather than as a distinct one, since it carries no information about
   * what kind of entity was found.
   */
  public static final String UNTYPED = NameSample.DEFAULT_TYPE;

  /** The message prefix of every absent-required-layer rejection in this adapter. */
  private static final String MISSING_LAYER = "document lacks the required layer ";

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

  /**
   * Finds names sentence by sentence and adds the {@link Layers#ENTITIES} layer.
   *
   * <p>For every sentence, the tokens whose spans lie inside the sentence span are
   * passed to the finder as one sequence, and each sentence-local mention is mapped
   * through the sentence's first token position onto character spans of the original
   * text. The required layers must be present, but they may be empty: a document
   * without sentences or tokens yields a present-but-empty entity layer, and a sentence
   * containing no tokens contributes nothing. A mention without a type is recorded with
   * the type {@link #UNTYPED}.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#SENTENCES} and {@link Layers#TOKENS} layers, with
   *                 every token lying inside a sentence.
   * @return A new {@link Document} with the {@link Layers#ENTITIES} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, the
   *         sentence layer or the token layer is absent, a token lies outside every
   *         sentence, or the finder returns a mention whose token indices lie outside
   *         its sentence's tokens.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final Set<LayerKey<?>> present = document.layers();
    if (!present.contains(Layers.SENTENCES)) {
      throw new IllegalArgumentException(MISSING_LAYER
          + Layers.SENTENCES);
    }
    if (!present.contains(Layers.TOKENS)) {
      throw new IllegalArgumentException(MISSING_LAYER
          + Layers.TOKENS);
    }
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> entities = new ArrayList<>();
    // Walk the token layer once: both layers are in text order, so each sentence
    // consumes the contiguous run of tokens whose spans it encloses. The adaptive data
    // is cleared even when annotation fails, so a rejected document cannot leak finder
    // state into the next one.
    try {
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
        final String[] words = new String[count];
        for (int i = 0; i < count; i++) {
          words[i] = tokens.get(first + i).value();
        }
        // The finder indexes within the sentence; shifting by the sentence's first token
        // position turns every mention boundary into a document-wide token index, whose
        // token spans already refer to the original text.
        for (final Span mention : finder.find(words)) {
          if (mention.getStart() < 0 || mention.getEnd() > count) {
            throw new IllegalArgumentException("finder returned mention " + mention
                + " outside the sentence's " + count + " tokens");
          }
          final int start = tokens.get(first + mention.getStart()).span().getStart();
          final int end = tokens.get(first + mention.getEnd() - 1).span().getEnd();
          final String type = mention.getType() == null ? UNTYPED : mention.getType();
          entities.add(new Annotation<>(new Span(start, end, type), type));
        }
      }
      if (next != tokens.size()) {
        throw new IllegalArgumentException("token at " + tokens.get(next).span()
            + " lies outside every sentence");
      }
    } finally {
      finder.clearAdaptiveData();
    }
    return document.with(Layers.ENTITIES, entities);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.SENTENCES, Layers.TOKENS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(Layers.ENTITIES);
  }
}
