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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DependencyParserME} end to end: training on a small corpus must let the
 * greedy parser reproduce the training sentences, which proves the oracle, event stream,
 * feature generation, and decode loop agree with each other.
 */
public class DependencyParserMETest {

  /** The language code of the test corpus. */
  private static final String LANGUAGE = "eng";

  /** The tokens of the first corpus sentence. */
  private static final String[] THE_DOG_BARKS_TOKENS = {"the", "dog", "barks"};

  /** The tags of the first corpus sentence. */
  private static final String[] THE_DOG_BARKS_TAGS = {"DT", "NN", "VBZ"};

  /** The gold graph of the first corpus sentence. */
  private static final DependencyGraph THE_DOG_BARKS_GRAPH =
      DependencyGraph.of(new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"});

  /** The total token count of {@link DependencyTestSamples#corpus()}. */
  private static final int CORPUS_WORD_COUNT = 320;

  /** The encoded shift outcome. */
  private static final String SHIFT_OUTCOME = "SHIFT";

  /** The encoded right arc outcome attaching a token to the root. */
  private static final String ROOT_ARC_OUTCOME = "RIGHT_ARC:root";

  private static DependencyModel model;
  private static DependencyParserME parser;

  /**
   * Trains the shared model once for all tests; the zero cutoff keeps every feature of
   * the test corpus.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    model = DependencyParserME.train(LANGUAGE,
        ObjectStreamUtils.createObjectStream(corpus()), parameters);
    parser = new DependencyParserME(model);
  }

  @Test
  void testMemorizesTrainingSentences() {
    final DependencyGraph parsed = parser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS);
    assertEquals(THE_DOG_BARKS_GRAPH, parsed);
  }

  @Test
  void testParseAlwaysYieldsASingleRootedTree() {
    // an unseen sentence must still decode to a valid graph, whatever its quality
    final DependencyGraph parsed = parser.parse(new String[] {"cats", "sleep"},
        new String[] {"NNS", "VBP"});
    assertEquals(2, parsed.size());
    parsed.root();
  }

  @Test
  void testEvaluatorScoresPerfectlyOnTrainingData() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
    assertEquals(CORPUS_WORD_COUNT, evaluator.getWordCount());
  }

  @Test
  void testParseValidatesArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(null, new String[] {"DT"}));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"the"}, null));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[0], new String[0]));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"the"}, new String[] {"DT", "NN"}));
  }

  @Test
  void testConstructorRejectsNullModel() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME((DependencyModel) null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME((MaxentModel) null));
  }

  @Test
  void testModelConstructorsRejectNullArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel(null, model.getParserModel(), null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel(LANGUAGE, null, null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel((InputStream) null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel((File) null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel((Path) null));
  }

  @Test
  void testTrainValidatesArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(LANGUAGE, null, TrainingParameters.defaultParams()));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(LANGUAGE,
            ObjectStreamUtils.createObjectStream(corpus()), null));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(null,
            ObjectStreamUtils.createObjectStream(corpus()),
            TrainingParameters.defaultParams()));
  }

  @Test
  void testModelRoundTripThroughSerialization() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    final DependencyModel reloaded = new DependencyModel(
        new ByteArrayInputStream(out.toByteArray()));
    final DependencyGraph parsed = new DependencyParserME(reloaded)
        .parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS);
    assertEquals(THE_DOG_BARKS_GRAPH, parsed);
  }

  @Test
  void testModelRejectsNullParserModel() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel(LANGUAGE, null, null));
  }

  @Test
  void testModelWithForeignOutcomesIsRejectedAtConstruction() {
    // The outcome inventory is decoded once up front, so a model trained for another
    // task is rejected when the parser is built rather than mid-sentence.
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME(new OutcomeOnlyModel("NN", "VB")));
  }

  @Test
  void testIncompleteActionInventoriesAreRejectedAtConstruction() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME(new OutcomeOnlyModel(SHIFT_OUTCOME)));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME(new OutcomeOnlyModel(ROOT_ARC_OUTCOME)));
  }

  @Test
  void testDuplicateActionsAreRejectedAtConstruction() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME(
            new OutcomeOnlyModel(SHIFT_OUTCOME, SHIFT_OUTCOME, ROOT_ARC_OUTCOME)));
  }

  @Test
  void testModelScoreCountIsValidated() {
    final DependencyParserME invalid = new DependencyParserME(
        new OutcomeOnlyModel(new double[] {1.0}, SHIFT_OUTCOME, ROOT_ARC_OUTCOME));
    final IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> invalid.parse(new String[] {"word"}, new String[] {"NN"}));
    assertEquals("model returned 1 scores for 2 outcomes", exception.getMessage());
  }

  @Test
  void testNonFiniteModelScoreIsRejected() {
    final DependencyParserME invalid = new DependencyParserME(
        new OutcomeOnlyModel(new double[] {Double.NaN, 1.0},
            SHIFT_OUTCOME, ROOT_ARC_OUTCOME));
    assertThrows(IllegalStateException.class,
        () -> invalid.parse(new String[] {"word"}, new String[] {"NN"}));
  }

  /**
   * A {@link MaxentModel} that only knows its outcome inventory, enough to build a
   * parser from; any other use fails.
   */
  private static final class OutcomeOnlyModel implements MaxentModel {

    private final String[] outcomes;
    private final double[] scores;

    /**
     * Initializes a model that fails on every evaluation.
     *
     * @param outcomes The outcome inventory, in index order.
     */
    private OutcomeOnlyModel(String... outcomes) {
      this(null, outcomes);
    }

    /**
     * Initializes a model returning fixed scores.
     *
     * @param scores The scores every evaluation returns, or {@code null} to fail instead.
     * @param outcomes The outcome inventory, in index order.
     */
    private OutcomeOnlyModel(double[] scores, String... outcomes) {
      this.scores = scores;
      this.outcomes = outcomes;
    }

    @Override
    public String getOutcome(int i) {
      return outcomes[i];
    }

    @Override
    public int getNumOutcomes() {
      return outcomes.length;
    }

    @Override
    public double[] eval(String[] context) {
      if (scores == null) {
        throw new UnsupportedOperationException();
      }
      return scores.clone();
    }

    @Override
    public double[] eval(String[] context, double[] probs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public double[] eval(String[] context, float[] values) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getBestOutcome(double[] outcomeScores) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getAllOutcomes(double[] outcomeScores) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getIndex(String outcome) {
      throw new UnsupportedOperationException();
    }
  }
}
