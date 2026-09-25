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
 * Matches file names against the wildcards accepted by {@link ClassPathModelFinder}
 * implementations. A {@code *} matches any run of code points, including none, a {@code ?}
 * matches exactly one code point, and every other code point stands for itself; there is no
 * escape character. The wildcard must cover the whole input and the comparison is
 * case-sensitive.
 */
final class WildcardMatcher {

  private static final int ANY_RUN = '*';
  private static final int ANY_ONE = '?';

  private WildcardMatcher() {
  }

  /**
   * Tests whether the whole {@code input} is covered by {@code wildcard}. Both are compared by
   * code point, so a supplementary character, or an unpaired surrogate, counts as one
   * code point for {@code ?}. The scan keeps a single backtracking point, the most recent
   * {@code *}, so it never takes more than
   * {@code wildcard.length() * input.length()} steps.
   *
   * @param wildcard The wildcard expression. Must not be {@code null}.
   * @param input The text to test. Must not be {@code null}.
   * @return {@code true} if {@code input} matches {@code wildcard} from start to end,
   *     {@code false} otherwise.
   * @throws IllegalArgumentException Thrown if {@code wildcard} or {@code input} is {@code null}.
   */
  static boolean matches(String wildcard, String input) {
    if (wildcard == null) {
      throw new IllegalArgumentException("wildcard must not be null");
    }
    if (input == null) {
      throw new IllegalArgumentException("input must not be null");
    }
    final int wildcardLength = wildcard.length();
    final int inputLength = input.length();
    int wi = 0;
    int ii = 0;
    int runStartW = -1;
    int runStartI = -1;
    while (ii < inputLength) {
      final int in = input.codePointAt(ii);
      final int w = wi < wildcardLength ? wildcard.codePointAt(wi) : -1;
      if (w == ANY_RUN) {
        runStartW = wi++;
        runStartI = ii;
      } else if (w == ANY_ONE || (w >= 0 && w == in)) {
        wi += Character.charCount(w);
        ii += Character.charCount(in);
      } else if (runStartW >= 0) {
        // let the most recent '*' absorb one more code point and retry after it
        wi = runStartW + 1;
        runStartI += Character.charCount(input.codePointAt(runStartI));
        ii = runStartI;
      } else {
        return false;
      }
    }
    while (wi < wildcardLength && wildcard.charAt(wi) == ANY_RUN) {
      wi++;
    }
    return wi == wildcardLength;
  }
}
