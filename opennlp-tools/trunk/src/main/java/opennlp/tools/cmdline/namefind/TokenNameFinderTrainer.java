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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;

import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.PlainTextByLineStream;

public class TokenNameFinderTrainer implements CmdLineTool {

  public String getName() {
    return "TokenNameFinderTrainer";
  }
  
  public String getShortDescription() {
    return "";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + 
        BasicTrainingParameters.getParameterUsage() + " trainingData model";
  }

  static ObjectStream<NameSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new NameSampleDataStream(lineStream);
  }
  
  public void run(String[] args) {
    
    if (args.length < 6) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    File trainingDataInFile = new File(args[args.length - 2]);
    File modelOutFile = new File(args[args.length - 1]);

    CmdLineUtil.checkOutputFile("name finder", modelOutFile);
    ObjectStream<NameSample> sampleStream = openSampleData("Training", trainingDataInFile,
        parameters.getEncoding());

    TokenNameFinderModel model;
    try {
      model = opennlp.tools.namefind.NameFinderME.train(parameters.getLanguage(),
           sampleStream, parameters.getNumberOfIterations(), parameters.getCutoff(), 
           Collections.<String, Object>emptyMap());
    } 
    catch (IOException e) {
      e.printStackTrace();
      System.exit(-1);
      model = null;
    }
    catch (InvalidFormatException e) {
      System.err.println("A resource is invalid: " + e.getMessage());
      model = null;
    }
    finally {
      try {
        sampleStream.close();
      } catch (ObjectStreamException e) {
        // sorry that this can fail
      }
    }
    
    CmdLineUtil.writeModel("name finder", modelOutFile, model);
  }
}
