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

package opennlp.tools.tokenize;

import java.io.IOException;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.CrossValidationPartitioner;
import opennlp.tools.util.eval.FMeasure;

/**
 * A cross validator for {@link Tokenizer tokenizers}.
 */
public class TokenizerCrossValidator {

  private final TrainingParameters params;

  private final FMeasure fmeasure = new FMeasure();
  private final TokenizerEvaluationMonitor[] listeners;
  private final TokenizerFactory factory;

  /**
   * Creates a {@link TokenizerCrossValidator} using the given {@link TokenizerFactory}.
   *
   * @param params The {@link TrainingParameters} for the context of cross validation.
   * @param factory The {@link TokenizerFactory} to be used.
   * @param listeners The {@link TokenizerEvaluationMonitor evaluation listeners}.
   */
  public TokenizerCrossValidator(TrainingParameters params,
      TokenizerFactory factory, TokenizerEvaluationMonitor... listeners) {
    this.params = params;
    this.listeners = listeners;
    this.factory = factory;
  }

  /**
   * Starts the evaluation.
   *
   * @param samples The {@link ObjectStream} of {@link TokenSample samples} to train and test with.
   * @param nFolds Number of folds. It must be greater than zero.
   *
   * @throws IOException Thrown if IO errors occurred during evaluation.
   */
  public void evaluate(ObjectStream<TokenSample> samples, int nFolds) throws IOException {

    CrossValidationPartitioner<TokenSample> partitioner =
        new CrossValidationPartitioner<>(samples, nFolds);

    while (partitioner.hasNext()) {

      CrossValidationPartitioner.TrainingSampleStream<TokenSample> trainingSampleStream =
          partitioner.next();

      // Maybe throws IOException if temporary file handling fails ...
      TokenizerModel model = TokenizerME.train(trainingSampleStream, this.factory, params);

      TokenizerEvaluator evaluator = new TokenizerEvaluator(new TokenizerME(model), listeners);

      evaluator.evaluate(trainingSampleStream.getTestSampleStream());
      fmeasure.mergeInto(evaluator.getFMeasure());
    }
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
