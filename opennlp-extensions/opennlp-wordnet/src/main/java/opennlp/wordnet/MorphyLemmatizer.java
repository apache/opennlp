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
package opennlp.wordnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.WordNetPOS;

/**
 * A {@link Lemmatizer} implementing the Morphy algorithm: exception-list lookup first, then the
 * per-part-of-speech iterative detachment rules, with every rule-derived candidate validated
 * against a {@link LexicalKnowledgeBase} before it is returned. A token is folded (lowercase with
 * the root locale, underscore as space) before lookup, and returned lemmas are in that folded
 * form.
 *
 * <p>Part-of-speech tags map to a {@link WordNetPOS} by their conventional Penn Treebank
 * prefixes ({@code N}, {@code V}, {@code J}, {@code R}), the names {@code ADJ} and {@code ADV},
 * and the one-letter WordNet codes {@code n}, {@code v}, {@code a}, {@code r}, and {@code s}
 * (satellite, treated as adjective), case-insensitively. A tag that maps to no part of speech
 * yields the unknown-word result.</p>
 *
 * <p>Following {@code opennlp.tools.lemmatizer.DictionaryLemmatizer}, a token with no lemma
 * yields {@link #UNKNOWN_LEMMA} from {@link #lemmatize(String[], String[])} and a singleton list
 * of it from {@link #lemmatize(List, List)}. Both a lexicon and exception lists are required.
 * Instances are immutable and safe for concurrent use.</p>
 */
@ThreadSafe
public final class MorphyLemmatizer implements Lemmatizer {

  /**
   * The output emitted for a token whose lemma is unknown, following the conventional
   * {@link Lemmatizer} unknown marker also used by
   * {@code opennlp.tools.lemmatizer.DictionaryLemmatizer}.
   */
  public static final String UNKNOWN_LEMMA = "O";

  private static final String[][] NOUN_RULES = {
      {"s", ""}, {"ses", "s"}, {"xes", "x"}, {"zes", "z"},
      {"ches", "ch"}, {"shes", "sh"}, {"men", "man"}, {"ies", "y"},
  };

  private static final String[][] VERB_RULES = {
      {"s", ""}, {"ies", "y"}, {"es", "e"}, {"es", ""},
      {"ed", "e"}, {"ed", ""}, {"ing", "e"}, {"ing", ""},
  };

  private static final String[][] ADJECTIVE_RULES = {
      {"er", ""}, {"est", ""}, {"er", "e"}, {"est", "e"},
  };

  private static final String[][] NO_RULES = {};

  private final LexicalKnowledgeBase lexicon;
  private final MorphyExceptions exceptions;

