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

package opennlp.tools.cmdline.chunker;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkerCrossValidator;
import opennlp.tools.chunker.ChunkerEvaluationMonitor;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.eval.EvaluationMonitor;
import opennlp.tools.util.eval.FMeasure;

public final class ChunkerCrossValidatorTool implements CmdLineTool {
  
  interface CVToolParams extends TrainingParams, CVParams, DetailedFMeasureEvaluatorParams {
    
  }

  public String getName() {
    return "ChunkerCrossValidator";
  }
  
  public String getShortDescription() {
    return "K-fold cross validator for the chunker";
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
    
    CVToolParams params = ArgumentParser.parse(args,
        CVToolParams.class);
    
    opennlp.tools.util.TrainingParameters mlParams = CmdLineUtil
        .loadTrainingParameters(params.getParams(), false);
    
    File trainingDataInFile = params.getData();
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    ObjectStream<ChunkSample> sampleStream =
        ChunkerTrainerTool.openSampleData("Training Data",
        trainingDataInFile, params.getEncoding());
    
    List<EvaluationMonitor<ChunkSample>> listeners = new LinkedList<EvaluationMonitor<ChunkSample>>();
    ChunkerDetailedFMeasureListener detailedFMeasureListener = null;
    if (params.getMisclassified()) {
      listeners.add(new ChunkEvaluationErrorListener());
    }
    if (params.getDetailedF()) {
      detailedFMeasureListener = new ChunkerDetailedFMeasureListener();
      listeners.add(detailedFMeasureListener);
    }
    
    if (mlParams == null) {
      mlParams = new TrainingParameters();
      mlParams.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
      mlParams.put(TrainingParameters.ITERATIONS_PARAM,
          Integer.toString(params.getIterations()));
      mlParams.put(TrainingParameters.CUTOFF_PARAM,
          Integer.toString(params.getCutoff()));
    }

    ChunkerCrossValidator validator = new ChunkerCrossValidator(
        params.getLang(), mlParams,
        listeners.toArray(new ChunkerEvaluationMonitor[listeners.size()]));
      
    try {
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
    
    if (detailedFMeasureListener == null) {
      FMeasure result = validator.getFMeasure();
      System.out.println(result.toString());
    } else {
      System.out.println(detailedFMeasureListener.toString());
    }
  }
}
