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

package opennlp.tools.tokenize.lattice;

import java.util.List;

import opennlp.tools.util.Span;

/**
 * One morpheme from lattice segmentation: the {@link Span} it covers in the original
 * text, its surface form, and the feature columns its dictionary entry carries.
 *
 * <p>The features are the entry's columns exactly as listed in the dictionary, since
 * different dictionaries carry different schemas: part of speech first by convention,
 * then dictionary-specific columns such as conjugation, base form, or reading. A
 * morpheme produced by unknown-word handling has the unknown entry's features and is
 * marked as such.</p>
 *
 * @param span The location of the morpheme in the original text. Must not be
 *             {@code null}.
 * @param surface The covered text. Must not be {@code null} or empty.
 * @param features The dictionary feature columns. Must not be {@code null}.
 * @param unknown Whether the morpheme came from unknown-word handling rather than a
 *                lexicon entry.
 *
 * @since 3.0.0
 */
public record Morpheme(Span span, String surface, List<String> features,
    boolean unknown) {

  /**
   * Validates the morpheme.
   *
   * @throws IllegalArgumentException Thrown if {@code span}, {@code surface}, or
   *         {@code features} is {@code null}, or {@code surface} is empty.
   */
  public Morpheme {
    if (span == null) {
      throw new IllegalArgumentException("span must not be null");
    }
    if (surface == null || surface.isEmpty()) {
      throw new IllegalArgumentException("surface must not be null or empty");
    }
    if (features == null) {
      throw new IllegalArgumentException("features must not be null");
    }
    features = List.copyOf(features);
  }
}
