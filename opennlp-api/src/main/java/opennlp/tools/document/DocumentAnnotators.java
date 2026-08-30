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

/**
 * Support methods shared by {@link DocumentAnnotator} implementations: the
 * required-layer check and the per-sentence walk over the token layer.
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
      final String[] words = new String[count];
      for (int i = 0; i < count; i++) {
        words[i] = tokens.get(first + i).value();
      }
      consumer.accept(first, words);
    }
    if (next != tokens.size()) {
      throw new IllegalArgumentException("token at " + tokens.get(next).span()
          + " lies outside every sentence");
    }
  }

  private DocumentAnnotators() {
    // Not instantiated; this class provides static support methods only.
  }
}
