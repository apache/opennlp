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
 * Small Unicode helpers shared by the {@link EditDistance} implementations so that all
 * metrics compare on Unicode code points rather than UTF-16 {@code char} units.
 */
final class CodePoints {

  private CodePoints() {
  }

  /**
   * Decodes {@code cs} into its sequence of Unicode code points, so that a supplementary
   * character (encoded as a surrogate pair) counts as a single symbol.
   *
   * @param cs the sequence to decode; must not be {@code null}
   * @return the code points of {@code cs}, in order
   */
  static int[] of(CharSequence cs) {
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

  /** @return the minimum of three integers. */
  static int min3(int x, int y, int z) {
    return Math.min(x, Math.min(y, z));
  }
}
