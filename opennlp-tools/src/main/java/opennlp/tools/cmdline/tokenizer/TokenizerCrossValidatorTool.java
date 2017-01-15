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

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.tokenizer.TokenizerCrossValidatorTool.CVToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerCrossValidator;
import opennlp.tools.tokenize.TokenizerEvaluationMonitor;
import opennlp.tools.tokenize.TokenizerFactory;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.model.ModelUtil;

public final class TokenizerCrossValidatorTool
    extends AbstractCrossValidatorTool<TokenSample, CVToolParams> {

  interface CVToolParams extends CVParams, TrainingParams {
  }

  public TokenizerCrossValidatorTool() {
    super(TokenSample.class, CVToolParams.class);
  }

  public String getShortDescription() {
    return "K-fold cross validator for the learnable tokenizer";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    TokenizerCrossValidator validator;

    TokenizerEvaluationMonitor listener = null;
    if (params.getMisclassified()) {
      listener = new TokenEvaluationErrorListener();
    }

    try {
      Dictionary dict = TokenizerTrainerTool.loadDict(params.getAbbDict());

      TokenizerFactory tokFactory = TokenizerFactory.create(
          params.getFactory(), params.getLang(), dict,
          params.getAlphaNumOpt(), null);
      validator = new opennlp.tools.tokenize.TokenizerCrossValidator(mlParams,
          tokFactory, listener);

      validator.evaluate(sampleStream, params.getFolds());
    }
    catch (IOException e) {
      throw createTerminationIOException(e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    FMeasure result = validator.getFMeasure();

    System.out.println(result.toString());
  }
}
