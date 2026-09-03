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

package opennlp.tools.termvector;

import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.Span;

/**
 * One entry of a term vector layer: a term, how often it occurs in the document, and
 * where.
 *
 * <p>The {@link #term()} is the term's identity as the producing annotator determined
 * it, typically a normalized form; the {@link #spans()} are the occurrence offsets and
 * always point into the document's <em>original</em> text, never into a normalized form,
 * so an index consumer can highlight every occurrence in what the caller supplied.</p>
 *
 * <p>A term vector comes in one of two shapes, told apart by whether {@link #spans()} is
 * empty. A <em>full</em> vector carries one span per occurrence, so
 * {@code spans().size() == frequency()}. A <em>scoring-only</em> vector carries no spans
 * at all, so consumers that only need term frequencies do not pay for offset storage.
 * There is no third shape: a non-empty span list must match the frequency exactly.</p>
 *
 * <p>Instances are immutable: the span list is copied on construction and the copy is
 * unmodifiable.</p>
 *
 * @param term The term string. Must not be {@code null}.
 * @param frequency The number of occurrences in the document. Must be at least one.
 * @param spans The occurrence spans in original text coordinates, one per occurrence,
 *              or an empty list for a scoring-only vector. Must not be {@code null} or
 *              contain {@code null} and, when non-empty, must hold exactly
 *              {@code frequency} spans.
 *
 * @since 3.0.0
 */
@ThreadSafe
public record TermVector(String term, int frequency, List<Span> spans) {

  /**
   * Validates the term vector and detaches the span list from the caller's input.
   *
   * @throws IllegalArgumentException Thrown if {@code term} is {@code null},
   *         {@code frequency} is below one, {@code spans} is or contains {@code null},
   *         or a non-empty {@code spans} list does not hold exactly {@code frequency}
   *         spans.
   */
  public TermVector {
    if (term == null) {
      throw new IllegalArgumentException("term must not be null");
    }
    if (frequency < 1) {
      throw new IllegalArgumentException("frequency must be at least one: " + frequency);
    }
    if (spans == null) {
      throw new IllegalArgumentException("spans must not be null");
    }
    for (final Span span : spans) {
      if (span == null) {
        throw new IllegalArgumentException("spans must not contain null");
      }
    }
    if (!spans.isEmpty() && spans.size() != frequency) {
      throw new IllegalArgumentException("a full term vector holds one span per "
          + "occurrence: frequency is " + frequency + " but spans holds " + spans.size());
    }
    spans = List.copyOf(spans);
  }

  /**
   * Creates a full {@link TermVector} whose frequency is derived from the occurrence
   * spans.
   *
   * @param term The term string. Must not be {@code null}.
   * @param spans The occurrence spans in original text coordinates. Must not be
   *              {@code null} or empty.
   * @return A {@link TermVector} with {@code frequency() == spans.size()}. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code term} is {@code null} or
   *         {@code spans} is {@code null} or empty.
   */
  public static TermVector withSpans(String term, List<Span> spans) {
    if (spans == null || spans.isEmpty()) {
      throw new IllegalArgumentException("spans must not be null or empty");
    }
    return new TermVector(term, spans.size(), spans);
  }

  /**
   * Creates a scoring-only {@link TermVector} that carries the occurrence count without
   * any offsets.
   *
   * @param term The term string. Must not be {@code null}.
   * @param frequency The number of occurrences in the document. Must be at least one.
   * @return A {@link TermVector} whose {@link #spans()} is empty. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code term} is {@code null} or
   *         {@code frequency} is below one.
   */
  public static TermVector count(String term, int frequency) {
    return new TermVector(term, frequency, List.of());
  }
}
