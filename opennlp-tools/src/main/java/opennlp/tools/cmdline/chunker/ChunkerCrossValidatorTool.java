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

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkerCrossValidator;
import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.parser.TrainingParameters;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.FMeasure;

public final class ChunkerCrossValidatorTool implements CmdLineTool {

  public String getName() {
    return "ChunkerCrossValidator";
  }
  
  public String getShortDescription() {
    return "10-fold cross validator for the chunker";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() + "\n"+
        BasicTrainingParameters.getDescription() + "\n"+
        "-data trainingData      training data used for cross validation";
  }

  public void run(String[] args) {
    if (args.length < 6) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    File trainingDataInFile = new File(CmdLineUtil.getParameter("-data", args));
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    ObjectStream<ChunkSample> sampleStream =
        ChunkerTrainerTool.openSampleData("Training Data",
        trainingDataInFile, parameters.getEncoding());
    
    ChunkerCrossValidator validator =
        new ChunkerCrossValidator(
        		parameters.getLanguage(), parameters.getCutoff(), parameters.getNumberOfIterations());
      
    try {
      validator.evaluate(sampleStream, 10);
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
