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
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.TrainingParameters;

import static opennlp.tools.depparse.DependencyTestSamples.CORPUS_WORDS;
import static opennlp.tools.depparse.DependencyTestSamples.LANGUAGE;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DependencyParserME} end to end: training on a small corpus must let the
 * greedy parser reproduce the training sentences, which proves the oracle, event stream,
 * feature generation, and decode loop agree with each other.
 */
public class DependencyParserMETest {

  /** A custom manifest entry name. */
  private static final String MANIFEST_ENTRY = "test-entry";

  /** The value stored under {@link #MANIFEST_ENTRY}. */
  private static final String MANIFEST_VALUE = "test-value";

  /** The manifest entry in which a model records its tool factory, if it has one. */
  private static final String FACTORY_MANIFEST_ENTRY = "factory";

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
    model = DependencyTestSamples.train(corpus());
    parser = new DependencyParserME(model);
  }

  @Test
  void testMemorizesTrainingSentences() {
    final DependencyTree parsed = parser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS);
    assertEquals(THE_DOG_BARKS_TREE, parsed);
  }

  @Test
  void testParseAlwaysYieldsASingleRootedTree() {
    // an unseen sentence must still decode to a valid tree, whatever its quality
    final DependencyTree parsed = parser.parse(new String[] {"cats", "sleep"},
        new String[] {"NNS", "VBP"});
    assertEquals(2, parsed.size());
    int roots = 0;
    for (int i = 0; i < parsed.size(); i++) {
      if (parsed.headOf(i) == DependencyArc.ROOT_HEAD) {
        roots++;
      }
    }
    assertEquals(1, roots);
    assertEquals(DependencyArc.ROOT_HEAD, parsed.headOf(parsed.root()));
  }

  @Test
  void testEvaluatorScoresPerfectlyOnTrainingData() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
    assertEquals(CORPUS_WORDS, evaluator.getWordCount());
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

  /**
   * The input checks of {@link DependencyParserME#parse(String[], String[])}, with the
   * messages {@link DependencySample} uses for the same violations.
   *
   * @return Token and tag arrays with the expected message.
   */
  static Stream<Arguments> rejectedParseInput() {
    final String[] tags = {"DT", "NN", "VBZ"};
    return Stream.of(
        Arguments.of(null, tags, "tokens must not be null"),
        Arguments.of(THE_DOG_BARKS_TOKENS, null, "tags must not be null"),
        Arguments.of(new String[0], new String[0], "tokens must not be empty"),
        Arguments.of(new String[] {"the"}, tags, "tokens and tags must have the same length: 1 != 3"),
        Arguments.of(new String[] {"the", null, "barks"}, tags, "token must not be null at index 1"),
        Arguments.of(THE_DOG_BARKS_TOKENS, new String[] {"DT", "NN", null},
            "tag must not be null at index 2"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("rejectedParseInput")
  void testParseMessagesMatchDependencySample(String[] tokens, String[] tags, String message) {
    final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> parser.parse(tokens, tags));
    assertEquals(message, exception.getMessage());
    assertEquals(message, assertThrows(IllegalArgumentException.class,
        () -> new DependencySample(tokens, tags, THE_DOG_BARKS_TREE)).getMessage());
  }

  @Test
  void testConstructorRejectsNullModel() {
    assertThrows(IllegalArgumentException.class, () -> new DependencyParserME(null));
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
    assertEquals("samples must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(LANGUAGE, null, TrainingParameters.defaultParams()))
        .getMessage());
    assertEquals("parameters must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(LANGUAGE,
            ObjectStreamUtils.createObjectStream(corpus()), null)).getMessage());
    assertEquals("languageCode must not be null", assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(null,
            ObjectStreamUtils.createObjectStream(corpus()),
            TrainingParameters.defaultParams())).getMessage());
  }

  @Test
  void testModelRoundTripThroughSerialization() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    final DependencyModel reloaded = new DependencyModel(
        new ByteArrayInputStream(out.toByteArray()));
    final DependencyTree parsed = new DependencyParserME(reloaded)
        .parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS);
    assertEquals(THE_DOG_BARKS_TREE, parsed);
    assertEquals(LANGUAGE, reloaded.getLanguage());
  }

  @Test
  void testManifestEntriesRoundTripWithoutAToolFactory() throws IOException {
    final DependencyModel withEntry = new DependencyModel(LANGUAGE, model.getParserModel(),
        Map.of(MANIFEST_ENTRY, MANIFEST_VALUE));
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    withEntry.serialize(out);
    final DependencyModel reloaded = new DependencyModel(
        new ByteArrayInputStream(out.toByteArray()));
    assertEquals(MANIFEST_VALUE, reloaded.getManifestProperty(MANIFEST_ENTRY));
    // no tool factory is recorded, so loading needs no extension class
    assertNull(reloaded.getManifestProperty(FACTORY_MANIFEST_ENTRY));
    assertEquals(THE_DOG_BARKS_TREE,
        new DependencyParserME(reloaded).parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  @Test
  void testModelWithForeignOutcomesIsRejectedAtConstruction() {
    // The outcome inventory is decoded once up front, so a model trained for another
    // task is rejected when the parser is built rather than mid-sentence.
    assertThrows(IllegalArgumentException.class,
        () -> parserOf(new OutcomeOnlyModel("NN", "VB")));
  }

  @Test
  void testIncompleteActionInventoriesAreRejectedAtConstruction() {
    assertThrows(IllegalArgumentException.class,
        () -> parserOf(new OutcomeOnlyModel(SHIFT_OUTCOME)));
    assertThrows(IllegalArgumentException.class,
        () -> parserOf(new OutcomeOnlyModel(ROOT_ARC_OUTCOME)));
  }

  @Test
  void testDuplicateActionsAreRejectedAtConstruction() {
    assertThrows(IllegalArgumentException.class,
        () -> parserOf(new OutcomeOnlyModel(SHIFT_OUTCOME, SHIFT_OUTCOME, ROOT_ARC_OUTCOME)));
  }

  @Test
  void testModelScoreCountIsValidated() {
    final DependencyParserME invalid = parserOf(
        new OutcomeOnlyModel(new double[] {1.0}, SHIFT_OUTCOME, ROOT_ARC_OUTCOME));
    final IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> invalid.parse(new String[] {"word"}, new String[] {"NN"}));
    assertEquals("model returned 1 scores for 2 outcomes", exception.getMessage());
  }

  @Test
  void testNonFiniteModelScoreIsRejected() {
    final DependencyParserME invalid = parserOf(new OutcomeOnlyModel(
        new double[] {Double.NaN, 1.0}, SHIFT_OUTCOME, ROOT_ARC_OUTCOME));
    assertThrows(IllegalStateException.class,
        () -> invalid.parse(new String[] {"word"}, new String[] {"NN"}));
  }

  /**
   * Wraps a raw transition model the way a trained model would be wrapped.
   *
   * @param model The transition classification model.
   * @return A parser over {@code model}. Never {@code null}.
   */
  private static DependencyParserME parserOf(MaxentModel model) {
    return new DependencyParserME(new DependencyModel(LANGUAGE, model, null));
  }

  /**
   * An {@link AbstractModel} that only knows its outcome inventory, enough to build a
   * model and a parser from; any other use fails.
   */
  private static final class OutcomeOnlyModel extends AbstractModel {

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
      super(new Context[0], new String[0], outcomes);
      this.scores = scores;
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
  }
}
