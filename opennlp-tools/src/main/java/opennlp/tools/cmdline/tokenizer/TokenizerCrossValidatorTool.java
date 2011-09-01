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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerCrossValidator;
import opennlp.tools.tokenize.TokenizerEvaluationMonitor;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.FMeasure;

public final class TokenizerCrossValidatorTool implements CmdLineTool {
  
  interface CVToolParams extends CVParams, TrainingParams {
    
  }

  public String getName() {
    return "TokenizerCrossValidator";
  }
  
  public String getShortDescription() {
    return "K-fold cross validator for the learnable tokenizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(CVToolParams.class);
  }

  public void run(String[] args) {
    if (!ArgumentParser.validateArguments(args, CVToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    CVToolParams params = ArgumentParser.parse(args, CVToolParams.class);
    
    opennlp.tools.util.TrainingParameters mlParams = CmdLineUtil
        .loadTrainingParameters(params.getParams(), false);
    
    File trainingDataInFile = params.getData();
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    Charset encoding = params.getEncoding(); 
    
    ObjectStream<TokenSample> sampleStream =
        TokenizerTrainerTool.openSampleData("Training Data",
        trainingDataInFile, encoding);
    
    
    TokenizerCrossValidator validator;
    
    if (mlParams == null)
      mlParams = TokenizerTrainerTool.createTrainingParameters(
          params.getIterations(), params.getCutoff());
    
    TokenizerEvaluationMonitor listener = null;
    if (params.getMisclassified()) {
      listener = new TokenEvaluationErrorListener();
    }
    
    try {
      Dictionary dict = TokenizerTrainerTool.loadDict(params.getAbbDict());

      validator = new opennlp.tools.tokenize.TokenizerCrossValidator(
          params.getLang(), dict, params.getAlphaNumOpt(), mlParams, listener);

      validator.evaluate(sampleStream, params.getFolds());
    }
    catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
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
