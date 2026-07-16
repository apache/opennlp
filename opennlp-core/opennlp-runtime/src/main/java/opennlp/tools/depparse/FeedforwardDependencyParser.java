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

package opennlp.tools.depparse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The pure-Java neural {@link DependencyParser}: an arc-standard decoder over the
 * {@link FeedforwardDependencyModel}, greedy by default and beamed when constructed
 * with a beam size above one.
 *
 * <p>With a beam, the decoder keeps the highest scoring transition sequences side by
 * side, scored by summed log-probabilities, so a single locally attractive but globally
 * wrong transition can still be recovered while the correct parse remains inside the
 * beam. Every complete arc-standard derivation of a sentence has the same length, which
 * keeps the summed scores comparable without length normalization.</p>
 *
 * <p>Inference is ordinary array arithmetic with no native runtime involved, so this
 * parser deploys exactly like the classical one while scoring configurations with
 * learned dense representations instead of sparse feature conjunctions.</p>
 *
 * <p>The parser holds an immutable model and no per-parse state, so one instance can be
 * shared between threads.</p>
 *
 * @see FeedforwardDependencyTrainer
 * @since 3.0.0
 */
public class FeedforwardDependencyParser implements DependencyParser {

  private final FeedforwardDependencyModel model;
  private final Transition[] transitions;
  private final int beamSize;

  /**
   * Initializes a greedy {@link FeedforwardDependencyParser}.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null} or an
   *         outcome of the model does not decode to a transition.
   */
  public FeedforwardDependencyParser(FeedforwardDependencyModel model) {
    this(model, 1);
  }

  /**
   * Initializes a {@link FeedforwardDependencyParser} with a beam.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @param beamSize The number of transition sequences to keep side by side. Must be
   *                 greater than zero; {@code 1} decodes greedily.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null},
   *         {@code beamSize} is not positive, or an outcome of the model does not
   *         decode to a transition.
   */
  public FeedforwardDependencyParser(FeedforwardDependencyModel model, int beamSize) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    if (beamSize < 1) {
      throw new IllegalArgumentException("beamSize must be positive: " + beamSize);
    }
    this.model = model;
    this.beamSize = beamSize;
    final String[] outcomes = model.transitions();
    this.transitions = new Transition[outcomes.length];
    for (int i = 0; i < outcomes.length; i++) {
      transitions[i] = Transition.decode(outcomes[i]);
    }
  }

  @Override
  public DependencyGraph parse(String[] tokens, String[] tags) {
    if (tokens == null || tags == null) {
      throw new IllegalArgumentException("tokens and tags must not be null");
    }
    if (tokens.length == 0) {
      throw new IllegalArgumentException("tokens must not be empty");
    }
    if (tokens.length != tags.length) {
      throw new IllegalArgumentException("tokens and tags must have the same length: "
          + tokens.length + " != " + tags.length);
    }
    if (beamSize == 1) {
      return greedyParse(tokens, tags);
    }
    return beamParse(tokens, tags);
  }

  /**
   * Decodes greedily: the highest scoring applicable transition wins each step.
   *
   * @param tokens The sentence tokens.
   * @param tags The POS tags, aligned with {@code tokens}.
   * @return The parse. Never {@code null}.
   */
  private DependencyGraph greedyParse(String[] tokens, String[] tags) {
    final ArcStandardState state = new ArcStandardState(tokens.length);
    while (!state.isTerminal()) {
      final double[] scores = model.score(
          model.featureIds(FeedforwardContext.extract(state, tokens, tags)));
      Transition best = null;
      double bestScore = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < scores.length; i++) {
        if (scores[i] > bestScore && state.canApply(transitions[i])) {
          best = transitions[i];
          bestScore = scores[i];
        }
      }
      if (best == null) {
        throw new IllegalStateException(
            "no applicable transition among the model outcomes in " + state);
      }
      state.apply(best);
    }
    return state.toGraph();
  }

  /** One search alternative: a configuration, its summed log-probability score, and the
   * transition that would advance it, {@code null} once complete. */
  private record Alternative(ArcStandardState state, double score, Transition next) {
  }

  /**
   * Decodes with a beam: the {@code beamSize} best transition sequences advance side by
   * side and the best complete one wins.
   *
   * @param tokens The sentence tokens.
   * @param tags The POS tags, aligned with {@code tokens}.
   * @return The parse. Never {@code null}.
   */
  private DependencyGraph beamParse(String[] tokens, String[] tags) {
    List<Alternative> beam =
        List.of(new Alternative(new ArcStandardState(tokens.length), 0.0, null));
    while (true) {
      boolean advanced = false;
      final List<Alternative> expansions = new ArrayList<>();
      for (final Alternative alternative : beam) {
        if (alternative.state().isTerminal()) {
          expansions.add(alternative);
          continue;
        }
        advanced = true;
        final double[] logProbabilities = logSoftmax(model.score(
            model.featureIds(FeedforwardContext.extract(alternative.state(), tokens, tags))));
        for (int i = 0; i < logProbabilities.length; i++) {
          if (alternative.state().canApply(transitions[i])) {
            expansions.add(new Alternative(alternative.state(),
                alternative.score() + logProbabilities[i], transitions[i]));
          }
        }
      }
      if (!advanced) {
        break;
      }
      expansions.sort(Comparator.comparingDouble(Alternative::score).reversed());
      final List<Alternative> survivors =
          new ArrayList<>(Math.min(beamSize, expansions.size()));
      for (int i = 0; i < expansions.size() && survivors.size() < beamSize; i++) {
        final Alternative expansion = expansions.get(i);
        if (expansion.next() == null) {
          survivors.add(expansion);
        } else {
          final ArcStandardState state = expansion.state().copy();
          state.apply(expansion.next());
          survivors.add(new Alternative(state, expansion.score(), null));
        }
      }
      if (survivors.isEmpty()) {
        throw new IllegalStateException(
            "no applicable transition among the model outcomes in the beam");
      }
      beam = survivors;
    }
    return beam.get(0).state().toGraph();
  }

  /**
   * Normalizes raw transition scores to log-probabilities.
   *
   * @param scores The raw output scores.
   * @return The log-softmax of {@code scores}. Never {@code null}.
   */
  private static double[] logSoftmax(double[] scores) {
    double max = Double.NEGATIVE_INFINITY;
    for (final double score : scores) {
      max = Math.max(max, score);
    }
    double sum = 0.0;
    for (final double score : scores) {
      sum += Math.exp(score - max);
    }
    final double logSum = max + Math.log(sum);
    final double[] logProbabilities = new double[scores.length];
    for (int i = 0; i < scores.length; i++) {
      logProbabilities[i] = scores[i] - logSum;
    }
    return logProbabilities;
  }
}
