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

package opennlp.embeddings.corpus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.util.StringUtil;

/**
 * Learns a vocabulary from corpus texts and dictionary headwords: every dictionary term
 * with its corpus frequency, plus the corpus words frequent enough to keep.
 *
 * <p>Texts fold to lower case and split into words (maximal runs of letters and
 * digits). Dictionary headwords fold and split the same way, so multi-word headwords
 * become word sequences, and the scan counts them by greedy longest match: at each word,
 * the longest dictionary sequence starting there wins and consumes its words, so
 * "habeas corpus" counts as one term and neither "habeas" nor "corpus" is counted for
 * it. Words not consumed by a dictionary term count individually.</p>
 *
 * <p>The result lists every dictionary term first (highest count first, zero-count terms
 * included), then the remaining corpus words with at least the configured minimum
 * frequency (highest count first, ties in first-seen order), truncated to the configured
 * maximum size. Dictionary terms are never truncated, even when they alone exceed the
 * maximum.</p>
 *
 * @since 3.0.0
 */
public final class VocabularyLearner {

  private final int minFrequency;
  private final int maxTerms;

  /**
   * Initializes a learner.
   *
   * @param minFrequency The smallest corpus frequency that keeps a non-dictionary word.
   *                     Must be at least one.
   * @param maxTerms The largest result size, dictionary terms exempt. Must be at least
   *                 one.
   * @throws IllegalArgumentException Thrown if a parameter is below one.
   */
  public VocabularyLearner(int minFrequency, int maxTerms) {
    if (minFrequency < 1) {
      throw new IllegalArgumentException("minFrequency must be at least one: " + minFrequency);
    }
    if (maxTerms < 1) {
      throw new IllegalArgumentException("maxTerms must be at least one: " + maxTerms);
    }
    this.minFrequency = minFrequency;
    this.maxTerms = maxTerms;
  }

  /**
   * Learns the vocabulary of the given texts.
   *
   * @param texts The corpus texts. Must not be {@code null} or contain {@code null}.
   * @param dictionaryHeadwords The dictionary headwords, any casing, multi-word allowed.
   *                            Headwords folding to the same word sequence merge. Must
   *                            not be {@code null} or contain {@code null}.
   * @return The learned terms as described in the class documentation. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if an argument is {@code null} or contains
   *         {@code null}.
   */
  public List<TermCount> learn(Iterable<String> texts, Collection<String> dictionaryHeadwords) {
    if (texts == null) {
      throw new IllegalArgumentException("texts must not be null");
    }
    if (dictionaryHeadwords == null) {
      throw new IllegalArgumentException("dictionaryHeadwords must not be null");
    }

    // Candidates are grouped by first word and ordered longest first.
    final Map<String, List<List<String>>> dictionaryByFirstWord = new HashMap<>();
    final Map<String, Long> dictionaryCounts = new LinkedHashMap<>();
    for (String headword : dictionaryHeadwords) {
      if (headword == null) {
        throw new IllegalArgumentException("dictionaryHeadwords must not contain null");
      }
      final List<String> words = words(headword);
      if (words.isEmpty()) {
        continue;
      }
      final String term = String.join(" ", words);
      if (dictionaryCounts.putIfAbsent(term, 0L) == null) {
        final List<List<String>> candidates =
            dictionaryByFirstWord.computeIfAbsent(words.get(0), first -> new ArrayList<>());
        candidates.add(words);
        candidates.sort((a, b) -> Integer.compare(b.size(), a.size()));
      }
    }

    final Map<String, Long> corpusCounts = new LinkedHashMap<>();
    for (String text : texts) {
      if (text == null) {
        throw new IllegalArgumentException("texts must not contain null");
      }
      final List<String> words = words(text);
      int i = 0;
      while (i < words.size()) {
        final int consumed = countDictionaryMatch(words, i,
            dictionaryByFirstWord, dictionaryCounts);
        if (consumed > 0) {
          i += consumed;
        } else {
          corpusCounts.merge(words.get(i), 1L, Long::sum);
          i++;
        }
      }
    }

    final List<TermCount> result = new ArrayList<>();
    for (Map.Entry<String, Long> entry : dictionaryCounts.entrySet()) {
      result.add(new TermCount(entry.getKey(), entry.getValue(), true));
    }
    result.sort((a, b) -> Long.compare(b.count(), a.count()));

    final List<TermCount> corpus = new ArrayList<>();
    for (Map.Entry<String, Long> entry : corpusCounts.entrySet()) {
      if (entry.getValue() >= minFrequency) {
        corpus.add(new TermCount(entry.getKey(), entry.getValue(), false));
      }
    }
    corpus.sort((a, b) -> Long.compare(b.count(), a.count()));
    for (TermCount term : corpus) {
      if (result.size() >= maxTerms) {
        break;
      }
      result.add(term);
    }
    return result;
  }

  /**
   * Counts the longest dictionary sequence starting at a position, if any.
   *
   * @param words The corpus words.
   * @param position The first word to compare.
   * @param dictionaryByFirstWord Dictionary sequences grouped by their first word.
   * @param dictionaryCounts The destination counts by normalized term.
   * @return The number of words consumed, zero when no sequence matches.
   */
  private static int countDictionaryMatch(List<String> words, int position,
      Map<String, List<List<String>>> dictionaryByFirstWord,
      Map<String, Long> dictionaryCounts) {
    final List<List<String>> candidates = dictionaryByFirstWord.get(words.get(position));
    if (candidates == null) {
      return 0;
    }
    for (List<String> candidate : candidates) {
      if (position + candidate.size() <= words.size()
          && words.subList(position, position + candidate.size()).equals(candidate)) {
        dictionaryCounts.merge(String.join(" ", candidate), 1L, Long::sum);
        return candidate.size();
      }
    }
    return 0;
  }

  /**
   * Folds text to lower case and finds maximal letter-or-digit runs.
   *
   * @param text The source text.
   * @return The normalized words.
   */
  private static List<String> words(String text) {
    final List<String> words = new ArrayList<>();
    final String folded = StringUtil.toLowerCase(text);
    final int length = folded.length();
    int i = 0;
    while (i < length) {
      final int c = folded.codePointAt(i);
      if (Character.isLetterOrDigit(c)) {
        final int start = i;
        while (i < length && Character.isLetterOrDigit(folded.codePointAt(i))) {
          i += Character.charCount(folded.codePointAt(i));
        }
        words.add(folded.substring(start, i));
      } else {
        i += Character.charCount(c);
      }
    }
    return words;
  }
}
