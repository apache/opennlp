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

package opennlp.tools.chunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.DocumentAnnotators;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

/**
 * Adapts a {@link Chunker} to the document pipeline: reads {@link Layers#SENTENCES},
 * {@link Layers#TOKENS}, and {@link Layers#POS_TAGS} and provides {@link #CHUNKS}, one
 * annotation per phrase chunk carrying the chunk type, for example {@code NP} or
 * {@code VP}, on the span from its first to its last token.
 *
 * <p>Each sentence is chunked separately with its tokens and tags as one sequence, the
 * way the chunker contract expects its input. A chunker's spans index tokens within the
 * sentence; the adapter maps them onto the token spans, which already refer to the
 * original text, so a chunk covers exactly the text of its tokens. Chunks are emitted in
 * text order.</p>
 *
 * <p>The adapter holds no per-call state; it is as thread-safe as the chunker it
 * wraps.</p>
 *
 * @since 3.0.0
 */
public final class ChunkerAnnotator implements DocumentAnnotator {

  /**
   * Phrase chunks; each annotation covers one chunk and carries its type, ordered by
   * text position.
   */
  public static final LayerKey<String> CHUNKS = Layers.key("chunks", String.class);

  /** Introduces a rejected chunk in exception messages. */
  private static final String CHUNK_SOURCE = "chunker returned chunk";

  private final Chunker chunker;

  /**
   * Initializes the adapter.
   *
   * @param chunker The chunker to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code chunker} is {@code null}.
   */
  public ChunkerAnnotator(Chunker chunker) {
    if (chunker == null) {
      throw new IllegalArgumentException("chunker must not be null");
    }
    this.chunker = chunker;
  }

  /**
   * Chunks the document sentence by sentence and adds the {@link #CHUNKS} layer.
   *
   * <p>The required layers must be present, but they may be empty: a document without
   * sentences or tokens yields a present-but-empty chunk layer. The token and tag
   * layers must be aligned one to one.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#SENTENCES}, {@link Layers#TOKENS}, and
   *                 {@link Layers#POS_TAGS} layers, with every token lying inside a
   *                 sentence.
   * @return A new {@link Document} with the {@link #CHUNKS} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, a
   *         required layer is absent, the token and tag layers differ in size, a token
   *         lies outside every sentence, or the chunker returns a span outside the
   *         sentence, an empty span, or a span without a type.
   */
  @Override
  public Document annotate(Document document) {
    DocumentAnnotators.requireLayers(document, Layers.SENTENCES, Layers.TOKENS,
        Layers.POS_TAGS);
    DocumentAnnotators.requireAligned(document, Layers.TOKENS, Layers.POS_TAGS);
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> tags = document.get(Layers.POS_TAGS);
    final List<Annotation<String>> chunks = new ArrayList<>();
    DocumentAnnotators.forEachSentence(sentences, tokens, (first, words) -> {
      final String[] sentenceTags = DocumentAnnotators.values(tags, first, words.length);
      for (final Span chunk : chunker.chunkAsSpans(words, sentenceTags)) {
        final Span covered = DocumentAnnotators.toCharacterSpan(tokens, first,
            words.length, chunk, CHUNK_SOURCE);
        if (chunk.getType() == null) {
          throw new IllegalArgumentException(CHUNK_SOURCE + " " + chunk + " without a type");
        }
        chunks.add(new Annotation<>(covered, chunk.getType()));
      }
    });
    return document.with(CHUNKS, chunks);
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS);
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(CHUNKS);
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
