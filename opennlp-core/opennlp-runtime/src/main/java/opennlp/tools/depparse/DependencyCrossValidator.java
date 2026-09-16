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
import java.util.function.Predicate;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.Mean;

/**
 * Cross validator for a {@link DependencyParser}.
 *
 * <p>The samples are split into {@code k} folds. For each fold a parser is trained on the
 * other {@code k - 1} folds through the supplied {@link Trainer} and scored on the
 * held-out fold with a {@link DependencyEvaluator}. The unlabeled and labeled attachment
 * scores are accumulated over all evaluated tokens, so every token of the input counts
 * once, in the fold it was held out from. Both parser implementations of this package can
 * be validated, because the trainer is supplied by the caller.</p>
 *
 * @see DependencyEvaluator
 * @see CrossValidationPartitioner
 * @since 3.0.0
 */
public class DependencyCrossValidator {

  /** The smallest fold count that leaves training data for every fold. */
  private static final int MIN_FOLDS = 2;

  /**
   * Trains a {@link DependencyParser} on the samples of the folds that are not held out.
   */
  @FunctionalInterface
  public interface Trainer {

    /**
     * Trains a parser.
     *
     * @param samples The training samples of the current fold. Never {@code null}; the
     *                stream is read once and not closed by the validator.
     * @return The trained parser. Must not be {@code null}.
     * @throws IOException Thrown if reading the samples or training fails.
     */
    DependencyParser train(ObjectStream<DependencySample> samples) throws IOException;
  }

  private final Trainer trainer;
  private final Predicate<String> punctuationTag;
  private final Mean uas = new Mean();
  private final Mean las = new Mean();
  private final Mean uasExcludingPunctuation = new Mean();
  private final Mean lasExcludingPunctuation = new Mean();

  /**
   * Initializes a {@link DependencyCrossValidator} that treats tokens tagged
   * {@link DependencyEvaluator#UNIVERSAL_PUNCTUATION_TAG} as punctuation.
   *
   * @param trainer Trains the parser of each fold. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code trainer} is {@code null}.
   */
  public DependencyCrossValidator(Trainer trainer) {
    this(trainer, DependencyEvaluator.UNIVERSAL_PUNCTUATION_TAG::equals);
  }

  /**
   * Initializes a {@link DependencyCrossValidator} with a custom notion of punctuation.
   *
   * @param trainer Trains the parser of each fold. Must not be {@code null}.
   * @param punctuationTag Decides from a gold part-of-speech tag whether the token is
   *                       punctuation and therefore left out of the punctuation-free
   *                       scores. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public DependencyCrossValidator(Trainer trainer, Predicate<String> punctuationTag) {
    if (trainer == null) {
      throw new IllegalArgumentException("trainer must not be null");
    }
    if (punctuationTag == null) {
      throw new IllegalArgumentException("punctuationTag must not be null");
    }
    this.trainer = trainer;
    this.punctuationTag = punctuationTag;
  }

  /**
   * Runs the cross validation and adds the scores of every fold to the totals.
   *
   * @param samples The samples to train and test with. Must not be {@code null} and must
   *                support {@link ObjectStream#reset()}, because every fold reads the
   *                stream from the start. The stream is not closed.
   * @param folds The number of folds. Must be at least {@code 2}.
   * @throws IOException Thrown if reading the samples or training fails.
   * @throws IllegalArgumentException Thrown if {@code samples} is {@code null} or
   *         {@code folds} is below {@code 2}.
   * @throws IllegalStateException Thrown if the trainer returns {@code null}.
   */
  public void evaluate(ObjectStream<DependencySample> samples, int folds) throws IOException {
    if (samples == null) {
      throw new IllegalArgumentException("samples must not be null");
    }
    if (folds < MIN_FOLDS) {
      throw new IllegalArgumentException("folds must be at least " + MIN_FOLDS + ": " + folds);
    }
    final CrossValidationPartitioner<DependencySample> partitioner =
        new CrossValidationPartitioner<>(samples, folds);
    int fold = 0;
    while (partitioner.hasNext()) {
      final CrossValidationPartitioner.TrainingSampleStream<DependencySample> training =
          partitioner.next();
      final DependencyParser parser = trainer.train(training);
      if (parser == null) {
        throw new IllegalStateException("trainer returned null for fold " + fold);
      }
      final DependencyEvaluator evaluator = new DependencyEvaluator(parser, punctuationTag);
      evaluator.evaluate(training.getTestSampleStream());
      uas.add(evaluator.getUas(), evaluator.getWordCount());
      las.add(evaluator.getLas(), evaluator.getWordCount());
      uasExcludingPunctuation.add(evaluator.getUasExcludingPunctuation(),
          evaluator.getWordCountExcludingPunctuation());
      lasExcludingPunctuation.add(evaluator.getLasExcludingPunctuation(),
          evaluator.getWordCountExcludingPunctuation());
      fold++;
    }
  }

  /**
   * @return The unlabeled attachment score over all tokens evaluated so far.
   */
  public double getUas() {
    return uas.mean();
  }

  /**
   * @return The labeled attachment score over all tokens evaluated so far.
   */
  public double getLas() {
    return las.mean();
  }

  /**
   * @return The number of tokens evaluated so far; over one complete run this is the
   *         number of tokens in the samples, because every token is held out once.
   */
  public long getWordCount() {
    return uas.count();
  }

  /**
   * @return The unlabeled attachment score over the evaluated tokens that are not
   *         punctuation.
   */
  public double getUasExcludingPunctuation() {
    return uasExcludingPunctuation.mean();
  }

  /**
   * @return The labeled attachment score over the evaluated tokens that are not
   *         punctuation.
   */
  public double getLasExcludingPunctuation() {
    return lasExcludingPunctuation.mean();
  }

  /**
   * @return The number of evaluated tokens that are not punctuation.
   */
  public long getWordCountExcludingPunctuation() {
    return uasExcludingPunctuation.count();
  }
}
