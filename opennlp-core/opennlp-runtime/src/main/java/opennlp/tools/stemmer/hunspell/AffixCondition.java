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

package opennlp.tools.stemmer.hunspell;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One parsed affix condition: a fixed-length sequence of literal code points and
 * bracketed character classes, matched with a single scan and no regular expressions.
 * A suffix condition anchors at the end of the candidate stem, a prefix condition at
 * its start; the condition {@code .} matches everything. Positions are Unicode code
 * points so supplementary characters agree with {@code FLAG UTF-8} flag reading.
 */
final class AffixCondition {

  /** The shared instance for the condition {@code .}, which accepts every stem. */
  private static final AffixCondition ANY = new AffixCondition(new int[0][], null, true);

  /** Per position: the accepted code points, or {@code null} for any code point. */
  private final int[][] accepted;
  /** Per position with a class: whether the class is negated; {@code null} rows unused. */
  private final boolean[] negated;
  /** Whether the owning rule is a suffix rule, which anchors the condition at the end. */
  private final boolean suffix;

  /**
   * Initializes the condition.
   *
   * @param accepted The accepted code points per position.
   * @param negated The negation marker per position.
   * @param suffix Whether the owning rule is a suffix rule.
   */
  private AffixCondition(int[][] accepted, boolean[] negated, boolean suffix) {
    this.accepted = accepted;
    this.negated = negated;
    this.suffix = suffix;
  }

  /**
   * Parses a condition field. Each pattern position is a literal code point, a
   * {@code .} matching any code point, or a bracketed class such as {@code [sx]}; a
   * class starting with {@code ^} is negated and matches any code point outside it.
   *
   * @param pattern The condition text from the affix rule.
   * @param suffix Whether the owning rule is a suffix rule.
   * @param lineNumber The affix file line, for error messages.
   * @return The parsed condition. Never {@code null}.
   * @throws IOException Thrown if a character class is unterminated.
   */
  static AffixCondition parse(String pattern, boolean suffix, int lineNumber)
      throws IOException {
    if (".".equals(pattern)) {
      return ANY;
    }
    final List<int[]> positions = new ArrayList<>();
    final List<Boolean> negations = new ArrayList<>();
    int i = 0;
    while (i < pattern.length()) {
      final int codePoint = pattern.codePointAt(i);
      if (codePoint == '[') {
        final int end = pattern.indexOf(']', i + 1);
        if (end < 0) {
          throw new IOException("unterminated character class at line " + lineNumber);
        }
        String members = pattern.substring(i + 1, end);
        boolean negate = false;
        if (members.startsWith("^")) {
          negate = true;
          members = members.substring(1);
        }
        positions.add(toCodePoints(members));
        negations.add(negate);
        i = end + 1;
      } else if (codePoint == '.') {
        positions.add(null);
        negations.add(false);
        i++;
      } else {
        positions.add(new int[] {codePoint});
        negations.add(false);
        i += Character.charCount(codePoint);
      }
    }
    final int[][] accepted = positions.toArray(new int[0][]);
    final boolean[] negated = new boolean[accepted.length];
    for (int p = 0; p < negated.length; p++) {
      negated[p] = negations.get(p);
    }
    return new AffixCondition(accepted, negated, suffix);
  }

  /**
   * Collects the code points of a character-class body.
   *
   * @param members The class body text.
   * @return The code points in order. Never {@code null}.
   */
  private static int[] toCodePoints(String members) {
    final int[] codePoints = new int[members.codePointCount(0, members.length())];
    int i = 0;
    int out = 0;
    while (i < members.length()) {
      final int codePoint = members.codePointAt(i);
      codePoints[out++] = codePoint;
      i += Character.charCount(codePoint);
    }
    return codePoints;
  }

  /**
   * Tests a candidate stem against the condition at its anchored side: the last
   * positions of the stem for a suffix condition, the first positions for a prefix
   * condition. A stem shorter than the condition never matches. Length is in code
   * points.
   *
   * @param stem The candidate stem after affix removal and strip restoration.
   * @return {@code true} if the stem satisfies the condition.
   */
  boolean matches(String stem) {
    if (accepted.length == 0) {
      return true;
    }
    final int stemPoints = stem.codePointCount(0, stem.length());
    if (stemPoints < accepted.length) {
      return false;
    }
    int offset = suffix ? stem.offsetByCodePoints(0, stemPoints - accepted.length) : 0;
    for (int p = 0; p < accepted.length; p++) {
      final int[] members = accepted[p];
      final int codePoint = stem.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (members == null) {
        continue;
      }
      boolean member = false;
      for (final int candidate : members) {
        if (candidate == codePoint) {
          member = true;
          break;
        }
      }
      if (member == negated[p]) {
        return false;
      }
    }
    return true;
  }
}
