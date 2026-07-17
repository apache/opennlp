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

import opennlp.tools.postag.POSTagger;

/**
 * Adapts a {@link POSTagger} to the document pipeline: reads {@link Layers#SENTENCES}
 * and {@link Layers#TOKENS} and provides {@link Layers#POS_TAGS}, one tag annotation per
 * token on the token's span.
 *
 * <p>Each sentence is tagged separately, the way the tagger contract expects its input,
 * so tagging decisions never cross a sentence boundary. Token spans already refer to the
 * original document text, so only the token sequence handed to the tagger is sliced per
 * sentence; the produced tag layer stays aligned with {@link Layers#TOKENS} by
 * position.</p>
 *
 * @since 3.0.0
 */
public class POSTaggerAnnotator implements DocumentAnnotator {

  private final POSTagger tagger;

  /**
   * Initializes the adapter.
   *
   * @param tagger The tagger to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code tagger} is {@code null}.
   */
  public POSTaggerAnnotator(POSTagger tagger) {
    if (tagger == null) {
      throw new IllegalArgumentException("tagger must not be null");
    }
    this.tagger = tagger;
  }

  /**
   * Tags the document sentence by sentence and adds the {@link Layers#POS_TAGS} layer.
   *
   * <p>For every sentence, the tokens whose spans lie inside the sentence span are
   * tagged as one sequence, and each tag is emitted on its token's span. The required
   * layers must be present, but they may be empty: a document without sentences or
   * tokens yields a present-but-empty tag layer, and a sentence containing no tokens
   * contributes nothing.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#SENTENCES} and {@link Layers#TOKENS} layers, with
   *                 every token lying inside a sentence.
   * @return A new {@link Document} with the {@link Layers#POS_TAGS} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, the
   *         sentence layer or the token layer is absent, a token lies outside every
   *         sentence, or the tagger does not return one tag per token of a sentence.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final Set<LayerKey<?>> present = document.layers();
    if (!present.contains(Layers.SENTENCES)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.SENTENCES);
    }
    if (!present.contains(Layers.TOKENS)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.TOKENS);
    }
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> tagAnnotations = new ArrayList<>(tokens.size());
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
      for (int i = 0; i < count; i++) {
        words[i] = tokens.get(first + i).value();
      }
      final String[] tags = tagger.tag(words);
      if (tags.length != count) {
        throw new IllegalArgumentException(
            "tagger returned " + tags.length + " tags for " + count + " tokens");
      }
      for (int i = 0; i < count; i++) {
        tagAnnotations.add(new Annotation<>(tokens.get(first + i).span(), tags[i]));
      }
    }
    if (next != tokens.size()) {
      throw new IllegalArgumentException("token at " + tokens.get(next).span()
          + " lies outside every sentence");
    }
    return document.with(Layers.POS_TAGS, tagAnnotations);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.SENTENCES, Layers.TOKENS);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(Layers.POS_TAGS);
  }
}
