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
import java.io.IOException;

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
    model = DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(corpus()), parameters);
    parser = new DependencyParserME(model);
  }

  @Test
  void testMemorizesTrainingSentences() {
    final DependencyGraph parsed = parser.parse(new String[] {"the", "dog", "barks"},
        new String[] {"DT", "NN", "VBZ"});
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), parsed);
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
    assertEquals(320, evaluator.getWordCount());
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
  void testTrainValidatesArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train("eng", null, TrainingParameters.defaultParams()));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train("eng",
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
        .parse(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"});
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), parsed);
  }

  @Test
  void testModelRejectsNullParserModel() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel("eng", null, null));
  }

  @Test
  void testModelWithForeignOutcomesIsRejectedAtConstruction() {
    // The outcome inventory is decoded once up front, so a model trained for another
    // task is rejected when the parser is built rather than mid-sentence.
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME(new OutcomeOnlyModel("NN", "VB")));
  }

  @Test
  void testModelScoreCountIsValidated() {
    final DependencyParserME invalid = new DependencyParserME(
        new OutcomeOnlyModel(new double[] {1.0}, "SHIFT", "RIGHT_ARC:root"));
    final IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> invalid.parse(new String[] {"word"}, new String[] {"NN"}));
    assertEquals("model returned 1 scores for 2 outcomes", exception.getMessage());
  }

  @Test
  void testNonFiniteModelScoreIsRejected() {
    final DependencyParserME invalid = new DependencyParserME(
        new OutcomeOnlyModel(new double[] {Double.NaN, 1.0},
            "SHIFT", "RIGHT_ARC:root"));
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

    private OutcomeOnlyModel(String... outcomes) {
      this(null, outcomes);
    }

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
