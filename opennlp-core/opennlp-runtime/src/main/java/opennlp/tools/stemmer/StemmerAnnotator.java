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

package opennlp.tools.stemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;

/**
 * Adapts a {@link Stemmer} to the document pipeline: stems the token layer and provides
 * {@link #STEMS}, one annotation per token on the token's span.
 *
 * <p>Stemming operates on the token surface alone, so this annotator requires only the
 * token layer; no part-of-speech tags are involved.</p>
 *
 * @since 3.0.0
 */
public class StemmerAnnotator implements DocumentAnnotator {

  /**
   * The stem layer. It is aligned with the token layer by position, and each annotation
   * carries the stem of its token on that token's span.
   */
  public static final LayerKey<String> STEMS = LayerKey.of("stems", String.class);

  private final Stemmer stemmer;

  /**
   * Initializes the adapter.
   *
   * @param stemmer The stemmer to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code stemmer} is {@code null}.
   */
  public StemmerAnnotator(Stemmer stemmer) {
    if (stemmer == null) {
      throw new IllegalArgumentException("stemmer must not be null");
    }
    this.stemmer = stemmer;
  }

  /**
   * Stems the token layer and adds the {@link #STEMS} layer.
   *
   * <p>The token layer must be present, but it may be empty: a document without tokens
   * yields a present-but-empty stem layer.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#TOKENS} layer.
   * @return A new {@link Document} with the {@link #STEMS} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null} or the
   *         token layer is absent.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    if (!document.layers().contains(Layers.TOKENS)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.TOKENS);
    }
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> layer = new ArrayList<>(tokens.size());
    for (final Annotation<String> token : tokens) {
      layer.add(new Annotation<>(token.span(), stemmer.stem(token.value()).toString()));
    }
    return document.with(STEMS, layer);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.TOKENS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(STEMS);
  }
}
