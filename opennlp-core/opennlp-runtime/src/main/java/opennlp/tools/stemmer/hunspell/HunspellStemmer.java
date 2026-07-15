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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import opennlp.tools.stemmer.Stemmer;
import opennlp.tools.stemmer.hunspell.HunspellDictionary.Affix;
import opennlp.tools.util.StringUtil;

/**
 * A dictionary-backed {@link Stemmer} over a {@link HunspellDictionary}: a surface form
 * is reduced to the dictionary words it can be derived from by removing one suffix, one
 * prefix, or a cross-product combination of both.
 *
 * <p>{@link #stem(CharSequence)} returns the first analysis, preferring the word's own
 * dictionary entry; {@link #stemAll(CharSequence)} returns every distinct analysis. A
 * word with no analysis is returned unchanged, so the stemmer degrades to identity on
 * unknown vocabulary. Capitalized forms also try their lowercase variant.</p>
 *
 * <p>The stemmer reads only immutable dictionary state and is safe to share between
 * threads, satisfying the single-thread confinement contract trivially.</p>
 *
 * @since 3.0.0
 */
public class HunspellStemmer implements Stemmer {

  private final HunspellDictionary dictionary;

  /**
   * Initializes the stemmer.
   *
   * @param dictionary The dictionary to analyze against. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code dictionary} is {@code null}.
   */
  public HunspellStemmer(HunspellDictionary dictionary) {
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    this.dictionary = dictionary;
  }

  @Override
  public CharSequence stem(CharSequence word) {
    final List<CharSequence> analyses = stemAll(word);
    return analyses.get(0);
  }

  @Override
  public List<CharSequence> stemAll(CharSequence word) {
    if (word == null) {
      throw new IllegalArgumentException("word must not be null");
    }
    final String surface = word.toString();
    final Set<String> analyses = new LinkedHashSet<>();
    for (final String variant : variants(surface)) {
      analyze(variant, analyses);
    }
    if (analyses.isEmpty()) {
      return List.of(surface);
    }
    return List.copyOf(new ArrayList<CharSequence>(analyses));
  }

  /** Collects the case variants to analyze: the surface form, then its lowercase. */
  private static List<String> variants(String surface) {
    final String lowered = StringUtil.toLowerCase(surface);
    return lowered.equals(surface) ? List.of(surface) : List.of(surface, lowered);
  }

  /** Runs direct lookup, suffix removal, prefix removal, and cross products. */
  private void analyze(String word, Set<String> analyses) {
    if (dictionary.lookup(word) != null) {
      analyses.add(word);
    }
    for (final Affix suffix : dictionary.suffixes()) {
      final String stem = removeSuffix(word, suffix);
      if (stem == null) {
        continue;
      }
      final List<int[]> flagSets = dictionary.lookup(stem);
      if (flagSets != null && HunspellDictionary.hasFlag(flagSets, suffix.flag())) {
        analyses.add(stem);
      }
    }
    for (final Affix prefix : dictionary.prefixes()) {
      final String stem = removePrefix(word, prefix);
      if (stem == null) {
        continue;
      }
      final List<int[]> flagSets = dictionary.lookup(stem);
      if (flagSets != null && HunspellDictionary.hasFlag(flagSets, prefix.flag())) {
        analyses.add(stem);
      }
      if (!prefix.crossProduct()) {
        continue;
      }
      for (final Affix suffix : dictionary.suffixes()) {
        if (!suffix.crossProduct()) {
          continue;
        }
        final String doubleStem = removeSuffix(stem, suffix);
        if (doubleStem == null) {
          continue;
        }
        final List<int[]> both = dictionary.lookup(doubleStem);
        if (both != null && HunspellDictionary.hasFlag(both, prefix.flag())
            && HunspellDictionary.hasFlag(both, suffix.flag())) {
          analyses.add(doubleStem);
        }
      }
    }
  }

  /**
   * Undoes one suffix rule.
   *
   * @param word The surface form.
   * @param suffix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private static String removeSuffix(String word, Affix suffix) {
    if (suffix.affix().isEmpty() || !word.endsWith(suffix.affix())
        || word.length() - suffix.affix().length() + suffix.strip().length() == 0) {
      return null;
    }
    final String stem =
        word.substring(0, word.length() - suffix.affix().length()) + suffix.strip();
    return suffix.condition().matches(stem) ? stem : null;
  }

  /**
   * Undoes one prefix rule.
   *
   * @param word The surface form.
   * @param prefix The rule to undo.
   * @return The candidate stem, or {@code null} when the rule does not apply.
   */
  private static String removePrefix(String word, Affix prefix) {
    if (prefix.affix().isEmpty() || !word.startsWith(prefix.affix())
        || word.length() - prefix.affix().length() + prefix.strip().length() == 0) {
      return null;
    }
    final String stem = prefix.strip() + word.substring(prefix.affix().length());
    return prefix.condition().matches(stem) ? stem : null;
  }
}
