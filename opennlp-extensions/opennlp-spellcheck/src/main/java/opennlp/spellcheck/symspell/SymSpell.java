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

package opennlp.spellcheck.symspell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import opennlp.spellcheck.SpellChecker;
import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;
import opennlp.spellcheck.distance.EditDistance;

/**
 * Symmetric Delete spelling correction engine (SymSpell).
 *
 * <p>The engine precomputes a deletes-only index: for every dictionary term it derives
 * all strings obtained by deleting up to {@code maxDictionaryEditDistance} symbols from
 * the term's prefix, and maps each such delete back to the originating terms. A query is
 * answered by generating the deletes of the query and intersecting them with the index,
 * which turns the costly fuzzy search into hash-map lookups; candidates are then verified
 * with the injected {@link EditDistance}.</p>
 *
 * <p>The algorithm and its compound-correction heuristic are ported from the SymSpell
 * reference implementation (MIT, Wolf Garbe). This is an independent Java 21 rewrite,
 * not a verbatim copy; attribution is recorded in the project NOTICE file.</p>
 *
 * <p>Populate the engine through {@link #add(String, long)} and
 * {@link #addBigram(String, String, long)} (typically driven by a separate loader), then
 * issue {@link #lookup} / {@link #lookupCompound} queries. After population the engine is
 * safe for concurrent reads.</p>
 */
public final class SymSpell implements SpellChecker {

  private final int maxDictionaryEditDistance;
  private final int prefixLength;
  private final long countThreshold;
  private final EditDistance editDistance;

  /** term -> corpus count. */
  private final Map<String, Long> words = new HashMap<>();

  /** delete-hash -> list of dictionary terms that produce it. */
  private final Map<String, String[]> deletes = new HashMap<>();

  /** "w1 w2" -> corpus count, for compound correction. */
  private final Map<String, Long> bigrams = new HashMap<>();

  private long maxBigramCount;

  /** Smallest bigram count seen; caps the Naive-Bayes estimate so real bigrams rank first. */
  private long minBigramCount = Long.MAX_VALUE;

  /** Length (in code points) of the longest indexed term. */
  private int maxLength;

  /**
   * Pinned corpus normalization constant <i>N</i>, or
   * {@link SymSpellConfig#DERIVE_CORPUS_WORD_COUNT} to derive it from {@link #totalCorpusCount}.
   */
  private final long configuredCorpusWordCount;

  /** Running sum of every count added (the derived corpus size <i>N</i>). */
  private long totalCorpusCount;

  public SymSpell(SymSpellConfig config) {
    Objects.requireNonNull(config, "config must not be null");
    this.maxDictionaryEditDistance = config.maxDictionaryEditDistance();
    this.prefixLength = config.prefixLength();
    this.countThreshold = config.countThreshold();
    this.editDistance = config.editDistance();
    this.configuredCorpusWordCount = config.corpusWordCount();
  }

  /** Creates an engine with the {@linkplain SymSpellConfig#defaultConfig() default} config. */
  public SymSpell() {
    this(SymSpellConfig.defaultConfig());
  }

  // ------------------------------------------------------------------
  // Build hooks (fed by the persistence layer; no I/O happens here).
  // ------------------------------------------------------------------

  /**
   * Adds (or accumulates) a dictionary term and its count, updating the deletes index.
   *
   * <p>If the term already exists, {@code count} is added to the existing count. Terms
   * whose accumulated count stays below the configured {@code countThreshold} are tracked
   * but not indexed until they reach the threshold.</p>
   *
   * @param word  the dictionary term; must not be {@code null}
   * @param count the corpus count to add; must be {@code >= 0}
   * @return {@code true} if the term became (or remained) indexed
   */
  public boolean add(String word, long count) {
    Objects.requireNonNull(word, "word must not be null");
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative: " + count);
    }
    // Every occurrence (even of sub-threshold terms) contributes to the corpus size N.
    totalCorpusCount = saturatedAdd(totalCorpusCount, count);
    if (count == 0 && countThreshold > 0) {
      return false;
    }

