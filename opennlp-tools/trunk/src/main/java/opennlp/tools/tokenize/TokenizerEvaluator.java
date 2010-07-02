/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import opennlp.tools.util.Span;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

/**
 * The {@link TokenizerEvaluator} measures the performance of
 * the given {@link Tokenizer} with the provided reference
 * {@link TokenSample}s.
 *
 * @see Evaluator
 * @see Tokenizer
 * @see TokenSample
 */
public class TokenizerEvaluator extends Evaluator<TokenSample> {

  private FMeasure fmeasure = new FMeasure();
  
  /**
   * The {@link Tokenizer} used to create the
   * predicted tokens.
   */
  private Tokenizer tokenizer;

  /**
   * Initializes the current instance with the
   * given {@link Tokenizer}.
   *
   * @param tokenizer the {@link Tokenizer} to evaluate.
   */
  public TokenizerEvaluator(Tokenizer tokenizer) {
    this.tokenizer = tokenizer;
  }

  /**
   * Evaluates the given reference {@link TokenSample} object.
   *
   * This is done by detecting the token spans with the
   * {@link Tokenizer}. The detected token spans are then
   * used to calculate calculate and update the scores.
   *
   * @param reference the reference {@link TokenSample}.
   */
  public void evaluateSample(TokenSample reference) {
    Span predictedSpans[] = tokenizer.tokenizePos(reference.getText());

    fmeasure.updateScores(reference.getTokenSpans(), predictedSpans);
  }
  
  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
