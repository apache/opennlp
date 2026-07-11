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

/**
 * Encodes a piece of text into a single fixed-length vector.
 *
 * <p>A text embedder maps whole texts (a sentence, a paragraph, a document) into one dense
 * vector whose geometry carries meaning: texts about the same thing land near each other. This
 * is the text-level counterpart of {@link opennlp.tools.util.wordvector.WordVectorTable}, which
 * looks up a stored vector for a single word; an embedder composes a vector for text it has
 * never seen, handling tokenization and pooling internally.</p>
 *
 * <p>Implementations are expected to be safe for concurrent use by multiple threads; any
 * implementation that is not must document it. Implementation failures during encoding (a
 * backing runtime error, a corrupted model) surface as unchecked exceptions carrying the
 * underlying cause.</p>
 */
public interface TextEmbedder {

  /**
   * Embeds a piece of text.
   *
   * @param text The text to embed; must not be null.
   * @return The embedding vector, of length {@link #dimension()}.
   * @throws IllegalArgumentException Thrown if {@code text} is null.
   */
  float[] embed(CharSequence text);

  /**
   * Embeds several texts.
   *
   * <p>The default implementation embeds one text at a time. Implementations backed by a
   * runtime that executes batches more efficiently than single inputs should override this
   * method.</p>
   *
   * @param texts The texts to embed; must not be null and must not contain null.
   * @return One embedding vector per input, in input order.
   * @throws IllegalArgumentException Thrown if {@code texts} is null or contains null.
   */
  default float[][] embedAll(List<? extends CharSequence> texts) {
    if (texts == null) {
      throw new IllegalArgumentException("Texts must not be null");
    }
    final float[][] vectors = new float[texts.size()][];
    for (int i = 0; i < vectors.length; i++) {
      vectors[i] = embed(texts.get(i));
    }
    return vectors;
  }

  /** {@return the dimension of every vector this embedder produces} */
  int dimension();
}
