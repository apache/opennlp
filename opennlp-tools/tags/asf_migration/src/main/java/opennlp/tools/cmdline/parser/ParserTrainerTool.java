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

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
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

  public String getName() {
    return "ParserTrainer";
  }
  
  public String getShortDescription() {
    return "trains the learnable parser";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() + 
        " -head-rules head_rules -data trainingData -model model\n" + TrainingParameters.getDescription();
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
  
  // TODO: Add param to train tree insert parser
  public void run(String[] args) {
    
    if (args.length < 10) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }

    TrainingParameters parameters = new TrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    } 
    
    ObjectStream<Parse> sampleStream = openTrainingData(new File(CmdLineUtil.getParameter("-data", args)), parameters.getEncoding());
    
    File modelOutFile = new File(CmdLineUtil.getParameter("-model", args));
    CmdLineUtil.checkOutputFile("parser model", modelOutFile);
    
    ParserModel model;
    try {
      
      HeadRules rules = new opennlp.tools.parser.lang.en.HeadRules(
          new InputStreamReader(new FileInputStream(new File(CmdLineUtil.getParameter("-head-rules", args))), 
          parameters.getEncoding()));
      
      if (ParserType.CHUNKING.equals(parameters.getParserType())) {
        model = opennlp.tools.parser.chunking.Parser.train(
            parameters.getLanguage(), sampleStream, rules, 
            parameters.getNumberOfIterations(), parameters.getCutoff());
      }
      else if (ParserType.TREEINSERT.equals(parameters.getParserType())) {
        model = opennlp.tools.parser.treeinsert.Parser.train(parameters.getLanguage(), sampleStream, rules, parameters.getNumberOfIterations(), 
            parameters.getCutoff());
      }
      else {
        throw new IllegalStateException();
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
