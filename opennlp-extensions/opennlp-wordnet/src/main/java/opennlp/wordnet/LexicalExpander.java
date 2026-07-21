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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.lemmatizer.Lemmatizer;
import opennlp.tools.util.StringUtil;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * Expands a term into related terms drawn from a {@link LexicalKnowledgeBase}: the synonyms
 * sharing its synsets, the lemmas of its hypernym ancestors up to a configured depth, and
 * optionally the lemmas of its direct hyponyms.
 *
 * <p>Each {@link Expansion} carries a deterministic heuristic weight, not a probability: the
 * first sense of a term starts at {@code 1.0}, each later sense is multiplied by the configurable
 * sense decay, and every hypernym or hyponym step multiplies by the configurable depth decay.
 * A decay product that underflows to zero in double arithmetic carries no ranking signal, so
 * such expansions are dropped rather than emitted outside the {@code (0, 1]} weight range.
 * When the term itself is not in the lexicon and a {@link Lemmatizer} is configured, the term is
 * lemmatized and the lemma expanded instead; the lemmatizer is invoked with the
 * {@link WordNetPOS} name as the tag.</p>
 *
 * <p>Hypernym walks follow both the direct and the instance relation, track visited synsets so
 * malformed cyclic data cannot loop, and never report the term itself. Results are deduplicated
 * case-insensitively, keeping the highest weight, and ordered by weight descending, then kind,
 * then term, so output is stable across runs.</p>
 *
 * <p>Instances are immutable and safe for concurrent use when the configured lexicon and
 * lemmatizer are.</p>
 */
@ThreadSafe
public final class LexicalExpander {

  /** How an expansion relates to the input term. */
  public enum Kind {

    /** A member of one of the term's own synsets. */
    SYNONYM,

    /** A lemma of an ancestor synset, {@link Expansion#depth()} steps up. */
    HYPERNYM,

    /** A lemma of a direct child synset. */
    HYPONYM
  }

  /**
   * One expansion of a term.
   *
   * @param term      The expanded term, in the lexicon's written form (multiword terms contain
   *                  spaces).
   * @param kind      How the term relates to the input.
   * @param depth     The relation distance: {@code 0} for synonyms, the number of hypernym steps
   *                  for {@link Kind#HYPERNYM}, {@code 1} for hyponyms.
   * @param senseRank The zero-based rank of the input sense this expansion came from.
   * @param weight    The heuristic weight in {@code (0, 1]}; higher is closer to the input term.
   */
  public record Expansion(String term, Kind kind, int depth, int senseRank, double weight) {

    /**
     * Validates every component against the documented ranges.
     *
     * @throws IllegalArgumentException Thrown if {@code term} is {@code null} or blank,
     *         {@code kind} is {@code null}, {@code depth} or {@code senseRank} is negative, or
     *         {@code weight} is not in {@code (0, 1]}.
     */
    public Expansion {
      if (term == null || StringUtil.isBlank(term)) {
        throw new IllegalArgumentException("term must not be null or blank");
      }
      if (kind == null) {
        throw new IllegalArgumentException("kind must not be null");
      }
      if (depth < 0) {
        throw new IllegalArgumentException("depth must not be negative: " + depth);
      }
      if (senseRank < 0) {
        throw new IllegalArgumentException("senseRank must not be negative: " + senseRank);
      }
      if (!(weight > 0.0 && weight <= 1.0)) {
        throw new IllegalArgumentException("weight must be in (0, 1]: " + weight);
      }
    }
  }

  private final LexicalKnowledgeBase lexicon;
  private final Lemmatizer lemmatizer;
  private final int maxSenses;
  private final int hypernymDepth;
  private final boolean includeHyponyms;
  private final int maxExpansions;
  private final double senseDecay;
  private final double depthDecay;

  /**
   * Creates an expander from a builder whose fields have already been validated.
   *
   * @param builder The configured builder. Must not be {@code null}.
   */
  private LexicalExpander(Builder builder) {
    this.lexicon = builder.lexicon;
    this.lemmatizer = builder.lemmatizer;
    this.maxSenses = builder.maxSenses;
    this.hypernymDepth = builder.hypernymDepth;
    this.includeHyponyms = builder.includeHyponyms;
    this.maxExpansions = builder.maxExpansions;
    this.senseDecay = builder.senseDecay;
    this.depthDecay = builder.depthDecay;
  }

