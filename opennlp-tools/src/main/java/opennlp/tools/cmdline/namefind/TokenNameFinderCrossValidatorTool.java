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

package opennlp.tools.cmdline.namefind;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderCrossValidator;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.EvaluationMonitor;

public final class TokenNameFinderCrossValidatorTool implements CmdLineTool {
  
  interface CVToolParams extends TrainingParams, CVParams, DetailedFMeasureEvaluatorParams{
    
  }

  public String getName() {
    return "TokenNameFinderCrossValidator";
  }

  public String getShortDescription() {
    return "K-fold cross validator for the learnable Name Finder";
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
        .loadTrainingParameters(params.getParams(),false);

    byte featureGeneratorBytes[] = TokenNameFinderTrainerTool
        .openFeatureGeneratorBytes(params.getFeaturegen());

    Map<String, Object> resources = TokenNameFinderTrainerTool
        .loadResources(params.getResources());

    File trainingDataInFile = params.getData();
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    Charset encoding = params.getEncoding();

    ObjectStream<NameSample> sampleStream = TokenNameFinderTrainerTool
        .openSampleData("Training Data", trainingDataInFile, encoding);

    TokenNameFinderCrossValidator validator;
    
    List<EvaluationMonitor<NameSample>> listeners = new LinkedList<EvaluationMonitor<NameSample>>();
    if (params.getMisclassified()) {
      listeners.add(new NameEvaluationErrorListener());
    }
    TokenNameFinderDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new TokenNameFinderDetailedFMeasureListener();
      listeners.add(detailedFListener);
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
      validator = new TokenNameFinderCrossValidator(params.getLang(),
          params.getType(), mlParams, featureGeneratorBytes, resources, listeners.toArray(new TokenNameFinderEvaluationMonitor[listeners.size()]));
      validator.evaluate(sampleStream, params.getFolds());
    } catch (IOException e) {
      CmdLineUtil.printTrainingIoError(e);
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
    
    if(detailedFListener == null) {
      System.out.println(validator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }
}
