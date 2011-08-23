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
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenizerEvaluationMonitor;
import opennlp.tools.tokenize.TokenizerEvaluator;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;

public final class TokenizerMEEvaluatorTool implements CmdLineTool {

  public String getName() {
    return "TokenizerMEEvaluator";
  }
  
  public String getShortDescription() {
    return "evaluator for the learnable tokenizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(EvaluatorParams.class);
  }

  public void run(String[] args) {
    if (!ArgumentParser
        .validateArguments(args, EvaluatorParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }

    EvaluatorParams params = ArgumentParser.parse(args,
        EvaluatorParams.class);

    Charset encoding = params.getEncoding();

    TokenizerModel model = new TokenizerModelLoader().load(params.getModel());

    TokenizerEvaluationMonitor missclassifiedListener = null;
    if (params.getMisclassified()) {
      missclassifiedListener = new TokenEvaluationErrorListener();
    }

    TokenizerEvaluator evaluator = new TokenizerEvaluator(
        new opennlp.tools.tokenize.TokenizerME(model), missclassifiedListener);

    System.out.print("Evaluating ... ");
    
    File testData = params.getData();
    CmdLineUtil.checkInputFile("Test data", testData);

    ObjectStream<TokenSample> sampleStream = TokenizerTrainerTool
        .openSampleData("Test", testData, encoding);

    try {
      evaluator.evaluate(sampleStream);
    } catch (IOException e) {
      System.err.println("failed");
      System.err.println("Reading test data error " + e.getMessage());
      throw new TerminateToolException(-1);
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
