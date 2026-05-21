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

package opennlp.tools.stopword;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

/**
 * A {@link Tokenizer} decorator which delegates tokenization to a wrapped
 * {@link Tokenizer} and then removes any tokens identified as stopwords by
 * the supplied {@link StopwordFilter}.
 * <p>
 * Both {@link #tokenize(String)} and {@link #tokenizePos(String)} apply the
 * filter using the same greedy longest-match window scan, so single-token
 * (1-gram) and multi-token (n-gram) stopword entries are dropped identically
 * across {@link #tokenize(String)}, {@link #tokenizePos(String)} and
 * {@link StopwordFilterStream}. For {@link #tokenizePos(String)} the
 * {@link Span Spans} covering a matched entry are dropped while the offsets of
 * the remaining spans are kept intact (they continue to refer to positions in
 * the original input string).
 * <p>
 * Instances are immutable and therefore safe for concurrent use provided that
 * both the wrapped {@link Tokenizer} and the {@link StopwordFilter} are
 * thread-safe. {@link DictionaryStopwordFilter} is unconditionally
 * thread-safe; combined with a thread-safe delegate tokenizer
 * (e.g. {@code SimpleTokenizer.INSTANCE}) the resulting decorator is
 * thread-safe with no further synchronization required.
 */
@ThreadSafe
public final class StopwordFilteringTokenizer implements Tokenizer {

  private final Tokenizer delegate;
  private final StopwordFilter filter;

  /**
   * Initializes a {@link StopwordFilteringTokenizer}.
   *
   * @param delegate The underlying {@link Tokenizer} that produces the raw
   *                 tokens. Must not be {@code null}.
   * @param filter   The {@link StopwordFilter} which decides whether a token
   *                 is a stopword. Must not be {@code null}.
   * @throws IllegalArgumentException if {@code delegate} or {@code filter} is
   *                                  {@code null}.
   */
  public StopwordFilteringTokenizer(final Tokenizer delegate, final StopwordFilter filter) {
    if (delegate == null) {
      throw new IllegalArgumentException("delegate must not be null");
    }
    if (filter == null) {
      throw new IllegalArgumentException("filter must not be null");
    }
    this.delegate = delegate;
    this.filter = filter;
  }

  /**
   * Tokenizes the supplied string with the wrapped {@link Tokenizer} and then
   * removes any tokens which the {@link StopwordFilter} considers a stopword.
   *
   * @param s The string to be tokenized.
   * @return  The remaining tokens in their original order.
   */
  @Override
  public String[] tokenize(final String s) {
    return filter.filter(delegate.tokenize(s));
  }

  /**
   * Computes token spans with the wrapped {@link Tokenizer} and then drops
   * the spans covering any stopword entry according to the
   * {@link StopwordFilter}. A greedy left-to-right window scan mirrors
   * {@link StopwordFilter#filter(String[])}: at each position the longest
   * window of consecutive spans whose covered texts form a registered entry is
   * removed; otherwise the current span is kept and the scan advances by one.
   * This way multi-word (n-gram) entries are dropped here exactly as they are
   * by {@link #tokenize(String)}. The relative order and the offsets of the
   * surviving spans are preserved.
   *
   * @param s The string to be tokenized.
   * @return  The remaining {@link Span Spans} in their original order.
   */
  @Override
  public Span[] tokenizePos(final String s) {
    final Span[] spans = delegate.tokenizePos(s);
    if (spans == null || spans.length == 0) {
      return spans;
    }
    final List<Span> kept = new ArrayList<>(spans.length);
    int i = 0;
    while (i < spans.length) {
      int matched = 0;
      // Try the longest possible window first, decreasing down to 1.
      for (int w = spans.length - i; w >= 1; w--) {
        final String[] window = new String[w];
        for (int k = 0; k < w; k++) {
          window[k] = spans[i + k].getCoveredText(s).toString();
        }
        if (filter.isStopword(window)) {
          matched = w;
          break;
        }
      }
      if (matched > 0) {
        i += matched;
      } else {
        kept.add(spans[i]);
        i++;
      }
    }
    return kept.toArray(new Span[0]);
  }
}
