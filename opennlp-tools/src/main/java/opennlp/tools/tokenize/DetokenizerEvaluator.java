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


import java.util.ArrayList;

import opennlp.tools.cmdline.tokenizer.DetokenEvaluationErrorListener;
import opennlp.tools.util.Span;
import opennlp.tools.util.eval.Evaluator;
import opennlp.tools.util.eval.FMeasure;

/**
 * The {@link DetokenizerEvaluator} measures the performance of
 * the given {@link Detokenizer} with the provided reference
 * {@link TokenSample}s.
 *
 * @see DetokenizerEvaluator
 * @see Detokenizer
 * @see TokenSample
 */

public class DetokenizerEvaluator extends Evaluator<TokenSample> {
  private FMeasure fmeasure = new FMeasure();

  /**
   * The {@link Detokenizer} used to create the
   * predicted tokens.
   */
  private Detokenizer detokenizer;

  /**
   * Initializes the current instance with the
   * given {@link Detokenizer}.
   *
   * @param detokenizer the {@link Detokenizer} to evaluate.
   * @param listeners   evaluation sample listeners
   */
  public DetokenizerEvaluator(Detokenizer detokenizer, DetokenEvaluationErrorListener... listeners) {
    super(listeners);
    this.detokenizer = detokenizer;
  }

  @Override
  protected TokenSample processSample(TokenSample reference) {
    String[] tokens = Span.spansToStrings(reference.getTokenSpans(), reference.getText());
    String tokensstring = detokenizer.detokenize(tokens, null);

    ArrayList<String> predictionsArray = new ArrayList<>();
    ArrayList<String> referencesArray = new ArrayList<>();

    predictionsArray.add(tokensstring);
    referencesArray.add(reference.getText());

    Object[] references = referencesArray.toArray();
    Object[] predictions = predictionsArray.toArray();
    fmeasure.updateScores(references, predictions);

    return new TokenSample(tokensstring, reference.getTokenSpans());
  }

  public FMeasure getFMeasure() {
    return fmeasure;
  }
}
