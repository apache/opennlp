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
package opennlp.tools.embeddings;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Encodes text into a fixed-length vector.
 *
 * <p>Unlike {@link opennlp.tools.util.wordvector.WordVectorTable}, which looks up a stored vector
 * for one word, this interface accepts a sentence, paragraph, or document.</p>
 *
 * <p>Implementations must be safe for concurrent use of {@link #embed(CharSequence)},
 * {@link #embedAll(List)} and {@link #dimension()} until {@link #close()} is called. They may
 * serialize calls internally: the guarantee is correctness, not parallelism.</p>
 *
 * <p>Every returned array is new and owned by the caller. Vectors are not necessarily of unit
 * length unless the implementation documents it. Text longer than the implementation accepts is
 * either truncated or rejected with an {@link IllegalArgumentException}; each implementation
 * documents which.</p>
 *
 * @see TextEmbedderProvider
 * @since 3.0.0
 */
public interface TextEmbedder extends Closeable {

  /**
   * Embeds a piece of text.
   *
   * <p>Behavior for empty text, or text with no tokens the embedder recognizes, is
   * implementation-defined: an implementation may return a zero vector, the vector of a special
   * or fallback token, or something else, and should document its choice. Callers that need a
   * uniform response should handle it themselves.</p>
   *
   * @param text The text to embed. Must not be {@code null}.
   * @return The embedding vector, of length {@link #dimension()}.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   * @throws EmbeddingException Thrown if the text could not be embedded.
   * @throws IllegalStateException Thrown if this embedder is closed.
   */
  float[] embed(CharSequence text);

  /**
   * Embeds several texts. All texts are checked before any of them is embedded. The
   * implementation forms its own batches, so callers may pass a list of any size.
   *
   * <p>The default implementation embeds one text at a time. Implementations backed by a
   * runtime that executes batches more efficiently than single inputs should override this
   * method.</p>
   *
   * @param texts The texts to embed. Must not be {@code null} and must not contain {@code null}.
   * @return One embedding vector per input, in input order.
   * @throws IllegalArgumentException Thrown if {@code texts} is {@code null} or contains
   *     {@code null}.
   * @throws EmbeddingException Thrown if the texts could not be embedded.
   * @throws IllegalStateException Thrown if this embedder is closed.
   */
  default float[][] embedAll(List<? extends CharSequence> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    final CharSequence[] checked = new CharSequence[texts.size()];
    for (int i = 0; i < checked.length; i++) {
      checked[i] = texts.get(i);
      if (checked[i] == null) {
        throw new IllegalArgumentException("texts[" + i + "] must not be null");
      }
    }
    final float[][] vectors = new float[checked.length][];
    for (int i = 0; i < checked.length; i++) {
      vectors[i] = embed(checked[i]);
    }
    return vectors;
  }

  /**
   * {@return the dimension of every vector this embedder produces} The value is constant and
   * never requires an inference.
   */
  int dimension();

  /**
   * Releases resources owned by this embedder. Calling it more than once has no further effect.
   * The default implementation does nothing. Callers must not race this method with embedding
   * calls.
   *
   * @throws IOException Thrown if releasing backend resources fails.
   */
  @Override
  default void close() throws IOException {
  }
}
