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
import java.nio.charset.Charset;

import opennlp.model.TrainUtil;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public final class POSTaggerTrainerTool implements CmdLineTool {

  public String getName() {
    return "POSTaggerTrainer";
  }
  
  public String getShortDescription() {
    return "trains a model for the part-of-speech tagger";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() 
        + " -data trainingData -model model\n" +
        TrainingParameters.getDescription();
  }

  static ObjectStream<POSSample> openSampleData(String sampleDataName,
      File sampleDataFile, Charset encoding) {
    CmdLineUtil.checkInputFile(sampleDataName + " Data", sampleDataFile);

    FileInputStream sampleDataIn = CmdLineUtil.openInFile(sampleDataFile);

    ObjectStream<String> lineStream = new PlainTextByLineStream(sampleDataIn
        .getChannel(), encoding);

    return new WordTagSampleStream(lineStream);
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
      CmdLineUtil.loadTrainingParameters(CmdLineUtil.getParameter("-params", args), true);
    
    if (mlParams != null && !TrainUtil.isValid(mlParams.getSettings())) {
      System.err.println("Training parameters file is invalid!");
      throw new TerminateToolException(-1);
    }
    
    File trainingDataInFile = new File(CmdLineUtil.getParameter("-data", args));
    File modelOutFile = new File(CmdLineUtil.getParameter("-model", args));
    
    CmdLineUtil.checkOutputFile("pos tagger model", modelOutFile);
    ObjectStream<POSSample> sampleStream = openSampleData("Training", trainingDataInFile, 
        parameters.getEncoding());
    
    
    Dictionary ngramDict = null;
    
    String ngramCutoffString = CmdLineUtil.getParameter("-ngram", args);
    
    if (ngramCutoffString != null) {
      System.err.print("Building ngram dictionary ... ");
      int ngramCutoff = Integer.parseInt(ngramCutoffString);
      try {
        ngramDict = POSTaggerME.buildNGramDictionary(sampleStream, ngramCutoff);
        sampleStream.reset();
      } catch (IOException e) {
        CmdLineUtil.printTrainingIoError(e);
        throw new TerminateToolException(-1);
      }
      System.err.println("done");
    }
    
    POSModel model;
    try {
      
      // TODO: Move to util method ...
      POSDictionary tagdict = null;
      if (parameters.getDictionaryPath() != null) {
        // TODO: Should re-factored as described in OPENNLP-193
        tagdict = new POSDictionary(parameters.getDictionaryPath());
      }
      
      if (mlParams == null) {
        // depending on model and sequence choose training method
        model = opennlp.tools.postag.POSTaggerME.train(parameters.getLanguage(),
             sampleStream, parameters.getModel(), tagdict, ngramDict, parameters.getCutoff(), parameters.getNumberOfIterations());
      }
      else {
        model = opennlp.tools.postag.POSTaggerME.train(parameters.getLanguage(),
            sampleStream, mlParams, tagdict, ngramDict);
      }
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
    
    CmdLineUtil.writeModel("pos tagger", modelOutFile, model);
  }
}
