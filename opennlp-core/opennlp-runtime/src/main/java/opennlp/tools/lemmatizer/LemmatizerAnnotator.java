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

package opennlp.tools.lemmatizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;

/**
 * Adapts a {@link Lemmatizer} to the document pipeline: reads {@link Layers#SENTENCES},
 * {@link Layers#TOKENS}, and {@link Layers#POS_TAGS} and provides {@link #LEMMAS}, one
 * annotation per token on the token's span.
 *
 * <p>Each sentence is lemmatized separately, the way the lemmatizer contract expects its
 * input, so lemmatization decisions never cross a sentence boundary. Token spans already
 * refer to the original document text, so only the token and tag sequences handed to the
 * lemmatizer are sliced per sentence; the produced lemma layer stays aligned with
 * {@link Layers#TOKENS} by position.</p>
 *
 * @since 3.0.0
 */
public class LemmatizerAnnotator implements DocumentAnnotator {

  /**
   * The lemma layer. It is aligned with the token layer by position, and each annotation
   * carries the lemma of its token on that token's span.
   */
  public static final LayerKey<String> LEMMAS = LayerKey.of("lemmas", String.class);

  private final Lemmatizer lemmatizer;

  /**
   * Initializes the adapter.
   *
   * @param lemmatizer The lemmatizer to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code lemmatizer} is {@code null}.
   */
  public LemmatizerAnnotator(Lemmatizer lemmatizer) {
    if (lemmatizer == null) {
      throw new IllegalArgumentException("lemmatizer must not be null");
    }
    this.lemmatizer = lemmatizer;
  }

  /**
   * Lemmatizes the document sentence by sentence and adds the {@link #LEMMAS} layer.
   *
   * <p>For every sentence, the tokens whose spans lie inside the sentence span are
   * lemmatized as one sequence together with their tags, and each lemma is emitted on
   * its token's span. The required layers must be present, but they may be empty: a
   * document without sentences or tokens yields a present-but-empty lemma layer, and a
   * sentence containing no tokens contributes nothing.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#SENTENCES} and {@link Layers#TOKENS} layers and a
   *                 {@link Layers#POS_TAGS} layer with exactly one tag per token, with
   *                 every token lying inside a sentence.
   * @return A new {@link Document} with the {@link #LEMMAS} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, the
   *         sentence layer, the token layer, or the tag layer is absent, the tag layer
   *         does not have exactly one tag per token, a token lies outside every
   *         sentence, or the lemmatizer does not return one lemma per token of a
   *         sentence.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    if (!document.layers().contains(Layers.SENTENCES)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.SENTENCES);
    }
    if (!document.layers().contains(Layers.TOKENS)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.TOKENS);
    }
    if (!document.layers().contains(Layers.POS_TAGS)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.POS_TAGS);
    }
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> tags = document.get(Layers.POS_TAGS);
    if (tags.size() != tokens.size()) {
      throw new IllegalArgumentException("document needs aligned "
          + Layers.TOKENS + " and " + Layers.POS_TAGS + " layers");
    }
    final List<Annotation<String>> layer = new ArrayList<>(tokens.size());
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
      final String[] words = new String[count];
      final String[] posTags = new String[count];
      for (int i = 0; i < count; i++) {
        words[i] = tokens.get(first + i).value();
        posTags[i] = tags.get(first + i).value();
      }
      final String[] lemmas = lemmatizer.lemmatize(words, posTags);
      if (lemmas.length != count) {
        throw new IllegalArgumentException(
            "lemmatizer returned " + lemmas.length + " lemmas for " + count + " tokens");
      }
      for (int i = 0; i < count; i++) {
        layer.add(new Annotation<>(tokens.get(first + i).span(), lemmas[i]));
      }
    }
    if (next != tokens.size()) {
      throw new IllegalArgumentException("token at " + tokens.get(next).span()
          + " lies outside every sentence");
    }
    return document.with(LEMMAS, layer);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(LEMMAS);
  }
}
