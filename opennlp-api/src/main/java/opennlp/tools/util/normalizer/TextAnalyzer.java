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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import opennlp.tools.util.Span;

/**
 * Splits text into tokens and normalizes each one, keeping every token's original character span.
 *
 * <p>This is the offset-preserving building block for search and BM25-style matching: tokens are
 * found with a {@link CharClass} splitter (O(1) membership, a single cursor pass, no regular
 * expression) and each token's text is run through a {@link CharSequenceNormalizer}. The result is
 * a list of {@link AnalyzedToken}, each carrying the source {@link Span} alongside its normalized
 * form, so a match on the normalized term can always be reported and highlighted against the
 * original text even when normalization changes a token's length.</p>
 */
public final class TextAnalyzer {

  private final CharClass splitter;
  private final CharSequenceNormalizer normalizer;

  /**
   * Creates an analyzer.
   *
   * @param splitter The character class whose members delimit tokens (typically
   *     {@link CharClass#whitespace()}).
   * @param normalizer The per-token normalizer.
   */
  public TextAnalyzer(CharClass splitter, CharSequenceNormalizer normalizer) {
    this.splitter = Objects.requireNonNull(splitter, "splitter");
    this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
  }

  /**
   * Creates an analyzer that splits on Unicode whitespace.
   *
   * @param normalizer The per-token normalizer.
   * @return The analyzer.
   */
  public static TextAnalyzer whitespace(CharSequenceNormalizer normalizer) {
    return new TextAnalyzer(CharClass.whitespace(), normalizer);
  }

  /**
   * Tokenizes {@code text} and normalizes each token.
   *
   * @param text The text to analyze.
   * @return The analyzed tokens, in order, each with its source span and normalized form.
   */
  public List<AnalyzedToken> analyze(CharSequence text) {
    Objects.requireNonNull(text, "text");
    final List<AnalyzedToken> tokens = new ArrayList<>();
    for (final Span span : splitter.splitSpans(text)) {
      final String original = text.subSequence(span.getStart(), span.getEnd()).toString();
      final String normalized = normalizer.normalize(original).toString();
      tokens.add(new AnalyzedToken(span, original, normalized));
    }
    return tokens;
  }

  /**
   * Tokenizes {@code text} and returns only the normalized terms.
   *
   * @param text The text to analyze.
   * @return The normalized token terms, in order.
   */
  public List<String> terms(CharSequence text) {
    final List<AnalyzedToken> analyzed = analyze(text);
    final List<String> terms = new ArrayList<>(analyzed.size());
    for (final AnalyzedToken token : analyzed) {
      terms.add(token.normalized());
    }
    return terms;
  }
}
