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
 * transpositions). Offered as a selectable alternative to the default
 * {@link DamerauOSADistance}, with which it shares the bounded {@link EditDistance}
 * contract; the only behavioural difference is that an adjacent transposition costs two
 * edits here (one deletion plus one insertion) rather than one.
 *
 * <p>The computation is bounded with early exit and is Unicode-aware: comparison happens
 * on Unicode code points, so characters outside the Basic Multilingual Plane (e.g. many
 * emoji) are treated as single symbols.</p>
 *
 * <p>Instances are immutable and thread-safe.</p>
 */
public final class LevenshteinDistance implements EditDistance {

  /** Shared, stateless instance. */
  public static final LevenshteinDistance INSTANCE = new LevenshteinDistance();

  public LevenshteinDistance() {
  }

  @Override
  public int distance(CharSequence a, CharSequence b, int max) {
    if (a == null || b == null) {
      throw new IllegalArgumentException("input sequences must not be null");
    }
    if (max < 0) {
      throw new IllegalArgumentException("max must not be negative: " + max);
    }

    int[] s1 = CodePoints.of(a);
    int[] s2 = CodePoints.of(b);

    // Keep the shorter string as the columns to minimise memory.
    if (s1.length > s2.length) {
      final int[] tmp = s1;
      s1 = s2;
      s2 = tmp;
    }

    int len1 = s1.length;
    int len2 = s2.length;

    // Trim the common suffix; it contributes nothing to the distance.
    while (len1 > 0 && s1[len1 - 1] == s2[len2 - 1]) {
      len1--;
      len2--;
    }

    // Trim the common prefix.
    int offset = 0;
    while (offset < len1 && s1[offset] == s2[offset]) {
      offset++;
    }
    len1 -= offset;
    len2 -= offset;

    if (len1 == 0) {
      return len2 <= max ? len2 : -1;
    }
    // Minimum possible distance is the length difference.
    if (len2 - len1 > max) {
      return -1;
    }

    // prev = row i-1, cur = row i of the classic two-row DP.
    final int[] prev = new int[len1 + 1];
    final int[] cur = new int[len1 + 1];
    for (int j = 0; j <= len1; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= len2; i++) {
      final int c2 = s2[offset + i - 1];
      cur[0] = i;
      int rowMin = cur[0];

      for (int j = 1; j <= len1; j++) {
        final int c1 = s1[offset + j - 1];
        final int cost = (c1 == c2) ? 0 : 1;
        final int value = CodePoints.min3(
            prev[j] + 1,        // deletion
            cur[j - 1] + 1,     // insertion
            prev[j - 1] + cost  // substitution / match
        );
        cur[j] = value;
        if (value < rowMin) {
          rowMin = value;
        }
      }

      // Every cell in this row is already at least max+1, so the result cannot drop to max.
      if (rowMin > max) {
        return -1;
      }

      System.arraycopy(cur, 0, prev, 0, len1 + 1);
    }

    final int result = prev[len1];
    return result <= max ? result : -1;
  }
}
