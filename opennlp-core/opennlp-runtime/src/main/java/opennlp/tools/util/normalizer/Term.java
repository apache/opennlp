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
package opennlp.tools.util.normalizer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import opennlp.tools.util.Span;

/**
 * One token as a stack of normalization layers. The {@link #original()} form is the source of
 * truth; the other layers are derived, increasingly aggressive {@link Dimension}s tuned for
 * matching. The dimensions configured on the producing {@link TermAnalyzer} are
 * computed eagerly and cached; any other dimension is computed on first request, applied on top of
 * the {@link #normalized() configured form}, and then cached.
 *
 * <p>Because the original is always retained, aggressive folding is safe: a match on a derived layer
 * can always be reported in original coordinates through {@link #span()}. Querying a configured
 * layer, or {@link #peel() peeling} the last-applied one, is O(1); adding an unconfigured dimension
 * costs one transform on first touch and is O(1) thereafter.</p>
 *
 * <p>Instances are created by {@link TermAnalyzer} and are thread-safe as long as the analyzer's
 * configured transforms are (see {@link TermAnalyzer} for the stemmer caveat). Concurrent first
 * requests for the same unconfigured dimension may run its transform more than once, but every
 * thread observes the same cached value.</p>
 */
public final class Term {

  private final TermAnalyzer analyzer;
  private final Span span;
  private final String posTag;
  private final Map<Dimension, String> layers = new ConcurrentHashMap<>();

  /**
   * Creates a term and eagerly computes the analyzer's configured dimensions.
   *
   * @param analyzer The producing analyzer. Must not be {@code null}.
   * @param original The original token text. Must not be {@code null}.
   * @param span     The source span of the token, or {@code null} for pre-tokenized input.
   * @param posTag   The part-of-speech tag, or {@code null} when none is available.
   * @throws NullPointerException if {@code analyzer} or {@code original} is {@code null}.
   * @throws IllegalStateException if a configured dimension needs an engine or tag that is
   *     missing (see {@link TermAnalyzer#apply(Dimension, String, String)}).
   */
  Term(TermAnalyzer analyzer, String original, Span span, String posTag) {
    this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
    Objects.requireNonNull(original, "original");
    this.span = span;
    this.posTag = posTag;
    String value = original;
    layers.put(Dimension.ORIGINAL, value);
    for (final Dimension dimension : analyzer.dimensions()) {
      value = analyzer.apply(dimension, value, posTag);
      layers.put(dimension, value);
    }
  }

  /**
   * {@return the source span of this token, or {@code null} if it was supplied as a pre-tokenized
   * string} The span indexes into the text passed to {@link TermAnalyzer#analyze(CharSequence)}.
   */
  public Span span() {
    return span;
  }

  /**
   * {@return the original token text}
   */
  public String original() {
    return layers.get(Dimension.ORIGINAL);
  }

  /**
   * {@return the token at the analyzer's final configured dimension; equal to {@link #original()}
   * when no dimensions were configured}
   */
  public String normalized() {
    return at(analyzer.finalDimension());
  }

  /**
   * Returns the token at {@code dimension}. Configured dimensions are cached; an unconfigured
   * dimension is computed by applying its transform to {@link #normalized()} and then cached.
   *
   * <p>Note: an unconfigured dimension is applied on top of {@link #normalized()} (the most
   * aggressive configured layer), not spliced into pipeline order. Because the transforms do not
   * commute (see {@link Dimension}), requesting a dimension that ranks <em>earlier</em> than the
   * configured ones can differ from having configured it. For example, asking for
   * {@link Dimension#CASE_FOLD} on an analyzer configured only through {@link Dimension#ACCENT_FOLD}
   * case-folds the already accent-folded text, which is not the same as case-folding first.
   * Configure the dimension on the analyzer when pipeline order matters.</p>
   *
   * @param dimension The dimension to project to. Must not be {@code null}.
   * @return The token at that dimension.
   * @throws NullPointerException if {@code dimension} is {@code null}.
   * @throws IllegalStateException if the dimension needs an engine or tag that was not configured
   *     (see {@link Dimension#STEM} and {@link Dimension#LEMMA}).
   */
  public String at(Dimension dimension) {
    Objects.requireNonNull(dimension, "dimension");
    final String cached = layers.get(dimension);
    if (cached != null) {
      return cached;
    }
    // Computed outside computeIfAbsent so no map lock is held while the transform runs: the
    // transform routes through normalized(), which reads this map, and ConcurrentHashMap forbids
    // re-entrant use from a mapping function. Racing threads may both compute the (deterministic)
    // value; putIfAbsent keeps one winner for everyone.
    final String value = analyzer.apply(dimension, normalized(), posTag);
    final String winner = layers.putIfAbsent(dimension, value);
    return winner != null ? winner : value;
  }

  /**
   * {@return the token at the dimension just below the final configured one} This is the
   * last-applied layer removed (for example the form before stemming when {@link Dimension#STEM}
   * is the final dimension); equal to {@link #original()} when at most one dimension is configured.
   */
  public String peel() {
    final List<Dimension> dimensions = analyzer.dimensions();
    if (dimensions.size() < 2) {
      return original();
    }
    return at(dimensions.get(dimensions.size() - 2));
  }
}