    final Long previous = words.get(word);
    long newCount;
    if (previous != null) {
      newCount = saturatedAdd(previous, count);
      words.put(word, newCount);
      // Already indexed previously if it had cleared the threshold.
      if (previous >= countThreshold) {
        return true;
      }
      if (newCount < countThreshold) {
        return false;
      }
    } else {
      newCount = count;
      words.put(word, newCount);
      if (newCount < countThreshold) {
        return false;
      }
    }

    final int wordLen = word.codePointCount(0, word.length());
    if (wordLen > maxLength) {
      maxLength = wordLen;
    }

    for (String delete : editsPrefix(word)) {
      final String[] existing = deletes.get(delete);
      if (existing == null) {
        deletes.put(delete, new String[] {word});
      } else {
        final String[] grown = new String[existing.length + 1];
        System.arraycopy(existing, 0, grown, 0, existing.length);
        grown[existing.length] = word;
        deletes.put(delete, grown);
      }
    }
    return true;
  }

  /**
   * Adds (or accumulates) a bigram and its count for compound correction.
   *
   * @param w1    the first word; must not be {@code null}
   * @param w2    the second word; must not be {@code null}
   * @param count the corpus count to add; must be {@code >= 0}
   */
  public void addBigram(String w1, String w2, long count) {
    Objects.requireNonNull(w1, "w1 must not be null");
    Objects.requireNonNull(w2, "w2 must not be null");
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative: " + count);
    }
    final String key = w1 + " " + w2;
    final long updated = saturatedAdd(bigrams.getOrDefault(key, 0L), count);
    bigrams.put(key, updated);
    if (updated > maxBigramCount) {
      maxBigramCount = updated;
    }
    if (updated < minBigramCount) {
      minBigramCount = updated;
    }
  }

  /** @return the number of indexed unigram entries (including sub-threshold terms). */
  public int wordCount() {
    return words.size();
  }

  /** @return the number of distinct delete keys in the index. */
  public int entryCount() {
    return deletes.size();
  }

  /** @return the number of bigram entries. */
  public int bigramCount() {
    return bigrams.size();
  }

  // ------------------------------------------------------------------
  // SpellChecker API
  // ------------------------------------------------------------------

  @Override
  public int maxEditDistance() {
    return maxDictionaryEditDistance;
  }

  @Override
  public List<SuggestItem> lookup(String term) {
    return lookup(term, Verbosity.TOP, maxDictionaryEditDistance);
  }

  @Override
  public List<SuggestItem> lookup(String term, Verbosity verbosity, int maxEditDistance) {
    Objects.requireNonNull(term, "term must not be null");
    Objects.requireNonNull(verbosity, "verbosity must not be null");
    if (maxEditDistance < 0) {
      throw new IllegalArgumentException("maxEditDistance must not be negative: " + maxEditDistance);
    }
    if (maxEditDistance > maxDictionaryEditDistance) {
      throw new IllegalArgumentException(
          "maxEditDistance (" + maxEditDistance + ") must not exceed maxDictionaryEditDistance ("
              + maxDictionaryEditDistance + ")");
    }

    final List<SuggestItem> suggestions = new ArrayList<>();
    final int termLen = term.codePointCount(0, term.length());

    // Early exit: the term is already longer than the longest known word by more than max.
    if (termLen - maxEditDistance > maxLength) {
      return suggestions;
    }

    // Exact match.
    final Long exactCount = words.get(term);
    if (exactCount != null && exactCount >= countThreshold) {
      suggestions.add(new SuggestItem(term, 0, exactCount));
      if (verbosity != Verbosity.ALL) {
        return suggestions;
      }
    }

    if (maxEditDistance == 0) {
      return suggestions;
    }

    final Set<String> consideredDeletes = new HashSet<>();
    final Set<String> consideredSuggestions = new HashSet<>();
    consideredSuggestions.add(term);

    int maxEdit2 = maxEditDistance;

    // The candidate strings to expand (BFS over deletes of the query prefix).
    final List<String> candidates = new ArrayList<>();

    // Only the prefix of the query participates in delete generation.
    final String termPrefix;
    if (termLen > prefixLength) {
      termPrefix = substringByCodePoints(term, 0, prefixLength);
    } else {
      termPrefix = term;
    }
    candidates.add(termPrefix);

    for (int idx = 0; idx < candidates.size(); idx++) {
      final String candidate = candidates.get(idx);
      final int candidateLen = candidate.codePointCount(0, candidate.length());
      final int lengthDiff = (Math.min(termLen, prefixLength)) - candidateLen;

      // No point expanding: already further from the query than the current best.
      if (lengthDiff > maxEdit2) {
        if (verbosity == Verbosity.ALL) {
          continue;
        }
        break;
      }

      final String[] dictSuggestions = deletes.get(candidate);
      if (dictSuggestions != null) {
        for (String suggestion : dictSuggestions) {
          if (suggestion.equals(term)) {
            continue;
          }
          final int suggestionLen = suggestion.codePointCount(0, suggestion.length());

          // Quick length-based pruning before the costly verification.
          if (Math.abs(suggestionLen - termLen) > maxEdit2
              || suggestionLen < candidateLen
              || (suggestionLen == candidateLen && !suggestion.equals(candidate))) {
            continue;
          }
          final int suggestionPrefixLen = Math.min(suggestionLen, prefixLength);
          if (suggestionPrefixLen > candidateLen && suggestionPrefixLen - candidateLen > maxEdit2) {
            continue;
          }

          final int distance;
          if (candidateLen == 0) {
            // The query delete-set produced the empty string; the distance is the
            // longer length, capped at the current max.
            distance = Math.max(termLen, suggestionLen);
            if (distance > maxEdit2 || !consideredSuggestions.add(suggestion)) {
              continue;
            }
          } else if (suggestionLen == 1) {
            distance = term.indexOf(suggestion.codePointAt(0)) < 0 ? termLen : termLen - 1;
            if (distance > maxEdit2 || !consideredSuggestions.add(suggestion)) {
              continue;
            }
          } else {
            if (!consideredSuggestions.add(suggestion)) {
              continue;
            }
            final int computed = editDistance.distance(term, suggestion, maxEdit2);
            if (computed < 0) {
              continue;
            }
            distance = computed;
          }

          if (distance <= maxEdit2) {
            // A delete key always maps to a term present in 'words' (add() populates words
            // before the deletes index); guard the invariant rather than risk an unbox NPE.
            final Long suggestionCount = words.get(suggestion);
            if (suggestionCount == null) {
              continue;
            }
            final SuggestItem item = new SuggestItem(suggestion, distance, suggestionCount);
            if (!suggestions.isEmpty()) {
              switch (verbosity) {
                case CLOSEST:
                  // Keep only the closest matches found so far.
                  if (distance < maxEdit2) {
                    suggestions.clear();
                  }
                  break;
                case TOP:
                  final SuggestItem best = suggestions.get(0);
                  if (distance < best.editDistance() || suggestionCount > best.frequency()) {
                    maxEdit2 = distance;
                    suggestions.set(0, item);
                  }
                  continue;
                default:
                  break;
              }
            }
            if (verbosity != Verbosity.ALL) {
              maxEdit2 = distance;
            }
            suggestions.add(item);
          }
        }
      }

      // Generate the next generation of deletes from this candidate.
      if (lengthDiff < maxEditDistance && candidateLen <= prefixLength) {
        if (verbosity != Verbosity.ALL && lengthDiff >= maxEdit2) {
          continue;
        }
        final int[] cps = candidate.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
          final String delete = removeCodePointAt(cps, i);
          if (consideredDeletes.add(delete)) {
            candidates.add(delete);
          }
        }
      }
    }

    if (suggestions.size() > 1) {
      Collections.sort(suggestions);
    }
    return suggestions;
  }

  @Override
  public List<SuggestItem> lookupCompound(String input, int maxEditDistance) {
    Objects.requireNonNull(input, "input must not be null");
    if (maxEditDistance < 0) {
      throw new IllegalArgumentException("maxEditDistance must not be negative: " + maxEditDistance);
    }

    final String[] termList = input.trim().isEmpty() ? new String[0] : input.trim().split("\\s+");
    final List<SuggestItem> suggestionParts = new ArrayList<>();

    boolean lastCombi = false;
    for (int i = 0; i < termList.length; i++) {
      final List<SuggestItem> suggestions = lookup(termList[i], Verbosity.TOP, maxEditDistance);

      // Try to merge the current token with the previous one (a wrongly inserted space).
      if (i > 0 && !lastCombi) {
        final List<SuggestItem> combi =
            lookup(termList[i - 1] + termList[i], Verbosity.TOP, maxEditDistance);
        if (!combi.isEmpty()) {
          final SuggestItem best1 = suggestionParts.get(suggestionParts.size() - 1);
          final SuggestItem best2 = suggestions.isEmpty()
              ? new SuggestItem(termList[i], maxEditDistance + 1, combiEstimatedCount(termList[i]))
              : suggestions.get(0);

          final int distance1 = best1.editDistance() + best2.editDistance();
          final SuggestItem combined = combi.get(0);
          if (distance1 >= 0
              && (combined.editDistance() + 1 < distance1
              || (combined.editDistance() + 1 == distance1
              && (double) combined.frequency()
              > (double) best1.frequency() / nMax() * best2.frequency()))) {
            suggestionParts.set(suggestionParts.size() - 1,
                new SuggestItem(combined.term(), combined.editDistance() + 1, combined.frequency()));
            lastCombi = true;
            continue;
          }
        }
      }
      lastCombi = false;

      // Never split a token that is already correct (ed==0) or is a single character.
      if (!suggestions.isEmpty()
          && (suggestions.get(0).editDistance() == 0 || termList[i].length() == 1)) {
        suggestionParts.add(suggestions.get(0));
        continue;
      }

      // Otherwise consider splitting the token into two words, with the single-term
      // correction (if any) seeded as the candidate to beat.
      SuggestItem suggestionSplitBest = suggestions.isEmpty() ? null : suggestions.get(0);
      if (termList[i].length() > 1) {
        for (int j = 1; j < termList[i].length(); j++) {
          final String part1 = termList[i].substring(0, j);
          final String part2 = termList[i].substring(j);
          final List<SuggestItem> suggestions1 = lookup(part1, Verbosity.TOP, maxEditDistance);
          if (suggestions1.isEmpty()) {
            continue;
          }
          final List<SuggestItem> suggestions2 = lookup(part2, Verbosity.TOP, maxEditDistance);
          if (suggestions2.isEmpty()) {
            continue;
          }
          final SuggestItem s1 = suggestions1.get(0);
          final SuggestItem s2 = suggestions2.get(0);
          final String split = s1.term() + " " + s2.term();

          // Joining two words spends one edit on the removed space, so the split distance
          // can legitimately exceed maxEditDistance; cap it instead of discarding the split.
          int splitDistance = editDistance.distance(termList[i], split, maxEditDistance);
          if (splitDistance < 0) {
            splitDistance = maxEditDistance + 1;
          }

          if (suggestionSplitBest != null) {
            if (splitDistance > suggestionSplitBest.editDistance()) {
              continue;
            }
            if (splitDistance < suggestionSplitBest.editDistance()) {
              suggestionSplitBest = null;
            }
          }

          long freq;
          final Long bigramCount = bigrams.get(split);
          if (bigramCount != null) {
            freq = bigramCount;
            // Boost the split's count when its parts reconstruct the input or agree with the
            // single-term correction, so a real bigram outranks that single-term correction.
            if (!suggestions.isEmpty()) {
              final SuggestItem best = suggestions.get(0);
              if ((s1.term() + s2.term()).equals(termList[i])) {
                freq = Math.max(freq, saturatedAdd(best.frequency(), 2));
              } else if (s1.term().equals(best.term()) || s2.term().equals(best.term())) {
                freq = Math.max(freq, saturatedAdd(best.frequency(), 1));
              }
            } else if ((s1.term() + s2.term()).equals(termList[i])) {
              freq = Math.max(freq, saturatedAdd(Math.max(s1.frequency(), s2.frequency()), 2));
            }
          } else {
            // Naive Bayes estimate P(AB)=P(A)*P(B), capped by the smallest real bigram count
            // so any genuine bigram still ranks above an estimated one.
            final double estimate = (double) s1.frequency() / (double) nMax() * (double) s2.frequency();
            freq = Math.min(minBigramCount, (long) estimate);
          }

          final SuggestItem candidateSplit = new SuggestItem(split, splitDistance, freq);
          if (suggestionSplitBest == null
              || candidateSplit.frequency() > suggestionSplitBest.frequency()) {
            suggestionSplitBest = candidateSplit;
          }
        }

        if (suggestionSplitBest != null) {
          suggestionParts.add(suggestionSplitBest);
        } else {
          suggestionParts.add(
              new SuggestItem(termList[i], maxEditDistance + 1, estimatedCount(termList[i])));
        }
      } else {
        suggestionParts.add(
            new SuggestItem(termList[i], maxEditDistance + 1, estimatedCount(termList[i])));
      }
    }

    // Assemble the final corrected phrase; the count is the Naive-Bayes product P=prod(P(part)).
    final StringBuilder joined = new StringBuilder();
    double freqProduct = nMax();
    final long n = Math.max(1L, nMax());
    for (SuggestItem part : suggestionParts) {
      if (joined.length() > 0) {
        joined.append(' ');
      }
      joined.append(part.term());
      freqProduct *= (double) part.frequency() / n;
    }

    final String corrected = joined.toString();
    final int distance = editDistance.distance(
        input.trim(), corrected, Integer.MAX_VALUE - 1);
    final long frequency = (long) freqProduct;
    return Collections.singletonList(
        new SuggestItem(corrected, Math.max(distance, 0), frequency));
  }

  // ------------------------------------------------------------------
  // Internals
  // ------------------------------------------------------------------

  /**
   * @return the corpus normalization constant N used in the Naive Bayes combination: the
   *     value pinned in {@link SymSpellConfig#corpusWordCount()} when set, otherwise the
   *     corpus size derived from the summed counts of the loaded dictionary.
   */
  private long nMax() {
    if (configuredCorpusWordCount != SymSpellConfig.DERIVE_CORPUS_WORD_COUNT) {
      return configuredCorpusWordCount;
    }
    return Math.max(1L, totalCorpusCount);
  }

  /** Estimated occurrence count for an uncorrectable token: P = 10^7 / 10^length. */
  private static long estimatedCount(String term) {
    return (long) (10_000_000.0 / Math.pow(10.0, term.codePointCount(0, term.length())));
  }

  /** Estimated occurrence count used as {@code best2} in the combine check: P = 10 / 10^length. */
  private static long combiEstimatedCount(String term) {
    return (long) (10.0 / Math.pow(10.0, term.codePointCount(0, term.length())));
  }

  /** Generates the prefix-restricted set of deletes for a dictionary term. */
  private Set<String> editsPrefix(String word) {
    final Set<String> result = new HashSet<>();
    final int wordLen = word.codePointCount(0, word.length());
    if (wordLen <= maxDictionaryEditDistance) {
      result.add("");
    }
    final String prefix = wordLen > prefixLength
        ? substringByCodePoints(word, 0, prefixLength)
        : word;
    result.add(prefix);
    edits(prefix, 0, result);
    return result;
  }

  /** Recursively generates all deletes of {@code word} up to the max edit distance. */
  private void edits(String word, int editDistanceSoFar, Set<String> deleteSet) {
    final int nextEdit = editDistanceSoFar + 1;
    final int[] cps = word.codePoints().toArray();
    if (cps.length <= 1) {
      return;
    }
    for (int i = 0; i < cps.length; i++) {
      final String delete = removeCodePointAt(cps, i);
      if (deleteSet.add(delete) && nextEdit < maxDictionaryEditDistance) {
        edits(delete, nextEdit, deleteSet);
      }
    }
  }

  private static String removeCodePointAt(int[] cps, int index) {
    final StringBuilder sb = new StringBuilder(cps.length);
    for (int k = 0; k < cps.length; k++) {
      if (k != index) {
        sb.appendCodePoint(cps[k]);
      }
    }
    return sb.toString();
  }

  private static String substringByCodePoints(String s, int beginCp, int endCp) {
    final int beginIdx = s.offsetByCodePoints(0, beginCp);
    final int endIdx = s.offsetByCodePoints(0, endCp);
    return s.substring(beginIdx, endIdx);
  }

  private static long saturatedAdd(long a, long b) {
    final long sum = a + b;
    if (((a ^ sum) & (b ^ sum)) < 0) {
      return Long.MAX_VALUE;
    }
    return sum;
  }
}
