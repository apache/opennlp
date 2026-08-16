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
package opennlp.embeddings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.StringUtil;

/**
 * The term rows of a static embedding matrix: whole words and multi-word phrases that were
 * distilled through the teacher as units and sit after the subword rows. Matching a text against
 * the table finds the greedily longest term at each word position, so "writ of habeas corpus"
 * wins over "habeas corpus" wins over the subword pieces of each word.
 *
 * <p>A term is stored in normalized form: the lower-cased letter-or-digit word runs of its text,
 * joined by single spaces (see {@link #normalizeTerm(String)}). Matching folds each word run of
 * the input text the same way, so "Habeas Corpus" and "habeas-corpus" both match the term
 * "habeas corpus". The fold is {@link StringUtil#toLowerCase(CharSequence)}, locale-independent
 * and one code point to one code point, so word-run boundaries are the same before and after
 * folding.</p>
 *
 * <p>Immutable and safe for concurrent reads after construction.</p>
 */
@ThreadSafe
final class TermTable {

  private final List<String> termsByOffset;
  private final Map<String, Integer> rowByTerm;
  private final int firstRow;
  private final int maxTermWords;

  /** Holds the validated term-to-row views; built by {@link #of(List, int, String)}. */
  private TermTable(List<String> termsByOffset, Map<String, Integer> rowByTerm, int firstRow,
                    int maxTermWords) {
    this.termsByOffset = termsByOffset;
    this.rowByTerm = rowByTerm;
    this.firstRow = firstRow;
    this.maxTermWords = maxTermWords;
  }

  /**
   * Builds a term table from terms in matrix row order.
   *
   * @param terms      The terms; the term at index {@code i} owns matrix row
   *                   {@code firstRow + i}. Every term must already be in its normalized form.
   *                   Must not be {@code null}.
   * @param firstRow   The matrix row of the first term, the number of subword rows.
   * @param sourceName The terms' source, for error messages.
   * @return The table.
   * @throws IllegalArgumentException Thrown if {@code terms} is {@code null}.
   * @throws InvalidFormatException Thrown if a term is {@code null}, not in normalized form, or
   *     appears more than once.
   */
  static TermTable of(List<String> terms, int firstRow, String sourceName)
      throws InvalidFormatException {
    if (terms == null) {
      throw new IllegalArgumentException("Terms must not be null");
    }
    final Map<String, Integer> rowByTerm = new HashMap<>(terms.size() * 2);
    int maxTermWords = 0;
    for (int i = 0; i < terms.size(); i++) {
      final String term = terms.get(i);
      if (term == null || !term.equals(normalizeTerm(term)) || term.isEmpty()) {
        throw new InvalidFormatException("Term " + i + " in " + sourceName + " ('" + term
            + "') is not in normalized form (lower-cased words joined by single spaces)");
      }
      if (rowByTerm.putIfAbsent(term, firstRow + i) != null) {
        throw new InvalidFormatException("Term '" + term + "' appears more than once in "
            + sourceName);
      }
      maxTermWords = Math.max(maxTermWords, countWords(term));
    }
    return new TermTable(List.copyOf(terms), Map.copyOf(rowByTerm), firstRow, maxTermWords);
  }

  /**
   * {@return a term's normalized form: its lower-cased letter-or-digit word runs joined by
   * single spaces, or the empty string when the text contains no such run}
   *
   * @param text The term text. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  static String normalizeTerm(String text) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    final StringBuilder normalized = new StringBuilder(text.length());
    final String folded = StringUtil.toLowerCase(text);
    final int length = folded.length();
    int i = 0;
    while (i < length) {
      final int c = folded.codePointAt(i);
      if (Character.isLetterOrDigit(c)) {
        if (normalized.length() > 0) {
          normalized.append(' ');
        }
        while (i < length && Character.isLetterOrDigit(folded.codePointAt(i))) {
          normalized.appendCodePoint(folded.codePointAt(i));
          i += Character.charCount(folded.codePointAt(i));
        }
      } else {
        i += Character.charCount(c);
      }
    }
    return normalized.toString();
  }

  /** {@return the number of space-separated words of a normalized term} */
  private static int countWords(String term) {
    int words = 1;
    for (int i = 0; i < term.length(); i++) {
      if (term.charAt(i) == ' ') {
        words++;
      }
    }
    return words;
  }

