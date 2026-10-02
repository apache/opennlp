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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DependencyCrossValidator}: the folds it trains, the tokens it counts, its
 * argument checks, and its agreement with a single {@link DependencyEvaluator} on a corpus
 * every fold can memorize.
 */
public class DependencyCrossValidatorTest {

  /** The tokens in {@link DependencyTestSamples#corpus()}: 40 repetitions of 3 + 2 + 3. */
  private static final int CORPUS_WORDS = 320;

  /** The sentences in {@link DependencyTestSamples#corpus()}. */
  private static final int CORPUS_SENTENCES = 120;

  /**
   * Trains the maximum-entropy transition parser with a zero cutoff so the small test
   * corpus keeps every feature.
   *
   * @param samples The training samples.
   * @return The trained parser. Never {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   */
  private static DependencyParser trainTransitionParser(ObjectStream<DependencySample> samples)
      throws IOException {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    return new DependencyParserME(DependencyParserME.train("eng", samples, parameters));
  }

  @ParameterizedTest(name = "folds = {0}")
  @ValueSource(ints = {2, 3, 5, 8})
  void testTrainsOneParserPerFoldOnTheOtherFolds(int folds) throws IOException {
    final List<Integer> trainingSizes = new ArrayList<>();
    final DependencyCrossValidator validator = new DependencyCrossValidator(samples -> {
      trainingSizes.add(count(samples));
      return trainTransitionParser(ObjectStreamUtils.createObjectStream(corpus()));
    });
    validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), folds);
    assertEquals(folds, trainingSizes.size());
    int heldOut = 0;
    for (int size : trainingSizes) {
      heldOut += CORPUS_SENTENCES - size;
    }
    assertEquals(CORPUS_SENTENCES, heldOut, "every sentence is held out exactly once");
  }

  @Test
  void testCountsEveryTokenOnce() throws IOException {
    final DependencyCrossValidator validator =
        new DependencyCrossValidator(DependencyCrossValidatorTest::trainTransitionParser);
    validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), 4);
    assertEquals(CORPUS_WORDS, validator.getWordCount());
    assertEquals(CORPUS_WORDS, validator.getWordCountExcludingPunctuation(),
        "the corpus has no punctuation, so no token is left out");
  }

  @Test
  void testAgreesWithSingleEvaluatorOnMemorizableCorpus() throws IOException {
    final DependencyCrossValidator validator =
        new DependencyCrossValidator(DependencyCrossValidatorTest::trainTransitionParser);
    validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), 4);

    final DependencyEvaluator evaluator = new DependencyEvaluator(
        trainTransitionParser(ObjectStreamUtils.createObjectStream(corpus())));
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));

    assertEquals(evaluator.getWordCount(), validator.getWordCount());
    assertEquals(evaluator.getUas(), validator.getUas());
    assertEquals(evaluator.getLas(), validator.getLas());
    assertEquals(evaluator.getUasExcludingPunctuation(),
        validator.getUasExcludingPunctuation());
    assertEquals(evaluator.getLasExcludingPunctuation(),
        validator.getLasExcludingPunctuation());
    assertEquals(1.0d, validator.getUas());
    assertEquals(1.0d, validator.getLas());
  }

  @Test
  void testAccumulatesAcrossRuns() throws IOException {
    final DependencyCrossValidator validator =
        new DependencyCrossValidator(DependencyCrossValidatorTest::trainTransitionParser);
    validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), 2);
    validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), 2);
    assertEquals(2 * CORPUS_WORDS, validator.getWordCount());
  }

  @Test
  void testScoresPunctuationSeparately() throws IOException {
    final DependencySample punctuated = DependencyTestSamples.sample(
        new String[] {"dogs", "bark", "."}, new String[] {"NOUN", "VERB", "PUNCT"},
        new int[] {1, -1, 1}, new String[] {"nsubj", "root", "punct"});
    final DependencyGraph misattachedPunctuation = DependencyGraph.of(
        new int[] {1, -1, 0}, new String[] {"nsubj", "root", "punct"});
    final DependencyCrossValidator validator =
        new DependencyCrossValidator(samples -> (tokens, tags) -> misattachedPunctuation);
    validator.evaluate(ObjectStreamUtils.createObjectStream(
        List.of(punctuated, punctuated)), 2);
    assertEquals(6, validator.getWordCount());
    assertEquals(4, validator.getWordCountExcludingPunctuation());
    assertEquals(2.0d / 3.0d, validator.getUas(), 1e-12);
    assertEquals(2.0d / 3.0d, validator.getLas(), 1e-12);
    assertEquals(1.0d, validator.getUasExcludingPunctuation());
    assertEquals(1.0d, validator.getLasExcludingPunctuation());
  }

  @Test
  void testEmptyValidatorScoresZero() {
    final DependencyCrossValidator validator =
        new DependencyCrossValidator(DependencyCrossValidatorTest::trainTransitionParser);
    assertEquals(0, validator.getWordCount());
    assertEquals(0.0d, validator.getUas());
    assertEquals(0.0d, validator.getLas());
  }

  @ParameterizedTest(name = "folds = {0}")
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1})
  void testRejectsFoldCountBelowTwo(int folds) {
    final AtomicInteger trainings = new AtomicInteger();
    final DependencyCrossValidator validator = new DependencyCrossValidator(samples -> {
      trainings.incrementAndGet();
      return trainTransitionParser(samples);
    });
    assertThrows(IllegalArgumentException.class,
        () -> validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), folds));
    assertEquals(0, trainings.get(), "nothing is trained when the fold count is invalid");
  }

  @Test
  void testRejectsNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> new DependencyCrossValidator(null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyCrossValidator(null, DependencyEvaluator.UNIVERSAL_PUNCTUATION_TAG::equals));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyCrossValidator(DependencyCrossValidatorTest::trainTransitionParser, null));
    final DependencyCrossValidator validator =
        new DependencyCrossValidator(DependencyCrossValidatorTest::trainTransitionParser);
    assertThrows(IllegalArgumentException.class, () -> validator.evaluate(null, 2));
  }

  @Test
  void testRejectsTrainerReturningNull() {
    final DependencyCrossValidator validator = new DependencyCrossValidator(samples -> null);
    assertThrows(IllegalStateException.class,
        () -> validator.evaluate(ObjectStreamUtils.createObjectStream(corpus()), 2));
  }

  /**
   * Drains a stream and counts its samples.
   *
   * @param samples The stream to drain.
   * @return The number of samples read.
   * @throws IOException Thrown if reading fails.
   */
  private static int count(ObjectStream<DependencySample> samples) throws IOException {
    int count = 0;
    while (samples.read() != null) {
      count++;
    }
    return count;
  }
}
