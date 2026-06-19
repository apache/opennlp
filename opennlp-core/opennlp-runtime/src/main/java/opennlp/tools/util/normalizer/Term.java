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

import java.util.EnumMap;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * One token as a stack of normalization layers. The {@link #original()} form is the canonical
 * source of truth; the other layers are derived, increasingly aggressive {@link Dimension}s tuned
 * for matching and search. The dimensions configured on the producing {@link TermAnalyzer} are
 * computed eagerly and cached; any other dimension is computed on first request, applied on top of
 * the {@link #normalized() configured form}, and then cached.
 *
 * <p>Because the original is always retained, aggressive folding is safe: a match on a derived layer
 * can always be reported in original coordinates through {@link #span()}. Querying a configured
 * layer, or {@link #peel() peeling} the last-applied one, is O(1); adding an unconfigured dimension
 * costs one transform on first touch and is O(1) thereafter.</p>
 *
 * <p>Instances are created by {@link TermAnalyzer} and are not thread-safe (the lazy cache is
 * mutated on first access of an unconfigured dimension).</p>
 */
public final class Term {

  private final TermAnalyzer analyzer;
  private final Span span;
  private final String posTag;
  private final EnumMap<Dimension, String> layers = new EnumMap<>(Dimension.class);

  Term(TermAnalyzer analyzer, String original, Span span, String posTag) {
    this.analyzer = analyzer;
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
   * {@return the token at the analyzer's final configured dimension} Equal to {@link #original()}
   * when no dimensions were configured.
   */
  public String normalized() {
    return at(analyzer.finalDimension());
  }

  /**
   * Returns the token at {@code dimension}. Configured dimensions are cached; an unconfigured
   * dimension is computed by applying its transform to {@link #normalized()} and then cached.
   *
   * @param dimension The dimension to project to.
   * @return The token at that dimension.
   * @throws IllegalStateException if the dimension needs an engine or tag that was not configured
   *     (see {@link Dimension#STEM} and {@link Dimension#LEMMA}).
   */
  public String at(Dimension dimension) {
    final String cached = layers.get(dimension);
    if (cached != null) {
      return cached;
    }
    final String value = analyzer.apply(dimension, normalized(), posTag);
    layers.put(dimension, value);
    return value;
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
