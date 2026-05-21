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

package opennlp.spellcheck.distance;

/**
 * Plain Levenshtein edit distance (insertions, deletions, substitutions; no
 * transpositions). This is a thin adapter over
 * {@link org.apache.commons.text.similarity.LevenshteinDistance} and is offered as a
 * selectable alternative to the default {@link DamerauOSADistance}.
 *
 * <p>It honors the bounded {@link EditDistance} contract by delegating to a
 * threshold-aware Commons Text instance.</p>
 *
 * <p>Note that Commons Text computes distances over UTF-16 {@code char} units, so
 * supplementary characters count as two symbols here. For full code-point correctness
 * prefer {@link DamerauOSADistance}.</p>
 */
public final class LevenshteinDistance implements EditDistance {

  /** Shared, stateless instance. */
  public static final LevenshteinDistance INSTANCE = new LevenshteinDistance();

  public LevenshteinDistance() {
  }

  @Override
  public int distance(CharSequence a, CharSequence b, int max) {
    if (a == null || b == null) {
      throw new NullPointerException("input sequences must not be null");
    }
    if (max < 0) {
      throw new IllegalArgumentException("max must not be negative: " + max);
    }
    // The threshold-aware Commons Text instance returns -1 when the distance exceeds
    // the supplied threshold, which matches our contract exactly.
    return new org.apache.commons.text.similarity.LevenshteinDistance(max).apply(a, b);
  }
}
