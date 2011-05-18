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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.model.TrainUtil;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public final class SentenceDetectorTrainerTool implements CmdLineTool {

  public String getName() {
    return "SentenceDetectorTrainer";
  }
  
  public String getShortDescription() {
    return "trainer for the learnable sentence detector";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() +
        " -data trainingData -model model\n" +
        TrainingParameters.getDescription();
  }

  static ObjectStream<SentenceSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new SentenceSampleStream(lineStream);
  }
  
  public void run(String[] args) {
    if (args.length < 8) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    TrainingParameters parameters = new TrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }

    opennlp.tools.util.TrainingParameters mlParams = 
      CmdLineUtil.loadTrainingParameters(CmdLineUtil.getParameter("-params", args));
    
    if (mlParams != null) {
      if (!TrainUtil.isValid(mlParams.getSettings())) {
        System.err.println("Training parameters file is invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (TrainUtil.isSequenceTraining(mlParams.getSettings())) {
        System.err.println("Sequence training is not supported!");
        throw new TerminateToolException(-1);
      }
    }
    
    File trainingDataInFile = new File(CmdLineUtil.getParameter("-data", args));
    File modelOutFile = new File(CmdLineUtil.getParameter("-model", args));

    CmdLineUtil.checkOutputFile("sentence detector model", modelOutFile);
    ObjectStream<SentenceSample> sampleStream = 
        openSampleData("Training", trainingDataInFile, parameters.getEncoding());

    SentenceModel model;
    try {
      if (mlParams == null) {
        model = SentenceDetectorME.train(parameters.getLanguage(), sampleStream, true, null, 
            parameters.getCutoff(), parameters.getNumberOfIterations());
      }
      else {
        model = SentenceDetectorME.train(parameters.getLanguage(), sampleStream, true, null, 
            mlParams);
      }
    } catch (IOException e) {
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
    
    CmdLineUtil.writeModel("sentence detector", modelOutFile, model);
  }
}
