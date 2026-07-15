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

import java.io.IOException;
import java.util.HashMap;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.TrainerFactory.TrainerType;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * A greedy transition-based {@link DependencyParser}: a maximum entropy classifier picks
 * the next arc-standard {@link Transition} for each configuration until the parse is
 * complete, always taking the highest scoring transition that is applicable.
 *
 * <p>The parser holds an immutable model and no per-parse state, so one instance can be
 * shared between threads.</p>
 *
 * @see DependencyParser
 * @since 3.0.0
 */
public class DependencyParserME implements DependencyParser {

  private final MaxentModel model;
  private final DependencyContextGenerator contextGenerator;

  /**
   * Initializes a {@link DependencyParserME} with a trained transition model.
   *
   * @param model The transition classification model. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null}.
   */
  public DependencyParserME(MaxentModel model) {
    if (model == null) {
      throw new IllegalArgumentException("model must not be null");
    }
    this.model = model;
    this.contextGenerator = new DependencyContextGenerator();
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
      state.apply(bestApplicable(state, tokens, tags));
    }
    return state.toGraph();
  }

  /**
   * Scores all outcomes for the current configuration and picks the best transition that
   * is applicable; inapplicable outcomes are passed over regardless of score.
   */
  private Transition bestApplicable(ArcStandardState state, String[] tokens, String[] tags) {
    final double[] probabilities = model.eval(contextGenerator.getContext(state, tokens, tags));
    Transition best = null;
    double bestProbability = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < probabilities.length; i++) {
      if (probabilities[i] <= bestProbability) {
        continue;
      }
      final Transition candidate;
      try {
        candidate = Transition.decode(model.getOutcome(i));
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "model outcome is not a transition: " + model.getOutcome(i), e);
      }
      if (state.canApply(candidate)) {
        best = candidate;
        bestProbability = probabilities[i];
      }
    }
    if (best == null) {
      throw new IllegalStateException(
          "no applicable transition among the model outcomes in " + state);
    }
    return best;
  }

  /**
   * Trains a greedy arc-standard parser from dependency samples.
   *
   * <p>Non-projective samples have no arc-standard derivation and are skipped during
   * event generation.</p>
   *
   * @param samples The training samples. Must not be {@code null}.
   * @param parameters The {@link TrainingParameters}. Must not be {@code null} and must
   *                   select an event model trainer.
   * @return A trained {@link DependencyParserME}. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null} or the
   *         configured trainer is not an event model trainer.
   */
  public static DependencyParserME train(ObjectStream<DependencySample> samples,
      TrainingParameters parameters) throws IOException {
    if (samples == null || parameters == null) {
      throw new IllegalArgumentException("samples and parameters must not be null");
    }
    final TrainerType trainerType = TrainerFactory.getTrainerType(parameters);
    if (!TrainerType.EVENT_MODEL_TRAINER.equals(trainerType)) {
      throw new IllegalArgumentException("Trainer type is not supported: " + trainerType);
    }
    final EventTrainer<TrainingParameters> trainer =
        TrainerFactory.getEventTrainer(parameters, new HashMap<>());
    final ObjectStream<Event> events =
        new DependencyEventStream(samples, new DependencyContextGenerator());
    return new DependencyParserME(trainer.train(events));
  }
}
