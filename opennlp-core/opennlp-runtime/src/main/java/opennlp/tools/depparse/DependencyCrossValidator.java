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

import opennlp.tools.util.ArgumentChecks;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;

/**
 * Cross validator for {@link DependencyParserME}.
 *
 * <p>The samples are split into {@code k} folds. For each fold a parser is trained with
 * {@link DependencyParserME#train} on the other {@code k - 1} folds and scored on the
 * held-out fold with a {@link DependencyEvaluator}. The unlabeled and labeled attachment
 * scores are accumulated over all evaluated tokens, so every token of the input counts
 * once, in the fold it was held out from. The listeners given at construction are told
 * about each held-out sample.</p>
 *
 * @see DependencyEvaluator
 * @see CrossValidationPartitioner
 * @since 3.0.0
 */
public class DependencyCrossValidator {

  /** The smallest fold count that leaves training data for every fold. */
  private static final int MIN_FOLDS = 2;

  private final String languageCode;
  private final TrainingParameters params;
  private final Predicate<String> punctuationTag;
  private final DependencyEvaluationMonitor[] listeners;
  private final AttachmentScores scores = new AttachmentScores();

  /**
   * Initializes a {@link DependencyCrossValidator} that treats tokens tagged
   * {@link DependencyEvaluator#UNIVERSAL_PUNCTUATION_TAG} as punctuation.
   *
   * @param languageCode The ISO language code of the samples. Must not be {@code null}.
   * @param params The {@link TrainingParameters} for the parser of each fold. Must not be
   *               {@code null}.
   * @param listeners The {@link DependencyEvaluationMonitor listeners} told about each
   *                  held-out sample; {@code null} entries are ignored.
   * @throws IllegalArgumentException Thrown if {@code languageCode} or {@code params}
   *         is {@code null}.
   */
  public DependencyCrossValidator(String languageCode, TrainingParameters params,
      DependencyEvaluationMonitor... listeners) {
    this(languageCode, params, DependencyEvaluator.UNIVERSAL_PUNCTUATION_TAG::equals,
        listeners);
  }

  /**
   * Initializes a {@link DependencyCrossValidator} with a custom notion of punctuation.
   *
   * @param languageCode The ISO language code of the samples. Must not be {@code null}.
   * @param params The {@link TrainingParameters} for the parser of each fold. Must not be
   *               {@code null}.
   * @param punctuationTag Decides from a gold part-of-speech tag whether the token is
   *                       punctuation and therefore left out of the punctuation-free
   *                       scores. Must not be {@code null}.
   * @param listeners The {@link DependencyEvaluationMonitor listeners} told about each
   *                  held-out sample; {@code null} entries are ignored.
   * @throws IllegalArgumentException Thrown if {@code languageCode}, {@code params} or
   *         {@code punctuationTag} is {@code null}.
   */
  public DependencyCrossValidator(String languageCode, TrainingParameters params,
      Predicate<String> punctuationTag, DependencyEvaluationMonitor... listeners) {
    ArgumentChecks.requireNonNullArg(languageCode, "languageCode");
    ArgumentChecks.requireNonNullArg(params, "params");
    ArgumentChecks.requireNonNullArg(punctuationTag, "punctuationTag");
    this.languageCode = languageCode;
    this.params = params;
    this.punctuationTag = punctuationTag;
    this.listeners = listeners == null ? new DependencyEvaluationMonitor[0] : listeners.clone();
  }

  /**
   * Runs the cross validation and adds the scores of every fold to the totals.
   *
   * @param samples The samples to train and test with. Must not be {@code null} and must
   *                support {@link ObjectStream#reset()}, because every fold reads the
   *                stream from the start. The stream is not closed.
   * @param folds The number of folds. Must be at least {@code 2}.
   * @throws IOException Thrown if reading the samples fails.
   * @throws IllegalArgumentException Thrown if {@code samples} is {@code null},
   *         {@code folds} is below {@code 2}, or the training parameters do not select
   *         an event model trainer.
   */
  public void evaluate(ObjectStream<DependencySample> samples, int folds) throws IOException {
    ArgumentChecks.requireNonNullArg(samples, "samples");
    if (folds < MIN_FOLDS) {
      throw new IllegalArgumentException("folds must be at least " + MIN_FOLDS + ": " + folds);
    }
    final CrossValidationPartitioner<DependencySample> partitioner =
        new CrossValidationPartitioner<>(samples, folds);
    while (partitioner.hasNext()) {
      final CrossValidationPartitioner.TrainingSampleStream<DependencySample> training =
          partitioner.next();
      final DependencyModel model = DependencyParserME.train(languageCode, training, params);
      final DependencyEvaluator evaluator =
          new DependencyEvaluator(new DependencyParserME(model), punctuationTag, listeners);
      evaluator.evaluate(training.getTestSampleStream());
      scores.add(evaluator.scores());
    }
  }

  /**
   * @return The unlabeled attachment score over all tokens evaluated so far.
   */
  public double getUas() {
    return scores.getUas();
  }

  /**
   * @return The labeled attachment score over all tokens evaluated so far.
   */
  public double getLas() {
    return scores.getLas();
  }

  /**
   * @return The number of tokens evaluated so far; over one complete run this is the
   *         number of tokens in the samples, because every token is held out once.
   */
  public long getWordCount() {
    return scores.getWordCount();
  }

  /**
   * @return The unlabeled attachment score over the evaluated tokens that are not
   *         punctuation.
   */
  public double getUasExcludingPunctuation() {
    return scores.getUasExcludingPunctuation();
  }

  /**
   * @return The labeled attachment score over the evaluated tokens that are not
   *         punctuation.
   */
  public double getLasExcludingPunctuation() {
    return scores.getLasExcludingPunctuation();
  }

  /**
   * @return The number of evaluated tokens that are not punctuation.
   */
  public long getWordCountExcludingPunctuation() {
    return scores.getWordCountExcludingPunctuation();
  }
}
