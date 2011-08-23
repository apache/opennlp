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

package opennlp.tools.cmdline.postag;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerCrossValidator;
import opennlp.tools.postag.POSTaggerEvaluationMonitor;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

public final class POSTaggerCrossValidatorTool implements CmdLineTool {
  
  interface CVToolParams extends CVParams, TrainingParams {
    
  }

  public String getName() {
    return "POSTaggerCrossValidator";
  }

  public String getShortDescription() {
    return "K-fold cross validator for the learnable POS tagger";
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

    ObjectStream<POSSample> sampleStream = POSTaggerTrainerTool.openSampleData(
        "Training Data", trainingDataInFile, params.getEncoding());

    POSTaggerCrossValidator validator;
    
    POSTaggerEvaluationMonitor missclassifiedListener = null;
    if (params.getMisclassified()) {
      missclassifiedListener = new POSEvaluationErrorListener();
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
      // TODO: Move to util method ...
      POSDictionary tagdict = null;
      if (params.getDict() != null) {
        tagdict = POSDictionary.create(new FileInputStream(params.getDict()));
      }

      validator = new POSTaggerCrossValidator(params.getLang(), mlParams,
          tagdict, params.getNgram(), missclassifiedListener);
      
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

    System.out.println("Accuracy: " + validator.getWordAccuracy());
  }
}
