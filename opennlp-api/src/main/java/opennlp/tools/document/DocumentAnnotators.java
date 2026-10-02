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

import java.util.List;
import java.util.Set;

import opennlp.tools.util.Span;

/**
 * Support methods shared by {@link DocumentAnnotator} implementations: the
 * required-layer and layer-alignment checks, the per-sentence walk over the token
 * layer, and the mapping of sentence-relative token spans onto character spans.
 *
 * <p>These helpers keep the annotators' shared behavior identical across
 * implementations: an absent required layer is always rejected with the same message
 * naming the layer, and every per-sentence adapter applies the same sentence-to-token
 * mapping, including the loud rejection of a token lying outside every sentence.</p>
 *
 * @since 3.0.0
 */
public final class DocumentAnnotators {

  /**
   * Receives one sentence's contiguous token run during
   * {@link #forEachSentence(List, List, SentenceTokenConsumer)}.
   */
  @FunctionalInterface
  public interface SentenceTokenConsumer {

    /**
     * Consumes one sentence's tokens.
     *
     * @param first The position of the sentence's first token in the token layer.
     * @param words The sentence's token values in layer order. Never {@code null} or
     *              empty; the run covers the token layer positions
     *              {@code [first, first + words.length)}.
     */
    void accept(int first, String[] words);
  }

  /**
   * Verifies that a document is present and carries every given layer.
   *
   * @param document The document to check.
   * @param layers The required layers, in the order they are to be reported.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, or if
   *         a layer is absent; the message names the first absent layer.
   */
  public static void requireLayers(Document document, LayerKey<?>... layers) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final Set<LayerKey<?>> present = document.layers();
    for (final LayerKey<?> layer : layers) {
      if (!present.contains(layer)) {
        throw new IllegalArgumentException("document lacks the required layer " + layer);
      }
    }
  }

  /**
   * Walks the token layer sentence by sentence and hands each sentence's contiguous
   * token run to the consumer.
   *
   * <p>Both layers must be in text order. Each sentence consumes the contiguous run of
   * tokens whose spans it encloses; a sentence without tokens is skipped. Every token
   * must belong to a sentence: a token lying outside every sentence is rejected loudly
   * after the walk, so it can never be silently dropped.</p>
   *
   * @param sentences The sentence layer, in text order. Must not be {@code null}.
   * @param tokens The token layer, in text order. Must not be {@code null}.
   * @param consumer Receives each token-carrying sentence's run. Must not be
   *                 {@code null}.
   * @throws IllegalArgumentException Thrown if a token lies outside every sentence.
   */
  public static void forEachSentence(List<Annotation<String>> sentences,
      List<Annotation<String>> tokens, SentenceTokenConsumer consumer) {
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
      consumer.accept(first, values(tokens, first, count));
    }
    if (next != tokens.size()) {
      throw new IllegalArgumentException("token at " + tokens.get(next).span()
          + " lies outside every sentence");
    }
  }

  /**
   * Verifies that two layers of a document hold the same number of annotations, so the
   * annotation at each position of one belongs to the annotation at the same position of
   * the other.
   *
   * @param document The document to check. Must not be {@code null}.
   * @param layer The first layer, for example {@link Layers#TOKENS}.
   * @param aligned The layer that must be aligned with {@code layer}, for example
   *                {@link Layers#POS_TAGS}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, if a
   *         layer is absent, or if the two layers differ in size; the message names the
   *         absent layer, or both layers.
   */
  public static void requireAligned(Document document, LayerKey<?> layer,
      LayerKey<?> aligned) {
    requireLayers(document, layer, aligned);
    if (document.get(layer).size() != document.get(aligned).size()) {
      throw new IllegalArgumentException("document needs aligned "
          + layer + " and " + aligned + " layers");
    }
  }

  /**
   * Collects the values of a contiguous run of annotations, for example the tags of one
   * sentence's tokens during {@link #forEachSentence(List, List, SentenceTokenConsumer)}.
   *
   * @param layer The layer to read. Must not be {@code null}.
   * @param first The position of the run's first annotation in {@code layer}.
   * @param count The number of annotations in the run.
   * @return The values at the layer positions {@code [first, first + count)}, in layer
   *         order. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code layer} is {@code null}, or if the
   *         run does not lie inside the layer.
   */
  public static String[] values(List<Annotation<String>> layer, int first, int count) {
    if (layer == null) {
      throw new IllegalArgumentException("layer must not be null");
    }
    if (first < 0 || count < 0 || first > layer.size() - count) {
      throw new IllegalArgumentException("run of " + count + " annotations at " + first
          + " lies outside the layer's " + layer.size() + " annotations");
    }
    final String[] values = new String[count];
    for (int i = 0; i < count; i++) {
      values[i] = layer.get(first + i).value();
    }
    return values;
  }

  /**
   * Maps a span over one sentence's tokens onto the character span those tokens cover.
   *
   * <p>A sentence-level component such as a chunker or a name finder indexes the tokens
   * within the sentence. The span is shifted by the sentence's first token position and
   * resolved through the token layer, whose spans already refer to the original text, so
   * the result runs from the start of the span's first token to the end of its last
   * token. An empty span covers no token and therefore has no character span; it is
   * rejected together with the out-of-bounds ones.</p>
   *
   * @param tokens The token layer. Must not be {@code null}.
   * @param first The position of the sentence's first token in {@code tokens}.
   * @param count The number of tokens in the sentence.
   * @param span The span over the sentence's tokens, with an exclusive end. Must not be
   *             {@code null}.
   * @param source How the rejection message introduces {@code span}, for example
   *               {@code "chunker returned chunk"}. Must not be {@code null}.
   * @return The character span covered by the tokens, without a type. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null}, if the
   *         sentence does not lie inside {@code tokens}, or if {@code span} is empty or
   *         reaches outside the sentence's {@code count} tokens; the message for a
   *         rejected span starts with {@code source}.
   */
  public static Span toCharacterSpan(List<Annotation<String>> tokens, int first,
      int count, Span span, String source) {
    if (tokens == null) {
      throw new IllegalArgumentException("tokens must not be null");
    }
    if (span == null) {
      throw new IllegalArgumentException("span must not be null");
    }
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    if (first < 0 || count < 0 || first > tokens.size() - count) {
      throw new IllegalArgumentException("sentence of " + count + " tokens at " + first
          + " lies outside the token layer's " + tokens.size() + " tokens");
    }
    if (span.getStart() < 0 || span.getEnd() > count || span.getStart() >= span.getEnd()) {
      throw new IllegalArgumentException(source + " " + span
          + " outside the sentence's " + count + " tokens");
    }
    return new Span(tokens.get(first + span.getStart()).span().getStart(),
        tokens.get(first + span.getEnd() - 1).span().getEnd());
  }

  private DocumentAnnotators() {
    // Not instantiated; this class provides static support methods only.
  }
}