  /**
   * Starts a builder.
   *
   * @param lexicon The knowledge base to expand against; must not be null.
   * @return A builder with the default configuration.
   * @throws IllegalArgumentException Thrown if {@code lexicon} is null.
   */
  public static Builder builder(LexicalKnowledgeBase lexicon) {
    return new Builder(lexicon);
  }

  /**
   * Expands a term for one part of speech.
   *
   * @param term The term to expand; must not be null or blank.
   * @param pos  The part of speech to expand as; must not be null.
   * @return The expansions, deduplicated and ordered by descending weight; empty when the term
   *     (and its lemma, when a lemmatizer is configured) is not in the lexicon.
   * @throws IllegalArgumentException Thrown if {@code term} is null or blank or {@code pos} is
   *     null.
   */
  public List<Expansion> expand(String term, WordNetPOS pos) {
    if (pos == null) {
      throw new IllegalArgumentException("The pos must not be null.");
    }
    return collect(term, List.of(pos));
  }

  /**
   * Expands a term across all parts of speech.
   *
   * @param term The term to expand; must not be null or blank.
   * @return The expansions across every part of speech, deduplicated and ordered by descending
   *     weight; empty when the term is not in the lexicon.
   * @throws IllegalArgumentException Thrown if {@code term} is null or blank.
   */
  public List<Expansion> expand(String term) {
    return collect(term, List.of(WordNetPOS.values()));
  }

  /**
   * Expands the term across the given parts of speech and returns the ranked, capped result.
   *
   * @param term  The term to expand. Must not be {@code null} or blank.
   * @param poses The parts of speech to expand as. Must not be {@code null}.
   * @return The expansions, deduplicated, ordered by descending weight, and capped at the
   *     configured maximum; empty when the term is not in the lexicon.
   * @throws IllegalArgumentException Thrown if {@code term} is {@code null} or blank.
   */
  private List<Expansion> collect(String term, List<WordNetPOS> poses) {
    if (term == null || StringUtil.isBlank(term)) {
      throw new IllegalArgumentException("The term must not be null or blank.");
    }
    final Map<String, Expansion> best = new HashMap<>();
    final Set<String> excluded = new HashSet<>();
    excluded.add(LemmaFolding.fold(term));

    for (final WordNetPOS pos : poses) {
      final String subject = resolveSubject(term, pos);
      if (subject == null) {
        continue;
      }
      final List<Synset> senses = lexicon.lookup(subject, pos);
      final int senseCount = Math.min(senses.size(), maxSenses);
      for (int rank = 0; rank < senseCount; rank++) {
        final double senseWeight = Math.pow(senseDecay, rank);
        expandSense(senses.get(rank), rank, senseWeight, best, excluded);
      }
    }

    final List<Expansion> ordered = new ArrayList<>(best.values());
    ordered.sort(Comparator.comparingDouble(Expansion::weight).reversed()
        .thenComparing(Expansion::kind)
        .thenComparing(Expansion::term));
    return ordered.size() > maxExpansions ? List.copyOf(ordered.subList(0, maxExpansions))
        : List.copyOf(ordered);
  }

  /**
   * Resolves the form actually expanded: the term when the lexicon knows it, otherwise its lemma
   * when a lemmatizer is configured and produces a known lemma.
   *
   * @param term The input term. Must not be {@code null}.
   * @param pos  The part of speech to look the term up as. Must not be {@code null}.
   * @return The known form to expand, or {@code null} when neither the term nor its lemma is in
   *     the lexicon.
   */
  private String resolveSubject(String term, WordNetPOS pos) {
    if (lexicon.contains(term, pos)) {
      return term;
    }
    if (lemmatizer == null) {
      return null;
    }
    final String[] lemmas =
        lemmatizer.lemmatize(new String[] {term}, new String[] {pos.name()});
    if (lemmas.length == 0 || lemmas[0] == null) {
      return null;
    }
    final String lemma = lemmas[0];
    // Lemmatizers report an unresolvable token with the contract's unknown marker.
    if (MorphyLemmatizer.UNKNOWN_LEMMA.equals(lemma) || !lexicon.contains(lemma, pos)) {
      return null;
    }
    return lemma;
  }

