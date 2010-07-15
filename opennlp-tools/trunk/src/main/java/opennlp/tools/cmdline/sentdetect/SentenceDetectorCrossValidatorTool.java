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

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.sentdetect.SDCrossValidator;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.eval.FMeasure;

public final class SentenceDetectorCrossValidatorTool implements CmdLineTool {

  public String getName() {
    return "SentenceDetectorCrossValidator";
  }
  
  public String getShortDescription() {
    return "10-fold cross validator for the learnable sentence detector";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() +
        " trainData\n" +
        TrainingParameters.getDescription();
  }

  public void run(String[] args) {
    if (args.length < 5) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    TrainingParameters parameters = new TrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    File trainingDataInFile = new File(args[args.length - 1]);
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    ObjectStream<SentenceSample> sampleStream = SentenceDetectorTrainerTool.openSampleData("Training Data",
        trainingDataInFile, parameters.getEncoding());
    
    SDCrossValidator validator = new SDCrossValidator(parameters.getLanguage(), parameters.getCutoff(), parameters.getNumberOfIterations());
    
    try {
      validator.evaluate(sampleStream, 10);
    }
    catch (ObjectStreamException e) {
      CmdLineUtil.printTrainingIoError(e);
      throw new TerminateToolException(-1);
    }
    finally {
      try {
        sampleStream.close();
      } catch (ObjectStreamException e) {
        // sorry that this can fail
      }
    }
    
    FMeasure result = validator.getFMeasure();
    
    System.out.println(result.toString());
  }
}
