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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static opennlp.tools.depparse.DependencyTestSamples.CACHE_BYTES;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.nonProjectiveSample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link DependencyParser} contract shared by {@link DependencyParserME} and
 * {@link FeedforwardDependencyParser}: input boundaries, non-projective input, and
 * thread sharing, plus the persistence of the maximum entropy model.
 */
public class DependencyParserEdgeCaseTest {

  /** The number of threads parsing concurrently in the sharing test. */
  private static final int THREADS = 8;

  /** The number of parses each thread performs in the sharing test. */
  private static final int ITERATIONS_PER_THREAD = 50;

  /** The beam size of the beamed feedforward parser under test. */
  private static final int BEAM_SIZE = 4;

  private static DependencyModel model;
  private static DependencyParserME parser;
  private static FeedforwardDependencyModel feedforwardModel;

  /**
   * Trains both models on the shared corpus.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParsers() throws IOException {
    model = DependencyTestSamples.train(corpus());
    parser = new DependencyParserME(model);
    feedforwardModel = DependencyTestSamples.trainFeedforward();
  }

  /**
   * @return One parser per implementation and decoding mode, each sharing the
   *         contract under test. Never {@code null}.
   */
  private static Stream<Arguments> parsers() {
    return Stream.of(
        Arguments.of("DependencyParserME", parser),
        Arguments.of("FeedforwardDependencyParser greedy",
            new FeedforwardDependencyParser(feedforwardModel)),
        Arguments.of("FeedforwardDependencyParser beam " + BEAM_SIZE,
            new FeedforwardDependencyParser(feedforwardModel, BEAM_SIZE)),
        Arguments.of("FeedforwardDependencyParser beam " + BEAM_SIZE + " with scoring cache",
            new FeedforwardDependencyParser(feedforwardModel, BEAM_SIZE, CACHE_BYTES)));
  }

  /**
   * Checks that an empty sentence is rejected before decoding.
   *
   * @param name The parser under test.
   * @param parser The parser under test.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parsers")
  void testEmptySentenceIsRejected(String name, DependencyParser parser) {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[0], new String[0]));
  }

  /** Checks that the transition system itself has no configuration for zero tokens. */
  @Test
  void testStateRejectsZeroTokens() {
    assertThrows(IllegalArgumentException.class, () -> new ArcStandardState(0));
  }

  /**
   * Checks that a {@code null} token or tag is rejected.
   *
   * @param name The parser under test.
   * @param parser The parser under test.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parsers")
  void testNullTokenOrTagIsRejected(String name, DependencyParser parser) {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {null}, new String[] {"NN"}));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"word"}, new String[] {null}));
  }

  /** Checks that the context generator rejects tags that do not align with the tokens. */
  @Test
  void testContextGeneratorRejectsMisalignedInput() {
    final DependencyContextGenerator generator = new DependencyContextGenerator();
    final ArcStandardState state = new ArcStandardState(2);
    assertThrows(IllegalArgumentException.class,
        () -> generator.getContext(state, new String[] {"one"}, new String[] {"NN"}));
  }

  /**
   * Checks that a single token attaches to the artificial root: the only derivation is
   * shift then right-arc, so the parser only chooses the relation label.
   *
   * @param name The parser under test.
   * @param parser The parser under test.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parsers")
  void testSingleTokenSentenceAttachesToTheRoot(String name, DependencyParser parser) {
    final DependencyTree parsed = parser.parse(new String[] {"Run"}, new String[] {"VB"});
    assertEquals(DependencyTree.of(new int[] {-1}, new String[] {"root"}), parsed);
  }

  /**
   * Checks that a non-projective sample, which has no arc-standard derivation, is
   * skipped and the maximum entropy parser still memorizes the projective sentences.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @Test
  void testNonProjectiveSamplesAreSkippedDuringTraining() throws IOException {
    final List<DependencySample> mixed = new ArrayList<>(corpus());
    mixed.add(nonProjectiveSample());
    final DependencyParserME mixedParser =
        new DependencyParserME(DependencyTestSamples.train(mixed));
    assertEquals(THE_DOG_BARKS_TREE,
        mixedParser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
    assertEquals(SHE_EATS_FISH_TREE,
        mixedParser.parse(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS));
  }

  /**
   * Checks that a sentence whose gold tree is non-projective decodes to a different,
   * projective tree, since the parser can only emit arc-standard derivations.
   *
   * @param name The parser under test.
   * @param parser The parser under test.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parsers")
  void testNonProjectiveGoldDecodesToAProjectiveTree(String name, DependencyParser parser) {
    final DependencySample gold = nonProjectiveSample();
    final DependencyTree parsed = parser.parse(gold.getTokens(), gold.getTags());
    assertNotEquals(gold.getTree(), parsed);
    assertTrue(ArcStandardOracle.isProjective(parsed), parsed.toString());
  }

  /**
   * Checks that a saved and reloaded maximum entropy model parses every corpus sentence
   * like the original.
   *
   * @param dir The directory for the model file.
   * @throws IOException Thrown if writing or loading the model fails.
   */
  @Test
  void testModelFileRoundTripParsesIdentically(@TempDir Path dir)
      throws IOException {
    final Path file = dir.resolve("depparse.bin");
    model.serialize(file);
    final DependencyParserME reloaded = new DependencyParserME(new DependencyModel(file));
    for (final DependencySample sample : corpus()) {
      assertEquals(parser.parse(sample.getTokens(), sample.getTags()),
          reloaded.parse(sample.getTokens(), sample.getTags()),
          Arrays.toString(sample.getTokens()));
    }
    assertEquals(THE_DOG_BARKS_TREE, reloaded.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  /**
   * Checks that one parser instance serves several threads at once.
   *
   * @param name The parser under test.
   * @param parser The parser under test.
   * @throws Exception Thrown if a worker fails.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parsers")
  void testParserInstancesCanBeSharedBetweenThreads(String name, DependencyParser parser)
      throws Exception {
    final List<Callable<Void>> tasks = new ArrayList<>();
    for (int task = 0; task < THREADS; task++) {
      tasks.add(() -> {
        for (int iteration = 0; iteration < ITERATIONS_PER_THREAD; iteration++) {
          assertEquals(THE_DOG_BARKS_TREE,
              parser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
        }
        return null;
      });
    }

    final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
    try {
      final List<Future<Void>> results = executor.invokeAll(tasks);
      for (final Future<Void> result : results) {
        result.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }
}