  /**
   * Offers the synonyms, hypernym ancestors, and optional hyponyms of one sense into the running
   * best-expansion map.
   *
   * @param sense       The sense to expand. Must not be {@code null}.
   * @param rank        The zero-based salience rank of the sense.
   * @param senseWeight The weight of the sense itself; each relation step decays from it.
   * @param best        The best expansion seen so far per folded term; updated in place.
   * @param excluded    The folded terms that are never reported, such as the input term.
   */
  private void expandSense(Synset sense, int rank, double senseWeight,
                           Map<String, Expansion> best, Set<String> excluded) {
    if (senseWeight == 0.0) {
      // The decay product underflowed to zero in double arithmetic. A zero weight
      // carries no ranking signal, so the sense and everything derived from it is
      // dropped instead of emitted outside the documented (0, 1] weight range.
      return;
    }
    for (final String lemma : sense.lemmas()) {
      offer(best, excluded, new Expansion(lemma, Kind.SYNONYM, 0, rank, senseWeight));
    }

    // Breadth-first hypernym walk, visited-checked so cyclic data terminates.
    if (hypernymDepth > 0) {
      final Set<String> visited = new HashSet<>();
      visited.add(sense.id());
      final ArrayDeque<String> frontier = new ArrayDeque<>(hypernymsOf(sense));
      final ArrayDeque<String> next = new ArrayDeque<>();
      for (int depth = 1; depth <= hypernymDepth && !frontier.isEmpty(); depth++) {
        final double depthWeight = senseWeight * Math.pow(depthDecay, depth);
        if (depthWeight == 0.0) {
          // Deeper levels only shrink further, so the walk stops at the first underflow.
          break;
        }
        while (!frontier.isEmpty()) {
          final String id = frontier.poll();
          if (!visited.add(id)) {
            continue;
          }
          final Synset ancestor = lexicon.synset(id).orElse(null);
          if (ancestor == null) {
            continue;
          }
          for (final String lemma : ancestor.lemmas()) {
            offer(best, excluded,
                new Expansion(lemma, Kind.HYPERNYM, depth, rank, depthWeight));
          }
          next.addAll(hypernymsOf(ancestor));
        }
        frontier.addAll(next);
        next.clear();
      }
    }

    if (includeHyponyms && senseWeight * depthDecay > 0.0) {
      final double hyponymWeight = senseWeight * depthDecay;
      final List<String> children = new ArrayList<>(sense.related(WordNetRelation.HYPONYM));
      children.addAll(sense.related(WordNetRelation.INSTANCE_HYPONYM));
      for (final String id : children) {
        lexicon.synset(id).ifPresent(child -> {
          for (final String lemma : child.lemmas()) {
            offer(best, excluded, new Expansion(lemma, Kind.HYPONYM, 1, rank, hyponymWeight));
          }
        });
      }
    }
  }

  /**
   * Collects the synset ids of both the direct and the instance hypernyms of a synset.
   *
   * @param synset The synset whose hypernyms are collected. Must not be {@code null}.
   * @return The hypernym synset ids in source order, direct relations first.
   */
  private static List<String> hypernymsOf(Synset synset) {
    final List<String> direct = synset.related(WordNetRelation.HYPERNYM);
    final List<String> instance = synset.related(WordNetRelation.INSTANCE_HYPERNYM);
    if (instance.isEmpty()) {
      return direct;
    }
    final List<String> all = new ArrayList<>(direct.size() + instance.size());
    all.addAll(direct);
    all.addAll(instance);
    return all;
  }

  /**
   * Records the candidate under its folded term when it is not excluded and it beats the current
   * best weight for that term. Folding through {@link LemmaFolding#fold(String)} keeps the
   * exclusion and deduplication keys aligned with the lexicon's own lemma keys.
   *
   * @param best      The best expansion seen so far per folded term; updated in place.
   * @param excluded  The folded terms that are never reported.
   * @param candidate The expansion to offer. Must not be {@code null}.
   */
  private static void offer(Map<String, Expansion> best, Set<String> excluded,
                            Expansion candidate) {
    final String key = LemmaFolding.fold(candidate.term());
    if (excluded.contains(key)) {
      return;
    }
    final Expansion current = best.get(key);
    if (current == null || candidate.weight() > current.weight()) {
      best.put(key, candidate);
    }
  }

  /** Configures and creates a {@link LexicalExpander}. */
  public static final class Builder {

