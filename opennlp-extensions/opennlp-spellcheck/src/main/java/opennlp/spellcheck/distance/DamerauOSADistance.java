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
 * Optimal String Alignment (restricted Damerau-Levenshtein) edit distance.
 *
 * <p>Counts insertions, deletions and substitutions, plus transpositions of two
 * adjacent symbols, each with a unit cost. As an optimal-string-alignment metric, a
 * given substring may not be edited more than once, which is the variant used by the
 * SymSpell reference implementation.</p>
 *
 * <p>This is the {@linkplain #INSTANCE default} edit distance for the engine. It is
 * Unicode-aware: comparison happens on Unicode code points, so characters outside the
 * Basic Multilingual Plane (e.g. many emoji) are treated as single symbols.</p>
 *
 * <p>Instances are immutable and thread-safe. A bounded computation with early exit is
 * provided through {@link #distance(CharSequence, CharSequence, int)}.</p>
 */
public final class DamerauOSADistance implements EditDistance {

  /** Shared, stateless instance. */
  public static final DamerauOSADistance INSTANCE = new DamerauOSADistance();

  public DamerauOSADistance() {
  }

  @Override
  public int distance(CharSequence a, CharSequence b, int max) {
    if (a == null || b == null) {
      throw new NullPointerException("input sequences must not be null");
    }
    if (max < 0) {
      throw new IllegalArgumentException("max must not be negative: " + max);
    }

    int[] s1 = toCodePoints(a);
    int[] s2 = toCodePoints(b);

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

    // prev = row i-1, cur = row i, before = row i-2 (for transpositions).
    final int[] prev = new int[len1 + 1];
    final int[] cur = new int[len1 + 1];
    final int[] before = new int[len1 + 1];
    for (int j = 0; j <= len1; j++) {
      prev[j] = j;
    }

    for (int i = 1; i <= len2; i++) {
      final int c2 = s2[offset + i - 1];
      cur[0] = i;
      int rowMin = cur[0];
      final int prevC2 = i >= 2 ? s2[offset + i - 2] : -1;

      for (int j = 1; j <= len1; j++) {
        final int c1 = s1[offset + j - 1];
        final int cost = (c1 == c2) ? 0 : 1;
        int value = min3(
            prev[j] + 1,        // deletion
            cur[j - 1] + 1,     // insertion
            prev[j - 1] + cost  // substitution / match
        );
        if (i >= 2 && j >= 2 && c1 == prevC2 && s1[offset + j - 2] == c2) {
          value = Math.min(value, before[j - 2] + 1); // transposition
        }
        cur[j] = value;
        if (value < rowMin) {
          rowMin = value;
        }
      }

      if (rowMin > max) {
        return -1;
      }

      // Rotate rows: before <- prev <- cur, and reuse 'before' array as next 'cur'.
      System.arraycopy(prev, 0, before, 0, len1 + 1);
      System.arraycopy(cur, 0, prev, 0, len1 + 1);
    }

    final int result = prev[len1];
    return result <= max ? result : -1;
  }

  private static int min3(int x, int y, int z) {
    return Math.min(x, Math.min(y, z));
  }

  private static int[] toCodePoints(CharSequence cs) {
    final int charLen = cs.length();
    final int[] cps = new int[charLen];
    int count = 0;
    for (int i = 0; i < charLen; ) {
      final int cp = Character.codePointAt(cs, i);
      cps[count++] = cp;
      i += Character.charCount(cp);
    }
    if (count == charLen) {
      return cps;
    }
    final int[] trimmed = new int[count];
    System.arraycopy(cps, 0, trimmed, 0, count);
    return trimmed;
  }
}
