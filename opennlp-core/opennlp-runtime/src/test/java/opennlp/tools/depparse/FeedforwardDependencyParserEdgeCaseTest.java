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

import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.SHE_EATS_FISH_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TREE;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.nonProjectiveSample;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests neural parser boundaries, non-projective training, persistence and thread sharing.
 */
public class FeedforwardDependencyParserEdgeCaseTest {

  /** The random seed making the feedforward training runs reproducible. */
  private static final long SEED = FeedforwardDependencyTrainer.Settings.DEFAULT_SEED;

  /**
   * Single-epoch feedforward settings for tests that only inspect the trained inventory.
   */
  private static final FeedforwardDependencyTrainer.Settings SINGLE_EPOCH_SETTINGS =
      new FeedforwardDependencyTrainer.Settings(8, 8, 1, 32, 0.05, 0.0, 0.0, 1, SEED);

  /** The number of threads parsing concurrently in the sharing test. */
  private static final int THREADS = 8;

  /** The number of parses each thread performs in the sharing test. */
  private static final int ITERATIONS_PER_THREAD = 50;

  private static FeedforwardDependencyModel model;
  private static FeedforwardDependencyParser parser;

  /**
   * Trains a neural model with dropout disabled and a fixed random seed.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 60, 32, 0.05, 0.0, 0.0, 1, SEED);
    model = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    parser = new FeedforwardDependencyParser(model);
  }

  @Test
  void testEmptySentenceIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[0], new String[0]));
  }

  @Test
  void testNullTokenOrTagIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {null}, new String[] {"NN"}));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"word"}, new String[] {null}));
  }

  @Test
  void testSingleTokenSentenceAttachesToTheRoot() {
    // A single token permits only the derivation shift then right-arc, so the head is
    // forced to the artificial root and the model only chooses the relation label.
    final DependencyTree feedforwardParse =
        parser.parse(new String[] {"Run"}, new String[] {"VB"});
    assertEquals(DependencyTree.of(new int[] {-1}, new String[] {"root"}),
        feedforwardParse);
  }

  @Test
  void testFeedforwardTrainingOmitsNonProjectiveLabels() throws IOException {
    final List<DependencySample> mixed = new ArrayList<>(corpus());
    mixed.add(nonProjectiveSample(new String[] {"a", "b", "c", "d"},
        new String[] {"DT", "NN", "VBZ", "RB"},
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
        () -> FeedforwardDependencyTrainer.refine(model,
            ObjectStreamUtils.createObjectStream(List.of(nonProjectiveSample())),
            settings, 2));
    assertEquals("no trainable samples for refinement", e.getMessage());
  }

  @Test
  void testFeedforwardModelFileRoundTripParsesIdentically(@TempDir Path dir)
      throws IOException {
    final Path file = dir.resolve("depparse-ff.bin");
    try (OutputStream out = Files.newOutputStream(file)) {
      model.serialize(out);
    }
    final FeedforwardDependencyParser reloaded =
        new FeedforwardDependencyParser(FeedforwardDependencyModel.load(file));
    for (final DependencySample sample : corpus()) {
      assertEquals(parser.parse(sample.getTokens(), sample.getTags()),
          reloaded.parse(sample.getTokens(), sample.getTags()),
          Arrays.toString(sample.getTokens()));
    }
    assertEquals(SHE_EATS_FISH_TREE, reloaded.parse(SHE_EATS_FISH_TOKENS, SHE_EATS_FISH_TAGS));
  }

  @Test
  void testParserInstancesCanBeSharedBetweenThreads() throws Exception {
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
