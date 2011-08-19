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


package opennlp.tools.cmdline.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import opennlp.model.TrainUtil;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.TrainingToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParseSampleStream;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.ParserType;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public final class ParserTrainerTool implements CmdLineTool {
  
  interface TrainerToolParams extends TrainingParams, TrainingToolParams{

  }

  public String getName() {
    return "ParserTrainer";
  }
  
  public String getShortDescription() {
    return "trains the learnable parser";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
      + ArgumentParser.createUsage(TrainerToolParams.class);
  }

  static ObjectStream<Parse> openTrainingData(File trainingDataFile, Charset encoding) {
    
    CmdLineUtil.checkInputFile("Training data", trainingDataFile);

    System.err.print("Opening training data ... ");
    
    FileInputStream trainingDataIn;
    try {
      trainingDataIn = new FileInputStream(trainingDataFile);
    } catch (FileNotFoundException e) {
      System.err.println("failed");
      System.err.println("File not found: " + e.getMessage());
      throw new TerminateToolException(-1);
    }
    
    System.err.println("done");
    
    return new ParseSampleStream(
        new PlainTextByLineStream(trainingDataIn.getChannel(),
        encoding));
  }
  
  static Dictionary buildDictionary(ObjectStream<Parse> parseSamples, HeadRules headRules, int cutoff) {
    System.err.print("Building dictionary ...");
    
    Dictionary mdict;
    try {
      mdict = Parser.
          buildDictionary(parseSamples, headRules, cutoff);
    } catch (IOException e) {
      System.err.println("Error while building dictionary: " + e.getMessage());
      mdict = null;
    }
    System.err.println("done");
    
    return mdict;
  }
  
  static ParserType parseParserType(String typeAsString) {
    ParserType type = null;
    if(typeAsString != null && typeAsString.length() > 0) {
      type = ParserType.parse(typeAsString);
      if(type == null) {
        System.err.println("ParserType training parameter is invalid!");
        throw new TerminateToolException(-1);
      }
    }
    
    return type;
  }
  
  // TODO: Add param to train tree insert parser
  public void run(String[] args) {
    
    if (!ArgumentParser.validateArguments(args, TrainerToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    TrainerToolParams params = ArgumentParser.parse(args,
        TrainerToolParams.class); 
    
    opennlp.tools.util.TrainingParameters mlParams = 
      CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    
    if (mlParams != null) {
      if (!TrainUtil.isValid(mlParams.getSettings("build"))) {
        System.err.println("Build training parameters are invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (!TrainUtil.isValid(mlParams.getSettings("check"))) {
        System.err.println("Check training parameters are invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (!TrainUtil.isValid(mlParams.getSettings("attach"))) {
        System.err.println("Attach training parameters are invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (!TrainUtil.isValid(mlParams.getSettings("tagger"))) {
        System.err.println("Tagger training parameters are invalid!");
        throw new TerminateToolException(-1);
      }
      
      if (!TrainUtil.isValid(mlParams.getSettings("chunker"))) {
        System.err.println("Chunker training parameters are invalid!");
        throw new TerminateToolException(-1);
      }
    }
    
    ObjectStream<Parse> sampleStream = openTrainingData(params.getData(), params.getEncoding());
    
    File modelOutFile = params.getModel();
    CmdLineUtil.checkOutputFile("parser model", modelOutFile);
    
    ParserModel model;
    try {
      
      HeadRules rules = new opennlp.tools.parser.lang.en.HeadRules(
          new InputStreamReader(new FileInputStream(params.getHeadRules()),
              params.getEncoding()));
      
      ParserType type = parseParserType(params.getParserType());
      
      if (mlParams == null) {
        if (ParserType.CHUNKING.equals(type)) {
          model = opennlp.tools.parser.chunking.Parser.train(
              params.getLang(), sampleStream, rules, 
              params.getIterations(), params.getCutoff());
        }
        else if (ParserType.TREEINSERT.equals(type)) {
          model = opennlp.tools.parser.treeinsert.Parser.train(params.getLang(), sampleStream, rules, params.getIterations(), 
              params.getCutoff());
        }
        else {
          throw new IllegalStateException();
        }
      }
      else {
        if (ParserType.CHUNKING.equals(type)) {
          model = opennlp.tools.parser.chunking.Parser.train(
              params.getLang(), sampleStream, rules, 
              mlParams);
        }
        else if (ParserType.TREEINSERT.equals(type)) {
          model = opennlp.tools.parser.treeinsert.Parser.train(params.getLang(), sampleStream, rules,
              mlParams);
        }
        else {
          throw new IllegalStateException();
        }

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
    
    CmdLineUtil.writeModel("parser", modelOutFile, model);
  }
}
