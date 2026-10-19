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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.BeamSearch;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.ResourceLimits;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.BaseModel;

import static opennlp.tools.depparse.DependencyTestSamples.CACHE_BYTES;
import static opennlp.tools.depparse.DependencyTestSamples.LANGUAGE;
import static opennlp.tools.depparse.DependencyTestSamples.MINIMAL_TRANSITIONS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.UNSEEN_RELATION;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.feedforwardParameters;
import static opennlp.tools.depparse.DependencyTestSamples.localFeedforward;
import static opennlp.tools.depparse.DependencyTestSamples.networkBytes;
import static opennlp.tools.depparse.DependencyTestSamples.nonProjectiveSample;
import static opennlp.tools.depparse.DependencyTestSamples.nonProjectiveSampleWithUnseenRelation;
import static opennlp.tools.depparse.DependencyTestSamples.sample;
import static opennlp.tools.depparse.DependencyTestSamples.score;
import static opennlp.tools.depparse.DependencyTestSamples.trainFeedforward;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests feedforward training, parsing, refinement, the model package, and the network
 * format.
 */
public class FeedforwardDependencyParserTest {

  /** The beam size of the beamed parsers under test. */
  private static final int BEAM_SIZE = 4;

  /** The refinement parameters: two passes at a small step size. */
  private static final TrainingParameters REFINE_PARAMETERS = feedforwardParameters(2, 0.01);

  /** One-pass parameters for tests that only inspect the result shape. */
  private static final TrainingParameters SINGLE_EPOCH_PARAMETERS = feedforwardParameters(1, 0.01);

  /** The reserved rows of the hand-written networks, one per reserved symbol. */
  private static final int MINIMAL_ROWS = FeedforwardNetwork.RESERVED_ROWS;

  /** The first tag row of the hand-written networks, after the reserved word rows. */
  private static final int FIRST_TAG_ROW = FeedforwardNetwork.RESERVED_WORD_SYMBOLS.length;

  /** The first label row of the hand-written networks, after the reserved tag rows. */
  private static final int FIRST_LABEL_ROW =
      FIRST_TAG_ROW + FeedforwardNetwork.RESERVED_TAG_SYMBOLS.length;

  /** An embedding width that every hand-written network uses. */
  private static final int UNIT_WIDTH = 1;

  /** The hidden matrix rows that push the smallest network over the cell limit. */
  private static final int OVERSIZED_HIDDEN_ROWS = Math.min(ResourceLimits.MAX_ENTRIES,
      ResourceLimits.MAX_MATRIX_CELLS / FeedforwardContext.FEATURE_COUNT + 1);

  /** The model package entry that holds the manifest. */
  private static final String MANIFEST_ENTRY_NAME = "manifest.properties";

  /** A symbol of 70000 bytes, longer than an unsigned 16-bit length can hold. */
  private static final String LONG_SYMBOL = "x".repeat(70_000);

  private static FeedforwardDependencyModel model;
  private static FeedforwardDependencyParser parser;

  /**
   * @return The distinct training sentences, one per parameterized invocation. Never
   *         {@code null}.
   */
  private static List<DependencySample> trainingSamples() {
    return DependencyTestSamples.sentences();
  }

  /**
   * Trains the shared model once for all tests.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    model = DependencyTestSamples.trainFeedforward();
    parser = new FeedforwardDependencyParser(model);
  }

  /** Checks that the greedy parser reproduces a training sentence. */
  @Test
  void testMemorizesTrainingSentences() {
    assertEquals(THE_DOG_BARKS_TREE, parser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  /** Checks UAS and LAS of 1.0 on the training corpus. */
  @Test
  void testEvaluatorScoresPerfectlyOnTrainingData() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
  }

  /** Checks that unseen words still parse to a tree with one root inside the sentence. */
  @Test
  void testUnknownWordsStillYieldASingleRootedTree() {
    final DependencyTree parsed = parser.parse(new String[] {"unseen", "words"},
        new String[] {"JJ", "NNS"});
    assertEquals(2, parsed.size());
    assertTrue(parsed.root() >= 0 && parsed.root() < parsed.size(), parsed.toString());
    assertEquals(DependencyArc.ROOT_HEAD, parsed.headOf(parsed.root()));
  }

  /** Checks that the transition inventory is exactly the sorted set the oracle produced. */
  @Test
  void testTransitionInventoryMatchesTheOracle() {
    final TreeSet<String> expected = new TreeSet<>();
    for (final DependencySample sample : DependencyTestSamples.sentences()) {
      for (final Transition transition : ArcStandardOracle.transitions(sample.getTree())) {
        expected.add(transition.encode());
      }
    }
    assertEquals(new ArrayList<>(expected), List.of(model.network().transitions()));
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
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, BEAM_SIZE);
    assertEquals(THE_DOG_BARKS_TREE, beamed.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  /** Checks that beam search is deterministic and single-rooted on unseen words. */
  @Test
  void testBeamedParseIsDeterministicAndSingleRooted() {
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, 8);
    final String[] tokens = {"unseen", "words", "everywhere"};
    final String[] tags = {"JJ", "NNS", "RB"};
    final DependencyTree first = beamed.parse(tokens, tags);
    assertEquals(first, beamed.parse(tokens, tags));
    assertEquals(3, first.size());
    assertEquals(DependencyArc.ROOT_HEAD, first.headOf(first.root()));
  }

  /** Checks that a cached parser returns the parses of an uncached one. */
  @Test
  void testCachedParserMatchesTheUncachedParser() {
    final FeedforwardDependencyParser cached =
        new FeedforwardDependencyParser(model, BEAM_SIZE, CACHE_BYTES);
    final FeedforwardDependencyParser uncached =
        new FeedforwardDependencyParser(model, BEAM_SIZE);
    for (final DependencySample sample : DependencyTestSamples.sentences()) {
      assertEquals(uncached.parse(sample.getTokens(), sample.getTags()),
          cached.parse(sample.getTokens(), sample.getTags()));
    }
    assertNotNull(cached.cache());
    assertTrue(cached.cache().entries() > 0);
    assertNull(uncached.cache());
  }

  /** Checks that refinement keeps perfect scores on the training corpus. */
  @Test
  void testRefinementKeepsToyPerformance() throws IOException {
    final FeedforwardDependencyModel local = localFeedforward();
    final TrainingParameters parameters = feedforwardParameters(2, 0.01);
    parameters.put(BeamSearch.BEAM_SIZE_PARAMETER, 2);
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        local, ObjectStreamUtils.createObjectStream(corpus()), parameters);

    final DependencyEvaluator evaluator =
        new DependencyEvaluator(new FeedforwardDependencyParser(refined, 2));
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
  }

