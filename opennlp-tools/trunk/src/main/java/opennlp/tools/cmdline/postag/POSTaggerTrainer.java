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

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.PlainTextByLineStream;

public class POSTaggerTrainer implements CmdLineTool {

  public String getName() {
    return "POSTaggerTrainer";
  }
  
  public String getShortDescription() {
    return "";
  }
  
  public String getHelp() {
    // TODO: specify all parameters
    return "Usage: " + CLI.CMD + " " + getName() + "[-dict dict] trainingData model ";
  }

  static ObjectStream<POSSample> openSampleData(String sampleDataName,
      File sampleDataFile, String encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new WordTagSampleStream(lineStream);
  }
  
  public void run(String[] args) {
    if (args.length < 6) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    TrainingParameters parameters = new TrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      System.exit(1);
    }    
    
    File trainingDataInFile = new File(args[args.length - 2]);
    
    ObjectStream<POSSample> sampleStream = openSampleData("Training", trainingDataInFile, 
        parameters.getEncoding());
    
    POSModel model;
    try {
      
      // TODO: Move to util method ...
      POSDictionary tagdict = null;
      if (parameters.getDictionaryPath() != null) {
        tagdict = new POSDictionary(parameters.getDictionaryPath());
      }
      
      // depending on model and sequence choose training method
      model = opennlp.tools.postag.POSTaggerME.train(parameters.getLanguage(),
           sampleStream, parameters.getModel(), tagdict, null, parameters.getCutoff(), parameters.getNumberOfIterations());
    }
    catch (IOException e) {
      System.err.println("Training io error: " + e.getMessage());
      System.exit(-1);
      model = null;
    }
    catch (ObjectStreamException e) {
      System.err.println("Training io error: " + e.getMessage());
      System.exit(-1);
      model = null;
    }
    finally {
      try {
        sampleStream.close();
      } catch (ObjectStreamException e) {
        // sorry that this can fail
      }
    }
    
    CmdLineUtil.writeModel("pos tagger", new File(args[args.length - 1]), model);
  }
}
