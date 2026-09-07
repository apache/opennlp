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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.StringUtil;

import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.sample;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests feedforward training, parsing, refinement, and model serialization.
 */
public class FeedforwardDependencyParserTest {

  /** The random seed shared by every training run in this class. */
  private static final long SEED = 17L;

  /** The header every serialized model starts with, pinned independently of the model. */
  private static final String MAGIC = "ONLP-FFDP-1";

  /** The embedding width of the test networks. */
  private static final int EMBEDDING_SIZE = 16;

  /** The hidden width of the test networks. */
  private static final int HIDDEN_SIZE = 32;

  /** The minibatch size of the test networks. */
  private static final int BATCH_SIZE = 32;

  /** The word cutoff that gives every corpus word its own embedding row. */
  private static final int WORD_CUTOFF = 1;

  /** The number of vocabularies a serialized model starts with. */
  private static final int VOCABULARY_COUNT = 3;

  /** The first tag row in the hand-written minimal model, after three word rows. */
  private static final int FIRST_TAG_ID = 3;

  /** The first label row in the hand-written minimal model, after three tag rows. */
  private static final int FIRST_LABEL_ID = 6;

  /** The total embedding rows of the hand-written minimal model. */
  private static final int MINIMAL_ROWS = 8;

  private static FeedforwardDependencyModel model;
  private static FeedforwardDependencyParser parser;

  /**
   * Builds the shared test hyperparameters: no dropout, no L2, and a fixed seed.
   *
   * @param epochs The number of passes over the corpus.
   * @param learningRate The AdaGrad step size.
   * @return The settings. Never {@code null}.
   */
  private static FeedforwardDependencyTrainer.Settings settings(int epochs,
      double learningRate) {
    return new FeedforwardDependencyTrainer.Settings(EMBEDDING_SIZE, HIDDEN_SIZE, epochs,
        BATCH_SIZE, learningRate, 0.0, 0.0, WORD_CUTOFF, SEED);
  }

  /**
   * @return The distinct training sentences, one per parameterized invocation. Never
   *         {@code null}.
   */
  private static List<DependencySample> trainingSamples() {
    return DependencyTestSamples.sentences();
  }

