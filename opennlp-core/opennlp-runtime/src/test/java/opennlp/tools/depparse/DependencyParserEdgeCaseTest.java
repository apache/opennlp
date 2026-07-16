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
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the boundary behavior of both dependency parsers: empty and single-token input,
 * non-projective sentences during training and parsing, and the file round trip of both
 * model formats, which must reproduce the exact parses of the original models.
 */
public class DependencyParserEdgeCaseTest {

  private static DependencyModel maxentModel;
  private static DependencyParserME maxentParser;
  private static FeedforwardDependencyModel feedforwardModel;
  private static FeedforwardDependencyParser feedforwardParser;

  /**
   * Builds one gold sample from its parallel arrays.
   *
   * @param tokens The sentence tokens. Must not be {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}.
   * @param heads The zero-based head per token, {@code -1} for the root.
   * @param relations The relation label per token.
   * @return The assembled sample. Never {@code null}.
   */
  private static DependencySample sample(String[] tokens, String[] tags, int[] heads,
      String[] relations) {
    return new DependencySample(tokens, tags, DependencyGraph.of(heads, relations));
  }

  /**
   * Builds the projective training corpus: three tiny sentences, each repeated so both
   * trainers see enough evidence to memorize them.
   *
   * @return The training samples. Never {@code null}.
   */
  private static List<DependencySample> corpus() {
    final List<DependencySample> distinct = List.of(
        sample(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"}),
        sample(new String[] {"dogs", "bark"}, new String[] {"NNS", "VBP"},
            new int[] {1, -1}, new String[] {"nsubj", "root"}),
        sample(new String[] {"she", "eats", "fish"}, new String[] {"PRP", "VBZ", "NN"},
            new int[] {1, -1, 1}, new String[] {"nsubj", "root", "obj"}));
    final List<DependencySample> corpus = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      corpus.addAll(distinct);
    }
    return corpus;
  }

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
   * Trains one classical and one neural model on the shared corpus. The feedforward
   * settings disable dropout and fix the seed, so the tiny network memorizes the corpus
   * deterministically.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParsers() throws IOException {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    maxentModel = DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(corpus()), parameters);
    maxentParser = new DependencyParserME(maxentModel);

    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 60, 32, 0.05, 0.0, 0.0, 1, 17L);
    feedforwardModel = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    feedforwardParser = new FeedforwardDependencyParser(feedforwardModel);
  }

  @Test
  void testEmptySentenceIsRejectedByBothParsers() {
    assertThrows(IllegalArgumentException.class,
        () -> maxentParser.parse(new String[0], new String[0]));
    assertThrows(IllegalArgumentException.class,
        () -> feedforwardParser.parse(new String[0], new String[0]));
    // The transition system itself has no configuration for zero tokens either.
    assertThrows(IllegalArgumentException.class, () -> new ArcStandardState(0));
  }

  @Test
  void testSingleTokenSentenceAttachesToTheRoot() {
    // A single token permits only the derivation shift then right-arc, so the head is
    // forced to the artificial root and the model only chooses the relation label.
    final DependencyGraph maxentParse =
        maxentParser.parse(new String[] {"Run"}, new String[] {"VB"});
    assertEquals(DependencyGraph.of(new int[] {-1}, new String[] {"root"}), maxentParse);

    final DependencyGraph feedforwardParse =
        feedforwardParser.parse(new String[] {"Run"}, new String[] {"VB"});
    assertEquals(DependencyGraph.of(new int[] {-1}, new String[] {"root"}),
        feedforwardParse);
  }

  @Test
  void testNonProjectiveSamplesAreSkippedDuringTraining() throws IOException {
    // One non-projective sample joins the corpus; it cannot yield events, so training
    // proceeds on the remaining samples and still memorizes the projective sentences.
    final List<DependencySample> mixed = new ArrayList<>(corpus());
    mixed.add(nonProjectiveSample());
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    final DependencyModel model = DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(mixed), parameters);
    final DependencyParserME parser = new DependencyParserME(model);
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
            new String[] {"det", "nsubj", "root"}),
        parser.parse(new String[] {"the", "dog", "barks"},
            new String[] {"DT", "NN", "VBZ"}));
    assertEquals(DependencyGraph.of(new int[] {1, -1, 1},
            new String[] {"nsubj", "root", "obj"}),
        parser.parse(new String[] {"she", "eats", "fish"},
            new String[] {"PRP", "VBZ", "NN"}));
  }

  @Test
  void testNonProjectiveGoldDecodesToAProjectiveTree() {
    // The parser can only emit arc-standard derivations, so for a sentence whose gold
    // tree is non-projective the prediction is necessarily a different, projective tree.
    final DependencySample gold = nonProjectiveSample();
    final DependencyGraph parsed = maxentParser.parse(gold.getTokens(), gold.getTags());
    assertNotEquals(gold.getGraph(), parsed);
    assertEquals(0, crossingArcCount(parsed));
    // The unseen final token becomes the root and the verb attaches under it; the
    // familiar determiner and subject arcs survive from the training evidence.
    assertEquals(DependencyGraph.of(new int[] {1, 2, 3, -1},
        new String[] {"det", "nsubj", "nsubj", "root"}), parsed);
  }

  @Test
  void testFeedforwardTrainingFailsLoudWithoutProjectiveSamples() {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(8, 8, 1, 32, 0.05, 0.0, 0.0, 1, 17L);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.train(
            ObjectStreamUtils.createObjectStream(List.of(nonProjectiveSample())),
            settings));
    assertEquals("no trainable examples in the samples", e.getMessage());
  }

  @Test
  void testRefinementFailsLoudWithoutProjectiveSamples() {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 1, 32, 0.01, 0.0, 0.0, 1, 17L);
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(feedforwardModel,
            ObjectStreamUtils.createObjectStream(List.of(nonProjectiveSample())),
            settings, 2));
    assertEquals("no trainable samples for refinement", e.getMessage());
  }

  @Test
  void testMaxentModelFileRoundTripParsesIdentically(@TempDir Path dir)
      throws IOException {
    final Path file = dir.resolve("depparse.bin");
    maxentModel.serialize(file);
    final DependencyParserME reloaded = new DependencyParserME(new DependencyModel(file));
    for (final DependencySample sample : corpus()) {
      assertEquals(maxentParser.parse(sample.getTokens(), sample.getTags()),
          reloaded.parse(sample.getTokens(), sample.getTags()));
    }
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
            new String[] {"det", "nsubj", "root"}),
        reloaded.parse(new String[] {"the", "dog", "barks"},
            new String[] {"DT", "NN", "VBZ"}));
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
          reloaded.parse(sample.getTokens(), sample.getTags()));
    }
    assertEquals(DependencyGraph.of(new int[] {1, -1, 1},
            new String[] {"nsubj", "root", "obj"}),
        reloaded.parse(new String[] {"she", "eats", "fish"},
            new String[] {"PRP", "VBZ", "NN"}));
  }

  /**
   * Counts the pairs of crossing arcs in a graph, treating the root arc as spanning
   * from a virtual position left of the sentence to its dependent. A projective tree
   * has zero crossing pairs.
   *
   * @param graph The graph to inspect. Must not be {@code null}.
   * @return The number of crossing arc pairs.
   * @throws IllegalArgumentException Thrown if {@code graph} is {@code null}.
   */
  private static int crossingArcCount(DependencyGraph graph) {
    if (graph == null) {
      throw new IllegalArgumentException("graph must not be null");
    }
    int crossings = 0;
    for (int i = 0; i < graph.size(); i++) {
      for (int j = i + 1; j < graph.size(); j++) {
        final int iLow = Math.min(i, graph.headOf(i));
        final int iHigh = Math.max(i, graph.headOf(i));
        final int jLow = Math.min(j, graph.headOf(j));
        final int jHigh = Math.max(j, graph.headOf(j));
        if ((iLow < jLow && jLow < iHigh && iHigh < jHigh)
            || (jLow < iLow && iLow < jHigh && jHigh < iHigh)) {
          crossings++;
        }
      }
    }
    return crossings;
  }
}
