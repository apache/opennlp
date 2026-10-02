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
import java.io.OutputStream;
import java.nio.file.Files;
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

import opennlp.tools.util.ObjectStreamUtils;

import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.sample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests neural parser boundaries, non-projective training, persistence and thread sharing.
 */
public class FeedforwardDependencyParserEdgeCaseTest {

  /** The language code of the test corpus. */
  private static final String LANGUAGE = "eng";

  /** The random seed making the feedforward training runs reproducible. */
  private static final long SEED = 17L;

  /**
   * Single-epoch feedforward settings for tests that only inspect the trained inventory.
   */
  private static final FeedforwardDependencyTrainer.Settings SINGLE_EPOCH_SETTINGS =
      new FeedforwardDependencyTrainer.Settings(8, 8, 1, 32, 0.05, 0.0, 0.0, 1, SEED);

  /** The tokens of the first corpus sentence. */
  private static final String[] THE_DOG_BARKS_TOKENS = {"the", "dog", "barks"};

  /** The tags of the first corpus sentence. */
  private static final String[] THE_DOG_BARKS_TAGS = {"DT", "NN", "VBZ"};

  /** The gold graph of the first corpus sentence. */
  private static final DependencyGraph THE_DOG_BARKS_GRAPH =
      DependencyGraph.of(new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"});

  /** The tokens of the third corpus sentence. */
  private static final String[] SHE_EATS_FISH_TOKENS = {"she", "eats", "fish"};

  /** The tags of the third corpus sentence. */
  private static final String[] SHE_EATS_FISH_TAGS = {"PRP", "VBZ", "NN"};

  /** The gold graph of the third corpus sentence. */
  private static final DependencyGraph SHE_EATS_FISH_GRAPH =
      DependencyGraph.of(new int[] {1, -1, 1}, new String[] {"nsubj", "root", "obj"});

  /** The number of threads parsing concurrently in the sharing test. */
  private static final int THREADS = 8;

  /** The number of parses each thread performs in the sharing test. */
  private static final int ITERATIONS_PER_THREAD = 50;

  private static FeedforwardDependencyModel feedforwardModel;
  private static FeedforwardDependencyParser feedforwardParser;

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
   * Trains a neural model with dropout disabled and a fixed random seed.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParsers() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 60, 32, 0.05, 0.0, 0.0, 1, SEED);
    feedforwardModel = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    feedforwardParser = new FeedforwardDependencyParser(feedforwardModel);
  }

  @Test
  void testEmptySentenceIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> feedforwardParser.parse(new String[0], new String[0]));
  }

  @Test
  void testNullTokenOrTagIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> feedforwardParser.parse(new String[] {null}, new String[] {"NN"}));
    assertThrows(IllegalArgumentException.class,
        () -> feedforwardParser.parse(new String[] {"word"}, new String[] {null}));
  }

  @Test
  void testSingleTokenSentenceAttachesToTheRoot() {
    // A single token permits only the derivation shift then right-arc, so the head is
    // forced to the artificial root and the model only chooses the relation label.
    final DependencyGraph feedforwardParse =
        feedforwardParser.parse(new String[] {"Run"}, new String[] {"VB"});
    assertEquals(DependencyGraph.of(new int[] {-1}, new String[] {"root"}),
        feedforwardParse);
  }

  @Test
  void testFeedforwardTrainingOmitsNonProjectiveLabels() throws IOException {
    final List<DependencySample> mixed = new ArrayList<>(corpus());
    mixed.add(sample(new String[] {"a", "b", "c", "d"},
        new String[] {"DT", "NN", "VBZ", "RB"}, new int[] {2, 3, -1, 2},
        new String[] {"det", "dislocated", "root", "obj"}));

    final FeedforwardDependencyModel trained = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(mixed), SINGLE_EPOCH_SETTINGS);

    assertEquals(0, List.of(trained.transitions()).stream()
        .filter(transition -> transition.contains("dislocated")).count());
  }

  @Test
  void testFeedforwardTrainingRejectsNoProjectiveSamples() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(List.of(nonProjectiveSample())),
            SINGLE_EPOCH_SETTINGS));
    assertEquals("no trainable examples in the samples", e.getMessage());
  }

  @Test
  void testRefinementRejectsNoProjectiveSamples() {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 1, 32, 0.01, 0.0, 0.0, 1, SEED);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(feedforwardModel,
            ObjectStreamUtils.createObjectStream(List.of(nonProjectiveSample())),
            settings, 2));
    assertEquals("no trainable samples for refinement", e.getMessage());
  }

  @Test
  void testFeedforwardModelFileRoundTripParsesIdentically(@TempDir Path dir)
      throws IOException {
    final Path file = dir.resolve("depparse-ff.bin");
    try (OutputStream out = Files.newOutputStream(file)) {
      feedforwardModel.serialize(out);
    }
    final FeedforwardDependencyParser reloaded =
        new FeedforwardDependencyParser(FeedforwardDependencyModel.load(file));
    for (final DependencySample sample : corpus()) {
      assertEquals(feedforwardParser.parse(sample.getTokens(), sample.getTags()),
          reloaded.parse(sample.getTokens(), sample.getTags()),
          Arrays.toString(sample.getTokens()));
    }
    assertEquals(SHE_EATS_FISH_GRAPH, reloaded.parse(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS));
  }

  @Test
  void testParserInstancesCanBeSharedBetweenThreads() throws Exception {
    final List<Callable<Void>> tasks = new ArrayList<>();
    for (int task = 0; task < THREADS; task++) {
      tasks.add(() -> {
        for (int iteration = 0; iteration < ITERATIONS_PER_THREAD; iteration++) {
          assertEquals(THE_DOG_BARKS_GRAPH,
              feedforwardParser.parse(THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS));
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
