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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.AlignedText;
import opennlp.tools.util.normalizer.OffsetAwareNormalizer;

/**
 * Rolls the token layer up into a term vector layer for index consumers: one
 * {@link TermVector} per distinct term, carrying the term string, its occurrence count,
 * and (in {@link Mode#FULL full mode}) the occurrence offsets.
 *
 * <p>Term identity comes from the annotator's inputs, not from logic of its own. Without
 * a normalizer, the term is the token layer's value as-is, that is, the token's covered
 * text in the original document. With an {@link OffsetAwareNormalizer}, the whole
 * document text is normalized once with its alignment recorded, each token span is
 * mapped forward to the normalized form, and the covered normalized text is the term, so
 * tokens that differ only by a normalization fold (case, an eszett expansion, collapsed
 * whitespace) group together. Either way, the occurrence spans emitted in
 * {@link Mode#FULL full mode} are the token layer's own spans and therefore always
 * point into the original text. A token whose normalized form is empty, for example one
 * the normalizer deleted entirely, groups under the empty string rather than being
 * dropped.</p>
 *
 * <p>The layer is {@link LayerKey.Scope#DOCUMENT document-scoped}: each {@link TermVector}
 * is a whole-document statistic, so the annotations carry no span of their own and the
 * occurrence offsets live inside the payload. The layer preserves first-occurrence
 * order: the first token of a term fixes its position in the layer.</p>
 *
 * <p>The annotator holds no per-call state; it is as thread-safe as the normalizer it
 * was built with.</p>
 *
 * @since 3.0.0
 */
public class TermVectorAnnotator implements DocumentAnnotator {

  /**
   * The key of the term vector layer this annotator provides: a document-scoped layer
   * of {@link TermVector} values, one per distinct term.
   */
  public static final LayerKey<TermVector> TERM_VECTORS =
      Layers.documentKey("term-vectors", TermVector.class);

  /** How much each {@link TermVector} records. */
  public enum Mode {
    /**
     * Counts occurrences and stores every occurrence span in original text coordinates.
     */
    FULL,
    /**
     * Counts occurrences only; the emitted {@link TermVector term vectors} carry no
     * spans, so scoring-only consumers do not pay for offset storage.
     */
    SCORING_ONLY
  }

  private final OffsetAwareNormalizer normalizer;
  private final Mode mode;

  /**
   * Initializes a {@link Mode#FULL full mode} annotator that groups tokens by their
   * covered text as-is.
   */
  public TermVectorAnnotator() {
    this(Mode.FULL);
  }

  /**
   * Initializes an annotator that groups tokens by their covered text as-is.
   *
   * @param mode How much each {@link TermVector} records. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code mode} is {@code null}.
   */
  public TermVectorAnnotator(Mode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    this.normalizer = null;
    this.mode = mode;
  }

  /**
   * Initializes a {@link Mode#FULL full mode} annotator that groups tokens by their
   * normalized form.
   *
   * @param normalizer The normalizer that defines term identity, applied to the whole
   *                   document text so token spans can be mapped into the normalized
   *                   form through its alignment. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code normalizer} is {@code null}.
   */
  public TermVectorAnnotator(OffsetAwareNormalizer normalizer) {
    this(normalizer, Mode.FULL);
  }

