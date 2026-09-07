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
package opennlp.tools.models;

/**
 * Matches file names against the wildcard globs accepted by {@link ClassPathModelFinder}
 * implementations. A {@code *} matches any run of characters, including none, a {@code ?}
 * matches exactly one character, and every other character stands for itself. Neither
 * wildcard crosses a line terminator (line feed, carriage return, next line, line
 * separator, or paragraph separator). The glob must cover the whole input.
 */
final class GlobMatcher {

  private static final int ANY_RUN = '*';
  private static final int ANY_ONE = '?';

  private GlobMatcher() {
  }

  /**
   * Tests whether the whole {@code input} is covered by {@code glob}. Both are compared by
   * code point, so a supplementary character counts as one character for {@code ?}.
   *
   * @param glob The wildcard expression. Must not be {@code null}.
   * @param input The text to test. Must not be {@code null}.
   * @return {@code true} if {@code input} matches {@code glob} from start to end,
   *     {@code false} otherwise.
   */
  static boolean matches(String glob, String input) {
    final int[] g = glob.codePoints().toArray();
    final int[] in = input.codePoints().toArray();
    int gi = 0;
    int ii = 0;
    int runStartG = -1;
    int runStartI = -1;
    while (ii < in.length) {
      if (gi < g.length && g[gi] == ANY_RUN) {
        runStartG = gi++;
        runStartI = ii;
      } else if (gi < g.length && matchesOne(g[gi], in[ii])) {
        gi++;
        ii++;
      } else if (runStartG >= 0 && !isLineTerminator(in[runStartI])) {
        // let the most recent '*' absorb one more character and retry after it
        gi = runStartG + 1;
        ii = ++runStartI;
      } else {
        return false;
      }
    }
    while (gi < g.length && g[gi] == ANY_RUN) {
      gi++;
    }
    return gi == g.length;
  }

  private static boolean matchesOne(int globCodePoint, int inputCodePoint) {
    if (globCodePoint == ANY_ONE) {
      return !isLineTerminator(inputCodePoint);
    }
    return globCodePoint == inputCodePoint;
  }

  private static boolean isLineTerminator(int codePoint) {
    return codePoint == '\n' || codePoint == '\r' || codePoint == '\u0085'
        || codePoint == '\u2028' || codePoint == '\u2029';
  }
}
