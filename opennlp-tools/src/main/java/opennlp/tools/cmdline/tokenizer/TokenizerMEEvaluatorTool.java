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

package opennlp.tools.cmdline.tokenizer;

import java.io.IOException;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.cmdline.tokenizer.TokenizerMEEvaluatorTool.EvalToolParams;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerEvaluationMonitor;
import opennlp.tools.tokenize.TokenizerEvaluator;
import opennlp.tools.tokenize.TokenizerModel;

public final class TokenizerMEEvaluatorTool
    extends AbstractEvaluatorTool<TokenSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams {
  }

  public TokenizerMEEvaluatorTool() {
    super(TokenSample.class, EvalToolParams.class);
  }

  public String getShortDescription() {
    return "evaluator for the learnable tokenizer";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    TokenizerModel model = new TokenizerModelLoader().load(params.getModel());

    TokenizerEvaluationMonitor misclassifiedListener = null;
    if (params.getMisclassified()) {
      misclassifiedListener = new TokenEvaluationErrorListener();
    }

    TokenizerEvaluator evaluator = new TokenizerEvaluator(
        new opennlp.tools.tokenize.TokenizerME(model), misclassifiedListener);

    System.out.print("Evaluating ... ");

    try {
      evaluator.evaluate(sampleStream);
    } catch (IOException e) {
      System.err.println("failed");
      throw new TerminateToolException(-1, "IO error while reading test data: " + e.getMessage(), e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.out.println("done");

    System.out.println();

    System.out.println(evaluator.getFMeasure());
  }
}
