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

package opennlp.tools.cmdline.sentdetect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.sentdetect.SentenceDetectorEvaluationMonitor;
import opennlp.tools.sentdetect.SentenceDetectorEvaluator;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;

public final class SentenceDetectorEvaluatorTool implements CmdLineTool {

  public String getName() {
    return "SentenceDetectorEvaluator";
  }
  
  public String getShortDescription() {
    return "evaluator for the learnable sentence detector";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(EvaluatorParams.class);
  }

  public void run(String[] args) {
    
    if (!ArgumentParser.validateArguments(args, EvaluatorParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    EvaluatorParams params = ArgumentParser.parse(args, EvaluatorParams.class);
    
    Charset encoding = params.getEncoding();
    
    SentenceModel model = new SentenceModelLoader().load(params.getModel());
    
    File trainingDataInFile = params.getData();
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    SentenceDetectorEvaluationMonitor errorListener = null;
    if (params.getMisclassified()) {
      errorListener = new SentenceEvaluationErrorListener();
    }
    
    SentenceDetectorEvaluator evaluator = new SentenceDetectorEvaluator(
        new SentenceDetectorME(model), errorListener);
    
    System.out.print("Evaluating ... ");
      ObjectStream<SentenceSample> sampleStream = SentenceDetectorTrainerTool.openSampleData("Test",
          trainingDataInFile, encoding);
      
      try {
      evaluator.evaluate(sampleStream);
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
      
      System.err.println("done");
      
      System.out.println();
      
      System.out.println(evaluator.getFMeasure());
  }
}