  /** {@return the number of terms in this table} */
  int size() {
    return termsByOffset.size();
  }

  /**
   * Looks up the term owning a matrix row.
   *
   * @param row The matrix row. Must be within {@code [firstRow, firstRow + size())}.
   * @return The term at that row.
   * @throws IllegalArgumentException Thrown if {@code row} is outside the term rows.
   */
  String term(int row) {
    final int offset = row - firstRow;
    if (offset < 0 || offset >= termsByOffset.size()) {
      throw new IllegalArgumentException("Row " + row + " is outside the term rows ["
          + firstRow + ", " + (firstRow + termsByOffset.size()) + ")");
    }
    return termsByOffset.get(offset);
  }

  /**
   * A term match in a text: the term's matrix row and the character range it consumed, from the
   * start of its first word to the end of its last.
   *
   * @param row   The matched term's matrix row.
   * @param start The inclusive start of the consumed range.
   * @param end   The exclusive end of the consumed range.
   */
  record Match(int row, int start, int end) {
  }

  /**
   * Finds every term of this table in a text, greedily longest-first: at each word, the longest
   * matching term consumes its words, and matching continues after them. Matched ranges never
   * overlap and appear in text order.
   *
   * @param text The text to match. Must not be {@code null}.
   * @return The matches in text order; empty when the table is empty or nothing matches.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  List<Match> matches(String text) {
    if (text == null) {
      throw new IllegalArgumentException("Text must not be null");
    }
    if (termsByOffset.isEmpty()) {
      return List.of();
    }
    final List<Run> runs = wordRuns(text);
    final List<Match> matches = new ArrayList<>();
    int i = 0;
    while (i < runs.size()) {
      int consumed = 0;
      for (int n = Math.min(maxTermWords, runs.size() - i); n >= 1; n--) {
        final Integer row = rowByTerm.get(joined(runs, i, n));
        if (row != null) {
          matches.add(new Match(row, runs.get(i).start(), runs.get(i + n - 1).end()));
          consumed = n;
          break;
        }
      }
      i += Math.max(consumed, 1);
    }
    return matches;
  }

  /** A word run of the matched text: its character range and its case-folded form. */
  private record Run(int start, int end, String folded) {
  }

  /**
   * {@return the letter-or-digit word runs of a text, each with its character range and its
   * case-folded form}
   *
   * @param text The text to scan.
   */
  private static List<Run> wordRuns(String text) {
    final List<Run> runs = new ArrayList<>();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int c = text.codePointAt(i);
      if (Character.isLetterOrDigit(c)) {
        final int start = i;
        while (i < length && Character.isLetterOrDigit(text.codePointAt(i))) {
          i += Character.charCount(text.codePointAt(i));
        }
        runs.add(new Run(start, i, StringUtil.toLowerCase(text.substring(start, i))));
      } else {
        i += Character.charCount(c);
      }
    }
    return runs;
  }

  /**
   * {@return the folded forms of {@code n} runs from {@code first}, joined by single spaces, the
   * lookup key of a candidate term}
   *
   * @param runs  The text's word runs.
   * @param first The first run of the candidate.
   * @param n     The number of runs of the candidate.
   */
  private static String joined(List<Run> runs, int first, int n) {
    if (n == 1) {
      return runs.get(first).folded();
    }
    final StringBuilder key = new StringBuilder();
    for (int i = 0; i < n; i++) {
      if (i > 0) {
        key.append(' ');
      }
      key.append(runs.get(first + i).folded());
    }
    return key.toString();
  }
}
