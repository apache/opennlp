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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.StringUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
  void testRefinementKeepsToyPerformance() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 60, 32, 0.05, 0.0, 0.0, 1, 17L);
    final FeedforwardDependencyModel local = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    final FeedforwardDependencyTrainer.Settings refineSettings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 2, 32, 0.01, 0.0, 0.0, 1, 17L);
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        local, ObjectStreamUtils.createObjectStream(corpus()), refineSettings, 2);

    final DependencyEvaluator evaluator =
        new DependencyEvaluator(new FeedforwardDependencyParser(refined, 2));
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
  }

  @Test
  void testRefineWithAnUnknownRelationFailsLoud() throws IOException {
    // A refinement corpus may carry a relation label the original training set never
    // used; the transition inventory is fixed at training time, so refinement cannot
    // score it and must say which transition it does not know.
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 1, 32, 0.01, 0.0, 0.0, 1, 17L);
    final List<DependencySample> unseenRelation = List.of(
        sample(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"det", "dislocated", "root"}));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> FeedforwardDependencyTrainer.refine(model,
            ObjectStreamUtils.createObjectStream(unseenRelation), settings, 2));
    assertEquals("unknown transition in the refinement samples: LEFT_ARC:dislocated",
        e.getMessage());
  }

  @Test
  void testRefineReturnsANewModelAndLeavesTheOriginalUntouched() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 60, 32, 0.05, 0.0, 0.0, 1, 17L);
    final FeedforwardDependencyModel local = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    final String[] tokens = {"the", "dog", "barks"};
    final String[] tags = {"DT", "NN", "VBZ"};
    final int[] features = local.featureIds(
        FeedforwardContext.extract(new ArcStandardState(tokens.length), tokens, tags));
    final double[] before = local.score(features);

    final FeedforwardDependencyTrainer.Settings refineSettings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 2, 32, 0.01, 0.0, 0.0, 1, 17L);
    final FeedforwardDependencyModel refined = FeedforwardDependencyTrainer.refine(
        local, ObjectStreamUtils.createObjectStream(corpus()), refineSettings, 2);

    // refinement produces a distinct model, so a model already shared between threads
    // cannot change underneath them
    assertNotSame(local, refined);
    assertArrayEquals(before, local.score(features));
    // and the returned model really carries the refinement
    assertFalse(Arrays.equals(before, refined.score(features)));
  }

  /**
   * Pins the one contextual rule layered over the per-code-point mapping: a Greek
   * capital sigma preceded by a letter and not followed by one lowercases to the
   * final form U+03C2, the way natural lowercase Greek spells it and the way every
   * treebank-derived vocabulary key spells it, so an uppercase Greek word normalizes
   * to a key the vocabulary can actually contain. The plain per-code-point mapping
   * would produce the medial sigma there and miss the vocabulary.
   */
  @Test
  void testNormalizeAppliesTheFinalSigmaRule() {
    // ODOS, road, all caps: the trailing sigma position lowers to U+03C2
    assertEquals("\u03BF\u03B4\u03BF\u03C2",
        FeedforwardDependencyModel.normalize("\u039F\u0394\u039F\u03A3"));
    // SOFIA: the word-initial sigma is not final and lowers to the medial U+03C3
    assertEquals("\u03C3\u03BF\u03C6\u03B9\u03B1",
        FeedforwardDependencyModel.normalize("\u03A3\u039F\u03A6\u0399\u0391"));
    // a lone capital sigma has no preceding letter, so the rule does not fire
    assertEquals("\u03C3", FeedforwardDependencyModel.normalize("\u03A3"));
  }

  /**
   * Pins the allocation-free fast path: a word the mapping leaves unchanged, the
   * overwhelming majority of parse-time input, is returned as the same instance
   * rather than a fresh copy built on every lookup of the scoring loop.
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
   * Pins the refinement contract for the sample kind the unknown-transition check
   * never sees: a non-projective gold graph has no arc-standard derivation and is
   * skipped before the transition inventory is consulted, so an unknown relation
   * riding on it must not trigger the unknown-transition failure, and refinement
   * proceeds on the remaining projective samples.
   */
  @Test
  void testNonProjectiveSampleWithUnknownRelationIsSkippedNotFatal() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(16, 32, 1, 32, 0.01, 0.0, 0.0, 1, 17L);
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
   * Pins the serialized vocabulary order: entries are written in ascending id order,
   * not in the iteration order of the underlying immutable maps, which the JDK salts
   * per launch, so serializing the same model produces the same bytes on every run.
   */
  @Test
  void testSerializedVocabulariesAreWrittenInAscendingIdOrder() throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.serialize(out);
    try (DataInputStream data = new DataInputStream(
        new ByteArrayInputStream(out.toByteArray()))) {
      data.readUTF();
      for (int vocab = 0; vocab < 3; vocab++) {
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
   * Pins the scoring cache against the direct path: the parser built in setup turned
   * the cache on for the shared model, so scoring the same configuration through a
   * fresh uncached copy must agree to float rounding, and the winning transition must
   * be identical. Repeated scoring exercises the cache-hit path as well as the
   * first-sight fill.
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
      assertEquals(direct.length, cached.length);
      int bestCached = 0;
      int bestDirect = 0;
      for (int o = 0; o < cached.length; o++) {
        assertEquals(direct[o], cached[o],
            Math.max(1.0e-6, Math.abs(direct[o]) * 1.0e-6),
            "score " + o + " must agree to float rounding");
        if (cached[o] > cached[bestCached]) {
          bestCached = o;
        }
        if (direct[o] > direct[bestDirect]) {
          bestDirect = o;
        }
      }
      assertEquals(bestDirect, bestCached, "the winning transition must be identical");
    }
  }

  @Test
  void testNormalizeUsesTheUnicodeDataCaseMapping() {
    // StringUtil maps per code point via UnicodeData, so no character expands; the JDK's
    // String.toLowerCase would render this word as "i" + COMBINING DOT ABOVE instead.
    assertEquals(StringUtil.toLowerCase("\u0130STANBUL"),
        FeedforwardDependencyModel.normalize("\u0130STANBUL"));
    assertEquals("istanbul", FeedforwardDependencyModel.normalize("\u0130STANBUL"));
    // special symbols still pass through untouched
    assertEquals(FeedforwardDependencyModel.UNKNOWN,
        FeedforwardDependencyModel.normalize(FeedforwardDependencyModel.UNKNOWN));
    assertNull(FeedforwardDependencyModel.normalize(null));
  }

  @Test
  void testRefineValidation() {
    final FeedforwardDependencyTrainer.Settings settings =
        FeedforwardDependencyTrainer.Settings.defaults();
    assertThrows(IllegalArgumentException.class, () -> FeedforwardDependencyTrainer
        .refine(null, ObjectStreamUtils.createObjectStream(corpus()), settings, 4));
    assertThrows(IllegalArgumentException.class, () -> FeedforwardDependencyTrainer
        .refine(model, ObjectStreamUtils.createObjectStream(corpus()), settings, 1));
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
