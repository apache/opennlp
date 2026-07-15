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
 * One parsed affix condition: a fixed-length sequence of literal characters and
 * bracketed character classes, matched with a single scan and no regular expressions.
 * A suffix condition anchors at the end of the candidate stem, a prefix condition at
 * its start; the condition {@code .} matches everything.
 */
final class AffixCondition {

  private static final AffixCondition ANY = new AffixCondition(new char[0][], null, true);

  /** Per position: the accepted characters, or {@code null} for any character. */
  private final char[][] accepted;
  /** Per position with a class: whether the class is negated; {@code null} rows unused. */
  private final boolean[] negated;
  private final boolean suffix;

  private AffixCondition(char[][] accepted, boolean[] negated, boolean suffix) {
    this.accepted = accepted;
    this.negated = negated;
    this.suffix = suffix;
  }

  /**
   * Parses a condition field.
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
    final List<char[]> positions = new ArrayList<>();
    final List<Boolean> negations = new ArrayList<>();
    int i = 0;
    while (i < pattern.length()) {
      final char c = pattern.charAt(i);
      if (c == '[') {
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
        positions.add(members.toCharArray());
        negations.add(negate);
        i = end + 1;
      } else if (c == '.') {
        positions.add(null);
        negations.add(false);
        i++;
      } else {
        positions.add(new char[] {c});
        negations.add(false);
        i++;
      }
    }
    final char[][] accepted = positions.toArray(new char[0][]);
    final boolean[] negated = new boolean[accepted.length];
    for (int p = 0; p < negated.length; p++) {
      negated[p] = negations.get(p);
    }
    return new AffixCondition(accepted, negated, suffix);
  }

  /**
   * Tests a candidate stem against the condition at its anchored side.
   *
   * @param stem The candidate stem after affix removal and strip restoration.
   * @return {@code true} if the stem satisfies the condition.
   */
  boolean matches(String stem) {
    if (accepted.length == 0) {
      return true;
    }
    if (stem.length() < accepted.length) {
      return false;
    }
    final int offset = suffix ? stem.length() - accepted.length : 0;
    for (int p = 0; p < accepted.length; p++) {
      final char[] members = accepted[p];
      if (members == null) {
        continue;
      }
      final char c = stem.charAt(offset + p);
      boolean member = false;
      for (final char candidate : members) {
        if (candidate == c) {
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