    private final LexicalKnowledgeBase lexicon;
    private Lemmatizer lemmatizer;
    private int maxSenses = 3;
    private int hypernymDepth = 1;
    private boolean includeHyponyms = false;
    private int maxExpansions = 20;
    private double senseDecay = 0.5;
    private double depthDecay = 0.5;

    /**
     * Creates a builder over the given lexicon; use {@link LexicalExpander#builder}.
     *
     * @param lexicon The knowledge base to expand against. Must not be {@code null}.
     * @throws IllegalArgumentException Thrown if {@code lexicon} is null.
     */
    private Builder(LexicalKnowledgeBase lexicon) {
      if (lexicon == null) {
        throw new IllegalArgumentException("The lexicon must not be null.");
      }
      this.lexicon = lexicon;
    }

    /**
     * Configures a lemmatizer used when the input term itself is not in the lexicon. It is
     * invoked with the {@link WordNetPOS} name as the tag.
     *
     * @param lemmatizer The fallback lemmatizer; must not be null.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if {@code lemmatizer} is null.
     */
    public Builder lemmatizer(Lemmatizer lemmatizer) {
      if (lemmatizer == null) {
        throw new IllegalArgumentException("The lemmatizer must not be null.");
      }
      this.lemmatizer = lemmatizer;
      return this;
    }

    /**
     * Configures how many senses of the term are expanded, most salient first.
     *
     * @param maxSenses The sense count; must be positive. The default is {@code 3}.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if {@code maxSenses} is not positive.
     */
    public Builder maxSenses(int maxSenses) {
      if (maxSenses < 1) {
        throw new IllegalArgumentException("The maxSenses must be positive: " + maxSenses);
      }
      this.maxSenses = maxSenses;
      return this;
    }

    /**
     * Configures how many hypernym steps are walked; {@code 0} disables hypernym expansion.
     *
     * @param hypernymDepth The depth; must not be negative. The default is {@code 1}.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if {@code hypernymDepth} is negative.
     */
    public Builder hypernymDepth(int hypernymDepth) {
      if (hypernymDepth < 0) {
        throw new IllegalArgumentException(
            "The hypernymDepth must not be negative: " + hypernymDepth);
      }
      this.hypernymDepth = hypernymDepth;
      return this;
    }

    /**
     * Configures whether direct hyponyms are included; off by default.
     *
     * @param includeHyponyms Whether to include direct hyponyms.
     * @return This builder.
     */
    public Builder includeHyponyms(boolean includeHyponyms) {
      this.includeHyponyms = includeHyponyms;
      return this;
    }

    /**
     * Configures the maximum number of expansions returned after ranking.
     *
     * @param maxExpansions The cap; must be positive. The default is {@code 20}.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if {@code maxExpansions} is not positive.
     */
    public Builder maxExpansions(int maxExpansions) {
      if (maxExpansions < 1) {
        throw new IllegalArgumentException(
            "The maxExpansions must be positive: " + maxExpansions);
      }
      this.maxExpansions = maxExpansions;
      return this;
    }

    /**
     * Configures the weight multiplier applied per sense rank step.
     *
     * @param senseDecay The decay in {@code (0, 1]}. The default is {@code 0.5}.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if {@code senseDecay} is outside {@code (0, 1]}.
     */
    public Builder senseDecay(double senseDecay) {
      if (!(senseDecay > 0 && senseDecay <= 1)) {
        throw new IllegalArgumentException(
            "The senseDecay must be in (0, 1]: " + senseDecay);
      }
      this.senseDecay = senseDecay;
      return this;
    }

    /**
     * Configures the weight multiplier applied per hypernym or hyponym step.
     *
     * @param depthDecay The decay in {@code (0, 1]}. The default is {@code 0.5}.
     * @return This builder.
     * @throws IllegalArgumentException Thrown if {@code depthDecay} is outside {@code (0, 1]}.
     */
    public Builder depthDecay(double depthDecay) {
      if (!(depthDecay > 0 && depthDecay <= 1)) {
        throw new IllegalArgumentException(
            "The depthDecay must be in (0, 1]: " + depthDecay);
      }
      this.depthDecay = depthDecay;
      return this;
    }

    /** {@return the configured expander} */
    public LexicalExpander build() {
      return new LexicalExpander(this);
    }
  }
}
