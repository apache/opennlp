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

package opennlp.spellcheck;

import java.util.Objects;

/**
 * An immutable spelling suggestion produced by a {@link SpellChecker}.
 *
 * <p>The {@linkplain #compareTo(SuggestItem) natural ordering} sorts by ascending
 * {@link #editDistance()} first, then by descending {@link #frequency()}, and finally by
 * the {@link #term()} as a tie-breaker, so the best candidate sorts first. The term
 * tie-breaker keeps the natural ordering <i>consistent with {@link #equals(Object)}</i>:
 * two items compare equal only when they are equal, so distinct suggestions that tie on
 * distance and frequency are never silently dropped from a {@link java.util.TreeSet} or
 * {@link java.util.TreeMap}.</p>
 *
 * @param term         the suggested (corrected) term; never {@code null}
 * @param editDistance the edit distance between the suggestion and the queried term
 * @param frequency    the corpus frequency (count) of the suggested term
 */
public record SuggestItem(String term, int editDistance, long frequency)
    implements Comparable<SuggestItem> {

  public SuggestItem {
    Objects.requireNonNull(term, "term must not be null");
  }

  @Override
  public int compareTo(SuggestItem other) {
    final int byDistance = Integer.compare(this.editDistance, other.editDistance);
    if (byDistance != 0) {
      return byDistance;
    }
    final int byFrequency = Long.compare(other.frequency, this.frequency);
    if (byFrequency != 0) {
      return byFrequency;
    }
    return this.term.compareTo(other.term);
  }
}
