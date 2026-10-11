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

import java.util.List;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.util.ParamChecks;

/**
 * The neural {@link DependencyParser}: an arc-standard decoder over the
 * {@link FeedforwardDependencyModel}, greedy by default and beamed when constructed
 * with a beam size above one. It scores configurations with learned dense
 * representations instead of the sparse feature conjunctions of
 * {@link DependencyParserME}.
 *
 * <p>Beam decoding retains the highest scoring transition sequences by summed
 * log-probability. Complete arc-standard derivations for a sentence have equal length,
 * so their scores need no length normalization.</p>
 *
 * <p>The parser does not modify its model and keeps no per-parse state, so one
 * instance can be shared between threads. An optional scoring cache, owned by the
 * parser and bounded in bytes, stores the hidden-layer contribution of each feature
 * position and embedding row it has scored.</p>
 *
 * @see FeedforwardDependencyTrainer
 * @since 3.0.0
 */
@ThreadSafe
public class FeedforwardDependencyParser implements DependencyParser {

  /** The beam size at which decoding is greedy. */
  private static final int GREEDY_BEAM_SIZE = 1;

  /** The scoring cache budget at which no cache is created. */
  private static final long NO_CACHE = 0L;

  private final FeedforwardNetwork network;
  private final Transition[] transitions;
  private final int beamSize;
  private final ContributionCache cache;

  /**
   * Initializes a greedy {@link FeedforwardDependencyParser} without a scoring cache.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null}.
   */
  public FeedforwardDependencyParser(FeedforwardDependencyModel model) {
    this(model, GREEDY_BEAM_SIZE);
  }

  /**
   * Initializes a {@link FeedforwardDependencyParser} with a beam and without a
   * scoring cache.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @param beamSize The number of transition sequences to retain. Must be positive;
   *                 {@code 1} decodes greedily.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null} or
   *         {@code beamSize} is not positive.
   */
  public FeedforwardDependencyParser(FeedforwardDependencyModel model, int beamSize) {
    this(model, beamSize, NO_CACHE);
  }

  /**
   * Initializes a {@link FeedforwardDependencyParser} with a beam and a scoring cache.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @param beamSize The number of transition sequences to retain. Must be positive;
   *                 {@code 1} decodes greedily.
   * @param scoringCacheBytes The bytes the scoring cache may use, or {@code 0} for no
   *                          cache. Must not be negative. The cache needs room for one
   *                          slot per vocabulary entry and feature position before it
   *                          stores a contribution.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null},
   *         {@code beamSize} is not positive, {@code scoringCacheBytes} is negative, or
   *         a positive {@code scoringCacheBytes} is too small for the model's
   *         vocabularies.
   */
  public FeedforwardDependencyParser(FeedforwardDependencyModel model, int beamSize,
      long scoringCacheBytes) {
    ParamChecks.requireNonNullArg(model, "model");
    ParamChecks.requirePositive(beamSize, "beamSize");
    ParamChecks.requireNonNegative(scoringCacheBytes, "scoringCacheBytes");
    this.network = model.network();
    this.beamSize = beamSize;
    this.transitions = network.decodedTransitions();
    this.cache = scoringCacheBytes == NO_CACHE
        ? null : new ContributionCache(network, scoringCacheBytes);
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalStateException Thrown if no model outcome is applicable in a configuration
   *         or the model produces a non-finite transition score.
   */
  @Override
  public DependencyTree parse(String[] tokens, String[] tags) {
    ParamChecks.requireNonEmptyParallelArrays(tokens, "tokens", tags, "tags");
    final FeedforwardContext context = new FeedforwardContext(network, tokens, tags);
    if (beamSize == GREEDY_BEAM_SIZE) {
      return greedyParse(context, tokens.length);
    }
    return beamParse(context, tokens.length);
  }

  /**
   * Decodes greedily: the highest scoring applicable transition wins each step.
   *
   * @param context The sentence features.
   * @param tokenCount The sentence length.
   * @return The parse. Never {@code null}.
   * @throws IllegalStateException Thrown if no model outcome is applicable in a configuration
   *         or the model produces a non-finite transition score.
   */
  private DependencyTree greedyParse(FeedforwardContext context, int tokenCount) {
    final ArcStandardState state = new ArcStandardState(tokenCount);
    final int[] features = new int[FeedforwardContext.FEATURE_COUNT];
    final double[] hidden = new double[network.hiddenSize()];
    final double[] scores = new double[transitions.length];
    while (!state.isTerminal()) {
      context.featureIds(state, features);
      network.score(features, cache, hidden, scores);
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
    return state.toTree();
  }

  /**
   * Decodes with a beam and returns its highest scoring complete sequence.
   *
   * @param context The sentence features.
   * @param tokenCount The sentence length.
   * @return The parse. Never {@code null}.
   * @throws IllegalStateException Thrown if no beam path can be advanced by a model
   *         outcome or the model produces a non-finite transition score.
   */
  private DependencyTree beamParse(FeedforwardContext context, int tokenCount) {
    List<ArcStandardBeam.Node> beam =
        List.of(new ArcStandardBeam.Node(new ArcStandardState(tokenCount)));
    final double[] hidden = new double[network.hiddenSize()];
    final double[] scores = new double[transitions.length];
    while (!beam.get(0).state.isTerminal()) {
      final List<ArcStandardBeam.Node> expansions = ArcStandardBeam.expand(beam, context,
          network, cache, transitions, ArcStandardBeam.NO_TRANSITION, hidden, scores);
      if (expansions.isEmpty()) {
        throw new IllegalStateException(
            "no applicable transition among the model outcomes in the beam");
      }
      beam = ArcStandardBeam.advance(expansions, beamSize, transitions);
    }
    return beam.get(0).state.toTree();
  }

  /**
   * @return The scoring cache, or {@code null} if the parser was created without one.
   */
  ContributionCache cache() {
    return cache;
  }
}
