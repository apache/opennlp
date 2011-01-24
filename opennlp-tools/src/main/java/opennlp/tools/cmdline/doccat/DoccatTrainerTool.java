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

package opennlp.tools.cmdline.doccat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class DoccatTrainerTool implements CmdLineTool {

  public String getName() {
    return "DoccatTrainer";
  }
  
  public String getShortDescription() {
    return "trainer for the learnable document categorizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + BasicTrainingParameters.getParameterUsage() +
        " -data trainingData -model model\n" +
        BasicTrainingParameters.getDescription();
  }
  
  static ObjectStream<DocumentSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new DocumentSampleStream(lineStream);
  }
  
  public void run(String[] args) {
    if (args.length < 8) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    File trainingDataInFile = new File(CmdLineUtil.getParameter("-data", args));
    File modelOutFile = new File(CmdLineUtil.getParameter("-model", args));

    CmdLineUtil.checkOutputFile("document categorizer model", modelOutFile);
    ObjectStream<DocumentSample> sampleStream = 
        openSampleData("Training", trainingDataInFile, parameters.getEncoding());
    
    DoccatModel model;
    try {
      model = DocumentCategorizerME.train(parameters.getLanguage(), sampleStream, 
          parameters.getCutoff(), parameters.getNumberOfIterations());
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
    
    CmdLineUtil.writeModel("document categorizer", modelOutFile, model);
  }
}
