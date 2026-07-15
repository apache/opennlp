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

/**
 * The pure-Java neural {@link DependencyParser}: a greedy arc-standard decoder over the
 * {@link FeedforwardDependencyModel}, picking the highest scoring applicable transition
 * for each configuration.
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

  /**
   * Initializes a {@link FeedforwardDependencyParser}.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null} or an
   *         outcome of the model does not decode to a transition.
   */
  public FeedforwardDependencyParser(FeedforwardDependencyModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    this.model = model;
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
}
