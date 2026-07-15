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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStreamUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the pure-Java neural tier end to end: training on a tiny corpus must let the
 * greedy feedforward parser reproduce the training sentences, and a model must survive
 * the serialization round trip bit-for-bit in behavior.
 */
public class FeedforwardDependencyParserTest {

  private static FeedforwardDependencyModel model;
  private static FeedforwardDependencyParser parser;

  private static DependencySample sample(String[] tokens, String[] tags, int[] heads,
      String[] relations) {
    return new DependencySample(tokens, tags, DependencyGraph.of(heads, relations));
  }

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

  @BeforeAll
  static void trainParser() throws IOException {
    // dropout off so the tiny network memorizes deterministically
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 120, 32, 0.05, 0.0, 0.0, 1, 17L);
    model = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    parser = new FeedforwardDependencyParser(model);
  }

  @Test
  void testMemorizesTrainingSentences() {
    final DependencyGraph parsed = parser.parse(new String[] {"the", "dog", "barks"},
        new String[] {"DT", "NN", "VBZ"});
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), parsed);
  }

  @Test
  void testEvaluatorScoresPerfectlyOnTrainingData() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
  }

  @Test
  void testUnknownWordsStillYieldASingleRootedTree() {
    final DependencyGraph parsed = parser.parse(new String[] {"unseen", "words"},
        new String[] {"JJ", "NNS"});
    assertEquals(2, parsed.size());
    parsed.root();
  }

  @Test
  void testBeamOfOneMatchesGreedy() {
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, 1);
    for (final DependencySample sample : corpus()) {
      assertEquals(parser.parse(sample.getTokens(), sample.getTags()),
          beamed.parse(sample.getTokens(), sample.getTags()));
    }
  }

  @Test
  void testBeamedParserReproducesTrainingSentences() {
    final FeedforwardDependencyParser beamed = new FeedforwardDependencyParser(model, 4);
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
            new String[] {"det", "nsubj", "root"}),
        beamed.parse(new String[] {"the", "dog", "barks"},
            new String[] {"DT", "NN", "VBZ"}));
  }

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

  @Test
  void testBeamSizeValidation() {
    assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyParser(model, 0));
    assertThrows(IllegalArgumentException.class,
        () -> new FeedforwardDependencyParser(null, 4));
  }

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

  @Test
  void testCorruptModelFailsLoud() {
    assertThrows(IOException.class, () -> FeedforwardDependencyModel.load(
        new ByteArrayInputStream("not a model".getBytes())));
  }

  @Test
  void testSettingsValidation() {
    assertThrows(IllegalArgumentException.class, () -> new FeedforwardDependencyTrainer
        .Settings(0, 32, 10, 32, 0.05, 0.0, 0.0, 1, 17L));
    assertThrows(IllegalArgumentException.class, () -> new FeedforwardDependencyTrainer
        .Settings(16, 32, 10, 32, -1.0, 0.0, 0.0, 1, 17L));
    assertThrows(IllegalArgumentException.class, () -> new FeedforwardDependencyTrainer
        .Settings(16, 32, 10, 32, 0.05, 0.0, 1.0, 1, 17L));
  }

  @Test
  void testPretrainedSeedingAppliesAndValidates() throws IOException {
    // near-zero learning keeps the seeded row observable after one epoch
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(4, 8, 1, 32, 1e-9, 0.0, 0.0, 1, 17L);
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
  }

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
}
