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
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;

import static opennlp.tools.depparse.DependencyTestSamples.LANGUAGE;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.feedforwardParameters;
import static opennlp.tools.depparse.DependencyTestSamples.localFeedforward;
import static opennlp.tools.depparse.DependencyTestSamples.networkBytes;
import static opennlp.tools.depparse.DependencyTestSamples.trainFeedforward;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two exits of beam refinement, the parameters local training and refinement
 * ignore, and the manifest a refined model carries.
 */
public class FeedforwardRefinementTest {

  /** The beam width of the refinement runs. */
  private static final int BEAM_SIZE = 2;

  /** An output bias low enough that the transition is never in the beam. */
  private static final float SUPPRESSED_BIAS = -50.0f;

  /** The dropout that would change local training if it were applied to refinement. */
  private static final double HEAVY_DROPOUT = 0.9;

  private static FeedforwardDependencyModel trained;

  /**
   * Trains the model that every test copies or refines.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainModel() throws IOException {
    trained = localFeedforward();
  }

  /**
   * @param epochs The number of refinement passes.
   * @return Refinement parameters with a beam of {@link #BEAM_SIZE}. Never {@code null}.
   */
  private static TrainingParameters refineParameters(int epochs) {
    final TrainingParameters parameters = feedforwardParameters(epochs, 0.01);
    parameters.put(BeamSearch.BEAM_SIZE_PARAMETER, BEAM_SIZE);
    return parameters;
  }

  /**
   * @param model The model to refine.
   * @param parameters The refinement parameters.
   * @return The model refined on the one-sentence sample. Never {@code null}.
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  private static FeedforwardDependencyModel refineOnTheDogBarks(
      FeedforwardDependencyModel model, TrainingParameters parameters) throws IOException {
    final List<DependencySample> samples = List.of(new DependencySample(
        THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS, THE_DOG_BARKS_TREE));
    return FeedforwardDependencyTrainer.refine(model,
        ObjectStreamUtils.createObjectStream(samples), parameters);
  }

  /**
   * Replays the beam on the sentence and reports whether the gold path survives every
   * step. Establishes which exit of the refinement a scenario reaches.
   *
   * @param network The network scoring the sentence.
   * @return {@code true} if the gold transition stays in a beam of {@link #BEAM_SIZE}
   *         for the whole derivation.
   */
  private static boolean goldStaysInTheBeam(FeedforwardNetwork network) {
    final List<Transition> oracle = ArcStandardOracle.transitions(THE_DOG_BARKS_TREE);
    final Transition[] transitions = network.decodedTransitions();
    final FeedforwardContext context =
        new FeedforwardContext(network, THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS);
    final double[] hidden = new double[network.hiddenSize()];
    final double[] scores = new double[transitions.length];
    List<ArcStandardBeam.Node> beam = List.of(
        new ArcStandardBeam.Node(new ArcStandardState(THE_DOG_BARKS_TOKENS.length)));
    for (final Transition gold : oracle) {
      final List<ArcStandardBeam.Node> survivors = ArcStandardBeam.advance(
          ArcStandardBeam.expand(beam, context, network, null, transitions,
              network.transitionIndex(gold), hidden, scores), BEAM_SIZE, transitions);
      if (survivors.stream().noneMatch(node -> node.gold)) {
        return false;
      }
      beam = survivors;
    }
    return true;
  }

  /**
   * Checks the early-update exit: with the gold {@code det} arc suppressed, the gold
   * path falls out of the beam, and refining on the sentence raises that transition's
   * bias, the update that moves the gold path back into the candidates.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testEarlyUpdateRaisesTheDroppedGoldTransition() throws IOException {
    final FeedforwardNetwork suppressed = trained.network().copy();
    final int det = suppressed.transitionIndex(Transition.leftArc("det"));
    suppressed.outputBias()[det] = SUPPRESSED_BIAS;
    assertFalse(goldStaysInTheBeam(suppressed), "the scenario must drop the gold path");

    final FeedforwardDependencyModel refined = refineOnTheDogBarks(
        new FeedforwardDependencyModel(LANGUAGE, suppressed, null), refineParameters(1));

    assertTrue(refined.network().outputBias()[det] > SUPPRESSED_BIAS,
        "bias " + refined.network().outputBias()[det]);
    assertEquals(SUPPRESSED_BIAS, suppressed.outputBias()[det], "the input is not modified");
  }

  /**
   * Checks the final-beam exit: when the gold path survives every step, refinement
   * still updates once the derivation ends, so the weights change.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testFinalBeamUpdateChangesTheWeights() throws IOException {
    assertTrue(goldStaysInTheBeam(trained.network()), "the scenario must keep the gold path");
    final byte[] before = networkBytes(trained.network());

    final FeedforwardDependencyModel refined =
        refineOnTheDogBarks(trained, refineParameters(1));

    assertFalse(Arrays.equals(before, networkBytes(refined.network())));
    assertArrayEquals(before, networkBytes(trained.network()), "the input is not modified");
  }

  /**
   * Checks that refinement applies no dropout: heavy dropout leaves the result
   * identical to none.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testRefinementIgnoresDropout() throws IOException {
    final TrainingParameters heavy = refineParameters(2);
    heavy.put(FeedforwardDependencyTrainer.DROPOUT_PARAM, HEAVY_DROPOUT);
    final TrainingParameters none = refineParameters(2);
    assertArrayEquals(networkBytes(refineOnTheDogBarks(trained, none).network()),
        networkBytes(refineOnTheDogBarks(trained, heavy).network()));
  }

  /**
   * Checks that local training ignores the beam size: widths of one and eight yield the
   * same network.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testTrainingIgnoresTheBeamSize() throws IOException {
    final TrainingParameters narrow = feedforwardParameters(2, 0.05);
    narrow.put(BeamSearch.BEAM_SIZE_PARAMETER, 1);
    final TrainingParameters wide = feedforwardParameters(2, 0.05);
    wide.put(BeamSearch.BEAM_SIZE_PARAMETER, 8);
    assertArrayEquals(networkBytes(trainFeedforward(corpus(), narrow).network()),
        networkBytes(trainFeedforward(corpus(), wide).network()));
  }

  /**
   * Checks the manifest of a refined model: the iterations and the beam size come from
   * the refinement parameters, and the word cutoff from the input model, since
   * refinement does not change the word list.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testManifestRecordsTheRefinementRun() throws IOException {
    assertEquals("60", trained.getManifestProperty(BaseModel.TRAINING_ITERATIONS_PROPERTY));
    final TrainingParameters parameters = refineParameters(3);
    parameters.put(Parameters.CUTOFF_PARAM, 7);
    final FeedforwardDependencyModel refined = refineOnTheDogBarks(trained, parameters);
    assertEquals("3", refined.getManifestProperty(BaseModel.TRAINING_ITERATIONS_PROPERTY));
    assertEquals(trained.getManifestProperty(BaseModel.TRAINING_CUTOFF_PROPERTY),
        refined.getManifestProperty(BaseModel.TRAINING_CUTOFF_PROPERTY));
    assertEquals(Integer.toString(BEAM_SIZE),
        refined.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER));
  }
}
