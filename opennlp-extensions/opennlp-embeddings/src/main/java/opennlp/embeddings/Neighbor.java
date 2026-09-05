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
package opennlp.embeddings;

import opennlp.tools.util.java.Experimental;

/**
 * One vocabulary token found near a query vector by {@link StaticEmbeddingModel#mostSimilar}
 * or {@link StaticEmbeddingModel#analogy}, most similar first.
 *
 * <p>Warning: Experimental new feature; the API might change in a later release.</p>
 *
 * @param token      The matrix row's text: a tokenizer piece or a term-table entry.
 * @param similarity Cosine similarity to the query vector, in {@code [-1, 1]}.
 * @throws IllegalArgumentException Thrown if {@code token} is {@code null}, or
 *     {@code similarity} is non-finite or outside {@code [-1, 1]}.
 */
@Experimental
public record Neighbor(String token, double similarity) {

  /** Validates the neighbor returned by a similarity search. */
  public Neighbor {
    if (token == null) {
      throw new IllegalArgumentException("token must not be null");
    }
    if (!Double.isFinite(similarity) || similarity < -1.0 || similarity > 1.0) {
      throw new IllegalArgumentException(
          "similarity must be finite and within [-1, 1], got " + similarity);
    }
  }
}
