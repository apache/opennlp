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
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.postag.POSDictionary;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.model.ModelType;

public final class POSTaggerTrainerTool implements CmdLineTool {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams{

  }

  public String getName() {
    return "POSTaggerTrainer";
  }
  
  public String getShortDescription() {
    return "trains a model for the part-of-speech tagger";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
      + ArgumentParser.createUsage(TrainerToolParams.class);
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
    if (!ArgumentParser.validateArguments(args, TrainerToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    TrainerToolParams params = ArgumentParser.parse(args,
        TrainerToolParams.class);    
    
    opennlp.tools.util.TrainingParameters mlParams = 
      CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    
    if (mlParams != null && !TrainUtil.isValid(mlParams.getSettings())) {
      System.err.println("Training parameters file is invalid!");
      throw new TerminateToolException(-1);
    }
    
    File trainingDataInFile = params.getData();
    File modelOutFile = params.getModel();
    
    CmdLineUtil.checkOutputFile("pos tagger model", modelOutFile);
    ObjectStream<POSSample> sampleStream = openSampleData("Training", trainingDataInFile, 
        params.getEncoding());
    
    
    Dictionary ngramDict = null;
    
    Integer ngramCutoff = params.getNgram();
    
    if (ngramCutoff != null) {
      System.err.print("Building ngram dictionary ... ");
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
      if (params.getDict() != null) {
        tagdict = POSDictionary.create(new FileInputStream(params.getDict()));
      }
      
      if (mlParams == null) {
        // depending on model and sequence choose training method
        model = opennlp.tools.postag.POSTaggerME.train(params.getLang(),
             sampleStream, getModelType(params.getType()), tagdict, ngramDict, params.getCutoff(), params.getIterations());
      }
      else {
        model = opennlp.tools.postag.POSTaggerME.train(params.getLang(),
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
  
  static ModelType getModelType(String modelString) {
    ModelType model;
    if (modelString == null)
      modelString = "maxent";
    
    if (modelString.equals("maxent")) {
      model = ModelType.MAXENT; 
    }
    else if (modelString.equals("perceptron")) {
      model = ModelType.PERCEPTRON; 
    }
    else if (modelString.equals("perceptron_sequence")) {
      model = ModelType.PERCEPTRON_SEQUENCE; 
    }
    else {
      model = null;
    }
    return model;
  }
}