  /**
   * Creates a Morphy lemmatizer over a loaded lexicon and exception lists.
   *
   * @param lexicon    The lexicon rule candidates are validated against. Must not be
   *                   {@code null}.
   * @param exceptions The irregular-form exception lists. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code lexicon} or {@code exceptions} is
   *     {@code null}.
   */
  public MorphyLemmatizer(LexicalKnowledgeBase lexicon, MorphyExceptions exceptions) {
    if (lexicon == null) {
      throw new IllegalArgumentException("Lexicon must not be null");
    }
    if (exceptions == null) {
      throw new IllegalArgumentException("Exceptions must not be null");
    }
    this.lexicon = lexicon;
    this.exceptions = exceptions;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if {@code toks} or {@code tags} is {@code null},
   *     contains a {@code null} element, or the two differ in length.
   */
  @Override
  public String[] lemmatize(String[] toks, String[] tags) {
    if (toks == null || tags == null) {
      throw new IllegalArgumentException("Toks and tags must not be null");
    }
    if (toks.length != tags.length) {
      throw new IllegalArgumentException("Toks and tags must have the same length, got "
          + toks.length + " and " + tags.length);
    }
    final String[] lemmas = new String[toks.length];
    for (int i = 0; i < toks.length; i++) {
      final List<String> candidates = lemmasOf(toks[i], tags[i]);
      lemmas[i] = candidates.isEmpty() ? UNKNOWN_LEMMA : candidates.get(0);
    }
    return lemmas;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if {@code toks} or {@code tags} is {@code null},
   *     contains a {@code null} element, or the two differ in size.
   */
  @Override
  public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
    if (toks == null || tags == null) {
      throw new IllegalArgumentException("Toks and tags must not be null");
    }
    if (toks.size() != tags.size()) {
      throw new IllegalArgumentException("Toks and tags must have the same size, got "
          + toks.size() + " and " + tags.size());
    }
    final List<List<String>> lemmas = new ArrayList<>(toks.size());
    for (int i = 0; i < toks.size(); i++) {
      final List<String> candidates = lemmasOf(toks.get(i), tags.get(i));
      lemmas.add(candidates.isEmpty() ? List.of(UNKNOWN_LEMMA) : candidates);
    }
    return lemmas;
  }

  /**
   * Finds all lemmas of one token, most preferred first.
   *
   * @param token The token to lemmatize.
   * @param tag   The part-of-speech tag.
   * @return The candidate lemmas, empty when the word is unknown or the tag maps to no part of
   *     speech.
   */
  private List<String> lemmasOf(String token, String tag) {
    if (token == null || tag == null) {
      throw new IllegalArgumentException("Tokens and tags must not contain null elements");
    }
    final WordNetPOS pos = posFromTag(tag);
    if (pos == null) {
      return List.of();
    }
    final String folded = LemmaFolding.fold(token);
    final List<String> irregular = exceptions.lookup(folded, pos);
    if (!irregular.isEmpty()) {
      return irregular;
    }
    final List<String> candidates = new ArrayList<>(2);
    if (lexicon.contains(folded, pos)) {
      candidates.add(folded);
    }
    for (final String[] rule : rulesFor(pos)) {
      final String suffix = rule[0];
      if (folded.length() > suffix.length() && folded.endsWith(suffix)) {
        final String candidate =
            folded.substring(0, folded.length() - suffix.length()) + rule[1];
        if (!candidates.contains(candidate) && lexicon.contains(candidate, pos)) {
          candidates.add(candidate);
        }
      }
    }
    return candidates;
  }

  /**
   * Selects the detachment-rule table for a part of speech.
   *
   * @param pos The part of speech.
   * @return The suffix-substitution rules, empty for adverbs.
   */
  private static String[][] rulesFor(WordNetPOS pos) {
    return switch (pos) {
      case NOUN -> NOUN_RULES;
      case VERB -> VERB_RULES;
      case ADJECTIVE -> ADJECTIVE_RULES;
      case ADVERB -> NO_RULES;
    };
  }

  /**
   * Maps a part-of-speech tag to a {@link WordNetPOS}. Package-private so tests can pin the
   * mapping directly.
   *
   * @param tag The tag to map. Must not be {@code null}.
   * @return The part of speech, or {@code null} when the tag names none.
   * @throws IllegalArgumentException Thrown if {@code tag} is {@code null}.
   */
  static WordNetPOS posFromTag(String tag) {
    if (tag == null) {
      throw new IllegalArgumentException("Tag must not be null");
    }
    if (tag.isEmpty()) {
      return null;
    }
    final String upper = tag.toUpperCase(Locale.ROOT);
    if (upper.startsWith("ADJ")) {
      return WordNetPOS.ADJECTIVE;
    }
    if (upper.startsWith("ADV")) {
      return WordNetPOS.ADVERB;
    }
    return switch (upper.charAt(0)) {
      case 'N' -> WordNetPOS.NOUN;
      case 'V' -> WordNetPOS.VERB;
      case 'J' -> WordNetPOS.ADJECTIVE;
      // Codes a and s mean adjective only as one-letter tags; AUX, ADP and the like do not.
      case 'A', 'S' -> tag.length() == 1 ? WordNetPOS.ADJECTIVE : null;
      case 'R' -> WordNetPOS.ADVERB;
      default -> null;
    };
  }
}
