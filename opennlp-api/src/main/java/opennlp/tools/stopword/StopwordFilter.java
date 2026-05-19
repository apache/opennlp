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

import java.util.Set;

/**
 * A pluggable filter that decides whether a token (or a sequence of tokens)
 * is a stopword that should be removed during downstream text processing.
 * <p>
 * Implementations may be backed by a static bundled list, a user-supplied
 * file, an in-memory data structure, or any other source.
 * Both single-token and multi-token (n-gram) membership tests are supported.
 *
 * @see opennlp.tools.util.LanguageCodeValidator
 */
public interface StopwordFilter {

  /**
   * Checks whether the given token is a single-token stopword.
   * Equivalent to {@code isStopword(new String[] { token.toString() })} when
   * {@code token} is non-{@code null}.
   *
   * @param token The token to test. May be {@code null}, in which case
   *     implementations should return {@code false}.
   * @return {@code true} if {@code token} is registered as a single-token
   *     stopword, {@code false} otherwise.
   */
  boolean isStopword(final CharSequence token);

  /**
   * Checks whether the given sequence of tokens is a multi-token stopword
   * (n-gram). For a single token this is equivalent to
   * {@link #isStopword(CharSequence)}.
   *
   * @param tokens The tokens to test as one entry. May be {@code null} or
   *     empty, in which case implementations should return {@code false}.
   * @return {@code true} if the sequence is registered as a stopword,
   *     {@code false} otherwise.
   */
  boolean isStopword(final String... tokens);

  /**
   * Returns a copy of {@code tokens} with stopword matches removed,
   * preserving the input order.
   * <p>
   * Implementations should honor both 1-gram and n-gram entries. A
   * recommended strategy is a greedy left-to-right window scan: at each
   * position try the longest registered window first; if it matches, skip
   * those tokens; otherwise advance by one and keep the current token.
   * Implementations that do not support n-gram entries may fall back to
   * 1-gram filtering.
   *
   * @param tokens The input token array. Must not be {@code null}.
   *     Individual array elements may be {@code null} and are kept as-is.
   * @return A new array containing the surviving tokens. Never {@code null}.
   * @throws IllegalArgumentException if {@code tokens} is {@code null}.
   */
  String[] filter(final String[] tokens);

  /**
   * @return {@code true} if this filter performs case-sensitive matching;
   *     {@code false} if matching is case-insensitive.
   */
  boolean isCaseSensitive();

  /**
   * Returns an unmodifiable snapshot of the registered single-token
   * stopwords. Multi-token (n-gram) entries are not included in this view
   * and must be tested via {@link #isStopword(String...)}.
   * <p>
   * Attempts to mutate the returned {@link Set} will fail.
   *
   * @return An unmodifiable {@link Set} of stopwords. Never {@code null}.
   * @throws UnsupportedOperationException if a caller attempts to add to,
   *     remove from, or otherwise mutate the returned {@link Set}.
   */
  Set<String> stopwords();
}
