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
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.SDCrossValidator;
import opennlp.tools.sentdetect.SentenceDetectorEvaluationMonitor;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.FMeasure;

public final class SentenceDetectorCrossValidatorTool implements CmdLineTool {
  
  interface CVToolParams extends TrainingParams, CVParams {
    
  }

  public String getName() {
    return "SentenceDetectorCrossValidator";
  }
  
  public String getShortDescription() {
    return "K-fold cross validator for the learnable sentence detector";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(CVToolParams.class);
  }

  public void run(String[] args) {
    
    if (!ArgumentParser.validateArguments(args, CVToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    CVToolParams params = ArgumentParser.parse(args, CVToolParams.class);
    
    opennlp.tools.util.TrainingParameters mlParams = 
      CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    
    File trainingDataInFile = params.getData();
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    Charset encoding = params.getEncoding();
    
    ObjectStream<SentenceSample> sampleStream = SentenceDetectorTrainerTool.openSampleData("Training Data",
        trainingDataInFile, encoding);
    
    SDCrossValidator validator;
    
    SentenceDetectorEvaluationMonitor errorListener = null;
    if (params.getMisclassified()) {
      errorListener = new SentenceEvaluationErrorListener();
    }
    
    if (mlParams == null) {
      mlParams = new TrainingParameters();
      mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
      mlParams.put(TrainingParameters.ITERATIONS_PARAM,
          Integer.toString(params.getIterations()));
      mlParams.put(TrainingParameters.CUTOFF_PARAM,
          Integer.toString(params.getCutoff()));
    }

    try {
      Dictionary abbreviations = SentenceDetectorTrainerTool.loadDict(params
          .getAbbDict());
      validator = new SDCrossValidator(params.getLang(), mlParams,
          abbreviations, errorListener);
      
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
