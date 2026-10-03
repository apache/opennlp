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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_GRAPH;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_GRAPH;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.sample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests parser boundaries, non-projective input, model persistence and thread sharing.
 */
public class DependencyParserEdgeCaseTest {

  /** The number of threads parsing concurrently in the sharing test. */
  private static final int THREADS = 8;

  /** The number of parses each thread performs in the sharing test. */
  private static final int ITERATIONS_PER_THREAD = 50;

  private static DependencyModel model;
  private static DependencyParserME parser;

  /**
   * Builds a four-token sample whose gold arcs (2,0) and (3,1) cross, so the tree is
   * non-projective and has no arc-standard derivation.
   *
   * @return The non-projective sample. Never {@code null}.
   */
  private static DependencySample nonProjectiveSample() {
    return sample(new String[] {"the", "dog", "barks", "today"},
        new String[] {"DT", "NN", "VBZ", "RB"},
        new int[] {2, 3, -1, 2}, new String[] {"det", "nsubj", "root", "advmod"});
  }

  /**
   * Trains a model on the shared corpus.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    model = DependencyTestSamples.train(corpus());
    parser = new DependencyParserME(model);
  }

  @Test
  void testEmptySentenceIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[0], new String[0]));
    // The transition system itself has no configuration for zero tokens either.
    assertThrows(IllegalArgumentException.class, () -> new ArcStandardState(0));
  }

  @Test
  void testNullTokenOrTagIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {null}, new String[] {"NN"}));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"word"}, new String[] {null}));
  }

  @Test
  void testContextGeneratorRejectsMisalignedInput() {
    final DependencyContextGenerator generator = new DependencyContextGenerator();
    final ArcStandardState state = new ArcStandardState(2);
    assertThrows(IllegalArgumentException.class,
        () -> generator.getContext(state, new String[] {"one"}, new String[] {"NN"}));
  }

  @Test
  void testSingleTokenSentenceAttachesToTheRoot() {
    // A single token permits only the derivation shift then right-arc, so the head is
    // forced to the artificial root and the model only chooses the relation label.
    final DependencyGraph parsed =
        parser.parse(new String[] {"Run"}, new String[] {"VB"});
    assertEquals(DependencyGraph.of(new int[] {-1}, new String[] {"root"}), parsed);
  }

  @Test
  void testNonProjectiveSamplesAreSkippedDuringTraining() throws IOException {
    // One non-projective sample joins the corpus; it cannot yield events, so training
    // proceeds on the remaining samples and still memorizes the projective sentences.
    final List<DependencySample> mixed = new ArrayList<>(corpus());
    mixed.add(nonProjectiveSample());
    final DependencyParserME mixedParser =
        new DependencyParserME(DependencyTestSamples.train(mixed));
    assertEquals(THE_DOG_BARKS_GRAPH,
        mixedParser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
    assertEquals(SHE_EATS_FISH_GRAPH,
        mixedParser.parse(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS));
  }

  @Test
  void testNonProjectiveGoldDecodesToAProjectiveTree() {
    // The parser can only emit arc-standard derivations, so for a sentence whose gold
    // tree is non-projective the prediction is necessarily a different, projective tree.
    final DependencySample gold = nonProjectiveSample();
    final DependencyGraph parsed = parser.parse(gold.getTokens(), gold.getTags());
    assertNotEquals(gold.getGraph(), parsed);
    assertTrue(ArcStandardOracle.isProjective(parsed), parsed.toString());
  }

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
    assertEquals(THE_DOG_BARKS_GRAPH, reloaded.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
  }

  @Test
  void testParserInstancesCanBeSharedBetweenThreads() throws Exception {
    final List<Callable<Void>> tasks = new ArrayList<>();
    for (int task = 0; task < THREADS; task++) {
      tasks.add(() -> {
        for (int iteration = 0; iteration < ITERATIONS_PER_THREAD; iteration++) {
          assertEquals(THE_DOG_BARKS_GRAPH,
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