  /** Checks that refinement fails loudly on a relation label the model never saw. */
  @Test
  void testRefineRejectsAnUnknownRelation() {
    final List<DependencySample> unseenRelation = List.of(
        sample(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS,
            new int[] {1, 2, -1}, new String[] {"det", UNSEEN_RELATION, "root"}));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(model,
            ObjectStreamUtils.createObjectStream(unseenRelation), REFINE_PARAMETERS));
    assertEquals("unknown transition in the refinement samples: LEFT_ARC:" + UNSEEN_RELATION,
        e.getMessage());
  }

  /** Checks that refinement returns a distinct model and leaves the input scores unchanged. */
  @Test
  void testRefineReturnsANewModelAndLeavesTheOriginalUntouched() throws IOException {
    final FeedforwardDependencyModel local = localFeedforward();
    final int[] features = initialFeatures(local.network(), THE_DOG_BARKS_TOKENS,
        THE_DOG_BARKS_TAGS);
    final double[] before = score(local.network(), features, null);

    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        local, ObjectStreamUtils.createObjectStream(corpus()), REFINE_PARAMETERS);

    assertNotSame(local, refined);
    assertArrayEquals(before, score(local.network(), features, null));
    assertFalse(Arrays.equals(before, score(refined.network(), features, null)));
    assertEquals(LANGUAGE, refined.getLanguage());
  }

  /** Checks that a model that was not refined records no beam size and parses greedily. */
  @Test
  void testUnrefinedModelParsesGreedilyByDefault() {
    assertNull(model.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER));
    assertEquals(1, model.getBeamSize());
    assertEquals(1, parser.beamSize());
  }

  /**
   * Checks that a parser created without a beam size uses the beam size the model was
   * refined with, also after the model is serialized and loaded again.
   */
  @Test
  void testRefinedModelParsesWithItsBeamSizeByDefault() throws IOException {
    final TrainingParameters parameters = feedforwardParameters(1, 0.01);
    parameters.put(BeamSearch.BEAM_SIZE_PARAMETER, 3);
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        localFeedforward(), ObjectStreamUtils.createObjectStream(corpus()), parameters);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    refined.serialize(out);
    final FeedforwardDependencyModel reloaded =
        new FeedforwardDependencyModel(new ByteArrayInputStream(out.toByteArray()));

    assertEquals(3, refined.getBeamSize());
    assertEquals(3, reloaded.getBeamSize());
    assertEquals(3, new FeedforwardDependencyParser(reloaded).beamSize());
    assertEquals(BEAM_SIZE, new FeedforwardDependencyParser(reloaded, BEAM_SIZE).beamSize());
  }

  /**
   * Checks that a model whose manifest records a beam size that is not a positive
   * integer is rejected when it is loaded.
   *
   * @param beamSize The recorded beam size.
   * @throws IOException Thrown if writing the model package fails.
   */
  @ParameterizedTest(name = "recorded beam size {0}")
  @ValueSource(strings = {"abc", "0", "-2", "2147483648"})
  void testInvalidRecordedBeamSizeIsRejected(String beamSize) throws IOException {
    final byte[] tampered = withChangedEntry(MANIFEST_ENTRY_NAME,
        content -> (new String(content, StandardCharsets.UTF_8)
            + BeamSearch.BEAM_SIZE_PARAMETER + "=" + beamSize + "\n")
            .getBytes(StandardCharsets.UTF_8));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyModel(new ByteArrayInputStream(tampered)));
    final InvalidFormatException cause = assertInstanceOf(InvalidFormatException.class,
        e.getCause());
    assertEquals("The manifest " + BeamSearch.BEAM_SIZE_PARAMETER
        + " must be a positive integer: " + beamSize, cause.getMessage());
  }

  /**
   * Serializes the shared model and changes the content of one package entry.
   *
   * @param entryName The name of the entry to change.
   * @param change Maps the entry content to its new content.
   * @return The changed model package. Never {@code null}.
   * @throws IOException Thrown if writing the model package fails.
   */
  private static byte[] withChangedEntry(String entryName, UnaryOperator<byte[]> change)
      throws IOException {
    final ByteArrayOutputStream valid = new ByteArrayOutputStream();
    model.serialize(valid);
    final ByteArrayOutputStream changed = new ByteArrayOutputStream();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(valid.toByteArray()));
         ZipOutputStream out = new ZipOutputStream(changed)) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        final byte[] content = in.readAllBytes();
        out.putNextEntry(new ZipEntry(entry.getName()));
        out.write(entryName.equals(entry.getName()) ? change.apply(content) : content);
        out.closeEntry();
      }
    }
    return changed.toByteArray();
  }

  /**
   * Checks that non-projective samples are skipped before transition validation and
   * that the refined model still parses the projective sample.
   */
  @Test
  void testNonProjectiveSampleWithUnknownRelationIsSkippedNotFatal() throws IOException {
    final List<DependencySample> mixed = List.of(nonProjectiveSampleWithUnseenRelation(),
        new DependencySample(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS, THE_DOG_BARKS_TREE));
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        model, ObjectStreamUtils.createObjectStream(mixed), SINGLE_EPOCH_PARAMETERS);
    assertEquals(THE_DOG_BARKS_TREE, new FeedforwardDependencyParser(refined, BEAM_SIZE)
        .parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  /** Checks that the inventory of a model trained on mixed data omits non-projective labels. */
  @Test
  void testTrainingOmitsNonProjectiveLabels() throws IOException {
    final List<DependencySample> mixed = new ArrayList<>(corpus());
    mixed.add(nonProjectiveSampleWithUnseenRelation());
    final FeedforwardDependencyModel trained = trainFeedforward(mixed, SINGLE_EPOCH_PARAMETERS);
    for (final String transition : trained.network().transitions()) {
      assertFalse(transition.contains(UNSEEN_RELATION), transition);
    }
  }

  /**
   * Checks that training on samples without an arc-standard derivation fails with the
   * documented message instead of producing an empty model.
   */
  @Test
  void testTrainingWithOnlyNonProjectiveSamplesFailsLoud() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> trainFeedforward(nonProjectiveCorpus(), SINGLE_EPOCH_PARAMETERS));
    assertEquals("no trainable examples in the samples", e.getMessage());
  }

  /**
   * Checks that refining on samples without an arc-standard derivation fails with the
   * documented message instead of returning the model unchanged.
   */
  @Test
  void testRefiningWithOnlyNonProjectiveSamplesFailsLoud() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(model,
            ObjectStreamUtils.createObjectStream(nonProjectiveCorpus()),
            SINGLE_EPOCH_PARAMETERS));
    assertEquals("no trainable samples for refinement", e.getMessage());
  }

  /**
   * @return Two non-projective samples, so that none has an arc-standard derivation.
   *         Never {@code null}.
   */
  private static List<DependencySample> nonProjectiveCorpus() {
    return List.of(nonProjectiveSampleWithUnseenRelation(), nonProjectiveSample());
  }

  /** Checks per-code-point lowering and reserved-symbol pass-through. */
  @Test
  void testNormalizeUsesTheUnicodeDataCaseMapping() {
    // StringUtil maps per code point via UnicodeData, so no character expands; the JDK's
    // String.toLowerCase would render this word as "i" + COMBINING DOT ABOVE instead.
    assertEquals(StringUtil.toLowerCase("İSTANBUL"),
        FeedforwardNetwork.normalize("İSTANBUL"));
    assertEquals("istanbul", FeedforwardNetwork.normalize("İSTANBUL"));
    assertEquals("*hello", FeedforwardNetwork.normalize("*HELLO"));
    // supplementary code points lower too: DESERET CAPITAL LETTER LONG I
    assertEquals("𐐨", FeedforwardNetwork.normalize("𐐀"));
    assertEquals(FeedforwardNetwork.UNKNOWN, FeedforwardNetwork.normalize(FeedforwardNetwork.UNKNOWN));
    assertNull(FeedforwardNetwork.normalize(null));
    assertEquals("barks", FeedforwardNetwork.normalize("barks"));
  }

  /**
   * @return Words with a Greek capital sigma and their lowering under the Final_Sigma
   *         context of the Unicode SpecialCasing data. Never {@code null}.
   */
  private static Stream<Arguments> finalSigmaWords() {
    return Stream.of(
        // word-final after a cased letter
        Arguments.of("\u039F\u0394\u039F\u03A3", "\u03BF\u03B4\u03BF\u03C2"),
        // punctuation after the sigma is case-ignorable, so it stays final
        Arguments.of("\u039F\u0394\u039F\u03A3.", "\u03BF\u03B4\u03BF\u03C2."),
        // a combining mark after the sigma is case-ignorable as well
        Arguments.of("\u0391\u03A3\u0301", "\u03B1\u03C2\u0301"),
        // a cased letter follows: not final, even across an apostrophe
        Arguments.of("\u0391\u03A3'\u0391", "\u03B1\u03C3'\u03B1"),
        Arguments.of("\u03A3\u039F\u03A6\u0399\u0391", "\u03C3\u03BF\u03C6\u03B9\u03B1"),
        // no cased letter before: a lone sigma or one after a digit is not final
        Arguments.of("\u03A3", "\u03C3"),
        Arguments.of("1\u03A3", "1\u03C3"),
        // two sigmas: only the last is final
        Arguments.of("\u0391\u03A3\u03A3", "\u03B1\u03C3\u03C2"),
        // a supplementary cased letter before the sigma counts as cased:
        // DESERET CAPITAL LETTER BEE
        Arguments.of("\uD801\uDC12\u03A3", "\uD801\uDC3A\u03C2"));
  }

  /**
   * Checks the Final_Sigma context of {@link FeedforwardNetwork#normalize(String)}
   * against the root-locale lowering of the JDK, which applies the same rule.
   *
   * @param word The word to normalize.
   * @param expected The expected vocabulary key.
   */
  @ParameterizedTest
  @MethodSource("finalSigmaWords")
  void testNormalizeAppliesTheFinalSigmaContext(String word, String expected) {
    assertEquals(expected, word.toLowerCase(Locale.ROOT), "oracle");
    assertEquals(expected, FeedforwardNetwork.normalize(word));
  }

  /**
   * Checks that embedding rows follow the sorted symbol order after the reserved rows,
   * so the trained weights depend only on the samples and the seed.
   */
  @Test
  void testVocabularyRowsFollowSortedSymbolOrder() {
    final FeedforwardNetwork network = model.network();
    assertSortedAfterReserved(network.wordIds(), FeedforwardNetwork.RESERVED_WORD_SYMBOLS);
    assertSortedAfterReserved(network.tagIds(), FeedforwardNetwork.RESERVED_TAG_SYMBOLS);
    assertSortedAfterReserved(network.labelIds(), FeedforwardNetwork.RESERVED_LABEL_SYMBOLS);
    assertEquals(0, network.firstWordRow());
    assertEquals(network.wordIds().size(), network.firstTagRow());
    assertEquals(network.wordIds().size() + network.tagIds().size(), network.firstLabelRow());
  }

  /**
   * Asserts that the reserved symbols take the first rows of a vocabulary block in
   * order and the remaining symbols follow in ascending string order.
   *
   * @param ids The vocabulary.
   * @param reserved The reserved symbols.
   */
  private static void assertSortedAfterReserved(Map<String, Integer> ids, String[] reserved) {
    final String[] byRow = new String[ids.size()];
    final int first = ids.values().stream().mapToInt(Integer::intValue).min().orElseThrow();
    for (final Map.Entry<String, Integer> entry : ids.entrySet()) {
      byRow[entry.getValue() - first] = entry.getKey();
    }
    assertArrayEquals(reserved, Arrays.copyOf(byRow, reserved.length));
    final String[] rest = Arrays.copyOfRange(byRow, reserved.length, byRow.length);
    final String[] sorted = rest.clone();
    Arrays.sort(sorted);
    assertArrayEquals(sorted, rest);
  }

  /** Checks that the same samples and parameters give byte-identical networks. */
  @Test
  void testTrainingIsDeterministic() throws IOException {
    final byte[] first = networkBytes(trainFeedforward(corpus(), SINGLE_EPOCH_PARAMETERS).network());
    final byte[] second = networkBytes(trainFeedforward(corpus(), SINGLE_EPOCH_PARAMETERS).network());
    assertArrayEquals(first, second);
  }

  /**
   * Pins which reserved rows training touches: the tag and label unknown rows keep
   * their initialization, while the word unknown row is trained once words fall below
   * the cutoff.
   */
  @Test
  void testUnknownRowsAreTrainedOnlyForWordsBelowTheCutoff() throws IOException {
    final TrainingParameters slow = feedforwardParameters(1, 0.001);
    final TrainingParameters fast = feedforwardParameters(1, 0.1);
    final FeedforwardNetwork slowNetwork = trainFeedforward(corpus(), slow).network();
    final FeedforwardNetwork fastNetwork = trainFeedforward(corpus(), fast).network();
    assertArrayEquals(slowNetwork.embeddings()[slowNetwork.firstTagRow()],
        fastNetwork.embeddings()[fastNetwork.firstTagRow()]);
    assertArrayEquals(slowNetwork.embeddings()[slowNetwork.firstLabelRow()],
        fastNetwork.embeddings()[fastNetwork.firstLabelRow()]);
    // every word reaches the cutoff of one, so the word unknown row is untouched too
    assertArrayEquals(slowNetwork.embeddings()[slowNetwork.firstWordRow()],
        fastNetwork.embeddings()[fastNetwork.firstWordRow()]);

    slow.put(Parameters.CUTOFF_PARAM, Integer.MAX_VALUE);
    fast.put(Parameters.CUTOFF_PARAM, Integer.MAX_VALUE);
    final FeedforwardNetwork slowUnknown = trainFeedforward(corpus(), slow).network();
    final FeedforwardNetwork fastUnknown = trainFeedforward(corpus(), fast).network();
    assertEquals(FeedforwardNetwork.RESERVED_WORD_SYMBOLS.length, slowUnknown.wordIds().size());
    assertFalse(Arrays.equals(slowUnknown.embeddings()[slowUnknown.firstWordRow()],
        fastUnknown.embeddings()[fastUnknown.firstWordRow()]));
  }

  /** Checks that the manifest records the component, language, and training run. */
  @Test
  void testManifestRecordsTheTrainingRun() {
    assertEquals(LANGUAGE, model.getLanguage());
    assertEquals("120", model.getManifestProperty(BaseModel.TRAINING_ITERATIONS_PROPERTY));
    assertEquals("1", model.getManifestProperty(BaseModel.TRAINING_CUTOFF_PROPERTY));
  }

  /** Checks that serialized vocabulary entries follow their numeric ids. */
  @Test
  void testSerializedVocabulariesAreWrittenInAscendingIdOrder() throws IOException {
    try (DataInputStream data = new DataInputStream(
        new ByteArrayInputStream(networkBytes(model.network())))) {
      data.readFully(new byte[FeedforwardNetwork.Serializer.MAGIC.length]);
      assertEquals(FeedforwardNetwork.Serializer.FORMAT_VERSION, data.readInt());
      for (int vocabulary = 0; vocabulary < 3; vocabulary++) {
        final int size = data.readInt();
        int previous = Integer.MIN_VALUE;
        for (int entry = 0; entry < size; entry++) {
          data.readFully(new byte[data.readInt()]);
          final int id = data.readInt();
          assertTrue(id > previous,
              "vocabulary " + vocabulary + " must be written in ascending id order");
          previous = id;
        }
      }
    }
  }

  /** Checks that a serialized and reloaded model parses like the original. */
  @Test
  void testModelRoundTripThroughSerialization() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    final FeedforwardDependencyModel reloaded =
        new FeedforwardDependencyModel(new ByteArrayInputStream(out.toByteArray()));
    assertEquals(LANGUAGE, reloaded.getLanguage());
    assertEquals(SHE_EATS_FISH_TREE, new FeedforwardDependencyParser(reloaded)
        .parse(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS));
    assertArrayEquals(networkBytes(model.network()), networkBytes(reloaded.network()));
  }

  /** Checks that a model file reloads and parses every corpus sentence identically. */
  @Test
  void testModelFileRoundTripParsesIdentically(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("depparse-ff.bin");
    model.serialize(file);
    final FeedforwardDependencyParser reloaded =
        new FeedforwardDependencyParser(new FeedforwardDependencyModel(file));
    for (final DependencySample sample : corpus()) {
      assertEquals(parser.parse(sample.getTokens(), sample.getTags()),
          reloaded.parse(sample.getTokens(), sample.getTags()),
          Arrays.toString(sample.getTokens()));
    }
    assertEquals(SHE_EATS_FISH_TREE, reloaded.parse(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS));
  }

  /** Checks that a model file reloads through the {@link java.io.File} constructor. */
  @Test
  void testModelFileConstructorParsesIdentically(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("depparse-ff-file.bin");
    model.serialize(file);
    final FeedforwardDependencyModel reloaded = new FeedforwardDependencyModel(file.toFile());
    assertEquals(LANGUAGE, reloaded.getLanguage());
    assertArrayEquals(networkBytes(model.network()), networkBytes(reloaded.network()));
    final FeedforwardDependencyParser reloadedParser = new FeedforwardDependencyParser(reloaded);
    for (final DependencySample sample : corpus()) {
      assertEquals(parser.parse(sample.getTokens(), sample.getTags()),
          reloadedParser.parse(sample.getTokens(), sample.getTags()),
          Arrays.toString(sample.getTokens()));
    }
  }

  /** Checks that a model package with a corrupt network entry is rejected. */
  @Test
  void testCorruptNetworkEntryIsRejected() throws IOException {
    final byte[] corrupt = withChangedEntry(FeedforwardDependencyModel.NETWORK_ENTRY_NAME,
        content -> "not a network".getBytes(StandardCharsets.UTF_8));
    assertThrows(InvalidFormatException.class, () -> new FeedforwardDependencyModel(
        new ByteArrayInputStream(corrupt)));
  }

  /**
   * Checks that a model package of another component is rejected the way
   * {@link BaseModel} rejects every foreign package.
   */
  @Test
  void testForeignModelPackageIsRejected() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    DependencyTestSamples.train(corpus()).serialize(out);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyModel(new ByteArrayInputStream(out.toByteArray())));
    assertInstanceOf(InvalidFormatException.class, e.getCause());
  }

  /**
   * @return Named invalid network payloads. Never {@code null}.
   * @throws IOException Thrown if building a payload fails.
   */
  private static Stream<Arguments> invalidNetworks() throws IOException {
    final NetworkBytes defaults = new NetworkBytes();
    final String limit = " exceeds safe limit of " + ResourceLimits.MAX_ENTRIES;
    return Stream.of(
        Arguments.of("wrong magic", "not a feedforward dependency network",
            new NetworkBytes().magic("ONLP".getBytes(StandardCharsets.US_ASCII)).bytes()),
        Arguments.of("unsupported version", "unsupported feedforward network version: 2",
            new NetworkBytes().version(2).bytes()),
        Arguments.of("negative vocabulary size", "word vocabulary size -1" + limit,
            new NetworkBytes().wordCount(-1).bytes()),
        Arguments.of("vocabulary size above the limit",
            "word vocabulary size " + (ResourceLimits.MAX_ENTRIES + 1) + limit,
            new NetworkBytes().wordCount(ResourceLimits.MAX_ENTRIES + 1).bytes()),
        Arguments.of("symbol length above the limit",
            "word vocabulary symbol length " + (ResourceLimits.MAX_ENTRIES + 1) + limit,
            new NetworkBytes().firstWordLength(ResourceLimits.MAX_ENTRIES + 1).bytes()),
        Arguments.of("truncated body", "feedforward network ends early",
            Arrays.copyOf(defaults.bytes(), defaults.bytes().length / 2)),
        Arguments.of("duplicate symbol",
            "duplicate symbol in tag vocabulary: " + FeedforwardNetwork.UNKNOWN,
            new NetworkBytes().tagSymbols(FeedforwardNetwork.UNKNOWN, FeedforwardNetwork.UNKNOWN,
                DependencyContextGenerator.ROOT_VALUE).bytes()),
        Arguments.of("missing reserved symbol", "tag vocabulary must have "
            + DependencyContextGenerator.ROOT_VALUE + " at row " + (FIRST_TAG_ROW + 2),
            new NetworkBytes().tagSymbols(FeedforwardNetwork.UNKNOWN,
                DependencyContextGenerator.NONE_VALUE, "NN").bytes()),
        Arguments.of("overlapping vocabulary ids", "duplicate embedding id: "
            + (FIRST_TAG_ROW - 1), new NetworkBytes().firstTagRow(FIRST_TAG_ROW - 1).bytes()),
        Arguments.of("vocabulary block with a gap", "label vocabulary id out of range for "
            + DependencyContextGenerator.NONE_VALUE + ": " + MINIMAL_ROWS,
            new NetworkBytes().firstTagRow(4).bytes()),
        Arguments.of("zero embedding size", "embedding size must be positive",
            new NetworkBytes().embeddingSize(0).bytes()),
        Arguments.of("zero hidden size", "hidden matrix rows must be positive",
            new NetworkBytes().hiddenRows(0).bytes()),
        Arguments.of("hidden matrix of the wrong width", "hidden matrix column count is 1, "
            + "expected " + FeedforwardContext.FEATURE_COUNT * UNIT_WIDTH,
            new NetworkBytes().hiddenColumns(1).bytes()),
        Arguments.of("hidden matrix above the cell limit",
            "hidden matrix exceeds the model cell limit of " + ResourceLimits.MAX_MATRIX_CELLS,
            new NetworkBytes().hiddenRows(OVERSIZED_HIDDEN_ROWS).bytes()),
        Arguments.of("non-finite weight", "output bias contains a non-finite value",
            new NetworkBytes().outputBias(Float.NaN).bytes()),
        Arguments.of("trailing data", "trailing data after feedforward network",
            concat(defaults.bytes(), new byte[] {1})),
        Arguments.of("no SHIFT", "transition inventory has no SHIFT action",
            new NetworkBytes().transitions("RIGHT_ARC:root").bytes()),
        Arguments.of("no RIGHT_ARC", "transition inventory has no RIGHT_ARC action",
            new NetworkBytes().transitions("SHIFT").bytes()),
        Arguments.of("invalid transition", "not a transition: JUMP",
            new NetworkBytes().transitions("SHIFT", "JUMP").bytes()),
        Arguments.of("duplicate transition", "duplicate transition: SHIFT",
            new NetworkBytes().transitions("SHIFT", "SHIFT", "RIGHT_ARC:root").bytes()));
  }

  /**
   * Checks that each invalid payload is rejected with an {@link InvalidFormatException}
   * that names the defect.
   *
   * @param name The defect.
   * @param message The expected message.
   * @param payload The network bytes.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidNetworks")
  void testInvalidNetworkIsRejected(String name, String message, byte[] payload) {
    final InvalidFormatException e = assertThrows(InvalidFormatException.class,
        () -> new FeedforwardNetwork.Serializer().create(new ByteArrayInputStream(payload)));
    assertEquals(message, e.getMessage());
  }

  /** Checks that a well-formed minimal payload loads, with a symbol of 70000 bytes. */
  @Test
  void testMinimalNetworkWithALongSymbolLoads() throws IOException {
    final byte[] payload = new NetworkBytes().tagSymbols(FeedforwardNetwork.UNKNOWN,
        DependencyContextGenerator.NONE_VALUE, DependencyContextGenerator.ROOT_VALUE,
        LONG_SYMBOL).bytes();
    final FeedforwardNetwork network =
        new FeedforwardNetwork.Serializer().create(new ByteArrayInputStream(payload));
    assertEquals(MINIMAL_ROWS + 1, network.embeddings().length);
    assertEquals(FIRST_LABEL_ROW, network.tagIds().get(LONG_SYMBOL));
    assertArrayEquals(payload, networkBytes(network));
  }

  /**
   * Checks that a symbol of exactly {@link ResourceLimits#MAX_ENTRIES} UTF-8 bytes loads
   * and reserializes unchanged, while one byte more is refused by the same check the
   * trainer applies. The limit is inclusive.
   *
   * @throws IOException Thrown if building the payload fails.
   */
  @Test
  void testSymbolOfExactlyTheLimitLoads() throws IOException {
    final String atLimit = "x".repeat(ResourceLimits.MAX_ENTRIES);
    final byte[] payload = new NetworkBytes().tagSymbols(FeedforwardNetwork.UNKNOWN,
        DependencyContextGenerator.NONE_VALUE, DependencyContextGenerator.ROOT_VALUE,
        atLimit).bytes();
    final FeedforwardNetwork network =
        new FeedforwardNetwork.Serializer().create(new ByteArrayInputStream(payload));
    assertEquals(FIRST_LABEL_ROW, network.tagIds().get(atLimit));
    assertArrayEquals(payload, networkBytes(network));
    FeedforwardNetwork.requireStorable(atLimit, "tag vocabulary");
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardNetwork.requireStorable(atLimit + "x", "tag vocabulary"));
  }

  /**
   * Checks the limit on the encoded length rather than the character count: a symbol of
   * two-byte characters that fills the limit is accepted, and one more byte is refused.
   * Such a symbol is too long in bytes for the length shortcut and takes the encoding path.
   */
  @Test
  void testMultiByteSymbolIsMeasuredInBytes() {
    final String twoByte = "\u00e9".repeat(ResourceLimits.MAX_ENTRIES / 2);
    FeedforwardNetwork.requireStorable(twoByte, "word vocabulary");
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardNetwork.requireStorable(twoByte + "x", "word vocabulary"));
  }

  /**
   * @return Named parameter sets that must be rejected. Never {@code null}.
   */
  private static Stream<Arguments> invalidParameters() {
    final String embeddingSize = FeedforwardDependencyTrainer.EMBEDDING_SIZE_PARAM;
    final String hiddenSize = FeedforwardDependencyTrainer.HIDDEN_SIZE_PARAM;
    final String learningRate = FeedforwardDependencyTrainer.LEARNING_RATE_PARAM;
    final String dropout = FeedforwardDependencyTrainer.DROPOUT_PARAM;
    return Stream.of(
        Arguments.of("embedding size 0", embeddingSize, 0, embeddingSize + " must be positive"),
        Arguments.of("hidden size 0", hiddenSize, 0, hiddenSize + " must be positive"),
        Arguments.of("epochs 0", Parameters.ITERATIONS_PARAM, 0,
            Parameters.ITERATIONS_PARAM + " must be positive"),
        Arguments.of("batch size 0", FeedforwardDependencyTrainer.BATCH_SIZE_PARAM, 0,
            FeedforwardDependencyTrainer.BATCH_SIZE_PARAM + " must be positive"),
        Arguments.of("negative learning rate", learningRate, -1.0,
            learningRate + " must be positive"),
        Arguments.of("NaN learning rate", learningRate, Double.NaN,
            learningRate + " must be finite"),
        Arguments.of("infinite learning rate", learningRate, Double.POSITIVE_INFINITY,
            learningRate + " must be finite"),
        Arguments.of("infinite l2", QNTrainer.L2COST_PARAM, Double.POSITIVE_INFINITY,
            QNTrainer.L2COST_PARAM + " must be finite"),
        Arguments.of("dropout 1", dropout, 1.0, dropout + " must be in [0, 1): 1.0"),
        Arguments.of("dropout -0.1", dropout, -0.1, dropout + " must be in [0, 1): -0.1"),
        Arguments.of("word cutoff -1", Parameters.CUTOFF_PARAM, -1,
            Parameters.CUTOFF_PARAM + " must not be negative"),
        Arguments.of("embedding size above the limit", embeddingSize,
            FeedforwardDependencyTrainer.MAX_EMBEDDING_SIZE + 1, embeddingSize + " exceeds "
                + FeedforwardDependencyTrainer.MAX_EMBEDDING_SIZE + ": "
                + (FeedforwardDependencyTrainer.MAX_EMBEDDING_SIZE + 1)),
        Arguments.of("hidden size above the limit", hiddenSize,
            FeedforwardDependencyTrainer.MAX_HIDDEN_SIZE + 1, hiddenSize + " exceeds "
                + FeedforwardDependencyTrainer.MAX_HIDDEN_SIZE + ": "
                + (FeedforwardDependencyTrainer.MAX_HIDDEN_SIZE + 1)));
  }

  /**
   * Checks that an out-of-range hyperparameter is rejected before any sample is read,
   * with a message that names the parameter.
   *
   * @param name The defect.
   * @param key The parameter key.
   * @param value The invalid value.
   * @param message The expected message.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidParameters")
  void testInvalidParametersAreRejected(String name, String key, Object value,
      String message) {
    final TrainingParameters parameters = feedforwardParameters(1, 0.01);
    if (value instanceof Integer i) {
      parameters.put(key, i);
    } else {
      parameters.put(key, (Double) value);
    }
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.Settings.from(parameters));
    assertEquals(message, e.getMessage());
  }

  /**
   * Checks that training rejects vocabularies that fit the limit one by one but not
   * together, the combined row count the loader checks.
   */
  @Test
  void testTrainingRejectsACombinedVocabularyAboveTheLoaderLimit() {
    FeedforwardDependencyTrainer.requireCombinedVocabularySize(
        ResourceLimits.MAX_ENTRIES - 2, 1, 1);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.requireCombinedVocabularySize(
            ResourceLimits.MAX_ENTRIES - 1, 1, 1));
    assertEquals("combined vocabulary size " + (ResourceLimits.MAX_ENTRIES + 1L)
        + " exceeds safe limit of " + ResourceLimits.MAX_ENTRIES, e.getMessage());
  }

  /** Checks that the largest widths and the largest network together are rejected or accepted. */
  @Test
  void testParameterLimitsAreInclusive() {
    final TrainingParameters wide = feedforwardParameters(1, 0.01);
    wide.put(FeedforwardDependencyTrainer.EMBEDDING_SIZE_PARAM,
        FeedforwardDependencyTrainer.MAX_EMBEDDING_SIZE);
    wide.put(FeedforwardDependencyTrainer.HIDDEN_SIZE_PARAM, 1);
    assertEquals(FeedforwardDependencyTrainer.MAX_EMBEDDING_SIZE,
        FeedforwardDependencyTrainer.Settings.from(wide).embeddingSize());

    final TrainingParameters deep = feedforwardParameters(1, 0.01);
    deep.put(FeedforwardDependencyTrainer.EMBEDDING_SIZE_PARAM, 1);
    deep.put(FeedforwardDependencyTrainer.HIDDEN_SIZE_PARAM,
        FeedforwardDependencyTrainer.MAX_HIDDEN_SIZE);
    assertEquals(FeedforwardDependencyTrainer.MAX_HIDDEN_SIZE,
        FeedforwardDependencyTrainer.Settings.from(deep).hiddenSize());

    final TrainingParameters dropout = feedforwardParameters(1, 0.01);
    dropout.put(FeedforwardDependencyTrainer.DROPOUT_PARAM, Math.nextDown(1.0));
    assertEquals(Math.nextDown(1.0),
        FeedforwardDependencyTrainer.Settings.from(dropout).dropout());

    final TrainingParameters oversized = feedforwardParameters(1, 0.01);
    oversized.put(FeedforwardDependencyTrainer.EMBEDDING_SIZE_PARAM,
        FeedforwardDependencyTrainer.MAX_EMBEDDING_SIZE);
    oversized.put(FeedforwardDependencyTrainer.HIDDEN_SIZE_PARAM,
        FeedforwardDependencyTrainer.MAX_HIDDEN_SIZE);
    assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.Settings.from(oversized));
  }

  /**
   * @return Single-epoch parameters with a beam size of one. Never {@code null}.
   */
  private static TrainingParameters greedyBeamParameters() {
    final TrainingParameters parameters = feedforwardParameters(1, 0.01);
    parameters.put(BeamSearch.BEAM_SIZE_PARAMETER, 1);
    return parameters;
  }

  /**
   * Checks that the beam size only constrains refinement: local training ignores it,
   * so parameters shared with a greedy parser train without an error.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testTrainAcceptsAGreedyBeamSize() throws IOException {
    final FeedforwardDependencyModel trained =
        trainFeedforward(corpus(), greedyBeamParameters());
    assertNotNull(new FeedforwardDependencyParser(trained)
        .parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  /** Checks that absent keys take the documented defaults. */
  @Test
  void testDefaultsApplyToAbsentKeys() {
    final FeedforwardDependencyTrainer.Settings settings =
        FeedforwardDependencyTrainer.Settings.from(new TrainingParameters());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_EMBEDDING_SIZE, settings.embeddingSize());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_HIDDEN_SIZE, settings.hiddenSize());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_EPOCHS, settings.epochs());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_BATCH_SIZE, settings.batchSize());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_LEARNING_RATE, settings.learningRate());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_L2, settings.l2());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_DROPOUT, settings.dropout());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_WORD_CUTOFF, settings.wordCutoff());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_SEED, settings.seed());
    assertEquals(FeedforwardDependencyTrainer.DEFAULT_BEAM_SIZE, settings.beamSize());
  }

  /**
   * Checks that training rejects a symbol the network loader could not read back,
   * instead of producing a model that does not load.
   */
  @Test
  void testTrainingRejectsASymbolAboveTheLoaderLimit() {
    final String oversized = "x".repeat(ResourceLimits.MAX_ENTRIES + 1);
    final List<DependencySample> samples = List.of(sample(new String[] {oversized, "barks"},
        new String[] {"NN", "VBZ"}, new int[] {1, -1}, new String[] {"nsubj", "root"}));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> trainFeedforward(samples, SINGLE_EPOCH_PARAMETERS));
    assertEquals("word vocabulary symbol length " + (ResourceLimits.MAX_ENTRIES + 1L)
        + " exceeds the limit of " + ResourceLimits.MAX_ENTRIES + " UTF-8 bytes",
        e.getMessage());
  }

  /** Checks that every reserved symbol, and nothing else, is recognized as reserved. */
  @Test
  void testReservedSymbolsAreRecognized() {
    for (final String[] reserved : List.of(FeedforwardNetwork.RESERVED_WORD_SYMBOLS,
        FeedforwardNetwork.RESERVED_TAG_SYMBOLS, FeedforwardNetwork.RESERVED_LABEL_SYMBOLS)) {
      for (final String symbol : reserved) {
        assertTrue(FeedforwardNetwork.isReservedSymbol(symbol), symbol);
      }
    }
    assertFalse(FeedforwardNetwork.isReservedSymbol("barks"));
    assertFalse(FeedforwardNetwork.isReservedSymbol(null));
  }

  /** Checks that a diverging run fails with an IllegalStateException. */
  @Test
  void testTrainingRejectsNonFiniteWeights() {
    final TrainingParameters parameters = feedforwardParameters(2, Double.MAX_VALUE);
    parameters.put(FeedforwardDependencyTrainer.EMBEDDING_SIZE_PARAM, 4);
    parameters.put(FeedforwardDependencyTrainer.HIDDEN_SIZE_PARAM, 4);
    parameters.put(FeedforwardDependencyTrainer.BATCH_SIZE_PARAM, 1);
    assertThrows(IllegalStateException.class, () -> trainFeedforward(corpus(), parameters));
  }

  /** Checks the feature length guards of the network. */
  @Test
  void testNetworkRejectsInvalidFeatureArrays() {
    assertThrows(IllegalArgumentException.class, () -> score(model.network(), new int[0], null));
    final int[] outOfRange = new int[FeedforwardContext.FEATURE_COUNT];
    outOfRange[0] = model.network().embeddings().length;
    assertThrows(IllegalArgumentException.class, () -> score(model.network(), outOfRange, null));
  }

  /**
   * @return Named invalid calls with their expected messages. Never {@code null}.
   */
  private static Stream<Arguments> invalidArguments() {
    final TrainingParameters parameters = new TrainingParameters();
    return Stream.of(
        Arguments.of("parser without model", "model must not be null",
            (Executable) () -> new FeedforwardDependencyParser(null)),
        Arguments.of("parser with beam 0", "beamSize must be positive",
            (Executable) () -> new FeedforwardDependencyParser(model, 0)),
        Arguments.of("parser with negative cache", "scoringCacheBytes must not be negative",
            (Executable) () -> new FeedforwardDependencyParser(model, 1, -1L)),
        Arguments.of("parser with a cache too small for the tables", null,
            (Executable) () -> new FeedforwardDependencyParser(model, 1, 1L)),
        Arguments.of("train without language", "languageCode must not be null",
            (Executable) () -> FeedforwardDependencyTrainer.train(null,
                ObjectStreamUtils.createObjectStream(corpus()), parameters)),
        Arguments.of("train without samples", "samples must not be null",
            (Executable) () -> FeedforwardDependencyTrainer.train(LANGUAGE, null, parameters)),
        Arguments.of("train without parameters", "parameters must not be null",
            (Executable) () -> FeedforwardDependencyTrainer.train(LANGUAGE,
                ObjectStreamUtils.createObjectStream(corpus()), null)),
        Arguments.of("refine without model", "model must not be null",
            (Executable) () -> FeedforwardDependencyTrainer.refine(null,
                ObjectStreamUtils.createObjectStream(corpus()), parameters)),
        Arguments.of("refine without samples", "samples must not be null",
            (Executable) () -> FeedforwardDependencyTrainer.refine(model, null, parameters)),
        Arguments.of("refine without parameters", "parameters must not be null",
            (Executable) () -> FeedforwardDependencyTrainer.refine(model,
                ObjectStreamUtils.createObjectStream(corpus()), null)),
        Arguments.of("refine with beam 1", BeamSearch.BEAM_SIZE_PARAMETER
            + " must be at least 2: 1",
            (Executable) () -> FeedforwardDependencyTrainer.refine(model,
                ObjectStreamUtils.createObjectStream(corpus()), greedyBeamParameters())),
        Arguments.of("parse without tokens", "tokens must not be null",
            (Executable) () -> parser.parse(null, new String[] {"DT"})),
        Arguments.of("parse without tags", "tags must not be null",
            (Executable) () -> parser.parse(new String[] {"the"}, null)),
        Arguments.of("parse with misaligned tags", null,
            (Executable) () -> parser.parse(new String[] {"the"}, new String[] {"DT", "NN"})),
        Arguments.of("score without features", "features must not be null",
            (Executable) () -> score(model.network(), null, null)),
        Arguments.of("model without stream", "in must not be null",
            (Executable) () -> new FeedforwardDependencyModel((InputStream) null)),
        Arguments.of("model without path", "modelPath must not be null",
            (Executable) () -> new FeedforwardDependencyModel((Path) null)));
  }

  /**
   * Checks the argument guards of the parser, trainer, network, and model.
   *
   * @param name The call.
   * @param message The expected message, or {@code null} to check only the type.
   * @param call The invalid call.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidArguments")
  void testInvalidArgumentsAreRejected(String name, String message, Executable call) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, call);
    if (message != null) {
      assertEquals(message, e.getMessage());
    }
  }

  /**
   * Verifies that training data using the reserved symbols as ordinary tags, labels,
   * or tokens does not displace the reserved rows: the model reloads and the colliding
   * symbols share the reserved rows the way unknown symbols do.
   *
   * @throws IOException Thrown if serialization or loading fails.
   */
  @Test
  void testReservedSymbolsInTrainingDataSurviveReload() throws IOException {
    final List<DependencySample> colliding = List.of(
        sample(new String[] {"*ROOT*", "*UNK*", "barks"}, new String[] {"*UNK*", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"*NULL*", "nsubj", "root"}),
        sample(new String[] {"dogs", "bark"}, new String[] {"*NULL*", "*ROOT*"},
            new int[] {1, -1}, new String[] {"*UNK*", "root"}));
    final FeedforwardDependencyModel trained = trainFeedforward(colliding, SINGLE_EPOCH_PARAMETERS);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    trained.serialize(out);
    final FeedforwardDependencyModel reloaded =
        new FeedforwardDependencyModel(new ByteArrayInputStream(out.toByteArray()));
    final String[] tokens = {"*ROOT*", "*UNK*", "barks"};
    final String[] tags = {"*UNK*", "NN", "VBZ"};
    assertEquals(new FeedforwardDependencyParser(trained).parse(tokens, tags),
        new FeedforwardDependencyParser(reloaded).parse(tokens, tags));
  }

  /**
   * @param network The network whose vocabularies map the features.
   * @param tokens The sentence tokens.
   * @param tags The tags aligned with {@code tokens}.
   * @return The features of the initial configuration. Never {@code null}.
   */
  private static int[] initialFeatures(FeedforwardNetwork network, String[] tokens,
      String[] tags) {
    return new FeedforwardContext(network, tokens, tags)
        .featureIds(new ArcStandardState(tokens.length));
  }

  /**
   * @param first The leading bytes.
   * @param second The trailing bytes.
   * @return Both arrays joined. Never {@code null}.
   */
  private static byte[] concat(byte[] first, byte[] second) {
    final byte[] joined = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, joined, first.length, second.length);
    return joined;
  }

  /**
   * Writes the smallest complete network in the artifact format, with selected fields
   * exposed for corruption. Every field defaults to a valid value.
   */
  private static final class NetworkBytes {
    private byte[] magic = FeedforwardNetwork.Serializer.MAGIC;
    private int version = FeedforwardNetwork.Serializer.FORMAT_VERSION;
    private int wordCount = FeedforwardNetwork.RESERVED_WORD_SYMBOLS.length;
    private int firstWordLength = -1;
    private String[] tagSymbols = FeedforwardNetwork.RESERVED_TAG_SYMBOLS;
    private int firstTagRow = FIRST_TAG_ROW;
    private String[] transitions = MINIMAL_TRANSITIONS;
    private int embeddingSize = UNIT_WIDTH;
    private int hiddenRows = 1;
    private int hiddenColumns = FeedforwardContext.FEATURE_COUNT * UNIT_WIDTH;
    private float outputBias = 0.0f;

    /**
     * @param value The leading bytes in place of the magic.
     * @return This builder.
     */
    NetworkBytes magic(byte[] value) {
      magic = value;
      return this;
    }

    /**
     * @param value The format version to write.
     * @return This builder.
     */
    NetworkBytes version(int value) {
      version = value;
      return this;
    }

    /**
     * @param value The word vocabulary size to write; the reserved word entries follow regardless.
     * @return This builder.
     */
    NetworkBytes wordCount(int value) {
      wordCount = value;
      return this;
    }

    /**
     * @param value The byte length to write for the first word symbol.
     * @return This builder.
     */
    NetworkBytes firstWordLength(int value) {
      firstWordLength = value;
      return this;
    }

    /**
     * @param value The tag symbols, in row order.
     * @return This builder.
     */
    NetworkBytes tagSymbols(String... value) {
      tagSymbols = value;
      return this;
    }

    /**
     * @param value The embedding row of the first tag.
     * @return This builder.
     */
    NetworkBytes firstTagRow(int value) {
      firstTagRow = value;
      return this;
    }

    /**
     * @param value The transition inventory.
     * @return This builder.
     */
    NetworkBytes transitions(String... value) {
      transitions = value;
      return this;
    }

    /**
     * @param value The embedding size to write in the header.
     * @return This builder.
     */
    NetworkBytes embeddingSize(int value) {
      embeddingSize = value;
      return this;
    }

    /**
     * @param value The hidden layer width.
     * @return This builder.
     */
    NetworkBytes hiddenRows(int value) {
      hiddenRows = value;
      return this;
    }

    /**
     * @param value The hidden matrix column count.
     * @return This builder.
     */
    NetworkBytes hiddenColumns(int value) {
      hiddenColumns = value;
      return this;
    }

    /**
     * @param value The output bias of the first transition.
     * @return This builder.
     */
    NetworkBytes outputBias(float value) {
      outputBias = value;
      return this;
    }

    /**
     * @return The payload. Never {@code null}.
     * @throws IOException Thrown if writing fails.
     */
    byte[] bytes() throws IOException {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (DataOutputStream data = new DataOutputStream(out)) {
        data.write(magic);
        data.writeInt(version);
        data.writeInt(wordCount);
        for (int i = 0; i < FeedforwardNetwork.RESERVED_WORD_SYMBOLS.length; i++) {
          final byte[] symbol =
              FeedforwardNetwork.RESERVED_WORD_SYMBOLS[i].getBytes(StandardCharsets.UTF_8);
          data.writeInt(i == 0 && firstWordLength >= 0 ? firstWordLength : symbol.length);
          data.write(symbol);
          data.writeInt(i);
        }
        writeVocabulary(data, firstTagRow, tagSymbols);
        final int firstLabelRow = firstTagRow + tagSymbols.length;
        writeVocabulary(data, firstLabelRow, FeedforwardNetwork.RESERVED_LABEL_SYMBOLS);
        final int rows = FeedforwardNetwork.RESERVED_WORD_SYMBOLS.length + tagSymbols.length
            + FeedforwardNetwork.RESERVED_LABEL_SYMBOLS.length;
        data.writeInt(transitions.length);
        for (final String transition : transitions) {
          writeSymbol(data, transition);
        }
        data.writeInt(embeddingSize);
        writeMatrix(data, rows, embeddingSize);
        writeMatrix(data, hiddenRows, hiddenColumns);
        writeVector(data, hiddenRows);
        writeMatrix(data, transitions.length, hiddenRows);
        data.writeInt(transitions.length);
        data.writeFloat(outputBias);
        for (int i = 1; i < transitions.length; i++) {
          data.writeFloat(0.0f);
        }
      }
      return out.toByteArray();
    }

    /**
     * Writes a vocabulary with consecutive embedding rows.
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
        writeSymbol(data, symbols[i]);
        data.writeInt(first + i);
      }
    }

    /**
     * Writes a symbol as its UTF-8 length and bytes.
     *
     * @param data The output to write to.
     * @param symbol The symbol.
     * @throws IOException Thrown if writing fails.
     */
    private static void writeSymbol(DataOutputStream data, String symbol) throws IOException {
      final byte[] bytes = symbol.getBytes(StandardCharsets.UTF_8);
      data.writeInt(bytes.length);
      data.write(bytes);
    }

    /**
     * Writes a zero-filled matrix; an oversized row count writes no values, because the
     * loader must reject it from the header.
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
      if (rows < OVERSIZED_HIDDEN_ROWS) {
        for (int i = 0; i < rows * columns; i++) {
          data.writeFloat(0.0f);
        }
      }
    }

    /**
     * Writes a zero-filled vector.
     *
     * @param data The output to write to.
     * @param length The vector length.
     * @throws IOException Thrown if writing fails.
     */
    private static void writeVector(DataOutputStream data, int length) throws IOException {
      data.writeInt(length);
      if (length < OVERSIZED_HIDDEN_ROWS) {
        for (int i = 0; i < length; i++) {
          data.writeFloat(0.0f);
        }
      }
    }
  }
}
