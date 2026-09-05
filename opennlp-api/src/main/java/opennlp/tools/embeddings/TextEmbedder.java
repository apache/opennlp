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

import java.util.List;

import opennlp.tools.util.java.Experimental;

/**
 * Encodes text into a fixed-length vector.
 *
 * <p>Unlike {@link opennlp.tools.util.wordvector.WordVectorTable}, which looks up a stored vector
 * for one word, this interface accepts a sentence, paragraph, or document.</p>
 *
 * <p>Thread safety is implementation specific.</p>
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 */
@Experimental
public interface TextEmbedder {

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
   */
  float[] embed(CharSequence text);

  /**
   * Embeds several texts.
   *
   * <p>The default implementation embeds one text at a time. Implementations backed by a
   * runtime that executes batches more efficiently than single inputs should override this
   * method.</p>
   *
   * @param texts The texts to embed. Must not be {@code null} and must not contain {@code null}.
   * @return One embedding vector per input, in input order.
   * @throws IllegalArgumentException Thrown if {@code texts} is {@code null} or contains
   *     {@code null}.
   */
  default float[][] embedAll(List<? extends CharSequence> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    final float[][] vectors = new float[texts.size()][];
    for (int i = 0; i < vectors.length; i++) {
      final CharSequence text = texts.get(i);
      if (text == null) {
        throw new IllegalArgumentException("texts[" + i + "] must not be null");
      }
      vectors[i] = embed(text);
    }
    return vectors;
  }

  /** {@return the dimension of every vector this embedder produces} */
  int dimension();
}