  /**
   * Initializes an annotator that groups tokens by their normalized form.
   *
   * @param normalizer The normalizer that defines term identity, applied to the whole
   *                   document text so token spans can be mapped into the normalized
   *                   form through its alignment. Must not be {@code null}.
   * @param mode How much each {@link TermVector} records. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code normalizer} or {@code mode} is
   *         {@code null}.
   */
  public TermVectorAnnotator(OffsetAwareNormalizer normalizer, Mode mode) {
    if (normalizer == null) {
      throw new IllegalArgumentException("normalizer must not be null");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    this.normalizer = normalizer;
    this.mode = mode;
  }

  /**
   * Aggregates the token layer into the {@link #TERM_VECTORS} layer. A present-but-empty
   * token layer yields a present-but-empty term vector layer.
   *
   * @param document The document to annotate. Must not be {@code null} and must contain
   *                 the {@link Layers#TOKENS} layer.
   * @return A new {@link Document} with the {@link #TERM_VECTORS} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, lacks
   *         the {@link Layers#TOKENS} layer, or already carries the
   *         {@link #TERM_VECTORS} layer.
   */
  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    if (!document.layers().contains(Layers.TOKENS)) {
      throw new IllegalArgumentException("document lacks the required layer "
          + Layers.TOKENS);
    }
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final AlignedText aligned =
        normalizer != null ? normalizer.normalizeAligned(document.text()) : null;
    final String normalized = aligned != null ? aligned.normalizedString() : null;
    return document.with(TERM_VECTORS, mode == Mode.FULL
        ? fullVectors(tokens, aligned, normalized)
        : countVectors(tokens, aligned, normalized));
  }

  /**
   * Aggregates the tokens into full term vectors, keeping every occurrence span.
   *
   * @param tokens The token layer.
   * @param aligned The normalized document text with its alignment, or {@code null} when
   *                no normalizer is present.
   * @param normalized The normalized document text, or {@code null} when no normalizer is
   *                   present.
   * @return One annotation per distinct term, in first-occurrence order.
   */
  private List<Annotation<TermVector>> fullVectors(List<Annotation<String>> tokens,
      AlignedText aligned, String normalized) {
    final Map<String, List<Span>> spansByTerm = new LinkedHashMap<>();
    for (final Annotation<String> token : tokens) {
      spansByTerm.computeIfAbsent(termOf(token, aligned, normalized), key -> new ArrayList<>())
          .add(token.span());
    }
    final List<Annotation<TermVector>> vectors = new ArrayList<>(spansByTerm.size());
    for (final Map.Entry<String, List<Span>> entry : spansByTerm.entrySet()) {
      vectors.add(Annotation.of(TermVector.withSpans(entry.getKey(), entry.getValue())));
    }
    return vectors;
  }

  /**
   * Aggregates the tokens into scoring-only term vectors, so no offset storage is ever
   * allocated.
   *
   * @param tokens The token layer.
   * @param aligned The normalized document text with its alignment, or {@code null} when
   *                no normalizer is present.
   * @param normalized The normalized document text, or {@code null} when no normalizer is
   *                   present.
   * @return One annotation per distinct term, in first-occurrence order.
   */
  private List<Annotation<TermVector>> countVectors(List<Annotation<String>> tokens,
      AlignedText aligned, String normalized) {
    final Map<String, Integer> frequencies = new LinkedHashMap<>();
    for (final Annotation<String> token : tokens) {
      frequencies.merge(termOf(token, aligned, normalized), 1, Integer::sum);
    }
    final List<Annotation<TermVector>> vectors = new ArrayList<>(frequencies.size());
    for (final Map.Entry<String, Integer> entry : frequencies.entrySet()) {
      vectors.add(Annotation.of(TermVector.count(entry.getKey(), entry.getValue())));
    }
    return vectors;
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.TOKENS);
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(TERM_VECTORS);
  }

  /**
   * Determines the term one token groups under: its covered text as-is, or the covered
   * text of its span mapped into the normalized form when a normalizer is present.
   *
   * @param token The token annotation.
   * @param aligned The normalized document text with its alignment, or {@code null}
   *                when no normalizer is present.
   * @param normalized The normalized document text, or {@code null} when no normalizer is
   *                   present.
   * @return The term string. Never {@code null}, possibly empty.
   */
  private String termOf(Annotation<String> token, AlignedText aligned, String normalized) {
    if (aligned == null) {
      return token.value();
    }
    final Span span = aligned.toNormalizedSpan(token.span().getStart(), token.span().getEnd());
    return normalized.substring(span.getStart(), span.getEnd());
  }
}