  /**
   * Trains the shared model once for all tests, with dropout off and a fixed seed so
   * the test network memorizes the corpus deterministically.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings = settings(120, 0.05);
    model = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    parser = new FeedforwardDependencyParser(model);
  }

  /** Checks that the greedy parser reproduces a training sentence. */
  @Test
  void testMemorizesTrainingSentences() {
    final DependencyGraph parsed = parser.parse(new String[] {"the", "dog", "barks"},
        new String[] {"DT", "NN", "VBZ"});
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), parsed);
  }

  /** Checks UAS and LAS of 1.0 on the training corpus. */
  @Test
  void testEvaluatorScoresPerfectlyOnTrainingData() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
  }

  /** Checks that unseen words still parse to a single-rooted tree. */
  @Test
  void testUnknownWordsStillYieldASingleRootedTree() {
    final DependencyGraph parsed = parser.parse(new String[] {"unseen", "words"},
        new String[] {"JJ", "NNS"});
    assertEquals(2, parsed.size());
    parsed.root();
  }

  /** Checks that the transition inventory holds only actions the oracle produced. */
  @Test
  void testTransitionInventoryContainsOnlyObservedActions() {
    assertFalse(List.of(model.transitions()).contains("LEFT_ARC:root"));
  }

  /**
   * Checks that a beam of one yields the greedy parse for every training sample.
   *
   * @param sample The training sample to parse both ways.
   */
  @ParameterizedTest(name = "beam of one matches greedy for {0}")
  @MethodSource("trainingSamples")
  void testBeamOfOneMatchesGreedy(DependencySample sample) {
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, 1);
    assertEquals(parser.parse(sample.getTokens(), sample.getTags()),
        beamed.parse(sample.getTokens(), sample.getTags()));
  }

  /** Checks that beam search reproduces a training sentence. */
  @Test
  void testBeamedParserReproducesTrainingSentences() {
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, 4);
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
            new String[] {"det", "nsubj", "root"}),
        beamed.parse(new String[] {"the", "dog", "barks"},
            new String[] {"DT", "NN", "VBZ"}));
  }

  /** Checks that beam search is deterministic and single-rooted on unseen words. */
  @Test
  void testBeamedParseIsDeterministicAndSingleRooted() {
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, 8);
    final String[] tokens = {"unseen", "words", "everywhere"};
    final String[] tags = {"JJ", "NNS", "RB"};
    final DependencyGraph first = beamed.parse(tokens, tags);
    assertEquals(first, beamed.parse(tokens, tags));
    assertEquals(3, first.size());
    first.root();
  }

  /** Checks that refinement keeps perfect scores on the training corpus. */
  @Test
  void testRefinementKeepsToyPerformance() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings = settings(60, 0.05);
    final FeedforwardDependencyModel local = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    final FeedforwardDependencyTrainer.Settings refineSettings = settings(2, 0.01);
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        local, ObjectStreamUtils.createObjectStream(corpus()), refineSettings, 2);

    final DependencyEvaluator evaluator =
        new DependencyEvaluator(new FeedforwardDependencyParser(refined, 2));
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
  }

  /** Checks that refinement fails loudly on a relation label the model never saw. */
  @Test
  void testRefineRejectsAnUnknownRelation() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings = settings(1, 0.01);
    final List<DependencySample> unseenRelation = List.of(
        sample(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"det", "dislocated", "root"}));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(model,
            ObjectStreamUtils.createObjectStream(unseenRelation), settings, 2));
    assertEquals("unknown transition in the refinement samples: LEFT_ARC:dislocated",
        e.getMessage());
  }

  /** Checks that refinement returns a distinct model and leaves the input scores unchanged. */
  @Test
  void testRefineReturnsANewModelAndLeavesTheOriginalUntouched() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings = settings(60, 0.05);
    final FeedforwardDependencyModel local = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    final String[] tokens = {"the", "dog", "barks"};
    final String[] tags = {"DT", "NN", "VBZ"};
    final int[] features = local.featureIds(
        FeedforwardContext.extract(new ArcStandardState(tokens.length), tokens, tags));
    final double[] before = local.score(features);

    final FeedforwardDependencyTrainer.Settings refineSettings = settings(2, 0.01);
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        local, ObjectStreamUtils.createObjectStream(corpus()), refineSettings, 2);

    assertNotSame(local, refined);
    assertArrayEquals(before, local.score(features));
    assertFalse(Arrays.equals(before, refined.score(features)));
  }

  /**
   * Checks the final-sigma rule applied after code-point case mapping.
   *
   * @param input The capitalized word.
   * @param expected The normalized word.
   */
  @ParameterizedTest(name = "normalize({0}) is {1}")
  @CsvSource({
      // ODOS, road, all caps: the trailing sigma position lowers to U+03C2
      "\u039F\u0394\u039F\u03A3, \u03BF\u03B4\u03BF\u03C2",
      // SOFIA: the word-initial sigma is not final and lowers to the medial U+03C3
      "\u03A3\u039F\u03A6\u0399\u0391, \u03C3\u03BF\u03C6\u03B9\u03B1",
      // a lone capital sigma has no preceding letter, so the rule does not fire
      "\u03A3, \u03C3",
      // case-ignorable combining marks and apostrophes do not change the cased-letter test
      "\u039F\u0301\u03A3, \u03BF\u0301\u03C2",
      "\u039F\u03A3\u0301\u0391, \u03BF\u03C3\u0301\u03B1",
      "\u039F\u2019\u03A3, \u03BF\u2019\u03C2",
      "\u039F\u03A3\u2019\u0391, \u03BF\u03C3\u2019\u03B1"
  })
  void testNormalizeAppliesTheFinalSigmaRule(String input, String expected) {
    assertEquals(expected, FeedforwardDependencyModel.normalize(input));
  }

  /** Checks that an uncased predecessor does not trigger the final-sigma rule. */
  @Test
  void testFinalSigmaRequiresCasedLetterContext() {
    assertEquals("\u4E2D\u03C3", FeedforwardDependencyModel.normalize("\u4E2D\u03A3"));
  }

  /**
   * Checks that normalization reuses a string when no case mapping is needed.
   */
  @Test
  void testNormalizeReturnsTheSameInstanceForLowercaseWords() {
    final String plain = "barks";
    assertSame(plain, FeedforwardDependencyModel.normalize(plain));
    // lowercase Greek with its native final sigma is already normalized
    final String greek = "\u03BF\u03B4\u03BF\u03C2";
    assertSame(greek, FeedforwardDependencyModel.normalize(greek));
  }

  /**
   * Checks that non-projective samples are skipped before transition validation.
   */
  @Test
  void testNonProjectiveSampleWithUnknownRelationIsSkippedNotFatal() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings = settings(1, 0.01);
    // heads {2, 3, -1, 2}: the arcs from 2 to 0 and from 3 to 1 cross, so the graph
    // is non-projective, and "dislocated" is a relation the model was never trained on
    final List<DependencySample> mixed = List.of(
        sample(new String[] {"a", "b", "c", "d"}, new String[] {"DT", "NN", "VBZ", "NN"},
            new int[] {2, 3, -1, 2}, new String[] {"det", "dislocated", "root", "obj"}),
        sample(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"}));

    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        model, ObjectStreamUtils.createObjectStream(mixed), settings, 2);
    assertNotSame(model, refined);
  }

  /**
   * Checks that serialized vocabulary entries follow their numeric ids.
   */
  @Test
  void testSerializedVocabulariesAreWrittenInAscendingIdOrder() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    try (DataInputStream data = new DataInputStream(
        new ByteArrayInputStream(out.toByteArray()))) {
      data.readUTF();
      for (int vocab = 0; vocab < VOCABULARY_COUNT; vocab++) {
        final int size = data.readInt();
        int previous = Integer.MIN_VALUE;
        for (int entry = 0; entry < size; entry++) {
          data.readUTF();
          final int id = data.readInt();
          assertTrue(id > previous,
              "vocabulary " + vocab + " must be written in ascending id order");
          previous = id;
        }
      }
    }
  }

  /**
   * Compares cached and direct scoring, including repeated cache reads.
   */
  @Test
  void testScoringCacheMatchesTheDirectPath() {
    final FeedforwardDependencyModel uncached = model.copy();
    final String[] tokens = {"the", "dog", "barks"};
    final String[] tags = {"DT", "NN", "VBZ"};
    final int[] features = model.featureIds(
        FeedforwardContext.extract(new ArcStandardState(tokens.length), tokens, tags));

    for (int round = 0; round < 3; round++) {
      final double[] cached = model.score(features);
      final double[] direct = uncached.score(features);
      assertArrayEquals(direct, cached);
    }
  }

  /** Checks per-code-point lowering and reserved-symbol pass-through. */
  @Test
  void testNormalizeUsesTheUnicodeDataCaseMapping() {
    // StringUtil maps per code point via UnicodeData, so no character expands; the JDK's
    // String.toLowerCase would render this word as "i" + COMBINING DOT ABOVE instead.
    assertEquals(StringUtil.toLowerCase("\u0130STANBUL"),
        FeedforwardDependencyModel.normalize("\u0130STANBUL"));
    assertEquals("istanbul", FeedforwardDependencyModel.normalize("\u0130STANBUL"));
    assertEquals("*hello", FeedforwardDependencyModel.normalize("*HELLO"));
    // reserved symbols still pass through untouched
    assertEquals(FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.normalize(FeedforwardDependencyModel.UNKNOWN));
    assertNull(FeedforwardDependencyModel.normalize(null));
  }

  /** Checks the null and beam-size guards of refinement. */
  @Test
  void testRefineValidation() {
    final FeedforwardDependencyTrainer.Settings settings =
        FeedforwardDependencyTrainer.Settings.defaults();
    assertThrows(IllegalArgumentException.class, () -> FeedforwardDependencyTrainer
        .refine(null, ObjectStreamUtils.createObjectStream(corpus()), settings, 4));
    assertThrows(IllegalArgumentException.class, () -> FeedforwardDependencyTrainer
        .refine(model, ObjectStreamUtils.createObjectStream(corpus()), settings, 1));
  }

  /** Checks the parser constructor guards. */
  @Test
  void testBeamSizeValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyParser(model, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyParser(null, 4));
  }

  /** Checks that a serialized and reloaded model parses like the original. */
  @Test
  void testModelRoundTripThroughSerialization() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    final FeedforwardDependencyModel reloaded =
        FeedforwardDependencyModel.load(new ByteArrayInputStream(out.toByteArray()));
    final DependencyGraph parsed = new FeedforwardDependencyParser(reloaded)
        .parse(new String[] {"she", "eats", "fish"}, new String[] {"PRP", "VBZ", "NN"});
    assertEquals(DependencyGraph.of(new int[] {1, -1, 1},
        new String[] {"nsubj", "root", "obj"}), parsed);
  }

  /** Checks that arbitrary bytes are rejected with an IOException. */
  @Test
  void testCorruptModelIsRejected() {
    assertThrows(IOException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream("not a model".getBytes(StandardCharsets.UTF_8))));
  }

  /** Checks that a negative vocabulary size is rejected with an InvalidFormatException. */
  @Test
  void testNegativeVocabularyCountFailsWithInvalidFormat() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(out)) {
      data.writeUTF(MAGIC);
      data.writeInt(-1);
    }
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /** Checks that a hidden matrix of the wrong width is rejected. */
  @Test
  void testInconsistentModelDimensionsFailDuringLoading() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(out)) {
      writeMinimalModel(data, FIRST_TAG_ID, 1, 0.0f);
    }
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /** Checks that overlapping vocabulary ids are rejected. */
  @Test
  void testDuplicateVocabularyIdsFailDuringLoading() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(out)) {
      writeMinimalModel(data, 0, FeedforwardContext.FEATURE_COUNT, 0.0f);
    }
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /** Checks that a NaN weight is rejected. */
  @Test
  void testNonFiniteWeightFailsDuringLoading() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(out)) {
      writeMinimalModel(data, FIRST_TAG_ID, FeedforwardContext.FEATURE_COUNT, Float.NaN);
    }
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /** Checks that bytes after the model are rejected. */
  @Test
  void testTrailingModelDataFailsDuringLoading() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    out.write(1);
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /** Checks that an inventory without SHIFT is rejected. */
  @Test
  void testModelWithoutShiftFailsDuringLoading() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(out)) {
      writeModelWithTransitions(data, Transition.rightArc("root").encode());
    }
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /** Checks that an inventory without RIGHT_ARC is rejected. */
  @Test
  void testModelWithoutRootArcFailsDuringLoading() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DataOutputStream data = new DataOutputStream(out)) {
      writeModelWithTransitions(data, Transition.SHIFT.encode());
    }
    assertThrows(InvalidFormatException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream(out.toByteArray())));
  }

  /**
   * Writes a test vocabulary with consecutive embedding indices.
   *
   * @param data The output to write to.
   * @param first The embedding row of the first symbol.
   * @param symbols The symbols, in row order.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeVocabulary(DataOutputStream data, int first, String... symbols)
      throws IOException {
    data.writeInt(symbols.length);
    for (int i = 0; i < symbols.length; i++) {
      data.writeUTF(symbols[i]);
      data.writeInt(first + i);
    }
  }

  /**
   * Writes the smallest complete model, with selected fields exposed for corruption.
   *
   * @param data The output to write to.
   * @param firstTagId The embedding row of the first tag symbol.
   * @param hiddenColumns The hidden matrix width.
   * @param outputBias The single output bias value.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeMinimalModel(DataOutputStream data, int firstTagId,
      int hiddenColumns, float outputBias) throws IOException {
    data.writeUTF(MAGIC);
    writeVocabulary(data, 0,
        FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT,
        FeedforwardDependencyModel.ROOT_SYMBOL);
    writeVocabulary(data, firstTagId,
        FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT,
        FeedforwardDependencyModel.ROOT_SYMBOL);
    writeVocabulary(data, FIRST_LABEL_ID,
        FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT);
    data.writeInt(1);
    data.writeUTF(Transition.SHIFT.encode());
    data.writeInt(1);
    writeMatrix(data, MINIMAL_ROWS, 1);
    writeMatrix(data, 1, hiddenColumns);
    writeVector(data, 1);
    writeMatrix(data, 1, 1);
    data.writeInt(1);
    data.writeFloat(outputBias);
  }

  /**
   * Writes a structurally complete model with the selected transition inventory.
   *
   * @param data The output to write to.
   * @param transitions The encoded transitions, in output order.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeModelWithTransitions(DataOutputStream data,
      String... transitions) throws IOException {
    data.writeUTF(MAGIC);
    writeVocabulary(data, 0,
        FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT,
        FeedforwardDependencyModel.ROOT_SYMBOL);
    writeVocabulary(data, FIRST_TAG_ID,
        FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT,
        FeedforwardDependencyModel.ROOT_SYMBOL);
    writeVocabulary(data, FIRST_LABEL_ID,
        FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.ABSENT);
    data.writeInt(transitions.length);
    for (final String transition : transitions) {
      data.writeUTF(transition);
    }
    data.writeInt(1);
    writeMatrix(data, MINIMAL_ROWS, 1);
    writeMatrix(data, 1, FeedforwardContext.FEATURE_COUNT);
    writeVector(data, 1);
    writeMatrix(data, transitions.length, 1);
    writeVector(data, transitions.length);
  }

  /**
   * Writes a zero-filled matrix in the model format.
   *
   * @param data The output to write to.
   * @param rows The row count.
   * @param columns The column count.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeMatrix(DataOutputStream data, int rows, int columns)
      throws IOException {
    data.writeInt(rows);
    data.writeInt(columns);
    for (int i = 0; i < rows * columns; i++) {
      data.writeFloat(0.0f);
    }
  }

  /**
   * Writes a zero-filled vector in the model format.
   *
   * @param data The output to write to.
   * @param length The vector length.
   * @throws IOException Thrown if writing fails.
   */
  private static void writeVector(DataOutputStream data, int length) throws IOException {
    data.writeInt(length);
    for (int i = 0; i < length; i++) {
      data.writeFloat(0.0f);
    }
  }

  /**
   * Checks that each out-of-range hyperparameter is rejected.
   *
   * @param embeddingSize The embedding width.
   * @param hiddenSize The hidden width.
   * @param learningRate The step size.
   * @param l2 The L2 penalty.
   * @param dropout The dropout probability.
   */
  @ParameterizedTest(name = "embedding {0}, hidden {1}, rate {2}, l2 {3}, dropout {4}")
  @CsvSource({
      "0, 32, 0.05, 0.0, 0.0",
      "16, 32, -1.0, 0.0, 0.0",
      "16, 32, 0.05, 0.0, 1.0",
      "16, 32, NaN, 0.0, 0.0",
      "16, 32, 0.05, Infinity, 0.0",
      "4097, 32, 0.05, 0.0, 0.0",
      "16, 65537, 0.05, 0.0, 0.0",
      "1000, 3000, 0.05, 0.0, 0.0"
  })
  void testSettingsValidation(int embeddingSize, int hiddenSize, double learningRate,
      double l2, double dropout) {
    assertThrows(IllegalArgumentException.class, () -> new FeedforwardDependencyTrainer
        .Settings(embeddingSize, hiddenSize, 10, BATCH_SIZE, learningRate, l2, dropout,
            WORD_CUTOFF, SEED));
  }

  /** Checks that a diverging run fails with an IllegalStateException. */
  @Test
  void testTrainingRejectsNonFiniteWeights() {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(
            4, 4, 2, 1, Double.MAX_VALUE, 0.0, 0.0, WORD_CUTOFF, SEED);
    assertThrows(IllegalStateException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(corpus()), settings));
  }

  /** Checks the feature length guards of the model. */
  @Test
  void testModelRejectsInvalidFeatureArrays() {
    assertThrows(IllegalArgumentException.class, () -> model.featureIds(new String[0]));
    assertThrows(IllegalArgumentException.class, () -> model.score(new int[0]));
  }

  /** Checks that pretrained vectors seed rows and malformed vectors are rejected. */
  @Test
  void testPretrainedSeedingAppliesAndValidates() throws IOException {
    // near-zero learning keeps the seeded row observable after one epoch
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(4, 8, 1, BATCH_SIZE, 1e-9, 0.0, 0.0,
            WORD_CUTOFF, SEED);
    final float[] vector = {0.25f, -0.5f, 0.75f, -1.0f};
    final FeedforwardDependencyModel seeded = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings,
        word -> "dog".equals(word) ? vector.clone() : null);
    final int row = seeded.wordIds().get("dog");
    for (int d = 0; d < vector.length; d++) {
      assertEquals(vector[d], seeded.embeddings()[row][d], 1e-4);
    }
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(corpus()), settings,
            word -> new float[] {1.0f}));
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(corpus()), settings,
            word -> new float[] {Float.NaN, 0.0f, 0.0f, 0.0f}));
  }

  /** Checks the null and length guards of the parser and trainer. */
  @Test
  void testArgumentValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyParser(null));
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(null,
            FeedforwardDependencyTrainer.Settings.defaults()));
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(corpus()), null));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(null, new String[] {"DT"}));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"the"}, new String[] {"DT", "NN"}));
  }

  /**
   * Verifies that training data using the model's reserved symbols as ordinary tags,
   * labels, or tokens does not displace the reserved rows: the model reloads and the
   * colliding symbols share the reserved rows the way unknown symbols do.
   *
   * @throws IOException Thrown if serialization or loading fails.
   */
  @Test
  void testReservedSymbolsInTrainingDataSurviveReload() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings = settings(1, 0.01);
    final List<DependencySample> colliding = List.of(
        sample(new String[] {"*ROOT*", "*UNK*", "barks"}, new String[] {"*UNK*", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"*NULL*", "nsubj", "root"}),
        sample(new String[] {"dogs", "bark"}, new String[] {"*NULL*", "*ROOT*"},
            new int[] {1, -1}, new String[] {"*UNK*", "root"}));
    final FeedforwardDependencyModel trained = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(colliding), settings);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    trained.serialize(out);
    final FeedforwardDependencyModel reloaded =
        FeedforwardDependencyModel.load(new ByteArrayInputStream(out.toByteArray()));
    final String[] tokens = {"*ROOT*", "*UNK*", "barks"};
    final String[] tags = {"*UNK*", "NN", "VBZ"};
    assertEquals(new FeedforwardDependencyParser(trained).parse(tokens, tags),
        new FeedforwardDependencyParser(reloaded).parse(tokens, tags));
  }
  /**
   * Checks that training on samples without an arc-standard derivation fails with the
   * documented message instead of producing an empty model.
   */
  @Test
  void testTrainingWithOnlyNonProjectiveSamplesFailsLoud() {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 1, 32, 0.01, 0.0, 0.0, 1, 17L);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(nonProjectiveCorpus()), settings));
    assertEquals("no trainable examples in the samples", e.getMessage());
  }

  /**
   * Checks that refining on samples without an arc-standard derivation fails with the
   * documented message instead of returning the model unchanged.
   */
  @Test
  void testRefiningWithOnlyNonProjectiveSamplesFailsLoud() {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 1, 32, 0.01, 0.0, 0.0, 1, 17L);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(model,
            ObjectStreamUtils.createObjectStream(nonProjectiveCorpus()), settings, 2));
    assertEquals("no trainable samples for refinement", e.getMessage());
  }

  /**
   * Builds samples whose arcs cross, so that none has an arc-standard derivation.
   *
   * @return Two non-projective samples. Never {@code null}.
   */
  private static List<DependencySample> nonProjectiveCorpus() {
    return List.of(
        sample(new String[] {"a", "b", "c", "d"}, new String[] {"DT", "NN", "VBZ", "NN"},
            new int[] {2, 3, -1, 2}, new String[] {"det", "dislocated", "root", "obj"}),
        sample(new String[] {"the", "dog", "barks", "loud"}, new String[] {"DT", "NN", "VBZ", "RB"},
            new int[] {2, 3, -1, 2}, new String[] {"det", "nsubj", "root", "advmod"}));
  }
}
