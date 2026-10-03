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
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStreamUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the scores of {@link DependencyEvaluator} against a parser with a known error
 * pattern, in particular the split between all tokens and the tokens that are not
 * punctuation.
 */
public class DependencyEvaluatorTest {

  private static final String[] TOKENS = {"dogs", "bark", "loudly", "."};
  private static final String[] UNIVERSAL_TAGS = {"NOUN", "VERB", "ADV", "PUNCT"};
  private static final String[] PENN_TAGS = {"NNS", "VBP", "RB", "."};

  private static final DependencyGraph GOLD = DependencyGraph.of(
      new int[] {1, -1, 1, 1}, new String[] {"nsubj", "root", "advmod", "punct"});

  /** Right heads throughout, but a wrong label on the adverb and a wrong head on the period. */
  private static final DependencyGraph PREDICTED = DependencyGraph.of(
      new int[] {1, -1, 1, 2}, new String[] {"nsubj", "root", "obl", "punct"});

  /**
   * Evaluates the fixed prediction against the gold tree with the given tags.
   *
   * @param evaluator The evaluator under test.
   * @param tags The gold tags of the fixture sentence.
   * @throws IOException Thrown if reading the in-memory sample fails.
   */
  private static void evaluate(DependencyEvaluator evaluator, String[] tags) throws IOException {
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(
        List.of(new DependencySample(TOKENS, tags, GOLD))));
  }

  @Test
  void testScoresAllTokensAndNonPunctuationSeparately() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator((tokens, tags) -> PREDICTED);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(4, evaluator.getWordCount());
    assertEquals(0.75d, evaluator.getUas());
    assertEquals(0.5d, evaluator.getLas());
    assertEquals(3, evaluator.getWordCountExcludingPunctuation());
    assertEquals(1.0d, evaluator.getUasExcludingPunctuation());
    assertEquals(2.0d / 3.0d, evaluator.getLasExcludingPunctuation(), 1e-12);
  }

  @Test
  void testDefaultPunctuationIsTheUniversalTag() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator((tokens, tags) -> PREDICTED);
    evaluate(evaluator, PENN_TAGS);
    assertEquals(4, evaluator.getWordCountExcludingPunctuation(),
        "a Penn tag set has no PUNCT tag, so nothing is excluded");
    assertEquals(evaluator.getUas(), evaluator.getUasExcludingPunctuation());
    assertEquals(evaluator.getLas(), evaluator.getLasExcludingPunctuation());
  }

  @Test
  void testCustomPunctuationPredicate() throws IOException {
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> PREDICTED, "."::equals);
    evaluate(evaluator, PENN_TAGS);
    assertEquals(3, evaluator.getWordCountExcludingPunctuation());
    assertEquals(1.0d, evaluator.getUasExcludingPunctuation());
  }

  @Test
  void testEmptyEvaluatorScoresZero() {
    final DependencyEvaluator evaluator = new DependencyEvaluator((tokens, tags) -> PREDICTED);
    assertEquals(0, evaluator.getWordCount());
    assertEquals(0, evaluator.getWordCountExcludingPunctuation());
    assertEquals(0.0d, evaluator.getUas());
    assertEquals(0.0d, evaluator.getUasExcludingPunctuation());
  }

  @Test
  void testListenersHearMisparsedSamples() throws IOException {
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> PREDICTED, monitor);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(List.of(), monitor.correct);
    assertEquals(List.of(new DependencySample(TOKENS, UNIVERSAL_TAGS, GOLD)), monitor.wrong);
    assertEquals(List.of(new DependencySample(TOKENS, UNIVERSAL_TAGS, PREDICTED)),
        monitor.predictions);
  }

  @Test
  void testListenersHearCorrectSamplesWithCustomPunctuation() throws IOException {
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> GOLD, "."::equals, monitor);
    evaluate(evaluator, PENN_TAGS);
    assertEquals(List.of(new DependencySample(TOKENS, PENN_TAGS, GOLD)), monitor.correct);
    assertEquals(List.of(), monitor.wrong);
    assertEquals(3, evaluator.getWordCountExcludingPunctuation());
  }

  @Test
  void testNullListenersAreIgnored() throws IOException {
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator = new DependencyEvaluator(
        (tokens, tags) -> PREDICTED, (DependencyEvaluationMonitor) null, monitor);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(1, monitor.wrong.size());
    assertEquals(4, evaluator.getWordCount());
  }

  /** A {@code null} listener array means no listeners, like the base evaluator. */
  @Test
  void testNullListenerArrayMeansNoListeners() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(
        (tokens, tags) -> PREDICTED, (DependencyEvaluationMonitor[]) null);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(4, evaluator.getWordCount());
    final DependencyEvaluator withPredicate = new DependencyEvaluator(
        (tokens, tags) -> PREDICTED, "."::equals, (DependencyEvaluationMonitor[]) null);
    evaluate(withPredicate, PENN_TAGS);
    assertEquals(3, withPredicate.getWordCountExcludingPunctuation());
  }

  /** The same listener given twice is told twice per sample; listeners are not deduplicated. */
  @Test
  void testDuplicateListenerIsToldTwice() throws IOException {
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> PREDICTED, monitor, monitor);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(2, monitor.wrong.size());
    assertEquals(4, evaluator.getWordCount(), "the sample itself is scored once");
  }

  /** A throwing listener aborts the evaluation after the sample has been scored. */
  @Test
  void testThrowingListenerAbortsAfterScoring() {
    final IllegalStateException failure = new IllegalStateException("listener failed");
    final DependencyEvaluationMonitor throwing = new DependencyEvaluationMonitor() {
      @Override
      public void correctlyClassified(DependencySample reference, DependencySample prediction) {
        throw failure;
      }

      @Override
      public void misclassified(DependencySample reference, DependencySample prediction) {
        throw failure;
      }
    };
    final RecordingMonitor later = new RecordingMonitor();
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> PREDICTED, throwing, later);
    final IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> evaluate(evaluator, UNIVERSAL_TAGS));
    assertSame(failure, thrown);
    assertEquals(4, evaluator.getWordCount());
    assertEquals(0, later.wrong.size(), "listeners after the failing one are not told");
  }

  /** A predicate that treats every token as punctuation leaves the punctuation-free scores at zero. */
  @Test
  void testAllTokensPunctuationLeavesPunctuationFreeScoresAtZero() throws IOException {
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> PREDICTED, tag -> true);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(4, evaluator.getWordCount());
    assertEquals(0.75d, evaluator.getUas());
    assertEquals(0, evaluator.getWordCountExcludingPunctuation());
    assertEquals(0.0d, evaluator.getUasExcludingPunctuation(), "no division by zero");
    assertEquals(0.0d, evaluator.getLasExcludingPunctuation());
  }

  /** An empty sample stream scores zero tokens and tells no listener anything. */
  @Test
  void testEmptyStreamScoresZeroAndTellsNoListener() throws IOException {
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> PREDICTED, monitor);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(List.<DependencySample>of()));
    assertEquals(0, evaluator.getWordCount());
    assertEquals(0.0d, evaluator.getUas());
    assertEquals(0.0d, evaluator.getLas());
    assertEquals(0.0d, evaluator.getUasExcludingPunctuation());
    assertEquals(0.0d, evaluator.getLasExcludingPunctuation());
    assertEquals(0, monitor.correct.size() + monitor.wrong.size());
  }

  /** A one-token sentence parsed as the root scores one correct token. */
  @Test
  void testSingleTokenSentence() throws IOException {
    final DependencyGraph root = DependencyGraph.of(new int[] {-1}, new String[] {"root"});
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator = new DependencyEvaluator((tokens, tags) -> root, monitor);
    final DependencySample sample = new DependencySample(new String[] {"Run"}, new String[] {"VERB"}, root);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(List.of(sample)));
    assertEquals(1, evaluator.getWordCount());
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
    assertEquals(List.of(sample), monitor.correct);
  }

  /** A prediction that differs from the gold tree only in a label is misparsed for the listeners. */
  @Test
  void testLabelOnlyDifferenceIsMisparsed() throws IOException {
    final DependencyGraph relabeled = DependencyGraph.of(
        new int[] {1, -1, 1, 1}, new String[] {"nsubj", "root", "obl", "punct"});
    final RecordingMonitor monitor = new RecordingMonitor();
    final DependencyEvaluator evaluator =
        new DependencyEvaluator((tokens, tags) -> relabeled, monitor);
    evaluate(evaluator, UNIVERSAL_TAGS);
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(0.75d, evaluator.getLas());
    assertEquals(List.of(new DependencySample(TOKENS, UNIVERSAL_TAGS, GOLD)), monitor.wrong);
    assertEquals(List.of(new DependencySample(TOKENS, UNIVERSAL_TAGS, relabeled)),
        monitor.predictions);
  }

  @Test
  void testRejectsNullArguments() {
    assertThrows(IllegalArgumentException.class, () -> new DependencyEvaluator(null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyEvaluator(null, DependencyEvaluator.UNIVERSAL_PUNCTUATION_TAG::equals));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyEvaluator((tokens, tags) -> PREDICTED, (Predicate<String>) null));
  }
}
